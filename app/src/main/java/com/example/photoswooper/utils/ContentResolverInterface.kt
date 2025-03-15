package com.example.photoswooper.utils

import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.ImageDecoder.decodeBitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.app.ActivityCompat.startIntentSenderForResult
import androidx.exifinterface.media.ExifInterface
import com.example.photoswooper.R
import com.example.photoswooper.data.models.Photo
import com.example.photoswooper.data.models.PhotoStatus
import com.example.photoswooper.data.photoLimit
import java.io.FileNotFoundException
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class ContentResolverInterface(val context: Context) {
    val contentResolver = context.contentResolver

    fun getPhotos(): List<Photo> {
        // TODO("Change so that MediaStore.Images changes to MediaStore.Videos for video types")
        // TODO("Check read permissions on each call to this function")
        val photos = mutableListOf<Photo>()
        val mediaStoreUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.ALBUM,
            MediaStore.Images.Media.DESCRIPTION,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.RESOLUTION,
        ) // The columns (metadata types) we want to retrieve from the MediaStore

        Log.i("MediaStore", "Querying MediaStore database")
        contentResolver.query(
            mediaStoreUri,
            projection, // The columns (metadata types) we want to retrieve from the MediaStore
            null, // selection parameter
            null, // (selectionArgs parameter) Fetch *all* image files
            "RANDOM()", // sortOrder parameter
        ) ?.use { cursor ->

            val idColumnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val dateTakenColumnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val dateAddedColumnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val sizeColumnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val albumColumnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.ALBUM)
            val descriptionColumnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DESCRIPTION)
            val displayNameColumnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val resolutionColumnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RESOLUTION)

            /* add these values to the list of tracks */
            Log.d("MediaStore", "Iterating over database output")
            while (cursor.moveToNext()) { // While there is another audio file to iterate over, iterate over to the next one and:
                val fetchedId = cursor.getLong(idColumnIndex)
                val fetchedDateTaken = cursor.getLong(dateTakenColumnIndex)
                val fetchedDateAdded = cursor.getLong(dateAddedColumnIndex)
                val fetchedSize = cursor.getLong(sizeColumnIndex)
                val fetchedAlbum = cursor.getString(albumColumnIndex)
                val fetchedDescription = cursor.getString(descriptionColumnIndex)
                val fetchedDisplayName = cursor.getString(displayNameColumnIndex)
                val fetchedResolution = cursor.getString(resolutionColumnIndex)
                val fetchedUri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    fetchedId
                )
                if (photos.size <= photoLimit) {
                    val formattedDate =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && fetchedDateTaken > 0)
                            LocalDateTime.ofInstant(Instant.ofEpochMilli(fetchedDateTaken), ZoneId.systemDefault())
                        else if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && fetchedDateTaken <= 0) // if date taken is not found, use date added
                            LocalDateTime.ofInstant(Instant.ofEpochMilli(fetchedDateAdded), ZoneId.systemDefault())
                        else
                            0 // TODO("Format date for Android version < O")

                    var latLong: DoubleArray = doubleArrayOf(0.0, 0.0)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val file = contentResolver.openInputStream(fetchedUri)
                        val exifInterface = ExifInterface(file)
                        latLong = exifInterface.latLong // Location the photo was taken at
                        file?.close()
                    }
                    photos.add(
                            Photo(
                                id = fetchedId,
                                uri = fetchedUri,
                                dateTaken = formattedDate.toString().substringBefore("T"),
                                size = fetchedSize,
                                location = latLong,
                                album = fetchedAlbum,
                                description = fetchedDescription,
                                title = fetchedDisplayName,
                                resolution = fetchedResolution,
                                status = PhotoStatus.UNSET
                            )
                        )
                } else { return photos.toList() }
            }
        }
        return photos.toList()
    }

    @RequiresApi(Build.VERSION_CODES.Q) // TODO("lower API - should be ok once transitioned to a viewer using media3 maybe?")
    fun getImageBitmap(uri: Uri, size: Size = Size(999999999, 999999999)): ImageBitmap {
        return try { // If there is embedded artwork found, return the artwork as bitmap
            contentResolver.loadThumbnail(uri, size, CancellationSignal()).asImageBitmap()
        } catch (e: FileNotFoundException) { // If there is no embedded artwork found, return the placeholder
            BitmapFactory.decodeResource(context.resources, R.drawable.file_not_found_cat).asImageBitmap()
        }
    }

    fun deletePhotos(uris: List<Uri>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val editPendingIntent =
                MediaStore.createTrashRequest(
                    contentResolver,
                    uris,
                    true
                )

            val activity: Activity = context as Activity
            // Launch a system prompt requesting user permission for the operation.
            startIntentSenderForResult(activity, editPendingIntent.intentSender, 100, null, 0, 0, 0, Bundle.EMPTY)
        }
        else {
        uris.forEach { uri ->

            val outputtedRows = contentResolver.delete(uri, null, null)

            val path = uri.encodedPath
            if (outputtedRows == 0) {
                Log.e("deletePhotos", "Could not delete $path :(")
            } else {
                Log.d("deletePhotos", "Deleted $path ^_^")
            }
        }
            }
    }
}