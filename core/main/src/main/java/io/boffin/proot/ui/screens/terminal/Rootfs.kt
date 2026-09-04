package io.boffin.proot.ui.screens.terminal

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import com.rk.libcommons.child
import com.rk.libcommons.localDir
import java.io.File

object Rootfs {
    var isInstalled = mutableStateOf(false)
    var isNethunterInstalled = mutableStateOf(false)

    fun checkInstallation(context: Context) {
        isInstalled.value = isRootfsInstalled(context)
        isNethunterInstalled.value = isNethunterRootfsInstalled(context)
    }

    fun isRootfsInstalled(context: Context): Boolean {
        val alpineDir = context.localDir().child("alpine")
        val isExtracted = alpineDir.exists() && (alpineDir.list()?.any { it != "root" && it != "tmp" } == true)
        val isArchivePresent = context.filesDir.child("alpine.tar.gz").exists()
        return isExtracted || isArchivePresent
    }

    fun isNethunterRootfsInstalled(context: Context): Boolean {
        val nethunterDir = context.localDir().child("nethunter")
        val isExtracted = nethunterDir.exists() && (nethunterDir.list()?.any { it != "root" && it != "tmp" } == true)
        val isArchivePresent = context.filesDir.child("nethunter.tar.xz").exists()
        return isExtracted || isArchivePresent
    }
}
