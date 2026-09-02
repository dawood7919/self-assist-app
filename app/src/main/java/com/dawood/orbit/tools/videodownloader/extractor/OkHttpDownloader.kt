package com.dawood.orbit.tools.videodownloader.extractor

import com.dawood.orbit.tools.videodownloader.resolve.HttpClients
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request as ExtractorRequest
import org.schabi.newpipe.extractor.downloader.Response as ExtractorResponse
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException

/**
 * The HTTP bridge NewPipeExtractor needs.
 *
 * The library deliberately ships no networking of its own: it hands the host
 * app a request and expects a response back, so it can be used with whatever
 * client the app already has. Reusing the downloader's own OkHttp instance
 * means one connection pool, one user agent, and identical behaviour between
 * "find the stream" and "fetch the stream" — a host that accepts one accepts
 * the other.
 */
internal class OkHttpDownloader(
    private val client: OkHttpClient = HttpClients.shared,
) : Downloader() {

    override fun execute(request: ExtractorRequest): ExtractorResponse {
        val url = request.url()
        val builder = Request.Builder().url(url)

        request.headers().forEach { (name, values) ->
            builder.removeHeader(name)
            values.forEach { value -> builder.addHeader(name, value) }
        }
        if (request.headers()["User-Agent"].isNullOrEmpty()) {
            builder.header("User-Agent", HttpClients.USER_AGENT)
        }

        val body = request.dataToSend()?.toRequestBody()
        builder.method(request.httpMethod(), body)

        client.newCall(builder.build()).execute().use { response ->
            // The library treats 429 specially: it means the site wants a human
            // to solve a challenge, which is a different problem from a
            // transport failure and is reported as such to the user.
            if (response.code == 429) {
                throw ReCaptchaException("The site asked for a captcha.", url)
            }

            return ExtractorResponse(
                response.code,
                response.message,
                response.headers.toMultimap(),
                response.body?.string(),
                response.request.url.toString(),
            )
        }
    }
}
