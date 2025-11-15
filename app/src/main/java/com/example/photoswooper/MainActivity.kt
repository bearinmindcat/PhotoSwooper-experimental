/*
 * SPDX-FileCopyrightText: 2025 Loowiz <loowiz@envs.net>
 *
 *  SPDX-License-Identifier: AGPL-3.0-only
 */

package com.example.photoswooper

import android.Manifest.permission.ACCESS_MEDIA_LOCATION
import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.Manifest.permission.READ_MEDIA_IMAGES
import android.Manifest.permission.READ_MEDIA_VIDEO
import android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import android.text.format.DateUtils
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE
import androidx.media3.common.C.USAGE_MEDIA
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import coil3.ImageLoader
import coil3.video.VideoFrameDecoder
import com.example.photoswooper.data.BooleanPreference
import com.example.photoswooper.data.IntPreference
import com.example.photoswooper.data.StringPreference
import com.example.photoswooper.data.database.MediaStatusDao
import com.example.photoswooper.data.database.MediaStatusDatabase
import com.example.photoswooper.data.models.MediaFilter
import com.example.photoswooper.data.models.MediaType
import com.example.photoswooper.data.uistates.MainUiState
import com.example.photoswooper.data.uistates.StatsUiState
import com.example.photoswooper.ui.theme.PhotoSwooperTheme
import com.example.photoswooper.ui.view.MainScreen
import com.example.photoswooper.ui.view.Onboardingcreen
import com.example.photoswooper.ui.viewmodels.MainViewModel
import com.example.photoswooper.ui.viewmodels.StatsViewModel
import com.example.photoswooper.utils.ContentResolverInterface
import com.example.photoswooper.utils.DataStoreInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.system.exitProcess

val REQUEST_PERMISSIONS_REQUEST_CODE = 101

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
lateinit var player: ExoPlayer

/** Permissions that should be granted for read access to all storage */
val fullReadPermissions = if (SDK_INT >= Build.VERSION_CODES.TIRAMISU)
    arrayOf(READ_MEDIA_IMAGES, READ_MEDIA_VIDEO)
else
    arrayOf(READ_EXTERNAL_STORAGE)

class MainActivity : AppCompatActivity() {
    private lateinit var mediaStatusDao: MediaStatusDao
    private lateinit var mainViewModel: MainViewModel

    @androidx.annotation.OptIn(UnstableApi::class)
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = MediaStatusDatabase.getDatabase(applicationContext)
        mediaStatusDao = database.mediaStatusDao()

        val dataStoreInterface = DataStoreInterface(dataStore)

