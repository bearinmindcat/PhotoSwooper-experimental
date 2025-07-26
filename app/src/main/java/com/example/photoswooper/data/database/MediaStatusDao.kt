package com.example.photoswooper.data.database

import androidx.room.*
import com.example.photoswooper.data.models.PhotoStatus

/**
 * Database Access Object that provides the functions to interact with the database
 */
@Dao
interface MediaStatusDao {

    @Query("SELECT * FROM mediaStatus WHERE (status = :status) AND (dateModified BETWEEN :firstDate AND :secondDate)")
    suspend fun getDeletedBetweenDates(firstDate: Long, secondDate: Long, status: PhotoStatus = PhotoStatus.DELETE): List<MediaStatus>?

    @Query("SELECT * FROM mediaStatus WHERE dateModified BETWEEN :firstDate AND :secondDate")
    suspend fun getAllBetweenDates(firstDate: Long, secondDate: Long): List<MediaStatus>?

    @Query("SELECT * FROM mediaStatus WHERE fileHash = :hash LIMIT 1")
    suspend fun findByHash(hash: String): MediaStatus?

    @Query("SELECT * FROM mediaStatus WHERE mediaStoreId = :id LIMIT 1")
    suspend fun findByMediaStoreId(id: Long): MediaStatus?

    @Update
    suspend fun update(vararg mediaStatus: MediaStatus)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vararg mediaStatus: MediaStatus)

    @Delete
    suspend fun delete(mediaStatusList: MediaStatus)
}