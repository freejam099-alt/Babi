package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "uploads")
data class UploadItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val mimeType: String,
    val size: Long,
    val progress: Float = 0f,
    val status: String = "PENDING", // PENDING, UPLOADING, SUCCESS, FAILED
    val driveId: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val errorMessage: String? = null
)
