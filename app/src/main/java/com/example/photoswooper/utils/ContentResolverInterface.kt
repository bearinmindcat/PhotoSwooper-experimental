package com.example.photoswooper.utils

import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.ContentResolver
import android.content.ContentUris
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
import com.example.photoswooper.data.uistates.BooleanPreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.DataInputStream
import java.io.File
import java.security.MessageDigest
import java.util.Calendar


class ContentResolverInterface(
    private val dao: MediaStatusDao,
    private val contentResolver: ContentResolver,
    private val dataStoreInterface: DataStoreInterface, //
    private val activity: Activity, // For delete/trash intent
) {

    /**
     * Get new photos from the MediaStore and each  into [onAddPhoto] (this is usually a function to add them to [com.example.photoswooper.data.uistates.MainUiState]
     *
     * @param photosAdded Photos already added to [com.example.photoswooper.data.uistates.MainUiState] to compare new ones to
     * @param numPhotos The number of photos to be passed into [onAddPhoto]
     */
    @OptIn(ExperimentalStdlibApi::class) // For .toHexString()
    suspend fun getPhotos(
        photosAdded: MutableSet<Photo> = mutableSetOf(),
        numPhotos: Int,
        onAddPhoto: (Photo) -> Unit
    ) {
        // TODO("Change so that MediaStore.Images changes to MediaStore.Videos for video types")
        // TODO("Automatically add unset photos from app database before fetching from MediaStore")

        val mediaStoreUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val projection = mutableListOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DESCRIPTION,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATA,
        ) // The columns (metadata types) we want to retrieve from the MediaStore

        /* Add extra columns supported by recent android versions */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            projection.addAll(listOf(
                MediaStore.Images.Media.ALBUM,
                MediaStore.Images.Media.RESOLUTION,
            ))

        Log.i("MediaStore", "Querying MediaStore database")
        contentResolver.query(
            mediaStoreUri,
            projection.toTypedArray(), // The columns (metadata types) we want to retrieve from the MediaStore
            null, // selection parameter
            null, // (selectionArgs parameter) Fetch *all* image files
            "RANDOM()", // sortOrder parameter
        ) ?.use { cursor ->

            val idColumnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val dateTakenColumnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val sizeColumnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val albumColumnIndex = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) cursor.getColumnIndexOrThrow(MediaStore.Images.Media.ALBUM)
                else 0 // 0 value not used
            val resolutionColumnIndex = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RESOLUTION)
                else 0 // 0 value not used
            val descriptionColumnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DESCRIPTION)
            val displayNameColumnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val absoluteFilePathColumnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)

            /* add these values to the list of tracks */
            Log.d("MediaStore", "Iterating over database output")
            while (cursor.moveToNext()) { // While there is another audio file to iterate over, iterate over to the next one and:
                val fetchedId = cursor.getLong(idColumnIndex)
                val fetchedDateTaken = cursor.getLong(dateTakenColumnIndex)
                val fetchedSize = cursor.getLong(sizeColumnIndex)
                val fetchedAlbum = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) cursor.getString(albumColumnIndex) else null
                val fetchedResolution = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) cursor.getString(resolutionColumnIndex) else null
                val fetchedDescription = cursor.getString(descriptionColumnIndex)
                val fetchedDisplayName = cursor.getString(displayNameColumnIndex)
                val fetchedUri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    fetchedId
                )
                val fetchedAbsoluteFilePath = cursor.getString(absoluteFilePathColumnIndex)

                val fileInputStream = contentResolver.openInputStream(fetchedUri)
                val digest: MessageDigest = MessageDigest.getInstance("SHA-512")
                val fileHash = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val hash: ByteArray = digest.digest(fileInputStream?.readAllBytes()?: ByteArray(0))
                    hash.toHexString()
                }
                    else {
                        /* Based on https://stackoverflow.com/a/59049461 */
                        val fileData = ByteArray(fileInputStream?.available()?: 0)
                        val dataInputStream = DataInputStream(fileInputStream)
                        dataInputStream.readFully(fileData)
                        val hash: ByteArray = digest.digest(fileData)
                        hash.toHexString()
                    }
                fileInputStream?.close()

                if (photosAdded.size < numPhotos) {

                    /** Add photo to the UI's photo list and database */
                    fun addPhoto() {
                        /* Decide which date to use */
                        val lastModified = File(fetchedAbsoluteFilePath).lastModified()
                        val date =
                            if (fetchedDateTaken > 0)
                                fetchedDateTaken
                            else if (lastModified > 0)// if date taken is not found, use date added
                                lastModified
                            else null

                        /* Find location of photo using EXIF */
                        var latLong: DoubleArray? = null
                        val fileInputStream2 = contentResolver.openInputStream(fetchedUri)
                        if (fileInputStream2 != null){
                            val exifInterface = ExifInterface(fileInputStream2)
                            latLong = exifInterface.latLong // Location the photo was taken at
                            fileInputStream2.close()
                        }

                        /* Decide album to use */
                        val album =
                            fetchedAlbum ?: fetchedAbsoluteFilePath.substringBeforeLast("/").substringAfterLast("/")

                        val photoToAdd = Photo(
                            id = fetchedId,
                            uri = fetchedUri,
                            dateTaken = date,
                            size = fetchedSize,
                            location = latLong,
                            album = album,
                            description = fetchedDescription,
                            title = fetchedDisplayName,
                            resolution = fetchedResolution,
                            status = PhotoStatus.UNSET,
                            fileHash = fileHash
                        )

                        photosAdded.add(photoToAdd)
                        onAddPhoto(photoToAdd)
                        Log.d("MediaStore", "Added photo with id $fetchedId")
                        CoroutineScope(Dispatchers.IO).launch {
                            dao.insert(photoToAdd.getMediaStatusEntity()) // Add to database
                        }
                    }

                    val currentDate = Calendar.getInstance().timeInMillis

                    val findPhotoByHash = dao.findByHash(fileHash)
                    val findPhotoById = dao.findByMediaStoreId(fetchedId)
                    val photoInDatabaseHasBeenSwiped = listOf(PhotoStatus.DELETE, PhotoStatus.KEEP).contains(findPhotoById?.status)
                    val photoFoundInSession = photosAdded.find { it.id == fetchedId } != null

                    when {
                        /* Photo MediaStore ID is in the database AND has been swiped (DELETE OR KEEP) */
                        ((photoFoundInSession || photoInDatabaseHasBeenSwiped)) -> {  }
                        /* Photo is in the database but its MediaStore ID has changed.
                        * This will therefore 1. update the MediaStore ID. 2. if the status is UNSET, add the photo */
                        (findPhotoByHash != null) -> {
                            Log.v("MediaStore", "Photo hash has been found in database, updating id to $fetchedId")
                            // TODO("This case is almost always true, so should remove the Id from the database, and use hash as primary key")
                            /* Update database */
                            CoroutineScope(Dispatchers.IO).launch {
                                dao.update(findPhotoByHash.copy(mediaStoreId = fetchedId, dateModified = currentDate))
                            }
                            /* Add photo if unset */
                            if (findPhotoByHash.status == PhotoStatus.UNSET)
                                addPhoto()
                            else if (findPhotoByHash.snoozedUntil != null && findPhotoByHash.snoozedUntil <= currentDate)
                                addPhoto()
                        }
                        /* Photo has not been swiped */
                        else -> {
                            if (findPhotoById?.snoozedUntil == null || findPhotoById.snoozedUntil <= currentDate)
                                addPhoto()
                        }
                    }
                } else {
                    return
                }
            }
        }
}

    suspend fun deletePhotos(
        uris: List<Uri>,
        onDelete: (List<Uri>) -> Unit
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val permanentlyDelete = dataStoreInterface.getBooleanSettingValue(BooleanPreference.permanently_delete.toString()).first()

            val editPendingIntent =
                if (permanentlyDelete)
                    MediaStore.createDeleteRequest(
                        contentResolver,
                        uris
                    )
                else
                    MediaStore.createTrashRequest(
                        contentResolver,
                        uris,
                        true // set IS_TRASHED to true
                    )

            // Launch a system prompt requesting user permission for the operation.
            startIntentSenderForResult(activity, editPendingIntent.intentSender, 100, null, 0, 0, 0, Bundle.EMPTY)
            // onDelete() is called in onActivityResult, defined in MainActivity.kt
        } else {
            val deletedMediaUris = mutableListOf<Uri>()
            uris.forEach { uri ->
                var outputtedRows: Int

                try {
                    outputtedRows = contentResolver.delete(
                        uri, null, null
                    )
                } catch (securityException: SecurityException) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val recoverableSecurityException =
                            securityException as? RecoverableSecurityException ?: throw RuntimeException(
                                securityException.message,
                                securityException
                            )

                        val intentSender = recoverableSecurityException.userAction.actionIntent.intentSender
                        intentSender.let {
                            startIntentSenderForResult(
                                activity,
                                intentSender,
                                102,
                                null,
                                0,
                                0,
                                0,
                                null
                            )
                        }
                    } else {
                        throw RuntimeException(securityException.message, securityException)
                    }
                    outputtedRows = 0
                }

                /* If deletion fails, exit. onActivityResult will run this function again when required permissions are granted */
                if (outputtedRows == 0) {
                    Log.e("deletePhotos", "Could not delete $uri :(")
                    return
                } else {
                    Log.d("deletePhotos", "Deleted $uri ^_^")
                    deletedMediaUris.add(uri)
                    if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q)
                        onDelete(listOf(uri))
                }

            }
            onDelete(deletedMediaUris)
        }
    }

    fun getMediaType(uri: Uri): String? {
        return contentResolver.getType(uri)
    }
}