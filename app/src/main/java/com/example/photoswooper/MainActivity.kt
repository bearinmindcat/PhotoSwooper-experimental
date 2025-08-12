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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import coil3.ImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import com.example.photoswooper.data.database.MediaStatusDao
import com.example.photoswooper.data.database.MediaStatusDatabase
import com.example.photoswooper.data.uistates.BooleanPreference
import com.example.photoswooper.ui.view.MainScreen
import com.example.photoswooper.ui.viewmodels.MainViewModel
import com.example.photoswooper.ui.viewmodels.StatsViewModel
import com.example.photoswooper.utils.ContentResolverInterface
import com.example.photoswooper.utils.DataStoreInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class MainActivity : AppCompatActivity() {
    private lateinit var mediaStatusDao: MediaStatusDao
    private lateinit var mainViewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = MediaStatusDatabase.getDatabase(applicationContext)
        mediaStatusDao = database.mediaStatusDao()

        val dataStoreInterface = DataStoreInterface(dataStore)

        /* Custom image loader for animated GIFs */
        val imageLoader = ImageLoader.Builder(this)
            .components {
                if (SDK_INT >= 28) {
                    add(AnimatedImageDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .memoryCache(null)
            .diskCache(null) // Disable cache so animation is called every time in AsyncPhoto (needs an onSuccess call)
            .build()

        val contentResolverInterface = ContentResolverInterface(
            dao = mediaStatusDao,
            contentResolver = contentResolver,
            dataStoreInterface = dataStoreInterface,
            activity = this as Activity
        )
        mainViewModel = MainViewModel(
            contentResolverInterface = contentResolverInterface,
            mediaStatusDao = mediaStatusDao,
            dataStoreInterface = dataStoreInterface,
            makeToast = {
                Toast.makeText(
                    this,
                    it,
                    Toast.LENGTH_SHORT
                    ).show()
            },
            startActivity = { this.startActivity(it) }
        )
        val statsViewModel = StatsViewModel(
            mediaStatusDao = mediaStatusDao,
            formatDateTime = { epochMillis, flags ->
                DateUtils.formatDateTime(
                    this,
                    epochMillis,
                    flags
                    )
            },
            formatDateTimeRange = {startMillis, endMillis, flags ->
                DateUtils.formatDateRange(
                    this,
                    startMillis,
                    endMillis,
                    flags
                )
            }
        )

        CoroutineScope(Dispatchers.Main).launch {
            checkPermissionsAndGetPhotos(
                context = this@MainActivity,
                onPermissionsGranted = { mainViewModel.getNewPhotos() }
            )
        }

        setContent {
            val systemFont by dataStoreInterface.getBooleanSettingValue(BooleanPreference.system_font.toString()).collectAsState(!BooleanPreference.system_font.default)
            val dynamicTheme by dataStoreInterface.getBooleanSettingValue(BooleanPreference.dynamic_theme.toString()).collectAsState(BooleanPreference.dynamic_theme.default)
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
                    MainScreen(
                        mainViewModel = mainViewModel,
                        imageLoader = imageLoader,
                        statsViewModel = statsViewModel,
                    )
                }
            }
        }
    }

    /* Handle permission request result */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val permissionMap: Map<String, Int> = permissions.associateWith { grantResults[permissions.indexOf(it)] }

        /** Permissions that should be granted for read access to all storage */
        val fullReadPermissions = if (SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            arrayOf(READ_MEDIA_IMAGES, READ_MEDIA_VIDEO)
        else
            arrayOf(READ_EXTERNAL_STORAGE)

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

        /* If permissions denied, notify user. Else, get photos from storage */
        if (!readAccess || !writeAccess) {
            if (!readAccess) {
                Toast.makeText(
                    this, // context
                    "Cannot read photos & videos without read permissions",
                    Toast.LENGTH_LONG
                ).show()
            }
            if (!writeAccess) {
                Toast.makeText(
                    this, // context
                    "Cannot delete photos & videos without write permissions",
                    Toast.LENGTH_LONG
                ).show()
            }
        } else {
            CoroutineScope(Dispatchers.Main).launch { mainViewModel.getNewPhotos() }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            in 100..102 -> { // Delete/trash a file
                if (resultCode != RESULT_CANCELED) {
                        if (SDK_INT >= Build.VERSION_CODES.R)
                            CoroutineScope(Dispatchers.Main).launch {
                                mainViewModel.onDeletePhotos(mainViewModel.getPhotosToDelete().map { it.uri })
                            }
                        else
                            CoroutineScope(Dispatchers.Main).launch {
                                mainViewModel.deletePhotos() // Delete the rest of the photos if on lower android versions
                            }
                    }
                else
                    if (SDK_INT >= Build.VERSION_CODES.R)
                        CoroutineScope(Dispatchers.Main).launch {
                            mainViewModel.onDeletePhotos(listOf(), true)
                        }
                    else
                        CoroutineScope(Dispatchers.Main).launch {
                            mainViewModel.onDeletePhotos(listOf(), true)
                        }
            }
        }
    }
}

fun checkPermissionsAndGetPhotos(
    context: Context,
    onPermissionsGranted: suspend () -> Unit
) {
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

    // TODO("Add button in app to reselect photos, rather than prompting reselection on each app launch")

    /* Check write permissions if android version doesn't support MediaStore delete/trash requests */
    if (SDK_INT < Build.VERSION_CODES.Q && ContextCompat.checkSelfPermission(context, WRITE_EXTERNAL_STORAGE) != PERMISSION_GRANTED)
        permissionsToRequest.add(WRITE_EXTERNAL_STORAGE)

    if (SDK_INT >= Build.VERSION_CODES.Q && ContextCompat.checkSelfPermission(context, ACCESS_MEDIA_LOCATION) != PERMISSION_GRANTED) {
        permissionsToRequest.add(ACCESS_MEDIA_LOCATION)
    }

    if (permissionsToRequest.isNotEmpty())
        ActivityCompat.requestPermissions(
            context as Activity,
            permissionsToRequest.toTypedArray(),
            101
        ) // The result of this is handled in the onRequestPermissionsResult() function
    else
        CoroutineScope(Dispatchers.IO).launch { onPermissionsGranted() }
}
