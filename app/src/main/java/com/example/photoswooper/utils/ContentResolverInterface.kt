package com.example.photoswooper.utils

import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.app.ActivityCompat.startIntentSenderForResult
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
        val photos = mutableListOf<Photo>()
        val mediaStoreUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media._ID,
        ) // The columns (metadata types) we want to retrieve from the MediaStore

        Log.i("MediaStore", "Querying MediaStore database")
        contentResolver.query(
            mediaStoreUri,
            projection, // The columns (metadata types) we want to retrieve from the MediaStore
            null, // selection parameter
            null, // (selectionArgs parameter) Fetch *all* image files
            "RANDOM()", // sortOrder parameter
        ) ?.use { cursor ->

            val idColumnIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val dateTakenColumnIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_TAKEN)
            val dateAddedColumnIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)

            /* add these values to the list of tracks */
            Log.d("MediaStore", "Iterating over database output")
            while (cursor.moveToNext()) { // While there is another audio file to iterate over, iterate over to the next one and:
                val fetchedId = cursor.getLong(idColumnIndex)
                val fetchedDateTaken = cursor.getLong(dateTakenColumnIndex)
                val fetchedDateAdded = cursor.getLong(dateAddedColumnIndex)
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
                    photos.add(
                            Photo(
                                id = fetchedId,
                                dateTaken = formattedDate.toString().substringBefore("T"),
                                uri = fetchedUri,
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