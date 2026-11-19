package com.sudosoc.sudoplay.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sudosoc.sudoplay.PlayerViewModel
import com.sudosoc.sudoplay.online.DownloadCenter

/**
 * The living "download" pill at the top of the player for songs that are streaming from the internet:
 * it pulses while the song is not on the phone, shows a progress ring while it downloads, and turns into "On your phone" when it is saved.
 */
@Composable
fun DownloadChip(vm: PlayerViewModel, modifier: Modifier = Modifier) {
    val t = LocalSudo.current
    val song = vm.current ?: return
    if (!song.online) return
    vm.olib.version
    val saved = vm.localCopyOf(song)
    val job = DownloadCenter.jobs.firstOrNull { (it.title == song.title || it.origIds.contains(song.id)) && (it.active || it.state == "Failed") }
    val shape = RoundedCornerShape(50)

    val inf = rememberInfiniteTransition(label = "dl")
    val pulse by inf.animateFloat(1f, 1.07f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse), label = "pulse")
    val bob by inf.animateFloat(-2f, 2.5f, infiniteRepeatable(tween(650, easing = LinearEasing), RepeatMode.Reverse), label = "bob")
    val glow by inf.animateFloat(0.25f, 0.9f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse), label = "glow")

    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        when {
            saved != null -> Row(
                Modifier.clip(shape).background(t.primary.copy(alpha = 0.18f)).border(1.dp, t.primary.copy(alpha = 0.5f), shape).padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(I.CheckCircle, null, tint = t.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(7.dp))
                Text("On your phone", color = t.primary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
            job != null && job.active -> Row(
                Modifier.clip(shape).background(t.surface.copy(alpha = 0.85f)).border(1.dp, t.primary.copy(alpha = 0.6f), shape)
                    .clickable { DownloadCenter.cancel(job) }.padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (job.state == "Queued" || job.progress <= 0.01f) CircularProgressIndicator(Modifier.size(18.dp), color = t.primary, strokeWidth = 2.dp)
                else CircularProgressIndicator(progress = { job.progress }, modifier = Modifier.size(18.dp), color = t.primary, trackColor = t.primary.copy(alpha = 0.2f), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (job.state == "Queued" || job.progress <= 0.01f) "Downloading…" else "Downloading ${(job.progress * 100).toInt()}%",
                    color = t.text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                )
                Spacer(Modifier.width(8.dp))
                Icon(I.Close, null, tint = t.text.copy(alpha = 0.5f), modifier = Modifier.size(14.dp))
            }
            else -> {
                val failed = job != null
                Row(
                    Modifier.scale(pulse).clip(shape)
                        .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(t.primary, t.secondary)))
                        .border(2.dp, t.primary.copy(alpha = glow * 0.55f), shape)
                        .clickable { vm.haptics.click(); vm.downloadCurrent() }.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(if (failed) I.Refresh else I.Download, null, tint = t.onAccent, modifier = Modifier.size(18.dp).graphicsLayer { translationY = if (failed) 0f else bob * density })
                    Spacer(Modifier.width(7.dp))
                    Text(if (failed) "Retry download" else "Download", color = t.onAccent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}
