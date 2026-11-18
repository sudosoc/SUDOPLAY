package com.sudosoc.sudoplay.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.sudosoc.sudoplay.PlayerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** The current song's cover if something is loaded, otherwise one of the bundled covers. */
@Composable
fun rememberPreviewArt(vm: PlayerViewModel): State<Bitmap?> {
    val ctx = LocalContext.current
    val cur by rememberArt(vm.current, 400)
    val asset by produceState<Bitmap?>(null) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val name = ctx.assets.list("covers")!!.sorted().let { it[3.coerceAtMost(it.lastIndex)] }
                ctx.assets.open("covers/$name").use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 2 }) }
            }.getOrNull()
        }
    }
    return rememberUpdatedState(cur ?: asset)
}

/**
 * A real, scaled-down render of the Now Playing page in [pt] — same background, cover frame, seek bar, buttons and
 * icon style the player uses, laid out on a fixed 360×640 canvas and shrunk to fit.
 */
@Composable
fun PlayerMock(
    pt: PlayerTheme,
    art: Bitmap?,
    btn: PlayerButtons,
    seekStyle: String,
    title: String,
    artist: String,
    modifier: Modifier = Modifier,
) {
    val app = LocalSudo.current
    val eff = remember(app, pt) { effectiveTheme(app, pt) }
    val dur = 215_000L
    val snap = remember(title, artist) { PlayerSnapshot(title, artist, (dur * 0.38f).toLong(), dur, true, false, 0, false, 7L) }
    val act = remember { PlayerActions() }

    Box(
        modifier.clip(RoundedCornerShape(24.dp)).layout { measurable, constraints ->
            val w = constraints.maxWidth
            val baseW = 360.dp.roundToPx()
            val baseH = 640.dp.roundToPx()
            val h = (w * 640f / 360f).roundToInt()
            val p = measurable.measure(Constraints.fixed(baseW, baseH))
            val sc = w.toFloat() / baseW
            layout(w, h) { p.placeWithLayer(0, 0) { scaleX = sc; scaleY = sc; transformOrigin = TransformOrigin(0f, 0f) } }
        },
    ) {
        CompositionLocalProvider(LocalSudo provides eff) {
            PlayerBackground(pt, art, animated = false) {
                PlayerLayout(
                    pt = pt, art = art, snap = snap, btn = btn, seekStyle = seekStyle, mode = 0, act = act,
                    animated = false, interactive = false,
                    modifier = Modifier.padding(top = 30.dp, bottom = 12.dp),
                    extras = mockChips(btn),
                )
            }
        }
    }
}

private fun mockChips(btn: PlayerButtons): @Composable RowScope.() -> Unit = {
    btn.chips.forEach { id ->
        when (id) {
            "speed" -> if (btn.speed) StaticChip(I.Speed, "1.25x", true)
            "eq" -> if (btn.eq) StaticChip(I.Equalizer, "EQ", true)
            "sleep" -> if (btn.sleep) StaticChip(I.Bedtime, "Sleep")
        }
    }
}
