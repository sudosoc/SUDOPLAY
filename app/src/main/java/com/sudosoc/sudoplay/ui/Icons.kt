package com.sudosoc.sudoplay.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.sharp.*
import androidx.compose.material.icons.twotone.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.automirrored.sharp.*
import androidx.compose.material.icons.automirrored.twotone.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.vector.ImageVector

/** Icon families the user can pick in Appearance → Icon style. */
enum class IconStyle(val label: String) { Rounded("Rounded"), Filled("Filled"), Outlined("Outlined"), Sharp("Sharp"), TwoTone("Two-tone") }

val LocalIconStyle = compositionLocalOf { IconStyle.Rounded }

object I {
    val Add: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.Add
        IconStyle.Filled -> Icons.Filled.Add
        IconStyle.Outlined -> Icons.Outlined.Add
        IconStyle.Sharp -> Icons.Sharp.Add
        IconStyle.TwoTone -> Icons.TwoTone.Add
    }
    val Album: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.Album
        IconStyle.Filled -> Icons.Filled.Album
        IconStyle.Outlined -> Icons.Outlined.Album
        IconStyle.Sharp -> Icons.Sharp.Album
        IconStyle.TwoTone -> Icons.TwoTone.Album
    }
    val Apps: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.Apps
        IconStyle.Filled -> Icons.Filled.Apps
        IconStyle.Outlined -> Icons.Outlined.Apps
        IconStyle.Sharp -> Icons.Sharp.Apps
        IconStyle.TwoTone -> Icons.TwoTone.Apps
    }
    val AutoAwesome: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.AutoAwesome
        IconStyle.Filled -> Icons.Filled.AutoAwesome
        IconStyle.Outlined -> Icons.Outlined.AutoAwesome
        IconStyle.Sharp -> Icons.Sharp.AutoAwesome
        IconStyle.TwoTone -> Icons.TwoTone.AutoAwesome
    }
    val Bedtime: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.Bedtime
        IconStyle.Filled -> Icons.Filled.Bedtime
        IconStyle.Outlined -> Icons.Outlined.Bedtime
        IconStyle.Sharp -> Icons.Sharp.Bedtime
        IconStyle.TwoTone -> Icons.TwoTone.Bedtime
    }
    val Check: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.Check
        IconStyle.Filled -> Icons.Filled.Check
        IconStyle.Outlined -> Icons.Outlined.Check
        IconStyle.Sharp -> Icons.Sharp.Check
        IconStyle.TwoTone -> Icons.TwoTone.Check
    }
    val CheckCircle: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.CheckCircle
        IconStyle.Filled -> Icons.Filled.CheckCircle
        IconStyle.Outlined -> Icons.Outlined.CheckCircle
        IconStyle.Sharp -> Icons.Sharp.CheckCircle
        IconStyle.TwoTone -> Icons.TwoTone.CheckCircle
    }
    val ChevronRight: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.ChevronRight
        IconStyle.Filled -> Icons.Filled.ChevronRight
        IconStyle.Outlined -> Icons.Outlined.ChevronRight
        IconStyle.Sharp -> Icons.Sharp.ChevronRight
        IconStyle.TwoTone -> Icons.TwoTone.ChevronRight
    }
    val Close: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.Close
        IconStyle.Filled -> Icons.Filled.Close
        IconStyle.Outlined -> Icons.Outlined.Close
        IconStyle.Sharp -> Icons.Sharp.Close
        IconStyle.TwoTone -> Icons.TwoTone.Close
    }
    val Collections: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.Collections
        IconStyle.Filled -> Icons.Filled.Collections
        IconStyle.Outlined -> Icons.Outlined.Collections
        IconStyle.Sharp -> Icons.Sharp.Collections
        IconStyle.TwoTone -> Icons.TwoTone.Collections
    }
    val Delete: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.Delete
        IconStyle.Filled -> Icons.Filled.Delete
        IconStyle.Outlined -> Icons.Outlined.Delete
        IconStyle.Sharp -> Icons.Sharp.Delete
        IconStyle.TwoTone -> Icons.TwoTone.Delete
    }
    val DeleteSweep: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.DeleteSweep
        IconStyle.Filled -> Icons.Filled.DeleteSweep
        IconStyle.Outlined -> Icons.Outlined.DeleteSweep
        IconStyle.Sharp -> Icons.Sharp.DeleteSweep
        IconStyle.TwoTone -> Icons.TwoTone.DeleteSweep
    }
    val Equalizer: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.Equalizer
        IconStyle.Filled -> Icons.Filled.Equalizer
        IconStyle.Outlined -> Icons.Outlined.Equalizer
        IconStyle.Sharp -> Icons.Sharp.Equalizer
        IconStyle.TwoTone -> Icons.TwoTone.Equalizer
    }
    val Explore: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.Explore
        IconStyle.Filled -> Icons.Filled.Explore
        IconStyle.Outlined -> Icons.Outlined.Explore
        IconStyle.Sharp -> Icons.Sharp.Explore
        IconStyle.TwoTone -> Icons.TwoTone.Explore
    }
    val Extension: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.Extension
        IconStyle.Filled -> Icons.Filled.Extension
        IconStyle.Outlined -> Icons.Outlined.Extension
        IconStyle.Sharp -> Icons.Sharp.Extension
        IconStyle.TwoTone -> Icons.TwoTone.Extension
    }
    val Favorite: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.Favorite
        IconStyle.Filled -> Icons.Filled.Favorite
        IconStyle.Outlined -> Icons.Outlined.Favorite
        IconStyle.Sharp -> Icons.Sharp.Favorite
        IconStyle.TwoTone -> Icons.TwoTone.Favorite
    }
    val FavoriteBorder: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.FavoriteBorder
        IconStyle.Filled -> Icons.Filled.FavoriteBorder
        IconStyle.Outlined -> Icons.Outlined.FavoriteBorder
        IconStyle.Sharp -> Icons.Sharp.FavoriteBorder
        IconStyle.TwoTone -> Icons.TwoTone.FavoriteBorder
    }
    val Folder: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.Folder
        IconStyle.Filled -> Icons.Filled.Folder
        IconStyle.Outlined -> Icons.Outlined.Folder
        IconStyle.Sharp -> Icons.Sharp.Folder
        IconStyle.TwoTone -> Icons.TwoTone.Folder
    }
    val History: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.History
        IconStyle.Filled -> Icons.Filled.History
        IconStyle.Outlined -> Icons.Outlined.History
        IconStyle.Sharp -> Icons.Sharp.History
        IconStyle.TwoTone -> Icons.TwoTone.History
    }
    val KeyboardArrowDown: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.KeyboardArrowDown
        IconStyle.Filled -> Icons.Filled.KeyboardArrowDown
        IconStyle.Outlined -> Icons.Outlined.KeyboardArrowDown
        IconStyle.Sharp -> Icons.Sharp.KeyboardArrowDown
        IconStyle.TwoTone -> Icons.TwoTone.KeyboardArrowDown
    }
    val LibraryMusic: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.LibraryMusic
        IconStyle.Filled -> Icons.Filled.LibraryMusic
        IconStyle.Outlined -> Icons.Outlined.LibraryMusic
        IconStyle.Sharp -> Icons.Sharp.LibraryMusic
        IconStyle.TwoTone -> Icons.TwoTone.LibraryMusic
    }
    val Lyrics: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.Lyrics
        IconStyle.Filled -> Icons.Filled.Lyrics
        IconStyle.Outlined -> Icons.Outlined.Lyrics
        IconStyle.Sharp -> Icons.Sharp.Lyrics
        IconStyle.TwoTone -> Icons.TwoTone.Lyrics
    }
    val MoreVert: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.MoreVert
        IconStyle.Filled -> Icons.Filled.MoreVert
        IconStyle.Outlined -> Icons.Outlined.MoreVert
        IconStyle.Sharp -> Icons.Sharp.MoreVert
        IconStyle.TwoTone -> Icons.TwoTone.MoreVert
    }
    val MusicNote: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.MusicNote
        IconStyle.Filled -> Icons.Filled.MusicNote
        IconStyle.Outlined -> Icons.Outlined.MusicNote
        IconStyle.Sharp -> Icons.Sharp.MusicNote
        IconStyle.TwoTone -> Icons.TwoTone.MusicNote
    }
    val NewReleases: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.NewReleases
        IconStyle.Filled -> Icons.Filled.NewReleases
        IconStyle.Outlined -> Icons.Outlined.NewReleases
        IconStyle.Sharp -> Icons.Sharp.NewReleases
        IconStyle.TwoTone -> Icons.TwoTone.NewReleases
    }
    val Palette: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.Palette
        IconStyle.Filled -> Icons.Filled.Palette
        IconStyle.Outlined -> Icons.Outlined.Palette
        IconStyle.Sharp -> Icons.Sharp.Palette
        IconStyle.TwoTone -> Icons.TwoTone.Palette
    }
    val Pause: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.Pause
        IconStyle.Filled -> Icons.Filled.Pause
        IconStyle.Outlined -> Icons.Outlined.Pause
        IconStyle.Sharp -> Icons.Sharp.Pause
        IconStyle.TwoTone -> Icons.TwoTone.Pause
    }
    val Person: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.Person
        IconStyle.Filled -> Icons.Filled.Person
        IconStyle.Outlined -> Icons.Outlined.Person
        IconStyle.Sharp -> Icons.Sharp.Person
        IconStyle.TwoTone -> Icons.TwoTone.Person
    }
    val PhotoLibrary: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.PhotoLibrary
        IconStyle.Filled -> Icons.Filled.PhotoLibrary
        IconStyle.Outlined -> Icons.Outlined.PhotoLibrary
        IconStyle.Sharp -> Icons.Sharp.PhotoLibrary
        IconStyle.TwoTone -> Icons.TwoTone.PhotoLibrary
    }
    val PlayArrow: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.PlayArrow
        IconStyle.Filled -> Icons.Filled.PlayArrow
        IconStyle.Outlined -> Icons.Outlined.PlayArrow
        IconStyle.Sharp -> Icons.Sharp.PlayArrow
        IconStyle.TwoTone -> Icons.TwoTone.PlayArrow
    }
    val PlayCircle: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.PlayCircle
        IconStyle.Filled -> Icons.Filled.PlayCircle
        IconStyle.Outlined -> Icons.Outlined.PlayCircle
        IconStyle.Sharp -> Icons.Sharp.PlayCircle
        IconStyle.TwoTone -> Icons.TwoTone.PlayCircle
    }
    val QueueMusic: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.QueueMusic
        IconStyle.Filled -> Icons.Filled.QueueMusic
        IconStyle.Outlined -> Icons.Outlined.QueueMusic
        IconStyle.Sharp -> Icons.Sharp.QueueMusic
        IconStyle.TwoTone -> Icons.TwoTone.QueueMusic
    }
    val Repeat: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.Repeat
        IconStyle.Filled -> Icons.Filled.Repeat
        IconStyle.Outlined -> Icons.Outlined.Repeat
        IconStyle.Sharp -> Icons.Sharp.Repeat
        IconStyle.TwoTone -> Icons.TwoTone.Repeat
    }
    val RepeatOne: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.RepeatOne
        IconStyle.Filled -> Icons.Filled.RepeatOne
        IconStyle.Outlined -> Icons.Outlined.RepeatOne
        IconStyle.Sharp -> Icons.Sharp.RepeatOne
        IconStyle.TwoTone -> Icons.TwoTone.RepeatOne
    }
    val RestartAlt: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.RestartAlt
        IconStyle.Filled -> Icons.Filled.RestartAlt
        IconStyle.Outlined -> Icons.Outlined.RestartAlt
        IconStyle.Sharp -> Icons.Sharp.RestartAlt
        IconStyle.TwoTone -> Icons.TwoTone.RestartAlt
    }
    val Search: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.Search
        IconStyle.Filled -> Icons.Filled.Search
        IconStyle.Outlined -> Icons.Outlined.Search
        IconStyle.Sharp -> Icons.Sharp.Search
        IconStyle.TwoTone -> Icons.TwoTone.Search
    }
    val Settings: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.Settings
        IconStyle.Filled -> Icons.Filled.Settings
        IconStyle.Outlined -> Icons.Outlined.Settings
        IconStyle.Sharp -> Icons.Sharp.Settings
        IconStyle.TwoTone -> Icons.TwoTone.Settings
    }
    val Shuffle: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.Shuffle
        IconStyle.Filled -> Icons.Filled.Shuffle
        IconStyle.Outlined -> Icons.Outlined.Shuffle
        IconStyle.Sharp -> Icons.Sharp.Shuffle
        IconStyle.TwoTone -> Icons.TwoTone.Shuffle
    }
    val SkipNext: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.SkipNext
        IconStyle.Filled -> Icons.Filled.SkipNext
        IconStyle.Outlined -> Icons.Outlined.SkipNext
        IconStyle.Sharp -> Icons.Sharp.SkipNext
        IconStyle.TwoTone -> Icons.TwoTone.SkipNext
    }
    val SkipPrevious: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.SkipPrevious
        IconStyle.Filled -> Icons.Filled.SkipPrevious
        IconStyle.Outlined -> Icons.Outlined.SkipPrevious
        IconStyle.Sharp -> Icons.Sharp.SkipPrevious
        IconStyle.TwoTone -> Icons.TwoTone.SkipPrevious
    }
    val Speed: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.Speed
        IconStyle.Filled -> Icons.Filled.Speed
        IconStyle.Outlined -> Icons.Outlined.Speed
        IconStyle.Sharp -> Icons.Sharp.Speed
        IconStyle.TwoTone -> Icons.TwoTone.Speed
    }
    val SwapVert: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.SwapVert
        IconStyle.Filled -> Icons.Filled.SwapVert
        IconStyle.Outlined -> Icons.Outlined.SwapVert
        IconStyle.Sharp -> Icons.Sharp.SwapVert
        IconStyle.TwoTone -> Icons.TwoTone.SwapVert
    }
    val Tune: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.Tune
        IconStyle.Filled -> Icons.Filled.Tune
        IconStyle.Outlined -> Icons.Outlined.Tune
        IconStyle.Sharp -> Icons.Sharp.Tune
        IconStyle.TwoTone -> Icons.TwoTone.Tune
    }
    val Whatshot: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.Whatshot
        IconStyle.Filled -> Icons.Filled.Whatshot
        IconStyle.Outlined -> Icons.Outlined.Whatshot
        IconStyle.Sharp -> Icons.Sharp.Whatshot
        IconStyle.TwoTone -> Icons.TwoTone.Whatshot
    }
    val ArrowBack: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.AutoMirrored.Rounded.ArrowBack
        IconStyle.Filled -> Icons.AutoMirrored.Filled.ArrowBack
        IconStyle.Outlined -> Icons.AutoMirrored.Outlined.ArrowBack
        IconStyle.Sharp -> Icons.AutoMirrored.Sharp.ArrowBack
        IconStyle.TwoTone -> Icons.AutoMirrored.TwoTone.ArrowBack
    }
}
