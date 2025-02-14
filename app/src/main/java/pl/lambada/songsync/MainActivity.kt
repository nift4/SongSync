package pl.lambada.songsync

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import pl.lambada.songsync.data.MainViewModel
import pl.lambada.songsync.data.dto.Song
import pl.lambada.songsync.ui.Navigator
import pl.lambada.songsync.ui.components.BottomBar
import pl.lambada.songsync.ui.components.TopBar
import pl.lambada.songsync.ui.components.dialogs.NoInternetDialog
import pl.lambada.songsync.ui.screens.LoadingScreen
import pl.lambada.songsync.ui.theme.SongSyncTheme
import java.io.FileNotFoundException
import java.io.IOException
import java.net.UnknownHostException

/**
 * The main activity of the SongSync app.
 */
class MainActivity : ComponentActivity() {

    private var permissionCallback: ((Boolean) -> Unit)? = null

    /**
     * Called when the activity is starting.
     *
     * @param savedInstanceState The saved instance state.
     */
    @OptIn(ExperimentalLayoutApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val storagePermissionResultLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                permissionCallback!!(Environment.isExternalStorageManager())
            }
        }
        val requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) {
            permissionCallback!!(
                it[android.Manifest.permission.READ_EXTERNAL_STORAGE] ?: false &&
                        it[android.Manifest.permission.WRITE_EXTERNAL_STORAGE] ?: false)
        }
        setContent {
            val context = LocalContext.current
            val viewModel: MainViewModel by viewModels()
            val navController = rememberNavController()
            var hasLoadedPermissions by remember { mutableStateOf(false) }
            var hasPermissions by remember { mutableStateOf(false) }
            var showRationale by remember { mutableStateOf(false) }
            var internetConnection by remember { mutableStateOf(true) }

            // Get token upon app start
            LaunchedEffect(Unit) {
                permissionCallback = {
                    hasPermissions = it
                    hasLoadedPermissions = true
                }
                launch(Dispatchers.IO) {
                    try {
                        viewModel.refreshToken()
                    } catch (e: Exception) {
                        if (e is UnknownHostException || e is FileNotFoundException || e is IOException)
                            internetConnection = false
                        else
                            throw e
                    }
                }
                // Request permissions and wait with check
                when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                            !Environment.isExternalStorageManager() -> {
                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                        intent.addCategory("android.intent.category.DEFAULT")
                        intent.data = android.net.Uri.parse(
                            String.format(
                                "package:%s",
                                context.applicationContext.packageName
                            )
                        )
                        storagePermissionResultLauncher.launch(intent)
                    }

                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                            Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q &&
                            !(context.checkSelfPermission(
                                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                            ) == PackageManager.PERMISSION_GRANTED
                                    && context.checkSelfPermission(
                                android.Manifest.permission.READ_EXTERNAL_STORAGE
                            ) == PackageManager.PERMISSION_GRANTED) -> {
                        if (shouldShowRequestPermissionRationale(
                                android.Manifest.permission.READ_EXTERNAL_STORAGE
                            )
                            || shouldShowRequestPermissionRationale(
                                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                            )
                        ) {
                            hasLoadedPermissions = true
                            showRationale = true
                        } else {
                            requestPermissionLauncher.launch(
                                arrayOf(
                                    android.Manifest.permission.READ_EXTERNAL_STORAGE,
                                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                                )
                            )
                        }
                    }

                    else -> {
                        permissionCallback!!(true)
                    }
                }
            }

            SongSyncTheme {
                // I'll cry if this crashes due to memory concerns
                val selected = rememberSaveable(saver = Saver(
                    save = { it.toTypedArray() }, restore = { mutableStateListOf(*it) }
                )) { mutableStateListOf<String>() }
                var allSongs by remember { mutableStateOf<List<Song>?>(null) }
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                Scaffold(
                    topBar = {
                        TopBar(
                            selected = selected, currentRoute = currentRoute, allSongs = allSongs)
                    },
                    bottomBar = {
                        BottomBar(currentRoute = currentRoute, navController = navController)
                    }
                ) { paddingValues ->
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .imePadding()
                            .let {
                                if (WindowInsets.isImeVisible) {
                                    // exclude bottom bar if ime is visible
                                    it.padding(PaddingValues(
                                        top = paddingValues.calculateTopPadding(),
                                        start = paddingValues.calculateStartPadding(
                                            LocalLayoutDirection.current),
                                        end = paddingValues.calculateEndPadding(
                                            LocalLayoutDirection.current)
                                    ))
                                } else {
                                    it.padding(paddingValues)
                                }
                            }
                    ) {
                        if (!hasLoadedPermissions) {
                            LoadingScreen()
                        } else if (!hasPermissions) {
                            AlertDialog(
                                onDismissRequest = { /* don't dismiss */ },
                                confirmButton = {
                                    if (!showRationale) {
                                        Button(
                                            onClick = {
                                                finishAndRemoveTask()
                                            }
                                        ) {
                                            Text(stringResource(R.string.close_app))
                                        }
                                    } else {
                                        Button(
                                            onClick = {
                                                requestPermissionLauncher.launch(
                                                    arrayOf(
                                                        android.Manifest.permission
                                                            .READ_EXTERNAL_STORAGE,
                                                        android.Manifest.permission
                                                            .WRITE_EXTERNAL_STORAGE
                                                    )
                                                )
                                            }
                                        ) {
                                            Text(stringResource(R.string.ok))
                                        }
                                    }
                                },
                                title = { Text(stringResource(R.string.permission_denied)) },
                                text = {
                                    Column {
                                        Text(stringResource(
                                            R.string.requires_higher_storage_permissions))
                                        if (!showRationale)
                                            Text(stringResource(R.string.already_granted_restart))
                                    }
                                }
                            )
                        } else if (!internetConnection) {
                            NoInternetDialog {
                                finishAndRemoveTask()
                            }
                        } else {
                            LaunchedEffect(Unit) {
                                launch(Dispatchers.IO) {
                                    allSongs = viewModel.getAllSongs(context)
                                }
                            }
                            Navigator(navController = navController, selected = selected,
                                allSongs = allSongs, viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }
}