package com.example.photoswooper.utils

import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.ActivityCompat.startIntentSenderForResult
import androidx.exifinterface.media.ExifInterface
import com.example.photoswooper.data.database.MediaStatusDao
import com.example.photoswooper.data.models.Photo
import com.example.photoswooper.data.models.PhotoStatus
import com.example.photoswooper.data.photoLimit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.util.*

class ContentResolverInterface(
    val dao: MediaStatusDao,
    val context: Context
) {
    val contentResolver = context.contentResolver

    @OptIn(ExperimentalStdlibApi::class) // For .toHexString()
    suspend fun getPhotos(
        numPhotos: Int = photoLimit,
        onAddPhoto: (Photo) -> Unit
    ) {
        // TODO("Change so that MediaStore.Images changes to MediaStore.Videos for video types")
        // TODO("Automatically add unset photos from app database before fetching from MediaStore")
        var numPhotosAdded = 0

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
                val file = contentResolver.openInputStream(fetchedUri)
                val fileHash = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val digest: MessageDigest = MessageDigest.getInstance("SHA-512")
                    val hash: ByteArray = digest.digest(file?.readAllBytes()?: ByteArray(0))
                    hash.toHexString()
                } else {
                    TODO("VERSION.SDK_INT < TIRAMISU")
                }

                if (numPhotosAdded <= numPhotos) {
                    val findPhotoByHash = dao.findByHash(fileHash)
                    val findById = dao.findByMediaStoreId(fetchedId)

                    /* Define function to add photo to photos list & database (for later use) */
                    suspend fun addPhoto() {
                        val date =
                            if (fetchedDateTaken > 0)
                                fetchedDateTaken
                            else // if date taken is not found, use date added
                                fetchedDateAdded

                        var latLong: DoubleArray = doubleArrayOf(0.0, 0.0)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            val exifInterface = ExifInterface(file)
                            latLong = exifInterface.latLong // Location the photo was taken at
                            file?.close()
                        } else {
                            // TODO("Find location of photo for Android < Q")
                        }
                        val photoToAdd = Photo(
                            id = fetchedId,
                            uri = fetchedUri,
                            dateTaken = date,
                            size = fetchedSize,
                            location = latLong,
                            album = fetchedAlbum,
                            description = fetchedDescription,
                            title = fetchedDisplayName,
                            resolution = fetchedResolution,
                            status = PhotoStatus.UNSET,
                            fileHash = fileHash
                        )
                        numPhotosAdded += 1
                        onAddPhoto(photoToAdd)
                        Log.d("MediaStore", "Added photo with id $fetchedId")
                        CoroutineScope(Dispatchers.IO).launch {
                            dao.insert(photoToAdd.getMediaStatusEntity()) // Add to database
                        }
                    }

                    when {
                        /* Photo MediaStore ID is in the database AND has been swiped (DELETE OR KEEP) */
                        (findById != null && findById.status != PhotoStatus.UNSET) -> { file?.close() }
                        /* Photo is in the database but its MediaStore ID has changed.
                        * This will therefore 1. update the MediaStore ID. 2. if the status is UNSET, add the photo */
                        (findPhotoByHash != null) -> {
                            Log.v("MediaStore", "Photo hash $fileHash has been found in database, updating id to $fetchedId")
                            /* Update database */
                            CoroutineScope(Dispatchers.IO).launch {
                                var currentDate: Long = 0
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                                    currentDate = Date().toInstant().toEpochMilli()
                                else
                                    null// TODO("Get current date in epoch milli for Android version < O")
                                dao.update(findPhotoByHash.copy(mediaStoreId = fetchedId, dateModified = currentDate))
                            }
                            /* Add photo if unset */
                            if (findPhotoByHash.status == PhotoStatus.UNSET)
                                addPhoto()
                        }
                        /* Photo has not been swiped */
                        else -> {
                            addPhoto()
                        }
                    }
                } else {
                    return
                }
            }
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
            // TODO("Check whether user actually deleted file before updating database")
        } else {
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