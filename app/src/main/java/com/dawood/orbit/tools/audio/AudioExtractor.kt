package com.dawood.orbit.tools.audio

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import com.dawood.orbit.core.files.DocumentStore
import com.dawood.orbit.core.files.FileFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import kotlin.coroutines.coroutineContext

/**
 * Lifts the audio track out of a video without re-encoding it.
 *
 * Samples are copied from the container straight into a new one, so the export
 * is bit-for-bit the audio that was already in the file: no quality is lost and
 * a long video takes seconds rather than minutes. The cost is that the output
 * format is whatever the video carried — usually AAC in an .m4a — rather than
 * anything the user picks.
 */
object AudioExtractor {

    sealed interface Result {
        data class Success(
            val file: File,
            val durationMs: Long,
            val bytes: Long,
            val codec: String,
        ) : Result

        data class Failure(val message: String) : Result
    }

    /** What a chosen video contains. */
    data class Source(
        val file: File,
        val displayName: String,
        val durationMs: Long,
        val bytes: Long,
        val audioCodec: String?,
        val hasAudio: Boolean,
    ) {
        val durationLabel: String get() = formatDuration(durationMs)
    }

    private const val MAX_SAMPLE_BYTES = 1 shl 20

    suspend fun inspect(context: Context, uri: Uri): Source? = withContext(Dispatchers.IO) {
        val name = DocumentStore.displayName(context, uri) ?: "video"
        val copied = DocumentStore.copyIn(context, uri, fallbackName = name) ?: return@withContext null

        // MediaMetadataRetriever only became AutoCloseable at API 29, and the
        // app supports 26, so it is released by hand.
        val duration = runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(copied.absolutePath)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            } finally {
                retriever.release()
            }
        }.getOrDefault(0L)

        val codec = runCatching {
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(copied.absolutePath)
                (0 until extractor.trackCount)
                    .map { extractor.getTrackFormat(it) }
                    .firstOrNull { it.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
                    ?.getString(MediaFormat.KEY_MIME)
            } finally {
                extractor.release()
            }
        }.getOrNull()

        Source(
            file = copied,
            displayName = name,
            durationMs = duration,
            bytes = copied.length(),
            audioCodec = codec,
            hasAudio = codec != null,
        )
    }

    /**
     * Copies the first audio track of [source] into its own file.
     *
     * [onProgress] reports how far through the timeline the copy is, which is
     * the only progress a sample copy can honestly report.
     */
    suspend fun extract(
        context: Context,
        source: Source,
        outputName: String,
        onProgress: (Float) -> Unit = {},
    ): Result = withContext(Dispatchers.IO) {
        if (!source.hasAudio) {
            return@withContext Result.Failure("${source.displayName} has no audio track")
        }

        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        val target = DocumentStore.reserve(context, ensureAudioExtension(outputName))

        try {
            extractor.setDataSource(source.file.absolutePath)

            val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return@withContext Result.Failure("No audio track in ${source.displayName}")

            val format = extractor.getTrackFormat(trackIndex)
            extractor.selectTrack(trackIndex)

            muxer = MediaMuxer(target.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val outputTrack = muxer.addTrack(format)
            muxer.start()

            // Some encoders do not report a buffer size; a megabyte is larger
            // than any single AAC frame, so it is a safe floor.
            val bufferSize = runCatching { format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) }
                .getOrDefault(MAX_SAMPLE_BYTES)
                .coerceAtLeast(64 * 1024)
            val buffer = ByteBuffer.allocate(bufferSize)
            val info = android.media.MediaCodec.BufferInfo()
            val durationUs = source.durationMs * 1000

            while (true) {
                coroutineContext.ensureActive()
                val read = extractor.readSampleData(buffer, 0)
                if (read < 0) break

                info.offset = 0
                info.size = read
                info.presentationTimeUs = extractor.sampleTime
                info.flags = extractor.sampleFlags

                muxer.writeSampleData(outputTrack, buffer, info)
                if (durationUs > 0) {
                    onProgress((info.presentationTimeUs.toFloat() / durationUs).coerceIn(0f, 1f))
                }
                extractor.advance()
            }
            onProgress(1f)

            muxer.stop()

            Result.Success(
                file = target,
                durationMs = source.durationMs,
                bytes = target.length(),
                codec = source.audioCodec ?: "audio",
            )
        } catch (error: Throwable) {
            target.delete()
            Result.Failure(error.message ?: "That video's audio could not be copied out")
        } finally {
            runCatching { muxer?.release() }
            runCatching { extractor.release() }
        }
    }

    /** "1:04:09" or "4:09". */
    fun formatDuration(millis: Long): String {
        if (millis <= 0) return "—"
        val totalSeconds = millis / 1000
        val seconds = totalSeconds % 60
        val minutes = (totalSeconds / 60) % 60
        val hours = totalSeconds / 3600
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%d:%02d", minutes, seconds)
        }
    }

    /**
     * The muxer writes an MP4 container whatever the codec inside, so the
     * extension is .m4a — calling it .mp3 would be a lie a player would catch.
     */
    private fun ensureAudioExtension(name: String): String {
        val cleaned = FileFormat.sanitise(name, fallback = "audio")
        return if (FileFormat.extension(cleaned) == "m4a") cleaned else "${FileFormat.baseName(cleaned)}.m4a"
    }
}
