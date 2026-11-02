package com.maxrave.simpmusic.expect

import androidx.compose.runtime.Composable
import com.maxrave.domain.data.entities.SongEntity

/** Whether songs on the device's own storage can be listed. Always false on Desktop. */
expect fun hasLocalMusicPermission(): Boolean

/**
 * A song on the device's own storage. [folder] is the path of the folder holding the file, relative
 * to the storage root and without a trailing slash ("Music/Pop"), so songs can be grouped the way
 * the user sorted them into folders.
 */
data class LocalSong(
    val song: SongEntity,
    val folder: String,
)

/** How the local songs are split into playlists. */
enum class LocalGroupBy { FOLDER, ALBUM, ARTIST }

/**
 * One playlist of local songs. [key] is what its songs share (the folder's relative path, the album
 * or the artist name; empty when the tag is missing), [name] what its chip says.
 */
data class LocalGroup(
    val key: String,
    val name: String,
    val count: Int,
)

/** What [this] song shares with the others of its group under [groupBy]. */
fun LocalSong.groupKey(groupBy: LocalGroupBy): String =
    when (groupBy) {
        LocalGroupBy.FOLDER -> folder
        LocalGroupBy.ALBUM -> song.albumName.orEmpty().trim()
        LocalGroupBy.ARTIST -> song.artistName?.firstOrNull().orEmpty().trim()
    }

/**
 * Every song on the device's own storage, as rows the rest of the app already knows how to show
 * and queue. Each videoId is [com.maxrave.common.LOCAL_SONG_PREFIX] plus its MediaStore id, which
 * is what makes the player read the file instead of asking YouTube. Empty on Desktop, and empty
 * without the permission.
 */
expect suspend fun scanLocalSongs(): List<LocalSong>

/**
 * The groups [songs] fall into under [groupBy], sorted by name. A folder group is one folder that
 * directly holds songs, named by its last path segment. Songs missing the tag share one group whose
 * name is [unknownName], sorted last.
 */
fun localGroupsOf(
    songs: List<LocalSong>,
    groupBy: LocalGroupBy,
    unknownName: String,
): List<LocalGroup> =
    songs
        .groupingBy { it.groupKey(groupBy) }
        .eachCount()
        .map { (key, count) ->
            val name = if (groupBy == LocalGroupBy.FOLDER) key.substringAfterLast('/') else key
            LocalGroup(key = key, name = name.ifEmpty { unknownName }, count = count)
        }.sortedWith(compareBy<LocalGroup> { it.key.isEmpty() }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })

/**
 * Asks for permission to read the device's music. The returned function starts the request, and
 * [onResult] hears whether it was granted. A no-op on Desktop.
 */
@Composable
expect fun rememberLocalMusicPermissionRequest(onResult: (Boolean) -> Unit): () -> Unit
