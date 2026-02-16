package com.example.photoswooper.experimental.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.photoswooper.data.models.MediaStatus

@Dao
interface DocumentStatusDao {

    @Query("SELECT * FROM documentStatus WHERE (status = :status) AND (dateModified BETWEEN :firstDate AND :secondDate)")
    suspend fun getDeletedBetweenDates(
        firstDate: Long,
        secondDate: Long,
        status: MediaStatus = MediaStatus.DELETE
    ): List<DocumentEntity>

    @Query("SELECT * FROM documentStatus WHERE (status != :status) AND (dateModified BETWEEN :firstDate AND :secondDate)")
    suspend fun getSwipedBetweenDates(
        firstDate: Long,
        secondDate: Long,
        status: MediaStatus = MediaStatus.UNSET
    ): List<DocumentEntity>

    @Query("SELECT * FROM documentStatus WHERE uriString = :uri")
    suspend fun findByUri(uri: String): DocumentEntity?

    @Query("SELECT * FROM documentStatus WHERE fileHash = :hash")
    suspend fun findByHash(hash: String): DocumentEntity?

    @Update
    suspend fun update(vararg entities: DocumentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vararg entities: DocumentEntity)

    @Delete
    suspend fun delete(entityList: List<DocumentEntity>)

    @Query("UPDATE documentStatus SET status = :newStatus WHERE status != :newStatus")
    suspend fun resetAllStatuses(newStatus: MediaStatus = MediaStatus.UNSET)
}
