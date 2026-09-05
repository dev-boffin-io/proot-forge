package io.boffin.proot.ui.screens.terminal

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import com.rk.libcommons.child
import com.rk.libcommons.localDir
import java.io.File

object Rootfs {
    var isInstalled = mutableStateOf(false)
    var isCustomInstalled = mutableStateOf(false)
    var isBoffinInstalled = mutableStateOf(false)

    fun checkInstallation(context: Context) {
        isInstalled.value = isRootfsInstalled(context)
        isCustomInstalled.value = isCustomRootfsInstalled(context)
        isBoffinInstalled.value = isBoffinRootfsInstalled(context)
    }

    fun isRootfsInstalled(context: Context): Boolean {
        val alpineDir = context.localDir().child("alpine")
        val isExtracted = alpineDir.exists() && (alpineDir.list()?.any { it != "root" && it != "tmp" } == true)
        val isArchivePresent = context.filesDir.child("alpine.tar.gz").exists()
        return isExtracted || isArchivePresent
    }

    // "Custom" session (formerly the dedicated NetHunter feature) - dir/archive names kept
    // as "nethunter" for backwards compatibility with already-downloaded installs.
    fun isCustomRootfsInstalled(context: Context): Boolean {
        val customDir = context.localDir().child("nethunter")
        val isExtracted = customDir.exists() && (customDir.list()?.any { it != "root" && it != "tmp" } == true)
        val isArchivePresent = context.filesDir.child("nethunter.tar.xz").exists()
        return isExtracted || isArchivePresent
    }

    fun isBoffinRootfsInstalled(context: Context): Boolean {
        val boffinDir = context.localDir().child("boffin")
        val isExtracted = boffinDir.exists() && (boffinDir.list()?.any { it != "root" && it != "tmp" } == true)
        val isArchivePresent = context.filesDir.child("boffin.tar.gz").exists()
        return isExtracted || isArchivePresent
    }
}
