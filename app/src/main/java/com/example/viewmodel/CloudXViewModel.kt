package com.example.viewmodel

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.UploadItem
import com.example.data.UploadRepository
import com.example.network.DriveService
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface AuthState {
    object LoggedOut : AuthState
    object Loading : AuthState
    data class LoggedIn(
        val displayName: String?,
        val email: String?,
        val photoUrl: String?,
        val accessToken: String,
        val account: GoogleSignInAccount
    ) : AuthState
    data class Error(val message: String) : AuthState
}

class CloudXViewModel(application: Application) : AndroidViewModel(application) {

    private val tag = "CloudXViewModel"
    private val database = AppDatabase.getDatabase(application)
    private val repository = UploadRepository(database.uploadDao())
    private val driveService = DriveService()

    // Authentication flow
    private val _authState = MutableStateFlow<AuthState>(AuthState.LoggedOut)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    // Google Sign-In Client
    val signInClient: GoogleSignInClient by lazy {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .requestScopes(Scope("https://www.googleapis.com/auth/drive.file"))
            .build()
        GoogleSignIn.getClient(application, gso)
    }

    // Local uploads tracking (from Room)
    val localUploads: StateFlow<List<UploadItem>> = repository.allUploads
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Remote Google Drive files uploaded by our app
    private val _driveFiles = MutableStateFlow<List<DriveService.DriveFile>>(emptyList())
    val driveFiles: StateFlow<List<DriveService.DriveFile>> = _driveFiles.asStateFlow()

    // State for loading/error in remote files listing
    private val _filesLoading = MutableStateFlow(false)
    val filesLoading: StateFlow<Boolean> = _filesLoading.asStateFlow()

    private val _filesError = MutableStateFlow<String?>(null)
    val filesError: StateFlow<String?> = _filesError.asStateFlow()

    init {
        // Try silent/previous sign-in on init
        checkExistingSignIn()
    }

    private fun checkExistingSignIn() {
        val lastAccount = GoogleSignIn.getLastSignedInAccount(getApplication())
        if (lastAccount != null) {
            handleSignInSuccess(lastAccount)
        }
    }

    /**
     * Resolves the Google account into an access token and enters the LoggedIn state.
     */
    fun handleSignInSuccess(account: GoogleSignInAccount) {
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            try {
                val token = withContext(Dispatchers.IO) {
                    val scopes = "oauth2:" +
                            "https://www.googleapis.com/auth/drive.file " +
                            "https://www.googleapis.com/auth/userinfo.profile " +
                            "https://www.googleapis.com/auth/userinfo.email " +
                            "openid"
                    GoogleAuthUtil.getToken(
                        getApplication(),
                        account.account ?: throw Exception("Account is null"),
                        scopes
                    )
                }
                Log.d(tag, "Successfully acquired Google Drive access token!")
                _authState.value = AuthState.LoggedIn(
                    displayName = account.displayName,
                    email = account.email,
                    photoUrl = account.photoUrl?.toString(),
                    accessToken = token,
                    account = account
                )
                // Fetch existing files from Google Drive
                fetchDriveFiles()
            } catch (e: UserRecoverableAuthException) {
                // In a production app, the user might need to resolve a consent screen.
                // Since AI Studio OAuth has authorized, it should be clean.
                Log.e(tag, "Recoverable auth exception", e)
                _authState.value = AuthState.Error("Auth action required: ${e.message}")
            } catch (e: Exception) {
                Log.e(tag, "Failed to get access token", e)
                _authState.value = AuthState.Error(e.message ?: "Authentication failed")
            }
        }
    }

    /**
     * Signs out from Google and local session.
     */
    fun logout() {
        viewModelScope.launch {
            try {
                // Clear the active token from GoogleAuthUtil cache so next logins can renew if scopes change
                val current = _authState.value
                if (current is AuthState.LoggedIn) {
                    withContext(Dispatchers.IO) {
                        try {
                            GoogleAuthUtil.clearToken(getApplication(), current.accessToken)
                        } catch (e: Exception) {
                            Log.e(tag, "Failed to clear token", e)
                        }
                    }
                }
                signInClient.signOut()
                _authState.value = AuthState.LoggedOut
                _driveFiles.value = emptyList()
            } catch (e: Exception) {
                Log.e(tag, "Sign out error", e)
                _authState.value = AuthState.Error("Sign out error: ${e.localizedMessage}")
            }
        }
    }

    /**
     * Refresh Google Drive files
     */
    fun fetchDriveFiles() {
        val currentAuth = _authState.value
        if (currentAuth !is AuthState.LoggedIn) return

        viewModelScope.launch {
            _filesLoading.value = true
            _filesError.value = null
            try {
                val files = driveService.listFiles(currentAuth.accessToken)
                _driveFiles.value = files
            } catch (e: Exception) {
                Log.e(tag, "Failed to fetch drive files", e)
                _filesError.value = e.localizedMessage ?: "Failed to load Google Drive files"
            } finally {
                _filesLoading.value = false
            }
        }
    }

    /**
     * Enqueues and executes a file upload to Google Drive.
     */
    fun uploadFile(
        contentResolver: ContentResolver,
        uri: Uri,
        fileName: String,
        mimeType: String,
        size: Long
    ) {
        val currentAuth = _authState.value as? AuthState.LoggedIn ?: return

        viewModelScope.launch {
            // Step 1: Record initial pending record in local DB
            var localUpload = UploadItem(
                name = fileName,
                mimeType = mimeType,
                size = size,
                progress = 0f,
                status = "PENDING"
            )
            val rowId = repository.insert(localUpload)
            localUpload = localUpload.copy(id = rowId.toInt())

            // Step 2: Start uploading
            localUpload = localUpload.copy(status = "UPLOADING")
            repository.update(localUpload)

            try {
                var lastProgressUpdate = 0f
                val driveId = driveService.uploadFile(
                    contentResolver = contentResolver,
                    uri = uri,
                    fileName = fileName,
                    mimeType = mimeType,
                    accessToken = currentAuth.accessToken
                ) { progress ->
                    // Throttling database writes to avoid performance hiccups
                    if (progress - lastProgressUpdate >= 0.05f || progress >= 0.99f) {
                        lastProgressUpdate = progress
                        viewModelScope.launch {
                            repository.update(localUpload.copy(progress = progress))
                        }
                    }
                }

                // Step 3: Record successful completion
                repository.update(
                    localUpload.copy(
                        progress = 1.0f,
                        status = "SUCCESS",
                        driveId = driveId
                    )
                )
                Log.d(tag, "File successfully uploaded. Drive ID: $driveId")

                // Auto refresh remote file list
                fetchDriveFiles()

            } catch (e: Exception) {
                Log.e(tag, "Upload error for file $fileName", e)
                repository.update(
                    localUpload.copy(
                        status = "FAILED",
                        errorMessage = e.localizedMessage ?: "Unknown upload error"
                    )
                )
            }
        }
    }

    /**
     * Local Room DB operations for visual history
     */
    fun deleteLocalUpload(item: UploadItem) {
        viewModelScope.launch {
            repository.delete(item)
        }
    }

    fun clearLocalHistory() {
        viewModelScope.launch {
            // Find all that are not UPLOADING/PENDING
            val list = localUploads.value
            list.forEach { item ->
                if (item.status != "UPLOADING" && item.status != "PENDING") {
                    repository.delete(item)
                }
            }
        }
    }
}
