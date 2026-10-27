package com.example.data.local

import android.content.Context
import com.example.domain.model.DownloadFolderConfig
import com.example.domain.model.DownloadFolderMode
import com.example.domain.model.ExtensionFilterConfig

/** User choices only. Live jobs, selection, sheet state, and stale detected items are not persisted. */
class DownloaderPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("downloader_settings", Context.MODE_PRIVATE)

    fun folderConfig(): DownloadFolderConfig = DownloadFolderConfig(
        folderMode = runCatching { DownloadFolderMode.valueOf(prefs.getString("folder_mode", "")!!) }
            .getOrDefault(DownloadFolderMode.AUTO_BY_PAGE_TITLE),
        customSubfolderName = prefs.getString("subfolder", null) ?: "Web_Media_Collection",
        customTreeUri = prefs.getString("tree_uri", null),
        customTreeDisplayName = prefs.getString("tree_name", null)
    )

    fun saveFolder(config: DownloadFolderConfig) {
        prefs.edit().putString("folder_mode", config.folderMode.name)
            .putString("subfolder", config.customSubfolderName)
            .putString("tree_uri", config.customTreeUri)
            .putString("tree_name", config.customTreeDisplayName).apply()
    }

    fun extensions(): ExtensionFilterConfig = ExtensionFilterConfig(
        enabledImageExtensions = prefs.getStringSet("images", null)?.toSet() ?: ExtensionFilterConfig.DEFAULT_IMAGE_EXTENSIONS,
        enabledVideoExtensions = prefs.getStringSet("videos", null)?.toSet() ?: ExtensionFilterConfig.DEFAULT_VIDEO_EXTENSIONS,
        customExtensions = prefs.getStringSet("custom", emptySet())!!.toSet(),
        minWidthPx = prefs.getInt("min_width", 32), minHeightPx = prefs.getInt("min_height", 32)
    )

    fun saveExtensions(config: ExtensionFilterConfig) {
        prefs.edit().putStringSet("images", config.enabledImageExtensions)
            .putStringSet("videos", config.enabledVideoExtensions).putStringSet("custom", config.customExtensions)
            .putInt("min_width", config.minWidthPx).putInt("min_height", config.minHeightPx).apply()
    }

    var skipDuplicates: Boolean
        get() = prefs.getBoolean("skip_duplicates", true)
        set(value) { prefs.edit().putBoolean("skip_duplicates", value).apply() }
}
