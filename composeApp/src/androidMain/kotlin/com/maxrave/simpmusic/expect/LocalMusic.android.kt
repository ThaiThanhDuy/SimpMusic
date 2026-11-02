package com.maxrave.simpmusic.expect

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.core.content.ContextCompat
import com.maxrave.common.LOCAL_SONG_PREFIX
import com.maxrave.domain.data.entities.SongEntity
import com.maxrave.logger.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.mp.KoinPlatform.getKoin

// READ_MEDIA_AUDIO replaced READ_EXTERNAL_STORAGE for audio in Android 13.
private val audioPermission =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

// RELATIVE_PATH ("Music/Pop/") exists from Android 10; before that the folder is read off the
// file's absolute path in DATA, which Android 10+ no longer guarantees to be readable.
private val folderColumnName =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Audio.Media.RELATIVE_PATH
    } else {
        @Suppress("DEPRECATION")
        MediaStore.Audio.Media.DATA
    }

/** The song's folder relative to the storage root, without a trailing slash: "Music/Pop". */
private fun folderOf(value: String?): String {
    if (value.isNullOrBlank()) return ""
    val folder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            value
        } else {
            // "/storage/emulated/0/Music/Pop/song.mp3" -> "Music/Pop"
            value.substringBeforeLast('/').substringAfter("/storage/emulated/0/")
        }
    return folder.trimEnd('/')
}

actual fun hasLocalMusicPermission(): Boolean {
    val context: Context = getKoin().get()
    return ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED
}

actual suspend fun scanLocalSongs(): List<LocalSong> =
    withContext(Dispatchers.IO) {
        if (!hasLocalMusicPermission()) return@withContext emptyList()
        val context: Context = getKoin().get()
        val projection =
            arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.DURATION,
                folderColumnName,
            )
        val songs = mutableListOf<LocalSong>()
        try {
            context.contentResolver
                .query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    // Leaves out ringtones, notification sounds and voice recordings.
                    "${MediaStore.Audio.Media.IS_MUSIC} != 0",
                    null,
                    null,
                )?.use { cursor ->
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                    val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                    val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                    val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                    val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                    val folderColumn = cursor.getColumnIndexOrThrow(folderColumnName)
                    while (cursor.moveToNext()) {
                        val durationSeconds = (cursor.getLong(durationColumn) / 1000).toInt()
                        // MediaStore writes "<unknown>" for a file with no artist tag.
                        val artist = cursor.getString(artistColumn)?.takeIf { it.isNotBlank() && it != MediaStore.UNKNOWN_STRING }
                        songs +=
                            LocalSong(
                                folder = folderOf(cursor.getString(folderColumn)),
                                song =
                                    SongEntity(
                                        videoId = LOCAL_SONG_PREFIX + cursor.getLong(idColumn),
                                        albumName = cursor.getString(albumColumn),
                                        artistName = listOf(artist ?: "Unknown artist"),
                                        duration = "%d:%02d".format(durationSeconds / 60, durationSeconds % 60),
                                        durationSeconds = durationSeconds,
                                        isAvailable = true,
                                        isExplicit = false,
                                        likeStatus = "INDIFFERENT",
                                        // The album's cover from MediaStore. The fragment is ignored when the
                                        // file is opened; it is the size marker the app reads to treat the
                                        // track as a square-artwork song rather than a video.
                                        thumbnails = "content://media/external/audio/albumart/${cursor.getLong(albumIdColumn)}#w544-h544",
                                        title = cursor.getString(titleColumn) ?: "",
                                        videoType = "",
                                        category = null,
                                        resultType = null,
                                    ),
                            )
                    }
                }
        } catch (e: Exception) {
            Logger.e("LocalMusic", "Scanning local songs failed: ${e.message}")
        }
        songs.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.song.title })
    }

@Composable
actual fun rememberLocalMusicPermissionRequest(onResult: (Boolean) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), onResult)
    return { launcher.launch(audioPermission) }
}
