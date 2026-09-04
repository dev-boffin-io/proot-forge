package io.boffin.proot.ui.screens.downloader

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.rk.libcommons.*
import com.rk.resources.strings
import io.boffin.proot.ui.activities.terminal.MainActivity
import io.boffin.proot.ui.screens.terminal.Rootfs
import io.boffin.proot.ui.screens.terminal.TerminalScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

// Base URL where the kali-<arch>.tar.gz.rootfs assets are published as GitHub Release
// files. Update the tag if you publish the rootfs files under a different release.
private const val ROOTFS_RELEASE_BASE_URL =
    "https://github.com/dev-boffin-io/ReTerminal/releases/download/rootfs-v1"

@Composable
fun SetupScreen(
    modifier: Modifier = Modifier,
    mainActivity: MainActivity,
    navController: NavHostController
) {
    val context = LocalContext.current
    val installingStr = stringResource(strings.installing)
    val setupFailedStr = stringResource(strings.setup_failed)
    var isSetupComplete by remember { mutableStateOf(Rootfs.isRootfsInstalled(context)) }
    var error by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        if (isSetupComplete) {
            Rootfs.isInstalled.value = true
            return@LaunchedEffect
        }

        withContext(Dispatchers.IO) {
            try {
                val abis = Build.SUPPORTED_ABIS
                val abi = abis.firstOrNull {
                    it in listOf("arm64-v8a", "armeabi-v7a", "x86_64")
                } ?: throw RuntimeException("Unsupported CPU architectures: ${abis.joinToString()}")

                val debianArch = when (abi) {
                    "arm64-v8a" -> "aarch64"
                    "armeabi-v7a" -> "armhf"
                    "x86_64" -> "x86_64"
                    else -> throw RuntimeException("Unsupported ABI: $abi")
                }

                val fileName = "kali-$debianArch.tar.gz.rootfs"
                val outputFile = context.filesDir.child("alpine.tar.gz")

                if (!outputFile.exists() || outputFile.length() == 0L) {
                    val url = URL("$ROOTFS_RELEASE_BASE_URL/$fileName")
                    val connection = url.openConnection() as HttpURLConnection
                    connection.connectTimeout = 15000
                    connection.readTimeout = 15000
                    connection.instanceFollowRedirects = true
                    connection.connect()

                    if (connection.responseCode !in 200..299) {
                        throw RuntimeException("Download failed: HTTP ${connection.responseCode}")
                    }

                    val totalSize = connection.contentLength
                    val tempFile = File(outputFile.path + ".part")

                    connection.inputStream.use { input ->
                        FileOutputStream(tempFile).use { output ->
                            val buffer = ByteArray(8 * 1024)
                            var bytesRead: Int
                            var totalRead = 0L
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                                totalRead += bytesRead
                                if (totalSize > 0) {
                                    val pct = ((totalRead * 100) / totalSize).toInt()
                                    withContext(Dispatchers.Main) { progress = pct }
                                }
                            }
                        }
                    }

                    if (!tempFile.renameTo(outputFile)) {
                        throw RuntimeException("Failed to finalize downloaded rootfs")
                    }
                }

                withContext(Dispatchers.Main) {
                    Rootfs.isInstalled.value = true
                    isSetupComplete = true
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    error = e.javaClass.simpleName + ": " + e.message
                    toast(setupFailedStr.format(e.message))
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (!isSetupComplete) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (error != null) {
                    Text("Setup Failed: $error", color = MaterialTheme.colorScheme.error)
                } else {
                    Text(installingStr, style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(16.dp))
                    if (progress > 0) {
                        CircularProgressIndicator(progress = { progress / 100f })
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("$progress%", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        CircularProgressIndicator()
                    }
                }
            }
        } else {
            TerminalScreen(mainActivity = mainActivity, navController = navController)
        }
    }
}
