package com.example.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.geometry.Offset
import com.skydoves.cloudy.Cloudy
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.UploadItem
import com.example.network.DriveService
import com.example.viewmodel.AuthState
import com.example.viewmodel.CloudXViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudXApp(viewModel: CloudXViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val localUploads by viewModel.localUploads.collectAsStateWithLifecycle()
    val driveFiles by viewModel.driveFiles.collectAsStateWithLifecycle()
    val filesLoading by viewModel.filesLoading.collectAsStateWithLifecycle()
    val filesError by viewModel.filesError.collectAsStateWithLifecycle()

    var activeTab by remember { mutableStateOf(0) }
    var fileToDelete by remember { mutableStateOf<DriveService.DriveFile?>(null) }
    var currentlyPlayingFileId by remember { mutableStateOf<String?>(null) }

    // Launcher for File Picker
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val contentResolver = context.contentResolver
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use { c ->
                if (c.moveToFirst()) {
                    val nameIndex = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = c.getColumnIndex(OpenableColumns.SIZE)
                    val name = if (nameIndex >= 0) c.getString(nameIndex) else "Unknown_file"
                    val size = if (sizeIndex >= 0) c.getLong(sizeIndex) else 0L
                    val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
                    viewModel.uploadFile(contentResolver, uri, name, mimeType, size)
                }
            }
        }
    }

    // Launcher for Google Sign-In
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            if (account != null) {
                viewModel.handleSignInSuccess(account)
            } else {
                viewModel.setAuthError("Google Sign-In dibatalkan atau data akun kosong.")
            }
        } catch (e: Exception) {
            Log.e("CloudXApp", "Google Sign-In failed", e)
            val apiException = e as? ApiException
            val errorCode = apiException?.statusCode ?: -1
            val errorMsg = getCommonStatusMessage(errorCode)
            viewModel.setAuthError("Masuk Google gagal (Kode: $errorCode). $errorMsg: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Cloud,
                            contentDescription = "CloudX Logo",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "CloudX",
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            letterSpacing = 1.sp
                        )
                    }
                },
                actions = {
                    if (authState is AuthState.LoggedIn) {
                        val loggedIn = authState as AuthState.LoggedIn
                        IconButton(
                            onClick = { viewModel.logout() },
                            modifier = Modifier.testTag("logout_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Logout,
                                contentDescription = "Logout",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { innerPadding ->
        LiquidGlassBackground(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val currentAuth = authState) {
                is AuthState.LoggedOut -> {
                    val (sha1, pkg) = remember { viewModel.getAppSha1AndPackageName() }
                    LoginScreen(
                        packageName = pkg,
                        sha1 = sha1,
                        onLoginClick = {
                            googleSignInLauncher.launch(viewModel.signInClient.signInIntent)
                        }
                    )
                }
                is AuthState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is AuthState.ResolvingAuth -> {
                    val recoveryLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.StartActivityForResult()
                    ) { result ->
                        val lastAccount = GoogleSignIn.getLastSignedInAccount(context)
                        if (lastAccount != null) {
                            viewModel.handleSignInSuccess(lastAccount)
                        } else {
                            viewModel.logout()
                        }
                    }

                    LaunchedEffect(currentAuth.intent) {
                        try {
                            recoveryLauncher.launch(currentAuth.intent)
                        } catch (e: Exception) {
                            Log.e("CloudXApp", "Failed to launch resolution intent", e)
                        }
                    }

                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(32.dp)
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Menyelesaikan otentikasi Google...",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onBackground,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Harap selesaikan verifikasi akun jika ada dialog pop-up.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                is AuthState.Error -> {
                    val (sha1, pkg) = remember { viewModel.getAppSha1AndPackageName() }
                    LoginScreen(
                        errorMessage = currentAuth.message,
                        packageName = pkg,
                        sha1 = sha1,
                        onLoginClick = {
                            googleSignInLauncher.launch(viewModel.signInClient.signInIntent)
                        }
                    )
                }
                is AuthState.LoggedIn -> {
                    LaunchedEffect(Unit) {
                        viewModel.fetchDriveFiles()
                    }

                    val driveQuota by viewModel.driveQuota.collectAsStateWithLifecycle()

                    // Confirmation Dialog
                    if (fileToDelete != null) {
                        AlertDialog(
                            onDismissRequest = { fileToDelete = null },
                            title = {
                                Text(
                                    text = "Hapus Berkas?",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                            },
                            text = {
                                Text(
                                    text = "Apakah Anda yakin ingin menghapus berkas \"${fileToDelete?.name}\" dari Google Drive? Tindakan ini tidak dapat dibatalkan.",
                                    fontSize = 14.sp
                                )
                            },
                            confirmButton = {
                                Button(
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                    onClick = {
                                        fileToDelete?.let {
                                            viewModel.deleteRemoteFile(it.id)
                                        }
                                        fileToDelete = null
                                    }
                                ) {
                                    Text("Hapus", color = MaterialTheme.colorScheme.onError)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { fileToDelete = null }) {
                                    Text("Batal")
                                }
                            }
                        )
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = 80.dp) // Leave space for floating dock
                        ) {
                            when (activeTab) {
                                0 -> {
                                    DriveFilesTab(
                                        files = driveFiles,
                                        isLoading = filesLoading,
                                        error = filesError,
                                        quota = driveQuota,
                                        onRefresh = { viewModel.fetchDriveFiles() },
                                        onDeleteRequest = { file -> fileToDelete = file },
                                        currentlyPlayingFileId = currentlyPlayingFileId,
                                        onPlayToggle = { file ->
                                            currentlyPlayingFileId = if (currentlyPlayingFileId == file.id) null else file.id
                                        }
                                    )
                                }
                                1 -> {
                                    UploadStationScreen(
                                        uploads = localUploads,
                                        onSelectFileClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                                        onDeleteLocal = { viewModel.deleteLocalUpload(it) }
                                    )
                                }
                                2 -> {
                                    UploadProgressTab(
                                        uploads = localUploads,
                                        onDelete = { viewModel.deleteLocalUpload(it) },
                                        onClearAll = { viewModel.clearLocalHistory() }
                                    )
                                }
                                3 -> {
                                    UserProfileScreen(
                                        user = currentAuth,
                                        quota = driveQuota,
                                        onLogoutClick = { viewModel.logout() }
                                    )
                                }
                            }
                        }

                        // iOS Floating Dock bottom navigation bar
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            BottomDockNavigationBar(
                                activeTab = activeTab,
                                onTabSelected = { activeTab = it }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LoginScreen(
    errorMessage: String? = null,
    packageName: String = "",
    sha1: String = "",
    onLoginClick: () -> Unit
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        GlassCard(
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.CloudUpload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(80.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "CloudX",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "File Manager Google Drive Native",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Unggah file zip, rar, mp3, mp4, wav, dan file umum lainnya langsung ke cloud Google Drive Anda.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
            }
        }

        if (errorMessage != null) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = "Error",
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Gagal Masuk Google",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 16.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                    
                    if (errorMessage.contains("10") || errorMessage.contains("12500") || errorMessage.contains("DEVELOPER_ERROR") || errorMessage.contains("SIGN_IN_FAILED")) {
                        Spacer(modifier = Modifier.height(12.dp))
                        // Compile-safe horizontal divider line
                        Spacer(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.2f))
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "💡 Petunjuk Solusi:",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Sertifikat SHA-1 aplikasi Anda belum terdaftar di kredensial Google Cloud Console. Silakan buka dashboard Google Cloud Anda dan daftarkan Kredensial Android baru dengan info berikut:",
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        SelectionContainer {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        MaterialTheme.colorScheme.onError.copy(alpha = 0.3f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = "Nama Paket (Package Name):",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = packageName,
                                    fontSize = 12.sp,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Sidik Jari SHA-1 (SHA-1 Fingerprint):",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = sha1,
                                    fontSize = 12.sp,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Styled Google Sign-In Button with liquid glass glowing border
        Button(
            onClick = onLoginClick,
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White.copy(alpha = 0.1f),
                contentColor = Color.White
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .border(
                    width = 1.5.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF00E5FF),
                            Color(0xFFA033FF)
                        )
                    ),
                    shape = RoundedCornerShape(50)
                )
                .testTag("google_login_button")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = "Google Icon",
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Masuk dengan Google",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun UserProfileCard(user: AuthState.LoggedIn) {
    GlassCard(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = user.photoUrl ?: "https://www.gravatar.com/avatar/00000000000000000000000000000000?d=mp&f=y",
                contentDescription = "Profile Photo",
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user.displayName ?: "User CloudX",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = user.email ?: "",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            ) {
                Text(
                    text = "drive.file",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriveFilesTab(
    files: List<DriveService.DriveFile>,
    isLoading: Boolean,
    error: String?,
    quota: DriveService.DriveQuota?,
    onRefresh: () -> Unit,
    onDeleteRequest: (DriveService.DriveFile) -> Unit,
    currentlyPlayingFileId: String?,
    onPlayToggle: (DriveService.DriveFile) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var activeFilter by remember { mutableStateOf("semua") }
    var isGridView by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Storage Quota Indicator at the very top
        quota?.let {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.04f)
                ),
                border = BorderStroke(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.12f),
                            Color.White.copy(alpha = 0.02f)
                        )
                    )
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 4.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Penyimpanan Google Drive",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        val percent = if (it.limit > 0) (it.usage.toDouble() / it.limit * 100).toInt() else 0
                        Text(
                            text = "$percent%",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { if (it.limit > 0) it.usage.toFloat() / it.limit else 0f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Terpakai ${formatFileSize(it.usage)} dari ${formatFileSize(it.limit)}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Search Bar in Google Drive Tab
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Cari berkas di Drive...", fontSize = 14.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Cari") },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Bersihkan")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White.copy(alpha = 0.8f),
                focusedBorderColor = Color(0xFF00E5FF),
                unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                focusedContainerColor = Color.White.copy(alpha = 0.05f),
                unfocusedContainerColor = Color.White.copy(alpha = 0.02f),
                focusedPlaceholderColor = Color.White.copy(alpha = 0.5f),
                unfocusedPlaceholderColor = Color.White.copy(alpha = 0.4f),
                focusedLeadingIconColor = Color(0xFF00E5FF),
                unfocusedLeadingIconColor = Color.White.copy(alpha = 0.5f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        )

        // Filter Chips Row (semua, video, audio, zip, dokumen)
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val filters = listOf("semua", "video", "audio", "zip", "dokumen")
            items(filters) { filter ->
                FilterChip(
                    selected = activeFilter == filter,
                    onClick = { activeFilter = filter },
                    label = { Text(filter.replaceFirstChar { it.uppercase() }) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF00E5FF).copy(alpha = 0.2f),
                        selectedLabelColor = Color(0xFF00E5FF),
                        unselectedContainerColor = Color.White.copy(alpha = 0.04f),
                        unselectedLabelColor = Color.White.copy(alpha = 0.7f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }

        // Header and Grid/List toggle view
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Daftar Berkas",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { isGridView = !isGridView }) {
                    Icon(
                        imageVector = if (isGridView) Icons.Default.List else Icons.Default.GridView,
                        contentDescription = if (isGridView) "Mode List" else "Mode Grid",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onRefresh) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Segarkan")
                }
            }
        }

        // Filtered and Searched file list calculations
        val filteredFiles = remember(files, searchQuery, activeFilter) {
            files.filter { file ->
                val matchesSearch = file.name.contains(searchQuery, ignoreCase = true)
                val ext = file.name.substringAfterLast('.', "").lowercase()
                val matchesFilter = when (activeFilter) {
                    "semua" -> true
                    "video" -> file.mimeType.startsWith("video/") || listOf("mp4", "mkv", "avi", "mov", "webm").contains(ext)
                    "audio" -> file.mimeType.startsWith("audio/") || listOf("mp3", "wav", "m4a", "flac", "ogg").contains(ext)
                    "zip" -> listOf("zip", "rar", "7z", "tar", "gz").contains(ext)
                    "dokumen" -> file.mimeType.contains("document") || file.mimeType.contains("pdf") || file.mimeType.contains("text") || listOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt").contains(ext)
                    else -> true
                }
                matchesSearch && matchesFilter
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            if (isLoading && files.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (error != null && files.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(60.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = error,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onRefresh) {
                        Text("Coba Lagi")
                    }
                }
            } else if (filteredFiles.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.FolderOpen,
                    title = "Tidak ada berkas",
                    desc = if (searchQuery.isNotEmpty() || activeFilter != "semua") "Tidak ditemukan berkas yang cocok dengan filter atau kata kunci Anda." else "Silakan ketuk tab 'Upload' di bawah untuk mulai mengunggah file Anda."
                )
            } else {
                if (isGridView) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        items(filteredFiles, key = { it.id }) { file ->
                            GridFileItem(
                                file = file,
                                onClick = {
                                    val isMedia = file.mimeType.startsWith("audio/") || file.mimeType.startsWith("video/") ||
                                            listOf("mp3", "wav", "m4a", "mp4", "mkv", "avi").contains(file.name.substringAfterLast('.', "").lowercase())
                                    if (isMedia) {
                                        onPlayToggle(file)
                                    }
                                },
                                onDeleteClick = { onDeleteRequest(file) }
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(filteredFiles, key = { it.id }) { file ->
                            val isPlaying = currentlyPlayingFileId == file.id
                            SwipeToDeleteContainer(
                                onDelete = { onDeleteRequest(file) }
                            ) {
                                DriveFileItem(
                                    file = file,
                                    isPlaying = isPlaying,
                                    onClick = {
                                        val isMedia = file.mimeType.startsWith("audio/") || file.mimeType.startsWith("video/") ||
                                                listOf("mp3", "wav", "m4a", "mp4", "mkv", "avi").contains(file.name.substringAfterLast('.', "").lowercase())
                                        if (isMedia) {
                                            onPlayToggle(file)
                                        }
                                    },
                                    onClosePreview = { onPlayToggle(file) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UploadStationScreen(
    uploads: List<UploadItem>,
    onSelectFileClick: () -> Unit,
    onDeleteLocal: (UploadItem) -> Unit
) {
    val activeUploads = remember(uploads) {
        uploads.filter { it.status == "PENDING" || it.status == "UPLOADING" }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(modifier = Modifier.height(20.dp))
        GlassCard(
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.CloudUpload,
                    contentDescription = null,
                    tint = Color(0xFF00E5FF),
                    modifier = Modifier.size(72.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Unggah Berkas Baru",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Unggah zip, rar, 7z, mp3, mp4, wav, pdf, doc, atau format lainnya langsung ke Google Drive.",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onSelectFileClick,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.1f),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .border(
                            width = 1.5.dp,
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF00E5FF),
                                    Color(0xFFA033FF)
                                )
                            ),
                            shape = RoundedCornerShape(50)
                        )
                        .testTag("upload_station_picker_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(imageVector = Icons.Default.UploadFile, contentDescription = null)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Pilih & Unggah File", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
        }

        if (activeUploads.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                Text(
                    text = "Sedang Mengunggah (${activeUploads.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(activeUploads, key = { it.id }) { item ->
                    UploadItemRow(
                        item = item,
                        onDelete = { onDeleteLocal(item) }
                    )
                }
            }
        } else {
            Spacer(modifier = Modifier.height(16.dp))
            EmptyState(
                icon = Icons.Default.CheckCircle,
                title = "Semua unggahan selesai",
                desc = "Ketuk tombol di atas untuk mengunggah file baru lainnya."
            )
        }
    }
}

@Composable
fun UploadProgressTab(
    uploads: List<UploadItem>,
    onDelete: (UploadItem) -> Unit,
    onClearAll: () -> Unit
) {
    if (uploads.isEmpty()) {
        EmptyState(
            icon = Icons.Default.History,
            title = "Riwayat kosong",
            desc = "Antrean progres unggah Anda akan muncul di sini saat Anda memilih file."
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Riwayat & Progres Unggah",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = onClearAll) {
                    Text("Bersihkan Riwayat", color = MaterialTheme.colorScheme.error)
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(uploads, key = { it.id }) { item ->
                    UploadItemRow(
                        item = item,
                        onDelete = { onDelete(item) }
                    )
                }
            }
        }
    }
}

@Composable
fun UserProfileScreen(
    user: AuthState.LoggedIn,
    quota: DriveService.DriveQuota?,
    onLogoutClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        // High fidelity user display
        AsyncImage(
            model = user.photoUrl ?: "https://www.gravatar.com/avatar/00000000000000000000000000000000?d=mp&f=y",
            contentDescription = "Profile Photo",
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .border(3.dp, Color(0xFF00E5FF), CircleShape)
                .background(Color(0xFF00E5FF).copy(alpha = 0.1f)),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = user.displayName ?: "Pengguna CloudX",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Text(
            text = user.email ?: "",
            fontSize = 14.sp,
            color = Color.White.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Info Cards about permission
        GlassCard(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Otorisasi Aplikasi",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color(0xFF00E5FF)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Aktif",
                        tint = Color(0xFF00FF7F),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Google Drive API: Scope drive.file",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Aplikasi hanya memiliki akses ke file yang diunggah atau dibuat melalui CloudX.",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.6f),
                    lineHeight = 15.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Quota display
        quota?.let {
            GlassCard(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Kapasitas Akun",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color(0xFF00E5FF)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Terpakai:", fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f))
                        Text(formatFileSize(it.usage), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Kapasitas Total:", fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f))
                        Text(formatFileSize(it.limit), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Big Red Sign Out
        Button(
            onClick = onLogoutClick,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            shape = RoundedCornerShape(50),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("profile_logout_button")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(imageVector = Icons.Default.Logout, contentDescription = "Keluar")
                Spacer(modifier = Modifier.width(10.dp))
                Text("Keluar dari Akun", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun BottomDockNavigationBar(
    activeTab: Int,
    onTabSelected: (Int) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .shadow(
                elevation = 20.dp,
                shape = RoundedCornerShape(28.dp),
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.4f),
                spotColor = Color.Black.copy(alpha = 0.4f)
            )
    ) {
        Cloudy(
            radius = 20,
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(28.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.05f))
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = Color.White.copy(alpha = 0.02f),
                    shape = RoundedCornerShape(28.dp)
                )
                .border(
                    width = 1.2.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.2f),
                            Color.White.copy(alpha = 0.02f)
                        )
                    ),
                    shape = RoundedCornerShape(28.dp)
                )
                .padding(vertical = 10.dp, horizontal = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val items = listOf(
                    Triple(Icons.Default.CloudQueue, "Drive", 0),
                    Triple(Icons.Default.CloudUpload, "Upload", 1),
                    Triple(Icons.Default.History, "Riwayat", 2),
                    Triple(Icons.Default.Person, "Profil", 3)
                )

                items.forEach { (icon, label, index) ->
                    val isSelected = activeTab == index
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(onClick = { onTabSelected(index) })
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = if (isSelected) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.45f),
                            modifier = Modifier.size(if (isSelected) 26.dp else 22.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.45f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SwipeToDeleteContainer(
    onDelete: () -> Unit,
    content: @Composable () -> Unit
) {
    val swipeLimit = -240f
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
    ) {
        // Red background with trash bin icon
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color(0xFFC62828))
                .clickable {
                    scope.launch {
                        onDelete()
                        offsetX.snapTo(0f)
                    }
                },
            contentAlignment = Alignment.CenterEnd
        ) {
            Row(
                modifier = Modifier
                    .padding(end = 24.dp)
                    .fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Hapus Berkas",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Hapus",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }

        // Sliding content card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .background(MaterialTheme.colorScheme.surface)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            val newOffset = (offsetX.value + dragAmount).coerceIn(swipeLimit, 0f)
                            scope.launch {
                                offsetX.snapTo(newOffset)
                            }
                        },
                        onDragEnd = {
                            scope.launch {
                                if (offsetX.value < swipeLimit * 0.5f) {
                                    offsetX.animateTo(swipeLimit, tween(200))
                                } else {
                                    offsetX.animateTo(0f, tween(200))
                                }
                            }
                        }
                    )
                }
        ) {
            content()
        }
    }
}

@Composable
fun GridFileItem(
    file: DriveService.DriveFile,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.03f)),
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.12f),
                    Color.White.copy(alpha = 0.02f)
                )
            )
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(6.dp)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getIconForMimeType(file.mimeType, file.name),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = file.name,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = formatFileSize(file.size),
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            IconButton(
                onClick = onDeleteClick,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Hapus",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun InlineMediaPreviewPlayer(
    file: DriveService.DriveFile,
    onClose: () -> Unit
) {
    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0.15f) }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (isPlaying && progress < 1f) {
                kotlinx.coroutines.delay(1000)
                progress = (progress + 0.05f).coerceAtMost(1f)
            }
            if (progress >= 1f) {
                isPlaying = false
            }
        }
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (file.mimeType.startsWith("video/")) Icons.Default.Movie else Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Memutar Preview: ${file.name}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 200.dp)
                    )
                }
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Tutup Preview",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                IconButton(onClick = { progress = (progress - 0.1f).coerceAtLeast(0f) }) {
                    Icon(imageVector = Icons.Default.FastRewind, contentDescription = "Rewind", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(
                    onClick = { isPlaying = !isPlaying },
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
                IconButton(onClick = { progress = (progress + 0.1f).coerceAtMost(1f) }) {
                    Icon(imageVector = Icons.Default.FastForward, contentDescription = "Forward", tint = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Slider(
                value = progress,
                onValueChange = { progress = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val totalSeconds = 210
                val currentSeconds = (progress * totalSeconds).toInt()
                Text(
                    text = String.format("%02d:%02d", currentSeconds / 60, currentSeconds % 60),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = String.format("%02d:%02d", totalSeconds / 60, totalSeconds % 60),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun DriveFileItem(
    file: DriveService.DriveFile,
    isPlaying: Boolean = false,
    onClick: () -> Unit = {},
    onClosePreview: () -> Unit = {}
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.03f)),
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.12f),
                    Color.White.copy(alpha = 0.02f)
                )
            )
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = getIconForMimeType(file.mimeType, file.name),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = file.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = formatFileSize(file.size),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (file.createdTime != null) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "•", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = formatDriveDate(file.createdTime),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (isPlaying) {
                InlineMediaPreviewPlayer(
                    file = file,
                    onClose = onClosePreview
                )
            }
        }
    }
}

@Composable
fun UploadItemRow(
    item: UploadItem,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                when (item.status) {
                                    "SUCCESS" -> Color(0xFFE8F5E9)
                                    "FAILED" -> Color(0xFFFFEBEE)
                                    else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (item.status) {
                                "SUCCESS" -> Icons.Default.CheckCircle
                                "FAILED" -> Icons.Default.Error
                                "UPLOADING" -> Icons.Default.CloudSync
                                else -> Icons.Default.AccessTime
                            },
                            contentDescription = null,
                            tint = when (item.status) {
                                "SUCCESS" -> Color(0xFF2E7D32)
                                "FAILED" -> Color(0xFFC62828)
                                else -> MaterialTheme.colorScheme.primary
                            },
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = formatFileSize(item.size),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Delete or Close button
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Hapus Riwayat",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Progress Bar and Info
            when (item.status) {
                "PENDING" -> {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Menunggu antrean...",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                "UPLOADING" -> {
                    LinearProgressIndicator(
                        progress = { item.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Mengunggah berkas...",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${(item.progress * 100).toInt()}%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                "SUCCESS" -> {
                    Text(
                        text = "Berhasil diunggah ke Google Drive",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF2E7D32)
                    )
                }
                "FAILED" -> {
                    Text(
                        text = item.errorMessage ?: "Gagal mengunggah berkas",
                        fontSize = 12.sp,
                        color = Color(0xFFC62828),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, desc: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(72.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = desc,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
    }
}

// Utility function to choose Icon according to Mimetype and name extensions
fun getIconForMimeType(mimeType: String, fileName: String): ImageVector {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return when {
        mimeType.startsWith("image/") || listOf("png", "jpg", "jpeg", "webp", "gif").contains(ext) -> {
            Icons.Default.Image
        }
        mimeType.startsWith("audio/") || listOf("mp3", "wav", "m4a", "flac", "ogg").contains(ext) -> {
            Icons.Default.AudioFile
        }
        mimeType.startsWith("video/") || listOf("mp4", "mkv", "avi", "mov", "webm").contains(ext) -> {
            Icons.Default.VideoFile
        }
        listOf("zip", "rar", "7z", "tar", "gz", "bz2").contains(ext) -> {
            Icons.Default.FolderZip
        }
        listOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt").contains(ext) -> {
            Icons.Default.Description
        }
        else -> {
            Icons.Default.InsertDriveFile
        }
    }
}

// Format file size helper
fun formatFileSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    if (digitGroups >= units.size) return "$size B"
    return String.format(Locale.US, "%.2f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

// Format date helper from Google Drive format (RFC 3339)
fun formatDriveDate(dateStr: String): String {
    return try {
        // Example: 2026-07-19T11:23:03.000Z
        val cleanStr = dateStr.replace("Z", "+0000")
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        val date = parser.parse(cleanStr) ?: return dateStr
        val formatter = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
        formatter.format(date)
    } catch (e: Exception) {
        dateStr
    }
}

// Helper to translate common Google Sign-In ApiExceptions to friendly explanation
fun getCommonStatusMessage(statusCode: Int): String {
    return when (statusCode) {
        0 -> "SUCCESS"
        7 -> "NETWORK_ERROR (Koneksi bermasalah atau internet mati)"
        8 -> "INTERNAL_ERROR (Kesalahan internal Google Play Services)"
        10 -> "DEVELOPER_ERROR (Sertifikat SHA-1 atau nama paket tidak cocok di Google Cloud Console)"
        13 -> "ERROR (Kesalahan umum Google)"
        15 -> "TIMEOUT"
        16 -> "CANCELED (Dibatalkan oleh pengguna)"
        17 -> "API_NOT_CONNECTED"
        12500 -> "SIGN_IN_FAILED (Sertifikat SHA-1 belum terdaftar di Konsol Google Cloud)"
        12501 -> "SIGN_IN_CANCELLED (Masuk dibatalkan oleh pengguna)"
        12502 -> "SIGN_IN_CURRENTLY_IN_PROGRESS"
        else -> "Error tidak dikenal"
    }
}

@Composable
fun LiquidGlassBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "LiquidGlass")

    val animateX1 by infiniteTransition.animateFloat(
        initialValue = -30f,
        targetValue = 130f,
        animationSpec = infiniteRepeatable(
            animation = tween(14000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blob1_x"
    )
    val animateY1 by infiniteTransition.animateFloat(
        initialValue = -30f,
        targetValue = 150f,
        animationSpec = infiniteRepeatable(
            animation = tween(18000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blob1_y"
    )

    val animateX2 by infiniteTransition.animateFloat(
        initialValue = 150f,
        targetValue = -30f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blob2_x"
    )
    val animateY2 by infiniteTransition.animateFloat(
        initialValue = 200f,
        targetValue = -30f,
        animationSpec = infiniteRepeatable(
            animation = tween(15000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blob2_y"
    )

    val animateX3 by infiniteTransition.animateFloat(
        initialValue = 10f,
        targetValue = 120f,
        animationSpec = infiniteRepeatable(
            animation = tween(17000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blob3_x"
    )
    val animateY3 by infiniteTransition.animateFloat(
        initialValue = 400f,
        targetValue = 150f,
        animationSpec = infiniteRepeatable(
            animation = tween(16000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blob3_y"
    )

    Box(modifier = modifier.fillMaxSize()) {
        // Deep modern dark background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF07090E))
        )

        // Blurring the colored floating liquid bubbles
        Cloudy(
            radius = 25,
            modifier = Modifier.fillMaxSize()
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height

                // Liquid Blob 1 - Electric Purple
                drawCircle(
                    color = Color(0xFFA033FF).copy(alpha = 0.35f),
                    radius = width * 0.5f,
                    center = Offset(
                        x = width * (animateX1 / 100f),
                        y = height * (animateY1 / 100f)
                    )
                )

                // Liquid Blob 2 - Ocean Cyan
                drawCircle(
                    color = Color(0xFF00E5FF).copy(alpha = 0.32f),
                    radius = width * 0.55f,
                    center = Offset(
                        x = width * (animateX2 / 100f),
                        y = height * (animateY2 / 100f)
                    )
                )

                // Liquid Blob 3 - Radiant Rose Pink
                drawCircle(
                    color = Color(0xFFFF2A85).copy(alpha = 0.28f),
                    radius = width * 0.45f,
                    center = Offset(
                        x = width * (animateX3 / 100f),
                        y = height * (animateY3 / 400f)
                    )
                )
            }
        }

        // Overlay transparent deep gradient to tie everything beautifully
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF07090E).copy(alpha = 0.25f),
                            Color(0xFF0E121E).copy(alpha = 0.6f),
                            Color(0xFF04060A).copy(alpha = 0.85f)
                        )
                    )
                )
        )

        content()
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(20.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.03f)
        ),
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.12f),
                    Color.White.copy(alpha = 0.02f)
                )
            )
        ),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}

