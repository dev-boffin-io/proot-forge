package io.boffin.proot.ui.screens.terminal

import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.libcommons.child
import com.rk.resources.strings
import io.boffin.proot.ui.activities.terminal.MainActivity
import io.boffin.proot.ui.activities.terminal.MainViewModel
import io.boffin.proot.ui.components.SetStatusBarTextColor
import io.boffin.proot.ui.screens.downloader.CustomInstaller
import io.boffin.proot.ui.screens.downloader.copyPickedRootfs
import io.boffin.proot.ui.screens.settings.SettingsCard
import io.boffin.proot.ui.screens.settings.WorkingMode
import io.boffin.proot.ui.screens.terminal.virtualkeys.VirtualKeysListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    mainActivity: MainActivity,
    navController: NavController,
    mainViewModel: MainViewModel = viewModel(mainActivity),
    terminalViewModel: TerminalViewModel = viewModel(mainActivity)
) {
    val context = LocalContext.current
    val isDarkMode = isSystemInDarkTheme()
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val configuration = LocalConfiguration.current
    val drawerWidth = (configuration.screenWidthDp * 0.84).dp
    var showAddDialog by remember { mutableStateOf(false) }
    var downloadingMode by remember { mutableStateOf<Int?>(null) }
    var downloadProgress by remember { mutableIntStateOf(0) }
    var downloadError by remember { mutableStateOf<String?>(null) }

    val sessionBinder = mainViewModel.sessionBinder

    fun proceedToCreateSession(mode: Int) {
        val binder = sessionBinder ?: return
        val sessionId = generateUniqueSessionId(binder.getService().sessionList.keys.toList())
        val terminal = terminalViewModel.terminalView ?: return
        val client = TerminalBackEnd(terminal, mainActivity)
        binder.createSession(sessionId, client, mode)
        terminalViewModel.changeSession(context, binder, sessionId)
        showAddDialog = false
    }

    val boffinFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) {
            // User backed out of the picker - nothing selected, nothing to do.
            return@rememberLauncherForActivityResult
        }
        downloadingMode = WorkingMode.BOFFIN
        downloadError = null
        downloadProgress = 0
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    copyPickedRootfs(context, uri, "boffin.tar.gz") { pct ->
                        downloadProgress = pct
                    }
                    withContext(Dispatchers.Main) {
                        downloadingMode = null
                        proceedToCreateSession(WorkingMode.BOFFIN)
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        downloadError = e.message ?: e.javaClass.simpleName
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            if (context.filesDir.child("background").exists().not()) {
                TerminalUtils.darkText.value = !isDarkMode
            } else if (terminalViewModel.bitmap == null) {
                BitmapFactory.decodeFile(context.filesDir.child("background").absolutePath)?.asImageBitmap()?.let {
                    terminalViewModel.bitmap = it
                }
            }
        }
    }
    
    // Update virtual keys when they are available
    terminalViewModel.virtualKeysView?.apply {
        virtualKeysViewClient = terminalViewModel.terminalView?.mTermSession?.let { VirtualKeysListener(it) }
        buttonTextColor = TerminalUtils.getViewColor()
    }

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    val isDarkIcons = if (drawerState.isClosed) TerminalUtils.darkText.value else !isDarkMode
    SetStatusBarTextColor(isDarkIcons = isDarkIcons)

    if (showAddDialog && sessionBinder != null) {
        AddSessionDialog(
            onDismiss = { showAddDialog = false },
            onCreateSession = { mode ->
                when (mode) {
                    WorkingMode.BOFFIN -> {
                        if (Rootfs.isBoffinRootfsInstalled(context)) {
                            proceedToCreateSession(mode)
                        } else {
                            showAddDialog = false
                            boffinFilePicker.launch(arrayOf("*/*"))
                        }
                    }
                    WorkingMode.CUSTOM -> {
                        if (Rootfs.isCustomRootfsInstalled(context)) {
                            proceedToCreateSession(mode)
                        } else {
                            showAddDialog = false
                            downloadingMode = mode
                            downloadError = null
                            downloadProgress = 0
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    try {
                                        CustomInstaller.downloadIfNeeded(context) { pct ->
                                            downloadProgress = pct
                                        }
                                        withContext(Dispatchers.Main) {
                                            downloadingMode = null
                                            proceedToCreateSession(mode)
                                        }
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            downloadError = e.message ?: e.javaClass.simpleName
                                        }
                                    }
                                }
                            }
                        }
                    }
                    else -> proceedToCreateSession(mode)
                }
            }
        )
    }

    if (downloadingMode != null) {
        val label = if (downloadingMode == WorkingMode.CUSTOM) "Custom" else "Boffin"
        val verb = if (downloadingMode == WorkingMode.CUSTOM) "Downloading" else "Copying"
        RootfsDownloadDialog(
            label = label,
            verb = verb,
            progress = downloadProgress,
            error = downloadError,
            onDismiss = {
                downloadingMode = null
                downloadError = null
            }
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen || !terminalViewModel.showToolbar,
        drawerContent = {
            TerminalDrawer(
                drawerWidth = drawerWidth,
                sessionBinder = sessionBinder,
                navController = navController,
                onAddSession = { showAddDialog = true },
                onSessionSelected = { id ->
                    sessionBinder?.let { terminalViewModel.changeSession(context, it, id) }
                    scope.launch { drawerState.close() }
                }
            )
        }
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            BackgroundImage(terminalViewModel)
            
            Column {
                if (terminalViewModel.showToolbar) {
                    TerminalTopBar(
                        sessionBinder = sessionBinder,
                        onMenuClick = { scope.launch { drawerState.open() } },
                        onAddClick = { showAddDialog = true },
                        color = TerminalUtils.getComposeColor()
                    )
                }

                val density = LocalDensity.current
                val topPadding = if (terminalViewModel.showToolbar) 0.dp else {
                    with(density) { TopAppBarDefaults.windowInsets.getTop(this).toDp() }
                }

                if (sessionBinder != null) {
                    TerminalViewLayout(
                        viewModel = terminalViewModel,
                        mainActivity = mainActivity,
                        sessionBinder = sessionBinder,
                        modifier = Modifier
                            .imePadding()
                            .navigationBarsPadding()
                            .padding(top = topPadding)
                            .fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
private fun BackgroundImage(viewModel: TerminalViewModel) {
    viewModel.bitmap?.let { bitmap ->
        Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .alpha(viewModel.wallAlpha)
                .let {
                    if (viewModel.backgroundBlur > 0f) {
                        it.blur(viewModel.backgroundBlur.dp)
                    } else {
                        it
                    }
                }
                .zIndex(-1f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSessionDialog(onDismiss: () -> Unit, onCreateSession: (Int) -> Unit) {
    val isArm64 = "arm64-v8a" in Build.SUPPORTED_ABIS
    BasicAlertDialog(onDismissRequest = onDismiss) {
        PreferenceGroup {
            SettingsCard(
                title = { Text("Kali") },
                description = { Text(stringResource(strings.alpine_desc)) },
                onClick = { onCreateSession(WorkingMode.ALPINE) }
            )
            SettingsCard(
                title = { Text("Android") },
                description = { Text(stringResource(strings.android_desc)) },
                onClick = { onCreateSession(WorkingMode.ANDROID) }
            )
            if (isArm64) {
                SettingsCard(
                    title = { Text("Custom") },
                    description = { Text("Kali NetHunter (full, arm64 only)") },
                    onClick = { onCreateSession(WorkingMode.CUSTOM) }
                )
            }
            SettingsCard(
                title = { Text("Boffin") },
                description = { Text("Debian 12 XFCE4 desktop (select rootfs file)") },
                onClick = { onCreateSession(WorkingMode.BOFFIN) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RootfsDownloadDialog(label: String, verb: String, progress: Int, error: String?, onDismiss: () -> Unit) {
    BasicAlertDialog(onDismissRequest = { if (error != null) onDismiss() }) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (error != null) {
                    val action = if (verb == "Copying") "copy" else "download"
                    Text("Failed to $action $label rootfs: $error", color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(onClick = onDismiss) { Text("Close") }
                } else {
                    Text("$verb $label rootfs\u2026")
                    Spacer(modifier = Modifier.height(16.dp))
                    if (progress > 0) {
                        CircularProgressIndicator(progress = { progress / 100f })
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("$progress%")
                    } else {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}

private fun generateUniqueSessionId(existingIds: List<String>): String {
    var index = 1
    var newId: String
    do {
        newId = "main$index"
        index++
    } while (newId in existingIds)
    return newId
}

const val VIRTUAL_KEYS = "[" +
    "\n  [\"ESC\", {\"key\": \"/\", \"popup\": \"\\\\\"}, {\"key\": \"-\", \"popup\": \"|\"}, \"HOME\", \"UP\", \"END\", \"PGUP\"]," +
    "\n  [\"TAB\", \"CTRL\", \"ALT\", \"LEFT\", \"DOWN\", \"RIGHT\", \"PGDN\"]" +
    "\n]"
