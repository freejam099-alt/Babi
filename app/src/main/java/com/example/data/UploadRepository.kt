package com.example.data

import kotlinx.coroutines.flow.Flow

class UploadRepository(private val uploadDao: UploadDao) {
    val allUploads: Flow<List<UploadItem>> = uploadDao.getAllUploads()

    suspend fun getById(id: Int): UploadItem? = uploadDao.getUploadById(id)

    suspend fun insert(upload: UploadItem): Long = uploadDao.insertUpload(upload)

    suspend fun update(upload: UploadItem) = uploadDao.updateUpload(upload)

    suspend fun delete(upload: UploadItem) = uploadDao.deleteUpload(upload)

    suspend fun clearAll() = uploadDao.clearAllUploads()
}
