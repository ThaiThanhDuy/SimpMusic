package com.maxrave.simpmusic.expect

import androidx.compose.runtime.Composable

// Desktop plays through mpv and has no MediaStore to read: local files are Android only.
actual fun hasLocalMusicPermission(): Boolean = false

actual suspend fun scanLocalSongs(): List<LocalSong> = emptyList()

@Composable
actual fun rememberLocalMusicPermissionRequest(onResult: (Boolean) -> Unit): () -> Unit = {}
