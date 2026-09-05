package io.boffin.proot.ui.screens.downloader

import android.content.Context
import com.rk.libcommons.child
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Shared "fetch a manifest.json, then download whatever URL it contains" flow.
 *
 * The manifest is a tiny JSON file kept in the repo (NOT bundled as an APK asset) so its
 * content — the actual rootfs download URL — can be updated at any time on GitHub without
 * rebuilding the app. Each installer object below fetches its own manifest first, then
 * downloads whatever URL it currently points to into context.filesDir/<outputFileName>.
 */
class InstallException(message: String) : Exception(message)

private fun downloadManifestRootfs(
    context: Context,
    manifestUrl: String,
    outputFileName: String,
    connectTimeoutMs: Int,
    readTimeoutMs: Int,
    label: String,
    onProgress: (Int) -> Unit
) {
    val outputFile = context.filesDir.child(outputFileName)
    if (outputFile.exists() && outputFile.length() > 0L) {
        return
    }

    val manifestConnection = (URL(manifestUrl).openConnection() as HttpURLConnection).apply {
        connectTimeout = connectTimeoutMs
        readTimeout = readTimeoutMs
        instanceFollowRedirects = true
    }
    manifestConnection.connect()
    if (manifestConnection.responseCode !in 200..299) {
        throw InstallException("Failed to fetch $label manifest: HTTP ${manifestConnection.responseCode}")
    }
    val manifestText = manifestConnection.inputStream.bufferedReader().use { it.readText() }
    val downloadUrl = JSONObject(manifestText).getString("url")

    val connection = (URL(downloadUrl).openConnection() as HttpURLConnection).apply {
        connectTimeout = connectTimeoutMs
        readTimeout = readTimeoutMs
        instanceFollowRedirects = true
    }
    connection.connect()
    if (connection.responseCode !in 200..299) {
        throw InstallException("Failed to download $label rootfs: HTTP ${connection.responseCode}")
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
        throw InstallException("Failed to finalize downloaded $label rootfs")
    }
}

/**
 * "Custom" session (formerly labelled NetHunter in the UI). Manifest/output filenames are
 * unchanged from the original NetHunter feature so existing installs that already downloaded
 * this rootfs don't need to re-download it after the rename.
 */
object CustomInstaller {
    private const val MANIFEST_URL =
        "https://raw.githubusercontent.com/dev-boffin-io/proot-forge/main/nethunter-manifest.json"

    fun downloadIfNeeded(context: Context, onProgress: (Int) -> Unit) {
        downloadManifestRootfs(
            context = context,
            manifestUrl = MANIFEST_URL,
            outputFileName = "nethunter.tar.xz",
            connectTimeoutMs = 15_000,
            readTimeoutMs = 15_000,
            label = "Custom",
            onProgress = onProgress
        )
    }
}

/**
 * "Boffin" session — a self-hosted Debian 12 XFCE4 desktop rootfs. Served from a Tailscale
 * Funnel endpoint (git.bowfin-pleco.ts.net), which can be slow/cold-start on the first
 * request, so this uses much longer timeouts than CustomInstaller.
 */
object BoffinInstaller {
    private const val MANIFEST_URL =
        "https://raw.githubusercontent.com/dev-boffin-io/proot-forge/main/boffin-manifest.json"

    // Generous timeouts: the Tailscale Funnel endpoint can be slow to respond, especially
    // on a cold start, and the download itself is a large desktop rootfs over a slow link.
    private const val CONNECT_TIMEOUT_MS = 120_000
    private const val READ_TIMEOUT_MS = 120_000

    fun downloadIfNeeded(context: Context, onProgress: (Int) -> Unit) {
        downloadManifestRootfs(
            context = context,
            manifestUrl = MANIFEST_URL,
            outputFileName = "boffin.tar.gz",
            connectTimeoutMs = CONNECT_TIMEOUT_MS,
            readTimeoutMs = READ_TIMEOUT_MS,
            label = "Boffin",
            onProgress = onProgress
        )
    }
}
