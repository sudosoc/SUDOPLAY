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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorNode
import androidx.compose.ui.graphics.vector.VectorPath

/** Icon families the user can pick in Appearance → Icon style. */
enum class IconStyle(val label: String) {
    Rounded("Rounded"), Filled("Filled"), Outlined("Outlined"), Sharp("Sharp"), TwoTone("Two-tone"),
    Line("Contour"), Heavy("Heavy"), Glass("Glass"), Bubble("Bubble"), Ink("Ink"),
}

val LocalIconStyle = compositionLocalOf { IconStyle.Rounded }

object I {
    val Add: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.Add
        IconStyle.Filled -> Icons.Filled.Add
        IconStyle.Outlined -> Icons.Outlined.Add
        IconStyle.Sharp -> Icons.Sharp.Add
        IconStyle.TwoTone -> Icons.TwoTone.Add
        IconStyle.Ink -> restyle(Icons.Sharp.Add, IconStyle.Ink)
        else -> restyle(Icons.Rounded.Add, LocalIconStyle.current)
    }
    val Album: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.Album
        IconStyle.Filled -> Icons.Filled.Album
        IconStyle.Outlined -> Icons.Outlined.Album
        IconStyle.Sharp -> Icons.Sharp.Album
        IconStyle.TwoTone -> Icons.TwoTone.Album
        IconStyle.Ink -> restyle(Icons.Sharp.Album, IconStyle.Ink)
        else -> restyle(Icons.Rounded.Album, LocalIconStyle.current)
    }
    val Apps: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.Apps
        IconStyle.Filled -> Icons.Filled.Apps
        IconStyle.Outlined -> Icons.Outlined.Apps
        IconStyle.Sharp -> Icons.Sharp.Apps
        IconStyle.TwoTone -> Icons.TwoTone.Apps
        IconStyle.Ink -> restyle(Icons.Sharp.Apps, IconStyle.Ink)
        else -> restyle(Icons.Rounded.Apps, LocalIconStyle.current)
    }
    val AutoAwesome: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.AutoAwesome
        IconStyle.Filled -> Icons.Filled.AutoAwesome
        IconStyle.Outlined -> Icons.Outlined.AutoAwesome
        IconStyle.Sharp -> Icons.Sharp.AutoAwesome
        IconStyle.TwoTone -> Icons.TwoTone.AutoAwesome
        IconStyle.Ink -> restyle(Icons.Sharp.AutoAwesome, IconStyle.Ink)
        else -> restyle(Icons.Rounded.AutoAwesome, LocalIconStyle.current)
    }
    val Bedtime: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.Bedtime
        IconStyle.Filled -> Icons.Filled.Bedtime
        IconStyle.Outlined -> Icons.Outlined.Bedtime
        IconStyle.Sharp -> Icons.Sharp.Bedtime
        IconStyle.TwoTone -> Icons.TwoTone.Bedtime
        IconStyle.Ink -> restyle(Icons.Sharp.Bedtime, IconStyle.Ink)
        else -> restyle(Icons.Rounded.Bedtime, LocalIconStyle.current)
    }
    val Check: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.Check
        IconStyle.Filled -> Icons.Filled.Check
        IconStyle.Outlined -> Icons.Outlined.Check
        IconStyle.Sharp -> Icons.Sharp.Check
        IconStyle.TwoTone -> Icons.TwoTone.Check
        IconStyle.Ink -> restyle(Icons.Sharp.Check, IconStyle.Ink)
        else -> restyle(Icons.Rounded.Check, LocalIconStyle.current)
    }
    val CheckCircle: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.CheckCircle
        IconStyle.Filled -> Icons.Filled.CheckCircle
        IconStyle.Outlined -> Icons.Outlined.CheckCircle
        IconStyle.Sharp -> Icons.Sharp.CheckCircle
        IconStyle.TwoTone -> Icons.TwoTone.CheckCircle
        IconStyle.Ink -> restyle(Icons.Sharp.CheckCircle, IconStyle.Ink)
        else -> restyle(Icons.Rounded.CheckCircle, LocalIconStyle.current)
    }
    val ChevronRight: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.ChevronRight
        IconStyle.Filled -> Icons.Filled.ChevronRight
        IconStyle.Outlined -> Icons.Outlined.ChevronRight
        IconStyle.Sharp -> Icons.Sharp.ChevronRight
        IconStyle.TwoTone -> Icons.TwoTone.ChevronRight
        IconStyle.Ink -> restyle(Icons.Sharp.ChevronRight, IconStyle.Ink)
        else -> restyle(Icons.Rounded.ChevronRight, LocalIconStyle.current)
    }
    val Close: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.Close
        IconStyle.Filled -> Icons.Filled.Close
        IconStyle.Outlined -> Icons.Outlined.Close
        IconStyle.Sharp -> Icons.Sharp.Close
        IconStyle.TwoTone -> Icons.TwoTone.Close
        IconStyle.Ink -> restyle(Icons.Sharp.Close, IconStyle.Ink)
        else -> restyle(Icons.Rounded.Close, LocalIconStyle.current)
    }
    val Collections: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.Collections
        IconStyle.Filled -> Icons.Filled.Collections
        IconStyle.Outlined -> Icons.Outlined.Collections
        IconStyle.Sharp -> Icons.Sharp.Collections
        IconStyle.TwoTone -> Icons.TwoTone.Collections
        IconStyle.Ink -> restyle(Icons.Sharp.Collections, IconStyle.Ink)
        else -> restyle(Icons.Rounded.Collections, LocalIconStyle.current)
    }
    val Delete: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.Delete
        IconStyle.Filled -> Icons.Filled.Delete
        IconStyle.Outlined -> Icons.Outlined.Delete
        IconStyle.Sharp -> Icons.Sharp.Delete
        IconStyle.TwoTone -> Icons.TwoTone.Delete
        IconStyle.Ink -> restyle(Icons.Sharp.Delete, IconStyle.Ink)
        else -> restyle(Icons.Rounded.Delete, LocalIconStyle.current)
    }
    val DeleteSweep: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.DeleteSweep
        IconStyle.Filled -> Icons.Filled.DeleteSweep
        IconStyle.Outlined -> Icons.Outlined.DeleteSweep
        IconStyle.Sharp -> Icons.Sharp.DeleteSweep
        IconStyle.TwoTone -> Icons.TwoTone.DeleteSweep
        IconStyle.Ink -> restyle(Icons.Sharp.DeleteSweep, IconStyle.Ink)
        else -> restyle(Icons.Rounded.DeleteSweep, LocalIconStyle.current)
    }
    val Equalizer: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.Equalizer
        IconStyle.Filled -> Icons.Filled.Equalizer
        IconStyle.Outlined -> Icons.Outlined.Equalizer
        IconStyle.Sharp -> Icons.Sharp.Equalizer
        IconStyle.TwoTone -> Icons.TwoTone.Equalizer
        IconStyle.Ink -> restyle(Icons.Sharp.Equalizer, IconStyle.Ink)
        else -> restyle(Icons.Rounded.Equalizer, LocalIconStyle.current)
    }
    val Explore: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.Explore
        IconStyle.Filled -> Icons.Filled.Explore
        IconStyle.Outlined -> Icons.Outlined.Explore
        IconStyle.Sharp -> Icons.Sharp.Explore
        IconStyle.TwoTone -> Icons.TwoTone.Explore
        IconStyle.Ink -> restyle(Icons.Sharp.Explore, IconStyle.Ink)
        else -> restyle(Icons.Rounded.Explore, LocalIconStyle.current)
    }
    val Extension: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.Extension
        IconStyle.Filled -> Icons.Filled.Extension
        IconStyle.Outlined -> Icons.Outlined.Extension
        IconStyle.Sharp -> Icons.Sharp.Extension
        IconStyle.TwoTone -> Icons.TwoTone.Extension
        IconStyle.Ink -> restyle(Icons.Sharp.Extension, IconStyle.Ink)
        else -> restyle(Icons.Rounded.Extension, LocalIconStyle.current)
    }
    val Favorite: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.Favorite
        IconStyle.Filled -> Icons.Filled.Favorite
        IconStyle.Outlined -> Icons.Outlined.Favorite
        IconStyle.Sharp -> Icons.Sharp.Favorite
        IconStyle.TwoTone -> Icons.TwoTone.Favorite
        IconStyle.Ink -> restyle(Icons.Sharp.Favorite, IconStyle.Ink)
        else -> restyle(Icons.Rounded.Favorite, LocalIconStyle.current)
    }
    val FavoriteBorder: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.FavoriteBorder
        IconStyle.Filled -> Icons.Filled.FavoriteBorder
        IconStyle.Outlined -> Icons.Outlined.FavoriteBorder
        IconStyle.Sharp -> Icons.Sharp.FavoriteBorder
        IconStyle.TwoTone -> Icons.TwoTone.FavoriteBorder
        IconStyle.Ink -> restyle(Icons.Sharp.FavoriteBorder, IconStyle.Ink)
        else -> restyle(Icons.Rounded.FavoriteBorder, LocalIconStyle.current)
    }
    val Folder: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.Folder
        IconStyle.Filled -> Icons.Filled.Folder
        IconStyle.Outlined -> Icons.Outlined.Folder
        IconStyle.Sharp -> Icons.Sharp.Folder
        IconStyle.TwoTone -> Icons.TwoTone.Folder
        IconStyle.Ink -> restyle(Icons.Sharp.Folder, IconStyle.Ink)
        else -> restyle(Icons.Rounded.Folder, LocalIconStyle.current)
    }
    val History: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.History
        IconStyle.Filled -> Icons.Filled.History
        IconStyle.Outlined -> Icons.Outlined.History
        IconStyle.Sharp -> Icons.Sharp.History
        IconStyle.TwoTone -> Icons.TwoTone.History
        IconStyle.Ink -> restyle(Icons.Sharp.History, IconStyle.Ink)
        else -> restyle(Icons.Rounded.History, LocalIconStyle.current)
    }
    val KeyboardArrowDown: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.KeyboardArrowDown
        IconStyle.Filled -> Icons.Filled.KeyboardArrowDown
        IconStyle.Outlined -> Icons.Outlined.KeyboardArrowDown
        IconStyle.Sharp -> Icons.Sharp.KeyboardArrowDown
        IconStyle.TwoTone -> Icons.TwoTone.KeyboardArrowDown
        IconStyle.Ink -> restyle(Icons.Sharp.KeyboardArrowDown, IconStyle.Ink)
        else -> restyle(Icons.Rounded.KeyboardArrowDown, LocalIconStyle.current)
    }
    val LibraryMusic: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.LibraryMusic
        IconStyle.Filled -> Icons.Filled.LibraryMusic
        IconStyle.Outlined -> Icons.Outlined.LibraryMusic
        IconStyle.Sharp -> Icons.Sharp.LibraryMusic
        IconStyle.TwoTone -> Icons.TwoTone.LibraryMusic
        IconStyle.Ink -> restyle(Icons.Sharp.LibraryMusic, IconStyle.Ink)
        else -> restyle(Icons.Rounded.LibraryMusic, LocalIconStyle.current)
    }
    val Lyrics: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.Lyrics
        IconStyle.Filled -> Icons.Filled.Lyrics
        IconStyle.Outlined -> Icons.Outlined.Lyrics
        IconStyle.Sharp -> Icons.Sharp.Lyrics
        IconStyle.TwoTone -> Icons.TwoTone.Lyrics
        IconStyle.Ink -> restyle(Icons.Sharp.Lyrics, IconStyle.Ink)
        else -> restyle(Icons.Rounded.Lyrics, LocalIconStyle.current)
    }
    val MoreVert: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.MoreVert
        IconStyle.Filled -> Icons.Filled.MoreVert
        IconStyle.Outlined -> Icons.Outlined.MoreVert
        IconStyle.Sharp -> Icons.Sharp.MoreVert
        IconStyle.TwoTone -> Icons.TwoTone.MoreVert
        IconStyle.Ink -> restyle(Icons.Sharp.MoreVert, IconStyle.Ink)
        else -> restyle(Icons.Rounded.MoreVert, LocalIconStyle.current)
    }
    val MusicNote: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.MusicNote
        IconStyle.Filled -> Icons.Filled.MusicNote
        IconStyle.Outlined -> Icons.Outlined.MusicNote
        IconStyle.Sharp -> Icons.Sharp.MusicNote
        IconStyle.TwoTone -> Icons.TwoTone.MusicNote
        IconStyle.Ink -> restyle(Icons.Sharp.MusicNote, IconStyle.Ink)
        else -> restyle(Icons.Rounded.MusicNote, LocalIconStyle.current)
    }
    val NewReleases: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.NewReleases
        IconStyle.Filled -> Icons.Filled.NewReleases
        IconStyle.Outlined -> Icons.Outlined.NewReleases
        IconStyle.Sharp -> Icons.Sharp.NewReleases
        IconStyle.TwoTone -> Icons.TwoTone.NewReleases
        IconStyle.Ink -> restyle(Icons.Sharp.NewReleases, IconStyle.Ink)
        else -> restyle(Icons.Rounded.NewReleases, LocalIconStyle.current)
    }
    val Palette: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.Palette
        IconStyle.Filled -> Icons.Filled.Palette
        IconStyle.Outlined -> Icons.Outlined.Palette
        IconStyle.Sharp -> Icons.Sharp.Palette
        IconStyle.TwoTone -> Icons.TwoTone.Palette
        IconStyle.Ink -> restyle(Icons.Sharp.Palette, IconStyle.Ink)
        else -> restyle(Icons.Rounded.Palette, LocalIconStyle.current)
    }
    val Pause: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.Pause
        IconStyle.Filled -> Icons.Filled.Pause
        IconStyle.Outlined -> Icons.Outlined.Pause
        IconStyle.Sharp -> Icons.Sharp.Pause
        IconStyle.TwoTone -> Icons.TwoTone.Pause
        IconStyle.Ink -> restyle(Icons.Sharp.Pause, IconStyle.Ink)
        else -> restyle(Icons.Rounded.Pause, LocalIconStyle.current)
    }
    val Person: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.Person
        IconStyle.Filled -> Icons.Filled.Person
        IconStyle.Outlined -> Icons.Outlined.Person
        IconStyle.Sharp -> Icons.Sharp.Person
        IconStyle.TwoTone -> Icons.TwoTone.Person
        IconStyle.Ink -> restyle(Icons.Sharp.Person, IconStyle.Ink)
        else -> restyle(Icons.Rounded.Person, LocalIconStyle.current)
    }
    val PhotoLibrary: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.PhotoLibrary
        IconStyle.Filled -> Icons.Filled.PhotoLibrary
        IconStyle.Outlined -> Icons.Outlined.PhotoLibrary
        IconStyle.Sharp -> Icons.Sharp.PhotoLibrary
        IconStyle.TwoTone -> Icons.TwoTone.PhotoLibrary
        IconStyle.Ink -> restyle(Icons.Sharp.PhotoLibrary, IconStyle.Ink)
        else -> restyle(Icons.Rounded.PhotoLibrary, LocalIconStyle.current)
    }
    val PlayArrow: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.PlayArrow
        IconStyle.Filled -> Icons.Filled.PlayArrow
        IconStyle.Outlined -> Icons.Outlined.PlayArrow
        IconStyle.Sharp -> Icons.Sharp.PlayArrow
        IconStyle.TwoTone -> Icons.TwoTone.PlayArrow
        IconStyle.Ink -> restyle(Icons.Sharp.PlayArrow, IconStyle.Ink)
        else -> restyle(Icons.Rounded.PlayArrow, LocalIconStyle.current)
    }
    val PlayCircle: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.PlayCircle
        IconStyle.Filled -> Icons.Filled.PlayCircle
        IconStyle.Outlined -> Icons.Outlined.PlayCircle
        IconStyle.Sharp -> Icons.Sharp.PlayCircle
        IconStyle.TwoTone -> Icons.TwoTone.PlayCircle
        IconStyle.Ink -> restyle(Icons.Sharp.PlayCircle, IconStyle.Ink)
        else -> restyle(Icons.Rounded.PlayCircle, LocalIconStyle.current)
    }
    val QueueMusic: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.QueueMusic
        IconStyle.Filled -> Icons.Filled.QueueMusic
        IconStyle.Outlined -> Icons.Outlined.QueueMusic
        IconStyle.Sharp -> Icons.Sharp.QueueMusic
        IconStyle.TwoTone -> Icons.TwoTone.QueueMusic
        IconStyle.Ink -> restyle(Icons.Sharp.QueueMusic, IconStyle.Ink)
        else -> restyle(Icons.Rounded.QueueMusic, LocalIconStyle.current)
    }
    val Repeat: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.Repeat
        IconStyle.Filled -> Icons.Filled.Repeat
        IconStyle.Outlined -> Icons.Outlined.Repeat
        IconStyle.Sharp -> Icons.Sharp.Repeat
        IconStyle.TwoTone -> Icons.TwoTone.Repeat
        IconStyle.Ink -> restyle(Icons.Sharp.Repeat, IconStyle.Ink)
        else -> restyle(Icons.Rounded.Repeat, LocalIconStyle.current)
    }
    val RepeatOne: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.RepeatOne
        IconStyle.Filled -> Icons.Filled.RepeatOne
        IconStyle.Outlined -> Icons.Outlined.RepeatOne
        IconStyle.Sharp -> Icons.Sharp.RepeatOne
        IconStyle.TwoTone -> Icons.TwoTone.RepeatOne
        IconStyle.Ink -> restyle(Icons.Sharp.RepeatOne, IconStyle.Ink)
        else -> restyle(Icons.Rounded.RepeatOne, LocalIconStyle.current)
    }
    val RestartAlt: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.RestartAlt
        IconStyle.Filled -> Icons.Filled.RestartAlt
        IconStyle.Outlined -> Icons.Outlined.RestartAlt
        IconStyle.Sharp -> Icons.Sharp.RestartAlt
        IconStyle.TwoTone -> Icons.TwoTone.RestartAlt
        IconStyle.Ink -> restyle(Icons.Sharp.RestartAlt, IconStyle.Ink)
        else -> restyle(Icons.Rounded.RestartAlt, LocalIconStyle.current)
    }
    val Search: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.Search
        IconStyle.Filled -> Icons.Filled.Search
        IconStyle.Outlined -> Icons.Outlined.Search
        IconStyle.Sharp -> Icons.Sharp.Search
        IconStyle.TwoTone -> Icons.TwoTone.Search
        IconStyle.Ink -> restyle(Icons.Sharp.Search, IconStyle.Ink)
        else -> restyle(Icons.Rounded.Search, LocalIconStyle.current)
    }
    val Settings: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.Settings
        IconStyle.Filled -> Icons.Filled.Settings
        IconStyle.Outlined -> Icons.Outlined.Settings
        IconStyle.Sharp -> Icons.Sharp.Settings
        IconStyle.TwoTone -> Icons.TwoTone.Settings
        IconStyle.Ink -> restyle(Icons.Sharp.Settings, IconStyle.Ink)
        else -> restyle(Icons.Rounded.Settings, LocalIconStyle.current)
    }
    val Shuffle: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.Shuffle
        IconStyle.Filled -> Icons.Filled.Shuffle
        IconStyle.Outlined -> Icons.Outlined.Shuffle
        IconStyle.Sharp -> Icons.Sharp.Shuffle
        IconStyle.TwoTone -> Icons.TwoTone.Shuffle
        IconStyle.Ink -> restyle(Icons.Sharp.Shuffle, IconStyle.Ink)
        else -> restyle(Icons.Rounded.Shuffle, LocalIconStyle.current)
    }
    val SkipNext: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.SkipNext
        IconStyle.Filled -> Icons.Filled.SkipNext
        IconStyle.Outlined -> Icons.Outlined.SkipNext
        IconStyle.Sharp -> Icons.Sharp.SkipNext
        IconStyle.TwoTone -> Icons.TwoTone.SkipNext
        IconStyle.Ink -> restyle(Icons.Sharp.SkipNext, IconStyle.Ink)
        else -> restyle(Icons.Rounded.SkipNext, LocalIconStyle.current)
    }
    val SkipPrevious: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.SkipPrevious
        IconStyle.Filled -> Icons.Filled.SkipPrevious
        IconStyle.Outlined -> Icons.Outlined.SkipPrevious
        IconStyle.Sharp -> Icons.Sharp.SkipPrevious
        IconStyle.TwoTone -> Icons.TwoTone.SkipPrevious
        IconStyle.Ink -> restyle(Icons.Sharp.SkipPrevious, IconStyle.Ink)
        else -> restyle(Icons.Rounded.SkipPrevious, LocalIconStyle.current)
    }
    val Speed: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.Speed
        IconStyle.Filled -> Icons.Filled.Speed
        IconStyle.Outlined -> Icons.Outlined.Speed
        IconStyle.Sharp -> Icons.Sharp.Speed
        IconStyle.TwoTone -> Icons.TwoTone.Speed
        IconStyle.Ink -> restyle(Icons.Sharp.Speed, IconStyle.Ink)
        else -> restyle(Icons.Rounded.Speed, LocalIconStyle.current)
    }
    val SwapVert: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.SwapVert
        IconStyle.Filled -> Icons.Filled.SwapVert
        IconStyle.Outlined -> Icons.Outlined.SwapVert
        IconStyle.Sharp -> Icons.Sharp.SwapVert
        IconStyle.TwoTone -> Icons.TwoTone.SwapVert
        IconStyle.Ink -> restyle(Icons.Sharp.SwapVert, IconStyle.Ink)
        else -> restyle(Icons.Rounded.SwapVert, LocalIconStyle.current)
    }
    val Tune: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.Tune
        IconStyle.Filled -> Icons.Filled.Tune
        IconStyle.Outlined -> Icons.Outlined.Tune
        IconStyle.Sharp -> Icons.Sharp.Tune
        IconStyle.TwoTone -> Icons.TwoTone.Tune
        IconStyle.Ink -> restyle(Icons.Sharp.Tune, IconStyle.Ink)
        else -> restyle(Icons.Rounded.Tune, LocalIconStyle.current)
    }
    val Whatshot: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.Whatshot
        IconStyle.Filled -> Icons.Filled.Whatshot
        IconStyle.Outlined -> Icons.Outlined.Whatshot
        IconStyle.Sharp -> Icons.Sharp.Whatshot
        IconStyle.TwoTone -> Icons.TwoTone.Whatshot
        IconStyle.Ink -> restyle(Icons.Sharp.Whatshot, IconStyle.Ink)
        else -> restyle(Icons.Rounded.Whatshot, LocalIconStyle.current)
    }
    val ArrowBack: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.AutoMirrored.Rounded.ArrowBack
        IconStyle.Filled -> Icons.AutoMirrored.Filled.ArrowBack
        IconStyle.Outlined -> Icons.AutoMirrored.Outlined.ArrowBack
        IconStyle.Sharp -> Icons.AutoMirrored.Sharp.ArrowBack
        IconStyle.TwoTone -> Icons.AutoMirrored.TwoTone.ArrowBack
        IconStyle.Ink -> restyle(Icons.AutoMirrored.Sharp.ArrowBack, IconStyle.Ink)
        else -> restyle(Icons.AutoMirrored.Rounded.ArrowBack, LocalIconStyle.current)
    }
    val Download: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.Download
        IconStyle.Filled -> Icons.Filled.Download
        IconStyle.Outlined -> Icons.Outlined.Download
        IconStyle.Sharp -> Icons.Sharp.Download
        IconStyle.TwoTone -> Icons.TwoTone.Download
        IconStyle.Ink -> restyle(Icons.Sharp.Download, IconStyle.Ink)
        else -> restyle(Icons.Rounded.Download, LocalIconStyle.current)
    }
    val CloudQueue: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.CloudQueue
        IconStyle.Filled -> Icons.Filled.CloudQueue
        IconStyle.Outlined -> Icons.Outlined.CloudQueue
        IconStyle.Sharp -> Icons.Sharp.CloudQueue
        IconStyle.TwoTone -> Icons.TwoTone.CloudQueue
        IconStyle.Ink -> restyle(Icons.Sharp.CloudQueue, IconStyle.Ink)
        else -> restyle(Icons.Rounded.CloudQueue, LocalIconStyle.current)
    }
    val PhoneAndroid: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.PhoneAndroid
        IconStyle.Filled -> Icons.Filled.PhoneAndroid
        IconStyle.Outlined -> Icons.Outlined.PhoneAndroid
        IconStyle.Sharp -> Icons.Sharp.PhoneAndroid
        IconStyle.TwoTone -> Icons.TwoTone.PhoneAndroid
        IconStyle.Ink -> restyle(Icons.Sharp.PhoneAndroid, IconStyle.Ink)
        else -> restyle(Icons.Rounded.PhoneAndroid, LocalIconStyle.current)
    }
    val PlaylistAdd: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.PlaylistAdd
        IconStyle.Filled -> Icons.Filled.PlaylistAdd
        IconStyle.Outlined -> Icons.Outlined.PlaylistAdd
        IconStyle.Sharp -> Icons.Sharp.PlaylistAdd
        IconStyle.TwoTone -> Icons.TwoTone.PlaylistAdd
        IconStyle.Ink -> restyle(Icons.Sharp.PlaylistAdd, IconStyle.Ink)
        else -> restyle(Icons.Rounded.PlaylistAdd, LocalIconStyle.current)
    }
    val TextFields: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.TextFields
        IconStyle.Filled -> Icons.Filled.TextFields
        IconStyle.Outlined -> Icons.Outlined.TextFields
        IconStyle.Sharp -> Icons.Sharp.TextFields
        IconStyle.TwoTone -> Icons.TwoTone.TextFields
        IconStyle.Ink -> restyle(Icons.Sharp.TextFields, IconStyle.Ink)
        else -> restyle(Icons.Rounded.TextFields, LocalIconStyle.current)
    }
    val FormatColorText: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.FormatColorText
        IconStyle.Filled -> Icons.Filled.FormatColorText
        IconStyle.Outlined -> Icons.Outlined.FormatColorText
        IconStyle.Sharp -> Icons.Sharp.FormatColorText
        IconStyle.TwoTone -> Icons.TwoTone.FormatColorText
        IconStyle.Ink -> restyle(Icons.Sharp.FormatColorText, IconStyle.Ink)
        else -> restyle(Icons.Rounded.FormatColorText, LocalIconStyle.current)
    }
    val Refresh: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.Refresh
        IconStyle.Filled -> Icons.Filled.Refresh
        IconStyle.Outlined -> Icons.Outlined.Refresh
        IconStyle.Sharp -> Icons.Sharp.Refresh
        IconStyle.TwoTone -> Icons.TwoTone.Refresh
        IconStyle.Ink -> restyle(Icons.Sharp.Refresh, IconStyle.Ink)
        else -> restyle(Icons.Rounded.Refresh, LocalIconStyle.current)
    }
    val FileOpen: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.FileOpen
        IconStyle.Filled -> Icons.Filled.FileOpen
        IconStyle.Outlined -> Icons.Outlined.FileOpen
        IconStyle.Sharp -> Icons.Sharp.FileOpen
        IconStyle.TwoTone -> Icons.TwoTone.FileOpen
        IconStyle.Ink -> restyle(Icons.Sharp.FileOpen, IconStyle.Ink)
        else -> restyle(Icons.Rounded.FileOpen, LocalIconStyle.current)
    }
    val SelectAll: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.SelectAll
        IconStyle.Filled -> Icons.Filled.SelectAll
        IconStyle.Outlined -> Icons.Outlined.SelectAll
        IconStyle.Sharp -> Icons.Sharp.SelectAll
        IconStyle.TwoTone -> Icons.TwoTone.SelectAll
        IconStyle.Ink -> restyle(Icons.Sharp.SelectAll, IconStyle.Ink)
        else -> restyle(Icons.Rounded.SelectAll, LocalIconStyle.current)
    }
    val Cancel: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.Cancel
        IconStyle.Filled -> Icons.Filled.Cancel
        IconStyle.Outlined -> Icons.Outlined.Cancel
        IconStyle.Sharp -> Icons.Sharp.Cancel
        IconStyle.TwoTone -> Icons.TwoTone.Cancel
        IconStyle.Ink -> restyle(Icons.Sharp.Cancel, IconStyle.Ink)
        else -> restyle(Icons.Rounded.Cancel, LocalIconStyle.current)
    }
    val ContentPaste: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.ContentPaste
        IconStyle.Filled -> Icons.Filled.ContentPaste
        IconStyle.Outlined -> Icons.Outlined.ContentPaste
        IconStyle.Sharp -> Icons.Sharp.ContentPaste
        IconStyle.TwoTone -> Icons.TwoTone.ContentPaste
        IconStyle.Ink -> restyle(Icons.Sharp.ContentPaste, IconStyle.Ink)
        else -> restyle(Icons.Rounded.ContentPaste, LocalIconStyle.current)
    }
    val TrendingUp: ImageVector @Composable get() = when (LocalIconStyle.current) {
        IconStyle.Rounded -> Icons.Rounded.TrendingUp
        IconStyle.Filled -> Icons.Filled.TrendingUp
        IconStyle.Outlined -> Icons.Outlined.TrendingUp
        IconStyle.Sharp -> Icons.Sharp.TrendingUp
        IconStyle.TwoTone -> Icons.TwoTone.TrendingUp
        IconStyle.Ink -> restyle(Icons.Sharp.TrendingUp, IconStyle.Ink)
        else -> restyle(Icons.Rounded.TrendingUp, LocalIconStyle.current)
    }
}


