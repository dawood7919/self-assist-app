package com.dawood.orbit.tools.videodownloader.engine

import android.content.Context
import com.dawood.orbit.tools.videodownloader.data.DownloadSettings
import com.dawood.orbit.tools.videodownloader.model.DownloadItem
import java.io.File

internal object DownloadFinish {
    fun export(context: Context, item: DownloadItem, partFile: File): String? {
        val settings = DownloadSettings.get(context)
        val subfolder = settings.relativePathFor(item.playlistTitle)
        return MediaExporter.export(
            context = context,
            partFile = partFile,
            fileName = item.fileName,
            mimeType = item.mimeType,
            relativeSubfolder = subfolder,
        )
    }
}
