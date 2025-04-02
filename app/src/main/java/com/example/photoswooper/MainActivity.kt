package com.example.photoswooper

import android.Manifest.permission.*
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import coil3.ImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import com.example.photoswooper.data.database.MediaStatusDao
import com.example.photoswooper.data.database.MediaStatusDatabase
import com.example.photoswooper.ui.theme.PhotoSwooperTheme
import com.example.photoswooper.ui.view.MainScreen
import com.example.photoswooper.ui.view.MainViewModel
import com.example.photoswooper.utils.ContentResolverInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var mediaStatusDao: MediaStatusDao
    lateinit var mainViewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val database = MediaStatusDatabase.getDatabase(applicationContext)
        mediaStatusDao = database.mediaStatusDao()

        /* Custom image loader for animated GIFs */
        val imageLoader = ImageLoader.Builder(this)
            .components {
                if (SDK_INT >= 28) {
                    add(AnimatedImageDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()

        val contentResolverInterface = ContentResolverInterface(mediaStatusDao, this)
        mainViewModel = MainViewModel(
            context = this,
            contentResolverInterface = contentResolverInterface,
            mediaStatusDao = mediaStatusDao
        )

        CoroutineScope(Dispatchers.Main).launch {
            checkPermissionsAndGetPhotos(
                context = this@MainActivity,
                onPermissionsGranted = { mainViewModel.getPhotos() }
            )
        }

        setContent {
            PhotoSwooperTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        viewModel = mainViewModel,
                        imageLoader = imageLoader
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

        /* Define permissions for read access of all photos/videos */
        val fullReadPermissions = if (SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            arrayOf(READ_MEDIA_IMAGES, READ_MEDIA_VIDEO)
        else
            arrayOf(READ_EXTERNAL_STORAGE)

        val fullReadAccess = permissionMap.filter {
            fullReadPermissions.contains(it.key)
        }.values.contains(PackageManager.PERMISSION_DENIED)

        val limitedReadAccess = if (SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            permissionMap[READ_MEDIA_VISUAL_USER_SELECTED] == PackageManager.PERMISSION_GRANTED
        else
            false

        Log.v("Permissions", "Permissions granted = ${limitedReadAccess && fullReadAccess}")

        /* If permissions denied, show Toast. Else, get photos from storage */
        if (
            !limitedReadAccess
            && !fullReadAccess
        ) {
            Toast.makeText(
                this, // context
                "Cannot read photos & videos without these permissions",
                Toast.LENGTH_LONG
            ).show()
        } else {
            CoroutineScope(Dispatchers.Main).launch { mainViewModel.getPhotos() }
        }
    }
}

fun checkPermissionsAndGetPhotos(
    context: Context,
    onPermissionsGranted: suspend () -> Unit
) {
    var permissionsToRequest = mutableListOf<String>()
    /* Permissions to check depending on the android version */
    val readPermissions =
        if (SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            arrayOf(READ_MEDIA_IMAGES, READ_MEDIA_VIDEO, READ_MEDIA_VISUAL_USER_SELECTED)
        // TODO: might need to set readPermissions to nothing in this case so that the user doesn't get prompted to reselect allowed photos
        else if (SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            arrayOf(READ_MEDIA_IMAGES, READ_MEDIA_VIDEO)
        else
            arrayOf(READ_EXTERNAL_STORAGE)

    /* Set readPermissionGranted to false if any of the permissions are denied */
    readPermissions.forEach { readPermission ->
        if (ContextCompat.checkSelfPermission(context, readPermission) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(readPermission)
        }
    }

    // TODO("Add button in app to reselect photos, rather than prompting reselection on each app launch")

    // Check write permissions
//        if (ContextCompat.checkSelfPermission(this, WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
//            permissionsToRequest.add(WRITE_EXTERNAL_STORAGE)
    // TODO: might need to request legacy write perms for older devices?

    if (permissionsToRequest.isNotEmpty())
        ActivityCompat.requestPermissions(
            context as Activity,
            permissionsToRequest.toTypedArray(),
            101
        ) // The result of this is handled in the onRequestPermissionsResult() function
    else
        CoroutineScope(Dispatchers.IO).launch { onPermissionsGranted() }
}
