package com.dawood.orbit.tools.videodownloader.model

import com.dawood.orbit.tools.videodownloader.resolve.ResolvedMedia
import kotlin.math.abs

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
                    it.mimeType.startsWith("video", ignoreCase = true) && !it.videoOnly
                } ?: candidates.firstOrNull {
                    it.mimeType.startsWith("video", ignoreCase = true)
                } ?: candidates.first()

                else -> {
                    val target = preference.targetHeight ?: return candidates.first()
                    val video = candidates.filter {
                        it.mimeType.startsWith("video", ignoreCase = true)
                    }
                    if (video.isEmpty()) return candidates.first()
                    video.minByOrNull { candidate ->
                        val h = heightOf(candidate.qualityLabel) ?: 0
                        val diff = abs(h - target)
                        val onlyPenalty = if (candidate.videoOnly) 25 else 0
                        (if (h > target) diff + 50 else diff) + onlyPenalty
                    }
                }
            }
        }

        fun heightOf(label: String?): Int? {
            if (label.isNullOrBlank()) return null
            return label.takeWhile { it.isDigit() }.toIntOrNull()?.takeIf { it in 144..4320 }
        }
    }
}
