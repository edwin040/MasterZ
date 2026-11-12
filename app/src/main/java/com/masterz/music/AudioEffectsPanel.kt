package com.masterz.music

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.ExoPlayer

@Composable
fun AudioEffectsPanel(player: ExoPlayer) {
    val sessionId = player.audioSessionId
    var equalizer by remember { mutableStateOf<Equalizer?>(null) }
    var bassBoost by remember { mutableStateOf<BassBoost?>(null) }
    var virtualizer by remember { mutableStateOf<Virtualizer?>(null) }
    var effectsError by remember { mutableStateOf(false) }

    DisposableEffect(sessionId) {
        effectsError = false
        try {
            if (sessionId > 0) {
                equalizer = Equalizer(0, sessionId).apply { enabled = true }
                bassBoost = BassBoost(0, sessionId).apply { enabled = true }
                virtualizer = Virtualizer(0, sessionId).apply { enabled = true }
            }
        } catch (_: Exception) {
            effectsError = true
        }
        onDispose {
            try { equalizer?.release() } catch (_: Exception) { }
            try { bassBoost?.release() } catch (_: Exception) { }
            try { virtualizer?.release() } catch (_: Exception) { }
            equalizer = null
            bassBoost = null
            virtualizer = null
        }
    }

    val eq = equalizer
    val bandCount = try { eq?.numberOfBands?.toInt() ?: 0 } catch (_: Exception) { 0 }
    val bandLevels = remember(eq, bandCount) {
        mutableStateListOf<Float>().apply {
            repeat(bandCount) { add(0f) }
        }
    }
    val range = try { eq?.bandLevelRange } catch (_: Exception) { null }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Audio Studio", style = MaterialTheme.typography.titleLarge)
        Text("Efek diproses oleh audio engine Android pada sesi pemutar MasterZ.", style = MaterialTheme.typography.bodySmall)
        if (effectsError || eq == null) {
            Text("Efek audio tidak didukung oleh perangkat atau sesi audio belum siap. Putar lagu terlebih dahulu.", color = MaterialTheme.colorScheme.error)
        } else {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Equalizer", style = MaterialTheme.typography.titleMedium)
                    for (i in 0 until bandCount) {
                        val centerHz = try { eq.getCenterFreq(i.toShort()) / 1000 } catch (_: Exception) { 0 }
                        val label = when {
                            centerHz < 100 -> "Bass · ${centerHz} Hz"
                            centerHz < 1000 -> "Low · ${centerHz} Hz"
                            centerHz < 5000 -> "Mid · ${centerHz} Hz"
                            else -> "Treble · ${centerHz} Hz"
                        }
                        Text(label, style = MaterialTheme.typography.labelMedium)
                        Slider(
                            value = bandLevels[i],
                            onValueChange = { value ->
                                bandLevels[i] = value
                                try { eq.setBandLevel(i.toShort(), value.toInt().toShort()) } catch (_: Exception) { }
                            },
                            valueRange = if (range != null) range[0].toFloat()..range[1].toFloat() else -1200f..1200f
                        )
                    }
                }
            }
        }
        EffectStrengthCard("Bass Boost", "Pertebal frekuensi rendah", bassBoost?.roundedStrength?.toFloat() ?: 0f) { value ->
            try { bassBoost?.setStrength(value.toInt().toShort()) } catch (_: Exception) { }
        }
        EffectStrengthCard("Virtualizer", "Lebarkan kesan ruang stereo", virtualizer?.roundedStrength?.toFloat() ?: 0f) { value ->
            try { virtualizer?.setStrength(value.toInt().toShort()) } catch (_: Exception) { }
        }
        Text("Catatan: hasil efek bergantung pada dukungan hardware dan kebijakan audio perangkat.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun EffectStrengthCard(title: String, description: String, initial: Float, onValue: (Float) -> Unit) {
    var value by remember(title) { mutableStateOf(initial) }
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(description, style = MaterialTheme.typography.bodySmall)
                }
                Text("${(value / 10).toInt()}%")
            }
            Slider(value = value, onValueChange = { value = it; onValue(it) }, valueRange = 0f..1000f)
        }
    }
}
