package com.tx24.spicyplayer.ui.canvas

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tx24.spicyplayer.animation.SpringSimulation
import kotlin.math.abs

internal class ScrollManager(
    private val scrollSpring: SpringSimulation = SpringSimulation(0f, 1.5f, 0.75f)
) {
    var userScrollOffset by mutableFloatStateOf(0f)
        private set
    var isUserScrolling by mutableStateOf(false)
        private set
    private var userScrollDecayTimer by mutableFloatStateOf(0f)
    private var lastInteractionTimeMs by mutableLongStateOf(0L)
    private var lastFrameSongTime by mutableLongStateOf(0L)
    private var wasPlaying by mutableStateOf(false)
    
    var animScrollY by mutableFloatStateOf(0f)
        private set

    private var scrollVelocity by mutableFloatStateOf(0f)
    private var lastDragTimeMs by mutableLongStateOf(0L)

    fun updateScroll(
        currentTime: Long,
        dt: Float,
        totalContentHeight: Float,
        targetY: Float?
    ) {
        if (targetY != null) {
            scrollSpring.setGoal(targetY)
        }
        
        val actualSpringY = scrollSpring.step(dt)
        val maxScrollDown = 0f
        val maxScrollUp = -totalContentHeight
        val totalScroll = actualSpringY + userScrollOffset
        
        val isPlaying = currentTime != lastFrameSongTime
        if (isPlaying && !wasPlaying) {
            lastInteractionTimeMs = 0L // Resume auto-scrolling when playback starts.
        }
        wasPlaying = isPlaying
        lastFrameSongTime = currentTime

        if (isUserScrolling) {
            lastInteractionTimeMs = System.currentTimeMillis()
        }
        
        val timeSinceInteraction = System.currentTimeMillis() - lastInteractionTimeMs
        
        if (!isUserScrolling) {
            // Apply inertia if there is remaining velocity.
            if (abs(scrollVelocity) > 0.1f) {
                userScrollOffset += scrollVelocity * dt
                // Friction / Decay
                scrollVelocity *= 0.95f 
                if (abs(scrollVelocity) < 10f) scrollVelocity = 0f
                
                // While moving with inertia, keep the interaction timer fresh.
                lastInteractionTimeMs = System.currentTimeMillis()
            }

            val totalScroll = actualSpringY + userScrollOffset
            // Clamp scroll and slowly decay user offset to return to auto-scroll.
            if (totalScroll > maxScrollDown) {
                userScrollOffset += (maxScrollDown - totalScroll) * 0.15f
                scrollVelocity = 0f
            } else if (totalScroll < maxScrollUp) {
                userScrollOffset += (maxScrollUp - totalScroll) * 0.15f
                scrollVelocity = 0f
            } else if (isPlaying && timeSinceInteraction > 3000L && userScrollOffset != 0f) {
                userScrollDecayTimer += dt
                if (userScrollDecayTimer > 0.5f) {
                    userScrollOffset *= 0.88f
                    if (abs(userScrollOffset) < 1f) userScrollOffset = 0f
                }
            } else if (!isPlaying) {
                userScrollDecayTimer = 0f
            }
        } else {
            userScrollDecayTimer = 0f
            val totalScroll = actualSpringY + userScrollOffset
            // Resistance when scrolling past boundaries.
            if (totalScroll > maxScrollDown) {
                userScrollOffset += (maxScrollDown - totalScroll) * 0.5f * dt
            } else if (totalScroll < maxScrollUp) {
                userScrollOffset += (maxScrollUp - totalScroll) * 0.5f * dt
            }
        }

        animScrollY = actualSpringY + userScrollOffset
    }

    fun onDragStart() {
        isUserScrolling = true
        userScrollDecayTimer = 0f
        scrollVelocity = 0f
        lastDragTimeMs = System.currentTimeMillis()
    }

    fun onDragEnd() {
        isUserScrolling = false
        userScrollDecayTimer = 0f
    }

    fun onDrag(dy: Float) {
        val now = System.currentTimeMillis()
        val dtSec = (now - lastDragTimeMs) / 1000f
        if (dtSec > 0) {
            val instantV = dy / dtSec
            scrollVelocity = scrollVelocity * 0.4f + instantV * 0.6f
        }
        lastDragTimeMs = now
        userScrollOffset += dy
    }

    fun onSeek() {
        userScrollOffset = 0f
        userScrollDecayTimer = 0f
        lastInteractionTimeMs = 0L
    }
}
