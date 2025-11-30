/*
 * SPDX-FileCopyrightText: 2025 Loowiz <loowiz@envs.net>
 *
 *  SPDX-License-Identifier: AGPL-3.0-only
 */

package com.example.photoswooper.utils

import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.ContentResolver
import android.content.ContentUris
import android.net.Uri
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.ActivityCompat.startIntentSenderForResult
import androidx.exifinterface.media.ExifInterface
import com.example.photoswooper.data.BooleanPreference
import com.example.photoswooper.data.IntPreference
import com.example.photoswooper.data.database.MediaStatusDao
import com.example.photoswooper.data.models.Media
import com.example.photoswooper.data.models.MediaFilter
import com.example.photoswooper.data.models.MediaSortField
import com.example.photoswooper.data.models.MediaStatus
import com.example.photoswooper.data.models.MediaType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.util.Calendar
import kotlin.time.Duration.Companion.days

/** Intent request code used by android version 10 */
val DELETE_FILE_REQUEST_CODE = 102
/** Intent request code used by android versions > 11 */
val DELETE_FILE_GROUP_REQUEST_CODE = 100

class ContentResolverInterface(
    private val dao: MediaStatusDao,
    private val contentResolver: ContentResolver,
    private val dataStoreInterface: DataStoreInterface, //
    private val activity: Activity, // For delete/trash intent
) {

    // File extensions not supported by Coil
    val unsupportedFileExtensions = arrayOf("psd", "esd")

    /** Calls [getMediaOfTypeFromMediaStore] twice - once for photos and once for videos
     *
     * If [targetNumVideos] of videos could not be found, add the number missing to the number of photos to fetch -
     * and vice versa with [targetNumPhotos] to try and preserve total number of photos & videos added
     * */
    suspend fun getAllMediaFromMediaStore(
        mediaAdded: MutableSet<Media> = mutableSetOf(),
        targetNumVideos: Int,
        targetNumPhotos: Int,
        mediaFilter: MediaFilter,
        onAddMedia: (Media) -> Unit
    ) {
        var numVideosNotFound = 0
        var numPhotosNotFound = 0
        // Get videos and save remaining number of videos to add as variable (user's device may not have the desired number)
        if (mediaFilter.mediaTypes.contains(MediaType.VIDEO)) {
            numVideosNotFound = getMediaOfTypeFromMediaStore(
                mediaAdded = mediaAdded,
                onAddMedia = {
                    onAddMedia(it)
                },
                numToAdd = targetNumVideos,
                type = MediaType.VIDEO,
                mediaFilter = mediaFilter
            )
            Log.i(
                "ContentResolverInterface",
                "Attempted to add $targetNumVideos videos - no. not found = $numVideosNotFound"
            )
        }
        if (mediaFilter.mediaTypes.contains(MediaType.PHOTO)) {
            // Get photos
            numPhotosNotFound = getMediaOfTypeFromMediaStore(
                mediaAdded = mediaAdded,
                onAddMedia = {
                    onAddMedia(it)
                },
                numToAdd = targetNumPhotos + numVideosNotFound,
                type = MediaType.PHOTO,
                mediaFilter = mediaFilter
            )
            Log.i(
                "ContentResolverInterface",
                "Attempted to add ${targetNumPhotos + numVideosNotFound} photos - no. not found = $numPhotosNotFound"
            )
        }
        // If more videos can be added to account for missing photos, add them
        if (numVideosNotFound == 0 && numPhotosNotFound > 0 && mediaFilter.mediaTypes.contains(MediaType.VIDEO)) {
            getMediaOfTypeFromMediaStore(
                mediaAdded = mediaAdded,
                onAddMedia = {
                    onAddMedia(it)
                },
                numToAdd = numPhotosNotFound,
                type = MediaType.VIDEO,
                mediaFilter = mediaFilter
            )
            Log.i("ContentResolverInterface", "Tried to add $numPhotosNotFound videos to compensate for missing photos")

        }
    }

    /**
     * Get new media of type [type] from the MediaStore and each  into [onAddMedia] (this is usually a function to add
     * them to [com.example.photoswooper.data.uistates.MainUiState]
     *
     * @param mediaAdded Media already added to [com.example.photoswooper.data.uistates.MainUiState] to compare new ones to
     * @param numToAdd The number of media items to be passed into [onAddMedia]
     *
     * @return The difference between the number of items actually added, and numToAdd
     */
    @OptIn(ExperimentalStdlibApi::class) // For .toHexString()
    private suspend fun getMediaOfTypeFromMediaStore(
        mediaAdded: MutableSet<Media> = mutableSetOf(),
        type: MediaType,
        mediaFilter: MediaFilter,
        numToAdd: Int,
        onAddMedia: (Media) -> Unit
    ): Int {
        // TODO("Automatically add unset media from app database before fetching from MediaStore")
        var numAdded = 0

        val swipeRetentionTimeMillis = dataStoreInterface
            .getIntSettingValue(IntPreference.NO_DAYS_TO_REMEMBER_SWIPES.setting).first().days.inWholeMilliseconds
        val statisticsEnabled = dataStoreInterface.getBooleanSettingValue(
            BooleanPreference.STATISTICS_ENABLED.setting
        ).first()

        val mediaStoreUri = if (SDK_INT >= Build.VERSION_CODES.Q) { // Might not need the when statements? Needs testing
            when (type) {
                MediaType.PHOTO -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                MediaType.VIDEO -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            }
        } else {
            when (type) {
                MediaType.PHOTO -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                MediaType.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }
        }
        val projection = mutableListOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.SIZE,
            if (type == MediaType.PHOTO) MediaStore.Images.ImageColumns.DESCRIPTION
            else MediaStore.Video.VideoColumns.DESCRIPTION,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATA,
        ) // The columns (metadata types) we want to retrieve from the MediaStore

        /* Add extra columns supported by recent android versions */
        if (SDK_INT >= Build.VERSION_CODES.R)
            projection.addAll(
                listOf(
                    MediaStore.MediaColumns.ALBUM,
                    MediaStore.MediaColumns.RESOLUTION,
                )
            )


        val sortField = mediaFilter.sortField.sortOrderString
        val sortDirection =
            if (mediaFilter.sortField == MediaSortField.RANDOM) ""
            else if (mediaFilter.sortAscending) "ASC"
            else "DESC"

        Log.i("MediaStore", "Querying MediaStore database")
        contentResolver.query(
            /* uri = */ mediaStoreUri,
            /* projection = */ projection.toTypedArray(), // The columns (metadata types) we want to retrieve from the MediaStore
            /* selection = */ null,
            /* selectionArgs = */ null,
            /* sortOrder = */ "$sortField $sortDirection",
        ) ?.use { cursor ->

            val idColumnIndex = cursor.getColumnIndexOrThrow(projection[0])
            val dateTakenColumnIndex = cursor.getColumnIndexOrThrow(projection[1])
            val dateModifiedColumnIndex = cursor.getColumnIndexOrThrow(projection[2])
            val sizeColumnIndex = cursor.getColumnIndexOrThrow(projection[3])
            val descriptionColumnIndex = cursor.getColumnIndexOrThrow(projection[4])
            val displayNameColumnIndex = cursor.getColumnIndexOrThrow(projection[5])
            val absoluteFilePathColumnIndex = cursor.getColumnIndexOrThrow(projection[6])
            val albumColumnIndex =
                if (SDK_INT >= Build.VERSION_CODES.R) cursor.getColumnIndexOrThrow(projection[7])
                else 0 // 0 value not used
            val resolutionColumnIndex =
                if (SDK_INT >= Build.VERSION_CODES.R) cursor.getColumnIndexOrThrow(projection[8])
                else 0 // 0 value not used

            /* add these values to the list of tracks */
            Log.d("MediaStore", "Iterating over database output")
            while (cursor.moveToNext() && numAdded < numToAdd) { // While there is another audio file to iterate over, iterate over to the next one and:
                val fetchedId = cursor.getLong(idColumnIndex)
                val fetchedDateTaken = cursor.getLong(dateTakenColumnIndex)
                val fetchedDateModified = cursor.getLong(dateModifiedColumnIndex)
                val fetchedSize = cursor.getLong(sizeColumnIndex)
                val fetchedAlbum = if (SDK_INT >= Build.VERSION_CODES.R) cursor.getString(albumColumnIndex) else null
                val fetchedResolution =
                    if (SDK_INT >= Build.VERSION_CODES.R) cursor.getString(resolutionColumnIndex) else null
                val fetchedDescription = cursor.getString(descriptionColumnIndex)
                val fetchedDisplayName = cursor.getString(displayNameColumnIndex)
                val fetchedUri = when (type) {
                    MediaType.PHOTO -> ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        fetchedId
                    )

                    MediaType.VIDEO -> ContentUris.withAppendedId(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        fetchedId
                    )
                }
                val fetchedAbsoluteFilePath = cursor.getString(absoluteFilePathColumnIndex)

                /* Decide album to use */
                val album =
                    fetchedAlbum ?: fetchedAbsoluteFilePath.substringBeforeLast("/").substringAfterLast("/")

                // Check all conditions for media to be added, before then calling addMedia() to add it

                val foundInSession = mediaAdded.find { it.id == fetchedId } != null
                if (foundInSession) continue

                if (unsupportedFileExtensions.contains(fetchedDisplayName.substringAfterLast(".")))
                    continue

                val mediaSatisfiesFilters =
                    fetchedAbsoluteFilePath.contains(mediaFilter.directory)
                            && (fetchedDescription?.contains(mediaFilter.containsText) ?: false
                            || fetchedDisplayName.contains(mediaFilter.containsText))
                            && fetchedSize in mediaFilter.sizeRange
                if (!mediaSatisfiesFilters) continue

//                    val findPhotoByHash = dao.findByHash(fileHash) TODO("Duplicate files feature: user can configure auto-delete or show both duplicates")
                val currentDate = Calendar.getInstance()
                val findById = dao.findByMediaStoreId(fetchedId)

                /** True when: media is not snoozed, or the snoozeUntil date has passed */
                val snoozeHasPassed =
                    findById?.snoozedUntil == null || findById.snoozedUntil <= currentDate.timeInMillis
                if (!snoozeHasPassed) continue

                val hasBeenSwiped = findById != null && listOf(MediaStatus.DELETE, MediaStatus.KEEP).contains(
                    findById.status
                )
                val swipeRetentionTimeHasPassed =
                    if (swipeRetentionTimeMillis != 0L) // value of 0 means remember swipes forever
                        (findById?.dateModified
                            ?: Long.MAX_VALUE) <= currentDate.timeInMillis - swipeRetentionTimeMillis
                    else false
                if (hasBeenSwiped && !swipeRetentionTimeHasPassed) continue

                addMedia(
                    add = { mediaItem ->
                        mediaAdded.add(mediaItem)
                        numAdded++
                        onAddMedia(mediaItem)
                    },
                    absoluteFilePath = fetchedAbsoluteFilePath,
                    dateTaken = fetchedDateTaken,
                    mediaStoreDateModified = fetchedDateModified,
                    uri = fetchedUri,
                    type = type,
                    id = fetchedId,
                    size = fetchedSize,
                    album = fetchedAlbum,
                    description = fetchedDescription,
                    displayName = fetchedDisplayName,
                    resolution = fetchedResolution,
                    statisticsEnabled = statisticsEnabled
                )

//                    when {
//                        /* Photo MediaStore ID is in the database AND has been swiped (DELETE OR KEEP) */
//                        ((photoFoundInSession || photoInDatabaseAndSwiped)) -> {  }
//                        /* Media is in the database but its MediaStore ID has changed.
//                        * This will therefore 1. update the MediaStore ID. 2. if the status is UNSET, add the media */
////                        (findPhotoByHash != null) -> {
////                            Log.v("MediaStore", "Media hash has been found in database, updating id to $fetchedId")
////                            /* Update database */
////                            CoroutineScope(Dispatchers.IO).launch {
////                                dao.update(findPhotoByHash.copy(mediaStoreId = fetchedId, dateModified = currentDate))
////                            }
////                            /* Add media if unset */
////                            if (findPhotoByHash.status == MediaStatus.UNSET)
////                                addMedia()
////                            else if (findPhotoByHash.snoozedUntil != null && findPhotoByHash.snoozedUntil <= currentDate)
////                                addMedia()
////                        }
//                        /* Media has not been swiped */
//                        else -> {
//                            if (findPhotoById?.snoozedUntil == null || findPhotoById.snoozedUntil <= currentDate)
//                                addMedia()
//                        }
//                    }
            }
        }
        return numToAdd - numAdded
    }

    /** Create Media data class, pass into [onAddMedia] & add to app database */
    private fun addMedia(
        absoluteFilePath: String,
        dateTaken: Long,
        mediaStoreDateModified: Long,
        uri: Uri,
        type: MediaType,
        id: Long,
        size: Long,
        album: String?,
        description: String?,
        displayName: String?,
        resolution: String?,
        statisticsEnabled: Boolean,
        add: (Media) -> Unit
    ) {
        /** Function called if file is not found in storage.
         *
         * It is assumed that the file has been deleted externally. If this was not the case, it will be read again
         * from a future MediaStore query
         * */
        fun deleteMediaFromDatabaseIfPresent() =
            CoroutineScope(Dispatchers.IO).launch {
                val mediaInDatabase = dao.findByMediaStoreId(id)
                if (mediaInDatabase != null) {
                    dao.delete(listOf(mediaInDatabase))
                }
            }
        // Decide which date to use
        val dateThreshold = 1823452331 // This is the minimum value a date must be to be used (20 days after epoch)
        val date =
            // If date taken exists, use that
            if (dateTaken > dateThreshold)
                dateTaken
            // Else use the date modified field from MediaStore
            else if (mediaStoreDateModified > dateThreshold)// if date taken is not found, use date added
                mediaStoreDateModified
            // Finally, try extracting date from the file's date modified field/
            else try {
                val fileLastModified = File(absoluteFilePath).lastModified()
                if (fileLastModified == 0L)
                    null
                else fileLastModified
            }
            catch (_: FileNotFoundException) {
                null
            }

        // Calculate hash value
        val fileHash: String
        try {
            contentResolver.openInputStream(uri).use { fileInputStream ->
                fileHash = calculateMediaHash(fileInputStream)
            }
        }
        // If the file is not found, delete from app's database if present as it no longer exists
        catch (_: FileNotFoundException) {
            deleteMediaFromDatabaseIfPresent()
            return
        }

        // Find embedded lat/long of media using EXIF
        var latLong: DoubleArray? = null
        try {
            contentResolver.openInputStream(uri).use {
                if (it != null) {
                    val exifInterface = ExifInterface(it)
                    latLong = exifInterface.latLong
                }
            }
        }
        // If the file is not found, delete from app's database if present as it no longer exists
        catch (_: FileNotFoundException) {
            deleteMediaFromDatabaseIfPresent()
            return
        }

        val mediaClassToAdd = Media(
            id = id,
            uri = uri,
            dateTaken = date,
            size = size,
            type = type,
            location = latLong,
            album = album,
            description = description,
            title = displayName,
            resolution = resolution,
            status = MediaStatus.UNSET,
            fileHash = fileHash,
        )

        add(mediaClassToAdd)
        CoroutineScope(Dispatchers.IO).launch {
            dao.insert(mediaClassToAdd.getMediaStatusEntity(statisticsEnabled)) // Add to database
        }
    }

    suspend fun deleteMedia(
        uris: List<Uri>,
        onDelete: (List<Uri>) -> Unit
    ) {
        if (SDK_INT >= Build.VERSION_CODES.R) {
            val permanentlyDelete =
                dataStoreInterface.getBooleanSettingValue(BooleanPreference.PERMANENTLY_DELETE.setting).first()

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
            startIntentSenderForResult(activity, editPendingIntent.intentSender, DELETE_FILE_GROUP_REQUEST_CODE, null, 0, 0, 0, Bundle.EMPTY)
            // onDelete() is called in onActivityResult, defined in MainActivity.kt
        }
        else {
            val deletedMediaUris = mutableListOf<Uri>()
            uris.forEach { uri ->
                var outputtedRows: Int

                // Try directly deleting files
                try {
                    outputtedRows = contentResolver.delete(
                        uri, null, null
                    )
                }
                // Ask user for permission if app does not have required permissions to delete file
                catch (securityException: SecurityException) {
                    if (SDK_INT >= Build.VERSION_CODES.Q) {
                        val recoverableSecurityException =
                            securityException as? RecoverableSecurityException ?: throw RuntimeException(
                                securityException.message,
                                securityException
                            )

                        val intentSender = recoverableSecurityException.userAction.actionIntent.intentSender
                        startIntentSenderForResult(
                            activity,
                            intentSender,
                            DELETE_FILE_REQUEST_CODE,
                            null,
                            0,
                            0,
                            0,
                            null
                        )
                    } else {
                        throw RuntimeException(securityException.message, securityException)
                    }
                    outputtedRows = 0
                }

                /* If deletion fails, exit. onActivityResult will run this function again when required permissions are granted */
                if (outputtedRows == 0) {
                    Log.e("deleteMedia", "Could not delete $uri :(")
                    return
                } else {
                    Log.d("deleteMedia", "Deleted $uri ^_^")
                    deletedMediaUris.add(uri)
                    if (SDK_INT == Build.VERSION_CODES.Q)
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

/** This function used to calculate the hash of the media file for a future duplicate file feature but was causing
 * memory usage issues and crashes so now returns a placeholder value of -1*/
fun calculateMediaHash(fileInputStream: InputStream?): String {
    return (-1).toString()
//    val digest: MessageDigest = MessageDigest.getInstance("SHA-512")
//
//    /** Limit on the number of bytes to hash of a video file to reduce memory use */
//    val numBytesToHash = 128
//    val availableBytes = fileInputStream?.available() ?: 0
//    val fileBytes: ByteArray
//
//
//    if (SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
//        fileBytes =
//            if (availableBytes > numBytesToHash)
//                fileInputStream?.readNBytes(numBytesToHash) ?: ByteArray(0)
//            else
//                fileInputStream?.readAllBytes() ?: ByteArray(0)
//    } else {
//        val hashLength =
//            if (availableBytes > numBytesToHash)
//                numBytesToHash
//            else
//                availableBytes
//        fileBytes = ByteArray(hashLength)
//        val dataInputStream = DataInputStream(fileInputStream)
//        dataInputStream.readFully(
//            fileBytes,
//            0,
//            hashLength
//        ) // Read less of file for videos to reduce memory use
//    }
//    val hash: ByteArray = digest.digest(fileBytes)
//    return hash.toHexString()
}