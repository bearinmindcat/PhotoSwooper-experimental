package com.example.photoswooper.utils

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import androidx.annotation.RequiresApi
import com.example.photoswooper.R
import com.example.photoswooper.data.models.Photo
import com.example.photoswooper.data.models.PhotoStatus
import com.example.photoswooper.data.photoLimit
import java.io.FileNotFoundException

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

            /* add these values to the list of tracks */
            Log.d("MediaStore", "Iterating over database output")
            while (cursor.moveToNext()) { // While there is another audio file to iterate over, iterate over to the next one and:
                val fetchedId = cursor.getLong(idColumnIndex)
                val fetchedDateTaken = cursor.getString(dateTakenColumnIndex)
                val fetchedUri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    fetchedId
                )
                Log.d("MediaStore", "Adding to photos list")
                if (photos.size <= photoLimit) {
                    photos.add(
                        Photo(
                            id = fetchedId,
                            dateTaken = fetchedDateTaken,
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
    fun getImageBitmap(uri: Uri, size: Size = Size(999999999, 999999999)): Bitmap {
        return try { // If there is embedded artwork found, return the artwork as bitmap
            contentResolver.loadThumbnail(uri, size, CancellationSignal())
        } catch (e: FileNotFoundException) { // If there is no embedded artwork found, return the placeholder
            BitmapFactory.decodeResource(context.resources, R.drawable.file_not_found_cat)
        }
    }
}