        // Show user crash log if app crashes, rather than immediately crashing
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            CoroutineScope(Dispatchers.IO).launch {
                // Store stack trace
                dataStoreInterface.setStringSettingValue(throwable.stackTraceToString(), StringPreference.CRASH_LOG.setting)

                // Start activity which shows the user the crash log
                startActivity(Intent(this@MainActivity, CrashActivity::class.java))

                // Exit current crashed activity
                exitProcess(10)
            }
        }

        // Custom image loader for animated GIFs
        val imageLoader = ImageLoader.Builder(this)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .memoryCache(null)
            .diskCache(null) // Disable cache so animation is called every time in AsyncImage (needs an onSuccess call)
            .build()

        initialisePlayer()

        val contentResolverInterface = ContentResolverInterface(
            dao = mediaStatusDao,
            contentResolver = contentResolver,
            dataStoreInterface = dataStoreInterface,
            activity = this as Activity
        )
        setContent {
            // Get settings for UI
            val systemFont by dataStoreInterface.getBooleanSettingValue(BooleanPreference.SYSTEM_FONT.setting)
                .collectAsState(!BooleanPreference.SYSTEM_FONT.default)
            val dynamicTheme by dataStoreInterface.getBooleanSettingValue(BooleanPreference.DYNAMIC_THEME.setting)
                .collectAsState(BooleanPreference.DYNAMIC_THEME.default)
            val skipReview by dataStoreInterface.getBooleanSettingValue(BooleanPreference.SKIP_REVIEW.setting)
                .collectAsState(BooleanPreference.SKIP_REVIEW.default)
            val tutorialIndex by dataStoreInterface.getIntSettingValue(IntPreference.TUTORIAL_INDEX.setting)
                .collectAsState(1000)

            PhotoSwooperTheme(
                systemFont = systemFont,
                dynamicColor = dynamicTheme
            ) {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier
                        .fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (tutorialIndex == 0) {
                        Onboardingcreen(dataStoreInterface)
                    } else {
                        // Create the mainViewModel
                        val uiCoroutineScope = rememberCoroutineScope()
                        val bottomSheetScaffoldState = rememberBottomSheetScaffoldState()
                        val savedUiState = rememberSaveable { mutableStateOf(MainUiState(isPlaying = player.isPlaying)) }
                        val savedMediaFilter = rememberSaveable { mutableStateOf<MediaFilter?>(null) }
                        mainViewModel = remember { MainViewModel(
                            contentResolverInterface = contentResolverInterface,
                            mediaStatusDao = mediaStatusDao,
                            uiCoroutineScope = uiCoroutineScope,
                            bottomSheetScaffoldState = bottomSheetScaffoldState,
                            dataStoreInterface = dataStoreInterface,
                            makeToast = {
                                this.lifecycleScope.launch {
                                    Toast.makeText(
                                        applicationContext,
                                        it,
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            checkPermissions = { checkPermissions(this, it) },
                            startActivity = { startActivity(it) },
                            savedUiState = savedUiState.value,
                            updateSavedUiState = {
                                savedUiState.value = it
                            },
                            savedMediaFilter = savedMediaFilter.value,
                            updateSavedMediaFilter = {
                                savedMediaFilter.value = it
                            }
                        ) }
                        val savedStatsUiState = rememberSaveable { mutableStateOf<StatsUiState?>(null) }
                        val statsViewModel = remember { StatsViewModel(
                            mediaStatusDao = mediaStatusDao,
                            formatDateTime = { epochMillis, flags ->
                                DateUtils.formatDateTime(
                                    this,
                                    epochMillis,
                                    flags
                                )
                            },
                            formatDateTimeRange = { startMillis, endMillis, flags ->
                                DateUtils.formatDateRange(
                                    this,
                                    startMillis,
                                    endMillis,
                                    flags
                                )
                            },
                            savedUiState = savedStatsUiState.value,
                            updateSavedUiState = { savedStatsUiState.value = it }
                        ) }
                        MainScreen(
                            mainViewModel = mainViewModel,
                            imageLoader = imageLoader,
                            statsViewModel = statsViewModel,
                            skipReview = skipReview
                        )
                    }
                }
            }
        }
    }

    private fun initialisePlayer() {
        val dataStoreInterface = DataStoreInterface(this.dataStore)

        player = ExoPlayer.Builder(this).build()
        player.prepare()
        player.addListener(
            object : Player.Listener {
                override fun onRenderedFirstFrame() {
                    super.onRenderedFirstFrame()
                    mainViewModel.onMediaLoaded(
                        player.videoSize.width / player.videoSize.height.toFloat()
                    )
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    super.onIsPlayingChanged(isPlaying)
                    mainViewModel.updateIsPlaying(isPlaying)
                }

                override fun onPlayerErrorChanged(error: PlaybackException?) {
                    super.onPlayerErrorChanged(error)
                    if (error != null)
                        mainViewModel.onMediaError(error.localizedMessage?: error.message)
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    super.onPlaybackStateChanged(playbackState)

                }
            }
        )
        // Set player attributes using user preferences
        CoroutineScope(Dispatchers.Main).launch {
            val handleAudioFocus = dataStoreInterface.getBooleanSettingValue(BooleanPreference.PAUSE_BACKGROUND_MEDIA.setting).first()
            val loopVideos = dataStoreInterface.getBooleanSettingValue(BooleanPreference.LOOP_VIDEOS.setting).first()
            player.setAudioAttributes(
                /* audioAttributes = */ AudioAttributes.Builder()
                    .setContentType(AUDIO_CONTENT_TYPE_MOVIE)
                    .setUsage(USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ handleAudioFocus
            )
            player.repeatMode = if (loopVideos) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        }
    }

    /* Pause video if the user leaves the app */
    override fun onPause() {
        super.onPause()
        try {
            mainViewModel.tempPause()
        } catch (_: RuntimeException) {/* mainViewModel is not yet initialised (first start) */
        }
    }

    /* Unpause the video when the user opens the app from the background */
    @androidx.annotation.OptIn(UnstableApi::class)
    override fun onResume() {
        super.onResume()
        try {
            if (player.isReleased) {
                initialisePlayer()
                if (mainViewModel.getCurrentMedia()?.type == MediaType.VIDEO) {
                    player.setMediaItem(
                        MediaItem.fromUri(
                            mainViewModel.getCurrentMedia()?.uri
                                ?: "android.resource://com.example.photoswooper/drawable/file_not_found_cat".toUri()
                        )
                    )
                    player.seekTo(mainViewModel.uiState.value.videoPosition)
                    if (mainViewModel.uiState.value.isPlaying)
                        player.play()
                    else
                        player.pause()
                }
            }
            mainViewModel.revertIsPlayingToBeforeTempPause()
        } catch (_: RuntimeException) {/* mainViewModel is not yet initialised (first start) */
        }
    }

    override fun onStop() {
        super.onStop()
        player.release()
    }

    /* Handle permission request result */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val permissionMap: Map<String, Int> = permissions.associateWith { grantResults[permissions.indexOf(it)] }

        /* Write access is only needed for lower android versions (< 11) which don't support MediaStore trash/delete requests */
        val writeAccess = if (SDK_INT < Build.VERSION_CODES.Q)
            permissionMap[WRITE_EXTERNAL_STORAGE] == PERMISSION_GRANTED
        else true

        val fullReadAccess = permissionMap.filter {
            fullReadPermissions.contains(it.key)
        }.values.contains(PERMISSION_GRANTED)

        val limitedReadAccess = if (SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            permissionMap[READ_MEDIA_VISUAL_USER_SELECTED] == PERMISSION_GRANTED
        else
            false

        Log.v("Permissions", "permissionsMap = $permissionMap")
        val readAccess = limitedReadAccess || fullReadAccess

        Log.i("Permissions", "Permissions granted = ${readAccess && writeAccess}")

        /* If permissions denied, notify user. Else, get media from storage */
        if (!readAccess || !writeAccess) {
            if (!readAccess) {
                Toast.makeText(
                    this, // context
                    "Cannot read photos & videos without read permissions",
                    Toast.LENGTH_LONG
                ).show()
                mainViewModel.updatePermissionsGranted(false)
            }
            if (!writeAccess) {
                Toast.makeText(
                    this, // context
                    "Cannot delete photos & videos without write permissions",
                    Toast.LENGTH_LONG
                ).show()
                mainViewModel.updatePermissionsGranted(false)
            }
        } else {
            mainViewModel.updatePermissionsGranted(true)
            CoroutineScope(Dispatchers.IO).launch {
                mainViewModel.resetAndGetNewMediaItems()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            // Delete a file
            in 100..102 -> {
                if (resultCode != RESULT_CANCELED) {
                    if (SDK_INT >= Build.VERSION_CODES.R)
                        CoroutineScope(Dispatchers.Main).launch {
                            mainViewModel.onDeletion(mainViewModel.getMediaToDelete().map { it.uri })
                        }
                    else
                        CoroutineScope(Dispatchers.Main).launch {
                            mainViewModel.deleteMarkedMedia() // Delete the rest if on lower android versions
                        }
                }
                else
                    CoroutineScope(Dispatchers.Main).launch {
                            mainViewModel.onDeletion(listOf(), true)
                        }
            }
        }
    }
}

fun checkPermissions(
    context: Context,
    onPermissionsGranted: suspend () -> Unit = { }
) {
    Log.i("Permissions", "Checking permissions")
    val permissionsToRequest = mutableListOf<String>()
    /* Permissions to check depending on the android version */
    val readPermissions =
        if (SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            arrayOf(READ_MEDIA_IMAGES, READ_MEDIA_VIDEO)
        else
            arrayOf(READ_EXTERNAL_STORAGE)

    /* Set readPermissionGranted to false if any of the permissions are denied */
    readPermissions.forEach { readPermission ->
        if (ContextCompat.checkSelfPermission(context, readPermission) != PERMISSION_GRANTED) {
            permissionsToRequest.add(readPermission)
        }
    }

    /* Add READ_MEDIA_VISUAL_USER_SELECTED permission if android version supports it */
    if (SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        permissionsToRequest.add(READ_MEDIA_VISUAL_USER_SELECTED)

    // TODO("Reselect media only on app start & at end of photos given read permission to, rather than prompting
    //  reselection on each app launch")

    /* Check write permissions if android version doesn't support MediaStore delete/trash requests */
    if (SDK_INT < Build.VERSION_CODES.Q && ContextCompat.checkSelfPermission(
            context,
            WRITE_EXTERNAL_STORAGE
        ) != PERMISSION_GRANTED
    )
        permissionsToRequest.add(WRITE_EXTERNAL_STORAGE)

    if (SDK_INT >= Build.VERSION_CODES.Q && ContextCompat.checkSelfPermission(
            context,
            ACCESS_MEDIA_LOCATION
        ) != PERMISSION_GRANTED
    ) {
        permissionsToRequest.add(ACCESS_MEDIA_LOCATION)
    }

    if (permissionsToRequest.isNotEmpty())
        ActivityCompat.requestPermissions(
            context as Activity,
            permissionsToRequest.toTypedArray(),
            REQUEST_PERMISSIONS_REQUEST_CODE
        ) // The result of this is handled in the onRequestPermissionsResult() function
    else
        CoroutineScope(Dispatchers.IO).launch {
            Log.i("Permissions", "Permissions already granted - calling onPermissionsGranted()")
            onPermissionsGranted()
        }
}
