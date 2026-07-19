package com.example.network

import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DriveService(private val client: OkHttpClient = OkHttpClient()) {

    private val tag = "DriveService"

    data class DriveFile(
        val id: String,
        val name: String,
        val mimeType: String,
        val size: Long,
        val createdTime: String?
    )

    /**
     * Custom RequestBody to stream data from Android Content URI and report progress.
     */
    class ProgressRequestBody(
        private val contentResolver: ContentResolver,
        private val uri: Uri,
        private val contentType: String,
        private val onProgress: (bytesWritten: Long, totalBytes: Long) -> Unit
    ) : RequestBody() {

        override fun contentType(): MediaType? = contentType.toMediaTypeOrNull()

        override fun contentLength(): Long {
            return try {
                contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
            } catch (e: Exception) {
                -1L
            }
        }

        override fun writeTo(sink: BufferedSink) {
            val totalBytes = contentLength()
            val inputStream = contentResolver.openInputStream(uri) ?: throw IOException("Could not open URI: $uri")
            val buffer = ByteArray(8192)
            var bytesWritten = 0L
            inputStream.use { input ->
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    sink.write(buffer, 0, read)
                    bytesWritten += read
                    onProgress(bytesWritten, totalBytes)
                }
            }
        }
    }

    /**
     * Lists files from Google Drive using the given access token.
     * Note: drive.file scope restricts this to files created or opened by this app.
     */
    suspend fun listFiles(accessToken: String): List<DriveFile> = withContext(Dispatchers.IO) {
        val url = "https://www.googleapis.com/drive/v3/files?q=trashed%3Dfalse&fields=files(id,name,mimeType,size,createdTime)&orderBy=createdTime%20desc"
        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("Authorization", "Bearer $accessToken")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Failed to list files: Code ${response.code} ${response.message}")
            }
            val bodyString = response.body?.string() ?: ""
            val json = JSONObject(bodyString)
            val filesArray: JSONArray = json.optJSONArray("files") ?: JSONArray()
            val result = mutableListOf<DriveFile>()
            for (i in 0 until filesArray.length()) {
                val obj = filesArray.getJSONObject(i)
                result.add(
                    DriveFile(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        mimeType = obj.getString("mimeType"),
                        size = obj.optLong("size", 0L),
                        createdTime = obj.optString("createdTime", null)
                    )
                )
            }
            result
        }
    }

    /**
     * Uploads a file using the Resumable Upload API and reports real-time progress.
     */
    suspend fun uploadFile(
        contentResolver: ContentResolver,
        uri: Uri,
        fileName: String,
        mimeType: String,
        accessToken: String,
        onProgress: (progress: Float) -> Unit
    ): String = withContext(Dispatchers.IO) {
        // Step 1: Initiate Resumable Session
        val metadataJson = JSONObject().apply {
            put("name", fileName)
            put("mimeType", mimeType)
        }

        val requestBody = metadataJson.toString().toRequestBody("application/json; charset=UTF-8".toMediaTypeOrNull())

        val initiateRequest = Request.Builder()
            .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable")
            .post(requestBody)
            .addHeader("Authorization", "Bearer $accessToken")
            .addHeader("Content-Type", "application/json; charset=UTF-8")
            .build()

        val sessionUrl = client.newCall(initiateRequest).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: ""
                Log.e(tag, "Initiate failed: $errorBody")
                throw IOException("Failed to initiate resumable session: Code ${response.code} - $errorBody")
            }
            response.header("Location") ?: throw IOException("Location header missing in session initiation response")
        }

        Log.d(tag, "Resumable session initiated at: $sessionUrl")

        // Step 2: Stream bytes to the session URL with progress
        val uploadBody = ProgressRequestBody(contentResolver, uri, mimeType) { bytesWritten, totalBytes ->
            if (totalBytes > 0) {
                val progressFraction = bytesWritten.toFloat() / totalBytes.toFloat()
                onProgress(progressFraction)
            }
        }

        val uploadRequest = Request.Builder()
            .url(sessionUrl)
            .put(uploadBody)
            .build()

        client.newCall(uploadRequest).execute().use { response ->
            if (!response.isSuccessful && response.code != 201 && response.code != 200) {
                val errorBody = response.body?.string() ?: ""
                Log.e(tag, "Upload bytes failed: $errorBody")
                throw IOException("Failed to stream bytes to session: Code ${response.code} - $errorBody")
            }
            val bodyString = response.body?.string() ?: ""
            val responseJson = JSONObject(bodyString)
            responseJson.getString("id")
        }
    }
}
