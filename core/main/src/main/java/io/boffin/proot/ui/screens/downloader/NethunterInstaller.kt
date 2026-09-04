package io.boffin.proot.ui.screens.downloader

import android.content.Context
import com.rk.libcommons.child
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * The manifest is a tiny JSON file kept in the repo (NOT bundled as an APK asset) so its
 * content — the actual rootfs download URL — can be updated at any time on GitHub without
 * rebuilding the app. The app fetches this manifest first, then downloads whatever URL it
 * currently points to.
 *
 * Update the branch/path below if the manifest is ever moved.
 */
private const val NETHUNTER_MANIFEST_URL =
    "https://raw.githubusercontent.com/dev-boffin-io/ReTerminal/kali/nethunter-manifest.json"

object NethunterInstaller {

    class InstallException(message: String) : Exception(message)

    /**
     * Fetches the manifest, then downloads the rootfs archive it points to into
     * context.filesDir/nethunter.tar.xz. Calls onProgress(0-100) as bytes arrive.
     * Safe to call even if already installed — it no-ops in that case.
     */
    fun downloadIfNeeded(context: Context, onProgress: (Int) -> Unit) {
        val outputFile = context.filesDir.child("nethunter.tar.xz")
        if (outputFile.exists() && outputFile.length() > 0L) {
            return
        }

        val manifestConnection = (URL(NETHUNTER_MANIFEST_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 15000
            instanceFollowRedirects = true
        }
        manifestConnection.connect()
        if (manifestConnection.responseCode !in 200..299) {
            throw InstallException("Failed to fetch NetHunter manifest: HTTP ${manifestConnection.responseCode}")
        }
        val manifestText = manifestConnection.inputStream.bufferedReader().use { it.readText() }
        val downloadUrl = JSONObject(manifestText).getString("url")

        val connection = (URL(downloadUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 15000
            instanceFollowRedirects = true
        }
        connection.connect()
        if (connection.responseCode !in 200..299) {
            throw InstallException("Failed to download NetHunter rootfs: HTTP ${connection.responseCode}")
        }

        val totalSize = connection.contentLengthLong
        val tempFile = File(outputFile.path + ".part")

        connection.inputStream.use { input ->
            FileOutputStream(tempFile).use { output ->
                val buffer = ByteArray(64 * 1024)
                var bytesRead: Int
                var totalRead = 0L
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalRead += bytesRead
                    if (totalSize > 0) {
                        onProgress(((totalRead * 100) / totalSize).toInt())
                    }
                }
            }
        }

        if (!tempFile.renameTo(outputFile)) {
            throw InstallException("Failed to finalize downloaded NetHunter rootfs")
        }
    }
}
