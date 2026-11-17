package com.sudosoc.sudoplay.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sudosoc.sudoplay.PlayerViewModel
import com.sudosoc.sudoplay.data.EqPresets

@Composable
private fun sliderColors(): SliderColors {
    val t = LocalSudo.current
    return SliderDefaults.colors(thumbColor = t.primary, activeTrackColor = t.primary, inactiveTrackColor = t.text.copy(alpha = 0.2f))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqSheet(vm: PlayerViewModel, onDismiss: () -> Unit) {
    val t = LocalSudo.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = t.surface, contentColor = t.text) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Equalizer & effects", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Switch(vm.eqEnabled, { vm.toggleEq(it) })
            }
            Spacer(Modifier.height(10.dp))

            if (vm.eqBands == 0) {
                Text("Equalizer isn't available yet. Start playing a song first, then reopen this.", color = t.text.copy(alpha = 0.7f))
            } else {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EqPresets.curves.keys.forEach { name ->
                        FilterChip(selected = vm.eqPreset == name, onClick = { vm.applyPreset(name) }, label = { Text(name) })
                    }
                }
                Spacer(Modifier.height(8.dp))
                vm.eqFreqs.forEachIndexed { i, f ->
                    val lvl = vm.eqLevels.getOrElse(i) { 0 }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (f >= 1000) "%.1f kHz".format(f / 1000f) else "$f Hz", Modifier.width(66.dp), style = MaterialTheme.typography.labelMedium)
                        Slider(
                            value = lvl.toFloat(), onValueChange = { vm.setEqLevel(i, it.toInt()) },
                            valueRange = vm.eqMin.toFloat()..vm.eqMax.toFloat(), modifier = Modifier.weight(1f), colors = sliderColors(),
                        )
                        Text("%+.1f".format(lvl / 100f), Modifier.width(42.dp), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            FxSlider("Bass boost", vm.bass) { vm.changeBass(it) }
            FxSlider("Surround", vm.virt) { vm.changeVirt(it) }
            FxSlider("Loudness gain", vm.loud) { vm.changeLoud(it) }
            Text("Effects apply to all playback while the switch is on.", color = t.text.copy(alpha = 0.55f), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun FxSlider(label: String, value: Int, onChange: (Int) -> Unit) {
    Column {
        Row {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
            Text("${value / 10}%", style = MaterialTheme.typography.labelMedium)
        }
        Slider(value.toFloat(), { onChange(it.toInt()) }, valueRange = 0f..1000f, colors = sliderColors())
    }
}
