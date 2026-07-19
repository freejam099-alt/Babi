package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UploadDao {
    @Query("SELECT * FROM uploads ORDER BY timestamp DESC")
    fun getAllUploads(): Flow<List<UploadItem>>

    @Query("SELECT * FROM uploads WHERE id = :id")
    suspend fun getUploadById(id: Int): UploadItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUpload(upload: UploadItem): Long

    @Update
    suspend fun updateUpload(upload: UploadItem)

    @Delete
    suspend fun deleteUpload(upload: UploadItem)

    @Query("DELETE FROM uploads")
    suspend fun clearAllUploads()
}
