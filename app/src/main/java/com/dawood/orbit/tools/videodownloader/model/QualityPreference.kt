package com.dawood.orbit.tools.videodownloader.model

import com.dawood.orbit.tools.videodownloader.resolve.ResolvedMedia
import kotlin.math.abs

/**
 * How to pick a stream when several candidates exist (typical for playlists).
 *
 * Applied to every selected playlist entry so the user does not have to open
 * each video just to choose 720p again.
 */
enum class QualityPreference(val label: String, val targetHeight: Int?) {
    Best("Best", null),
    P1080("1080p", 1080),
    P720("720p", 720),
    P480("480p", 480),
    P360("360p", 360),
    AudioOnly("Audio", null),
    ;

    companion object {
        fun pick(candidates: List<ResolvedMedia>, preference: QualityPreference): ResolvedMedia? {
            if (candidates.isEmpty()) return null
            return when (preference) {
                AudioOnly -> candidates.firstOrNull {
                    it.mimeType.startsWith("audio", ignoreCase = true) ||
                        it.qualityLabel?.contains("kbps", ignoreCase = true) == true ||
                        it.title.contains("audio only", ignoreCase = true)
                } ?: candidates.lastOrNull()

                Best -> candidates.firstOrNull {
                    it.mimeType.startsWith("video", ignoreCase = true)
                } ?: candidates.first()

                else -> {
                    val target = preference.targetHeight ?: return candidates.first()
                    val video = candidates.filter {
                        it.mimeType.startsWith("video", ignoreCase = true)
                    }
                    if (video.isEmpty()) return candidates.first()
                    // Prefer exact match, else nearest height without going much lower first.
                    video.minByOrNull { candidate ->
                        val h = heightOf(candidate.qualityLabel) ?: 0
                        val diff = abs(h - target)
                        // Slight penalty for going above target so 720 prefers 720 over 1080 when both exist.
                        if (h > target) diff + 50 else diff
                    }
                }
            }
        }

        /** Parses "1080p", "1080p60", "720" into a height. */
        fun heightOf(label: String?): Int? {
            if (label.isNullOrBlank()) return null
            return label.takeWhile { it.isDigit() }.toIntOrNull()?.takeIf { it in 144..4320 }
        }
    }
}
