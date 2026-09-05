package io.boffin.proot.ui.screens.downloader

import android.content.Context
import android.os.Environment
import com.rk.libcommons.child
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class InstallException(message: String) : Exception(message)

/**
 * Fetches a manifest.json, then downloads whatever URL it contains, into
 * context.filesDir/<outputFileName>. The manifest is a tiny JSON file kept in the repo
 * (NOT bundled as an APK asset) so its content — the actual rootfs download URL — can be
 * updated at any time on GitHub without rebuilding the app.
 */
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
 * Fixed, well-known location the user drops the Boffin rootfs archive at (e.g. via Termux's
 * `cp`/`mv`/`wget`, adb push, or any file manager with storage access) - see
 * [copyExternalRootfs]. Chosen over a system file picker (SAF/ACTION_OPEN_DOCUMENT) because on
 * at least one real device the OPEN_DOCUMENT round-trip through DocumentsUI's PickActivity
 * reliably crashes inside the OS's own ActivityThread when delivering the result (a MIUI-side
 * bug, reproduced even via the classic startActivityForResult/onActivityResult path - not
 * something app code can route around). A fixed path needs no picker UI at all.
 */
fun boffinSourceFile(): File =
    File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "boffin-rootfs.tar.gz")

/**
 * Copies a plain file already sitting on shared storage (see [boffinSourceFile]) into
 * context.filesDir/<outputFileName>. Requires "All files access" (MANAGE_EXTERNAL_STORAGE) on
 * Android 11+ to read arbitrary paths under shared storage; the app already declares/requests
 * that permission elsewhere (Settings screen).
 */
fun copyExternalRootfs(
    context: Context,
    sourceFile: File,
    outputFileName: String,
    onProgress: (Int) -> Unit
) {
    val outputFile = context.filesDir.child(outputFileName)
    if (outputFile.exists() && outputFile.length() > 0L) {
        return
    }
    if (!sourceFile.exists()) {
        throw IOException("${sourceFile.path} does not exist")
    }

    val totalSize = sourceFile.length()
    val tempFile = File(outputFile.path + ".part")

    FileInputStream(sourceFile).use { input ->
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
        throw IOException("Failed to finalize copied Boffin rootfs")
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
