package com.sudosoc.sudoplay.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp

/** Grey placeholder rows that pulse while a list loads — the screen has its shape at once instead of a lone spinner. */
@Composable
fun SkeletonList(rows: Int = 9) {
    val t = LocalSudo.current
    val pulse by rememberInfiniteTransition(label = "skeleton").animateFloat(0.35f, 0.9f, infiniteRepeatable(tween(750), RepeatMode.Reverse), label = "a")
    val block = t.text.copy(alpha = 0.10f)
    Column(Modifier.fillMaxWidth().alpha(pulse)) {
        repeat(rows) { i ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(50.dp).background(block, RoundedCornerShape(12.dp)))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Box(Modifier.fillMaxWidth(if (i % 3 == 0) 0.7f else if (i % 3 == 1) 0.55f else 0.8f).height(14.dp).background(block, RoundedCornerShape(7.dp)))
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth(0.4f).height(10.dp).background(block, RoundedCornerShape(5.dp)))
                }
            }
        }
    }
}
