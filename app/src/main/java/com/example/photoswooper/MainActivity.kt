package com.example.photoswooper

import android.Manifest.permission.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
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
    private var permissionsGranted = false // Will be changed if permissions are false
    private lateinit var mediaStatusDao: MediaStatusDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val database = MediaStatusDatabase.getDatabase(applicationContext)
        mediaStatusDao = database.mediaStatusDao()

        val contentResolverInterface = ContentResolverInterface(mediaStatusDao, this)
        val mainViewModel = MainViewModel(
            context = this,
            contentResolverInterface = contentResolverInterface,
            mediaStatusDao = mediaStatusDao
        )

        val permissionsLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
                if (results.containsValue(false)) {
                   Toast.makeText(
                        this, // context
                        "Cannot read photos & videos without these permissions",
                        Toast.LENGTH_LONG
                    ).show()
                    permissionsGranted = false
                } else {
                    permissionsGranted = true
                }
            }
        /* check if storage permissions are given, if not then ask for them */
        val permissionsToRequest = checkPermissions()
        if (permissionsToRequest.isNotEmpty())
            permissionsLauncher.launch(permissionsToRequest)

        // FIXME("Unable to use if statement on whether permissions are obtained?")
//        if (permissionsToRequest.isEmpty() || permissionsGranted) {
        CoroutineScope(Dispatchers.IO).launch { mainViewModel.getPhotos() }
//        }

        setContent {
            PhotoSwooperTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        viewModel = mainViewModel
                    )
                }
            }
        }

    }

    private fun checkPermissions(): Array<String> {
        var permissionsToRequest = mutableListOf<String>()
        /* Permissions to check depending on the android version */
        val readPermissions =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                arrayOf(READ_MEDIA_IMAGES, READ_MEDIA_VIDEO, READ_MEDIA_VISUAL_USER_SELECTED)
            // TODO: might need to set readPermissions to nothing in this case so that the user doesn't get prompted to reselect allowed photos
            else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                arrayOf(READ_MEDIA_IMAGES, READ_MEDIA_VIDEO)
            else
                arrayOf(READ_EXTERNAL_STORAGE)

        /* Set readPermissionGranted to false if any of the permissions are denied */
        readPermissions.forEach { readPermission ->
            if (ContextCompat.checkSelfPermission(this, readPermission) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(readPermission)
            }
        }

        // Check write permissions FIXME("This permission is not being requested for some reason. Might need to display rationale")
        if (ContextCompat.checkSelfPermission(this, WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
            permissionsToRequest.add(WRITE_EXTERNAL_STORAGE)
        // TODO: might need to request legacy write perms for older devices?

        return permissionsToRequest.toTypedArray()
    }
}