// ---- restyled families: the Material glyphs re-drawn with different stroke / fill treatments ----
private val restyleCache = HashMap<Pair<ImageVector, IconStyle>, ImageVector>()

fun restyle(src: ImageVector, style: IconStyle): ImageVector = restyleCache.getOrPut(src to style) {
    val b = ImageVector.Builder(
        src.name + "_" + style.name, src.defaultWidth, src.defaultHeight, src.viewportWidth, src.viewportHeight,
        src.tintColor, src.tintBlendMode, src.autoMirror,
    )
    fun walk(g: VectorGroup) {
        for (n in g) when (n) {
            is VectorGroup -> {
                b.addGroup(n.name, n.rotation, n.pivotX, n.pivotY, n.scaleX, n.scaleY, n.translationX, n.translationY, n.clipPathData)
                walk(n); b.clearGroup()
            }
            is VectorPath -> {
                val ink = SolidColor(Color.Black)
                when (style) {
                    IconStyle.Line -> b.addPath(n.pathData, n.pathFillType, n.name, null, 1f, ink, 1f, 1.0f, StrokeCap.Round, StrokeJoin.Round, 4f)
                    IconStyle.Heavy -> b.addPath(n.pathData, n.pathFillType, n.name, ink, 1f, ink, 1f, 1.1f, StrokeCap.Round, StrokeJoin.Round, 4f)
                    IconStyle.Glass -> b.addPath(n.pathData, n.pathFillType, n.name, ink, 0.32f, ink, 1f, 1.0f, StrokeCap.Round, StrokeJoin.Round, 4f)
                    IconStyle.Bubble -> b.addPath(n.pathData, n.pathFillType, n.name, ink, 1f, ink, 1f, 1.8f, StrokeCap.Round, StrokeJoin.Round, 4f)
                    IconStyle.Ink -> b.addPath(n.pathData, n.pathFillType, n.name, ink, 1f, ink, 1f, 0.7f, StrokeCap.Butt, StrokeJoin.Miter, 5f)
                    else -> b.addPath(n.pathData, n.pathFillType, n.name, n.fill, n.fillAlpha, n.stroke, n.strokeAlpha, n.strokeLineWidth, n.strokeLineCap, n.strokeLineJoin, n.strokeLineMiter)
                }
            }
        }
    }
    walk(src.root)
    b.build()
}
