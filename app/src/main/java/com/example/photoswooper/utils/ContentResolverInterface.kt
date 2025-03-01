package com.example.photoswooper.utils

import android.content.ContentResolver
import android.content.ContentUris
import android.icu.text.DateFormat
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.example.photoswooper.data.models.Photo

class ContentResolverInterface() {
    fun getPhotos(contentResolver: ContentResolver): List<Photo> {
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
            "RANDOM() LIMIT 25", // sortOrder parameter
        ) ?.use { cursor ->

            val idColumnIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val dateTakenColumnIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_TAKEN)

            /* add these values to the list of tracks */
            Log.i("MediaStore", "Iterating over database output")
            while (cursor.moveToNext()) { // While there is another audio file to iterate over, iterate over to the next one and:
                val fetchedId = cursor.getLong(idColumnIndex)
                val fetchedDateTaken = cursor.getString(dateTakenColumnIndex)
                val fetchedUri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    fetchedId
                )
                photos.add(
                    Photo(
                        id = fetchedId,
                        dateTaken = DateFormat.getInstance().parse(fetchedDateTaken),
                        uri = fetchedUri
                    )
                )
            }
        }
        return photos.toList()
    }
}