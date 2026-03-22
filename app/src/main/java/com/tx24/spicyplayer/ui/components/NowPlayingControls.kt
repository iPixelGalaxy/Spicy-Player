package com.tx24.spicyplayer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Floating bottom controls for playback, seekbar, and technical information.
 */
@Composable
fun NowPlayingControls(
    controlsCollapsed: Boolean,
    onToggleCollapse: () -> Unit,
    currentTimeMs: Long,
    currentDurationMs: Long,
    onSeek: (Long) -> Unit,
    onSeekFinished: () -> Unit,
    isDraggingSlider: Boolean,
    onDraggingChanged: (Boolean) -> Unit,
    currentBitrate: String,
    currentFormat: String,
    primaryTint: Color,
    onShowMenu: () -> Unit,
    onShuffle: () -> Unit,
    isShuffleActive: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    loopMode: Int,
    onToggleLoop: () -> Unit,
    colorScheme: ColorScheme,
    formatTime: (Long) -> String
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp, start = 12.dp, end = 12.dp),
        shape = RoundedCornerShape(32.dp),
        color = Color.Black.copy(alpha = 0.5f),
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Row 1: Collapse | Seekbar Area | Menu ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onToggleCollapse) {
                    Icon(
                        if (controlsCollapsed) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Collapse",
                        tint = Color.White
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Slider(
                        value = (currentTimeMs.toFloat() / currentDurationMs.toFloat()).coerceIn(0f, 1f),
                        onValueChange = { fraction ->
                            onDraggingChanged(true)
                            onSeek((fraction * currentDurationMs).toLong())
                        },
                        onValueChangeFinished = {
                            onSeekFinished()
                            onDraggingChanged(false)
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = primaryTint,
                            activeTrackColor = primaryTint,
                            inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    // Time & Tech Info row
                    AnimatedVisibility(visible = !controlsCollapsed) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formatTime(currentTimeMs),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                            if (currentBitrate.isNotEmpty() || currentFormat.isNotEmpty()) {
                                Surface(
                                    color = primaryTint.copy(alpha = 0.2f),
                                    shape = CircleShape,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        primaryTint.copy(alpha = 0.4f)
                                    )
                                ) {
                                    Text(
                                        text = "${currentFormat} • ${currentBitrate}",
                                        modifier = Modifier.padding(
                                            horizontal = 8.dp,
                                            vertical = 2.dp
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White
                                    )
                                }
                            }
                            Text(
                                text = formatTime(currentDurationMs),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                    }
                }

                IconButton(onClick = onShowMenu) {
                    Icon(
                        Icons.Rounded.MoreVert,
                        contentDescription = "Menu",
                        tint = Color.White
                    )
                }
            }

            // ── Row 2: Playback Section [Shuffle | [Prev | Play | Next] | Loop] ──
            AnimatedVisibility(visible = !controlsCollapsed) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Shuffle
                    IconButton(onClick = onShuffle) {
                        Icon(
                            Icons.Rounded.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (isShuffleActive) primaryTint else Color.White.copy(alpha = 0.7f)
                        )
                    }

                    // Playback Pill
                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.1f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            IconButton(
                                onClick = onPrevious,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.SkipPrevious,
                                    contentDescription = "Previous",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            
                            Spacer(modifier = Modifier.width(4.dp))
                            
                            FilledIconButton(
                                onClick = onTogglePlay,
                                modifier = Modifier.size(64.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = primaryTint,
                                    contentColor = colorScheme.onPrimary
                                )
                            ) {
                                Icon(
                                    if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = "Play/Pause",
                                    modifier = Modifier.size(36.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            IconButton(
                                onClick = onNext,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.SkipNext,
                                    contentDescription = "Next",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }

                    // Loop
                    IconButton(onClick = onToggleLoop) {
                        Icon(
                            if (loopMode == 2) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                            contentDescription = "Repeat",
                            tint = if (loopMode != 0) primaryTint else Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}
