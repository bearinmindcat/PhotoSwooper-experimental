/*
 * SPDX-FileCopyrightText: 2025 Loowiz <loowiz@envs.net>
 *
 *  SPDX-License-Identifier: AGPL-3.0-only
 */

package com.example.photoswooper.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.photoswooper.data.models.MediaStatus

/**
 * Database Access Object that provides the functions to interact with the database
 */
@Dao
interface MediaStatusDao {

    @Query("SELECT * FROM mediaStatus WHERE (status = :status) AND (dateModified BETWEEN :firstDate AND :secondDate)")
    suspend fun getDeletedBetweenDates(
        firstDate: Long,
        secondDate: Long,
        status: MediaStatus = MediaStatus.DELETE
    ): List<MediaEntity>

    @Query("SELECT * FROM mediaStatus WHERE (status != :status) AND (dateModified BETWEEN :firstDate AND :secondDate)")
    suspend fun getSwipedMediaBetweenDates(
        firstDate: Long,
        secondDate: Long,
        status: MediaStatus = MediaStatus.UNSET
    ): List<MediaEntity>

    @Query("SELECT * FROM mediaStatus WHERE fileHash = :hash")
    suspend fun findByHash(hash: String): MediaEntity?

    @Query("SELECT * FROM mediaStatus WHERE mediaStoreId = :id")
    suspend fun findByMediaStoreId(id: Long): MediaEntity?

    @Update
    suspend fun update(vararg mediaEntities: MediaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vararg mediaEntities: MediaEntity)

    @Delete
    suspend fun delete(mediaEntityList: MediaEntity)
}