package com.example.photoswooper

import android.Manifest.permission.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.photoswooper.ui.theme.PhotoSwooperTheme
import com.example.photoswooper.ui.view.MainScreen
import com.example.photoswooper.ui.view.MainViewModel
import com.example.photoswooper.utils.ContentResolverInterface

class MainActivity : AppCompatActivity() {
    private lateinit var permissionsLauncher: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val contentResolverInterface = ContentResolverInterface()
        val mainViewModel = MainViewModel(contentResolverInterface)

        permissionsLauncher = registerForActivityResult(RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                mainViewModel.getPhotos(contentResolver)
            } else {
                Toast.makeText(
                    this, // context
                    "Cannot read photos & videos without these permissions",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        /* check if storage permissions are given, if not then ask for them */
        checkReadPermissions()
        checkWritePermissions()

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

    private fun checkReadPermissions() {
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
            if (ContextCompat.checkSelfPermission(this, readPermission) == PackageManager.PERMISSION_DENIED) {
                permissionsLauncher.launch(readPermission)
            } // TODO: Not confident in this - PERMISSION_DENIED accounts for no permissions obtained?
        }
    }

    private fun checkWritePermissions() {
        if (ContextCompat.checkSelfPermission(this, WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED)
            permissionsLauncher.launch(WRITE_EXTERNAL_STORAGE)
        // TODO: might need to request legacy write perms for older devices?
    }
}