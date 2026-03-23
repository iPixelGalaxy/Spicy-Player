package com.tx24.spicyplayer.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import com.tx24.spicyplayer.ui.settings.components.SettingsSection
import com.tx24.spicyplayer.ui.settings.components.SettingsSectionHeader

private val EQ_PRESETS = listOf("Flat", "Bass", "Treble", "Vocal", "Custom")
private val EQ_BANDS = listOf("60 Hz", "230 Hz", "910 Hz", "3.6 kHz", "14 kHz")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerScreen(
    currentPreset: String,
    onPresetChange: (String) -> Unit,
    bassBoostEnabled: Boolean,
    onBassBoostEnabledChange: (Boolean) -> Unit,
    bassBoostStrength: Int,
    onBassBoostStrengthChange: (Int) -> Unit,
    onBack: () -> Unit,
) {
    // Band gains in dB (–12 to +12), keyed by preset
    val defaultGains = remember(currentPreset) {
        when (currentPreset) {
            "BASS"        -> listOf(6f,  4f,  0f, -2f, -2f)
            "TREBLE"      -> listOf(-2f, -2f, 0f,  4f,  6f)
            "VOCAL"       -> listOf(-1f,  0f,  3f,  3f,  0f)
            else          -> listOf(0f,  0f,  0f,  0f,  0f)
        }
    }
    val gains = remember(currentPreset) { defaultGains.map { mutableStateOf(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        onClick = onBack,
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(end = 24.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onBack) {
                                Icon(
                                    Icons.Rounded.ArrowBackIosNew,
                                    contentDescription = "Back",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Text(
                                text = "Equalizer",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                    }
                },
                title = {},
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = 32.dp
            )
        ) {
            // Preset selector
            item {
                SettingsSectionHeader("Preset")
                SettingsSection {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            EQ_PRESETS.forEachIndexed { index, label ->
                                SegmentedButton(
                                    selected = currentPreset.equals(
                                        label.replace(" ", "_").uppercase(), ignoreCase = true
                                    ),
                                    onClick = {
                                        onPresetChange(label.replace(" ", "_").uppercase())
                                    },
                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = EQ_PRESETS.size),
                                    label = {
                                        Text(
                                            label,
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Band sliders
            item { SettingsSectionHeader("Bands") }
            item {
                SettingsSection {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        EQ_BANDS.forEachIndexed { i, band ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = band,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(56.dp)
                                )
                                Slider(
                                    value = gains[i].value,
                                    onValueChange = {
                                        gains[i].value = it
                                        onPresetChange("CUSTOM")
                                    },
                                    valueRange = -12f..12f,
                                    modifier = Modifier.weight(1f)
                                )
                                SuggestionChip(
                                    onClick = {},
                                    label = {
                                        val g = gains[i].value
                                        Text(
                                            "${if (g >= 0) "+" else ""}${"%.0f".format(g)} dB",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        labelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    ),
                                    border = null,
                                    modifier = Modifier.width(64.dp)
                                )
                            }
                            if (i < EQ_BANDS.lastIndex) {
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            }
                        }
                    }
                }
            }

            // EQ Visualization placeholder
            item {
                SettingsSectionHeader("Visualization")
                SettingsSection {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.GraphicEq,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxSize()
                        )
                        Text(
                            "Live visualization coming soon",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Bass Boost section
            item { SettingsSectionHeader("Extra Effects") }
            item {
                SettingsSection {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "Bass Boost",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Switch(
                                checked = bassBoostEnabled,
                                onCheckedChange = onBassBoostEnabledChange
                            )
                        }
                        
                        if (bassBoostEnabled) {
                            Spacer(Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "Strength",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(56.dp)
                                )
                                Slider(
                                    value = bassBoostStrength.toFloat(),
                                    onValueChange = { onBassBoostStrengthChange(it.toInt()) },
                                    valueRange = 0f..1000f,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    "${(bassBoostStrength / 10)}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(32.dp)
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}
