package com.sudosoc.sudoplay.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.sharp.*
import androidx.compose.material.icons.twotone.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

/** Same style switching as [I], for the icons added later. */
@Composable
private fun styled(r: ImageVector, f: ImageVector, o: ImageVector, s: ImageVector, tt: ImageVector): ImageVector = when (LocalIconStyle.current) {
    IconStyle.Rounded -> r
    IconStyle.Filled -> f
    IconStyle.Outlined -> o
    IconStyle.Sharp -> s
    IconStyle.TwoTone -> tt
    IconStyle.Ink -> restyle(s, IconStyle.Ink)
    else -> restyle(r, LocalIconStyle.current)
}

val I.Share: ImageVector @Composable get() = styled(Icons.Rounded.Share, Icons.Filled.Share, Icons.Outlined.Share, Icons.Sharp.Share, Icons.TwoTone.Share)
val I.Info: ImageVector @Composable get() = styled(Icons.Rounded.Info, Icons.Filled.Info, Icons.Outlined.Info, Icons.Sharp.Info, Icons.TwoTone.Info)
val I.Movie: ImageVector @Composable get() = styled(Icons.Rounded.Movie, Icons.Filled.Movie, Icons.Outlined.Movie, Icons.Sharp.Movie, Icons.TwoTone.Movie)
val I.Wifi: ImageVector @Composable get() = styled(Icons.Rounded.Wifi, Icons.Filled.Wifi, Icons.Outlined.Wifi, Icons.Sharp.Wifi, Icons.TwoTone.Wifi)
val I.CloudOff: ImageVector @Composable get() = styled(Icons.Rounded.CloudOff, Icons.Filled.CloudOff, Icons.Outlined.CloudOff, Icons.Sharp.CloudOff, Icons.TwoTone.CloudOff)
val I.Undo: ImageVector @Composable get() = styled(Icons.Rounded.Undo, Icons.Filled.Undo, Icons.Outlined.Undo, Icons.Sharp.Undo, Icons.TwoTone.Undo)
val I.DragHandle: ImageVector @Composable get() = styled(Icons.Rounded.DragHandle, Icons.Filled.DragHandle, Icons.Outlined.DragHandle, Icons.Sharp.DragHandle, Icons.TwoTone.DragHandle)
val I.Queue: ImageVector @Composable get() = styled(Icons.Rounded.Queue, Icons.Filled.Queue, Icons.Outlined.Queue, Icons.Sharp.Queue, Icons.TwoTone.Queue)
val I.PlaylistPlay: ImageVector @Composable get() = styled(Icons.Rounded.PlaylistPlay, Icons.Filled.PlaylistPlay, Icons.Outlined.PlaylistPlay, Icons.Sharp.PlaylistPlay, Icons.TwoTone.PlaylistPlay)
val I.PlaylistRemove: ImageVector @Composable get() = styled(Icons.Rounded.PlaylistRemove, Icons.Filled.PlaylistRemove, Icons.Outlined.PlaylistRemove, Icons.Sharp.PlaylistRemove, Icons.TwoTone.PlaylistRemove)
