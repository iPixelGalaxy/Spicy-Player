package com.example.spicyplayer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.spicyplayer.animation.LyricsAnimator
import com.example.spicyplayer.animation.SpringSimulation
import com.example.spicyplayer.models.Letter
import com.example.spicyplayer.models.Line
import com.example.spicyplayer.models.Word
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Internal data class representing the layout and position of a single word.
 */
private data class WordLayout(
    val word: Word,
    val textLayoutResult: TextLayoutResult,
    val relativeOffset: Offset,
)

/**
 * Internal data class representing the layout and position of a full line of lyrics.
 */
private data class LineLayout(
    val line: Line,
    val words: List<WordLayout>,
    /** Absolute vertical position of the line. */
    val yOffset: Float,
    val height: Float,
    val totalWidth: Float,
    val maxRowWidth: Float,
    val isInterlude: Boolean,
    val isBackground: Boolean,
    val oppositeAligned: Boolean,
    val isSongwriter: Boolean,
)

/**
 * The main lyrics display component.
 * It handles text measurement, layout (including word wrapping), 
 * interactive scrolling, and custom canvas-based rendering with animations.
 *
 * @param lines The list of lyric lines to display.
 * @param currentTimeMs The current playback time in milliseconds.
 * @param onSeekWord Callback triggered when a user taps a line to seek to its start time.
 */
@Composable
fun SpicyCanvas(
    lines: List<Line>,
    currentTimeMs: Long,
    onSeekWord: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    var lineLayouts by remember { mutableStateOf<List<LineLayout>>(emptyList()) }
    val coroutineScope = rememberCoroutineScope()

    // The animator manages the spring-driven visual states of words and lines.
    val animator = remember { LyricsAnimator(coroutineScope) }
    LaunchedEffect(lines) { animator.reset() }

    // Use updated states to ensure the animation loop always has the latest data.
    val currentTimeMsUpdated by rememberUpdatedState(currentTimeMs)
    val linesUpdated by rememberUpdatedState(lines)
    val lineLayoutsUpdated by rememberUpdatedState(lineLayouts)

    // Scroll state management.
    val scrollSpring = remember { SpringSimulation(0f, 1.5f, 0.75f) }
    var userScrollOffset by remember { mutableFloatStateOf(0f) }
    var isUserScrolling by remember { mutableStateOf(false) }
    var userScrollDecayTimer by remember { mutableFloatStateOf(0f) }
    var lastInteractionTimeMs by remember { mutableLongStateOf(0L) }
    var lastFrameSongTime by remember { mutableLongStateOf(currentTimeMs) }
    var wasPlaying by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = modifier.fillMaxSize().clipToBounds()) {
        val canvasWidth = constraints.maxWidth.toFloat()
        val canvasHeight = constraints.maxHeight.toFloat()
        val centerY = canvasHeight / 2f
        val horizontalPadding = 40f
        
        // Layout calculations: Determine line wrapping and alignment.
        val hasDuet = remember(lines) { lines.any { it.oppositeAligned } }
        val maxLineWidth = if (hasDuet) {
            (canvasWidth - (horizontalPadding * 2)) * 0.85f
        } else {
            canvasWidth - (horizontalPadding * 2)
        }
        
        val baseFontSize = (canvasWidth / 20f).coerceIn(16f, 32f).sp
        val bgFontSize = baseFontSize * 0.7f
        val songwriterFontSize = baseFontSize * 0.5f

        // Recalculate layouts whenever the lyrics or dimensions change.
        LaunchedEffect(lines, canvasWidth) {
            withContext(Dispatchers.Default) {
                val layouts = mutableListOf<LineLayout>()
                var currentY = 0f
                val lineSpacing = 32f

                for (line in lines) {
                    val isInterlude = line.isInterlude
                    val isBg = line.isBackground
                    val fontSize = if (line.isSongwriter) songwriterFontSize else if (isBg) bgFontSize else baseFontSize

                    if (isInterlude) {
                        // Instrumental interludes are rendered as three dots.
                        // You can adjust the multiplier here to make the dots bigger or smaller:
                        val dotFontSize = baseFontSize * 1.7f
                        val dotLayouts = (0 until 3).map { dotIdx ->
                            val chunkDuration = line.duration / 3L
                            val dotStart = line.startMs + dotIdx * chunkDuration
                            val dotEnd = if (dotIdx == 2) line.endMs else dotStart + chunkDuration
                            val dotWord = Word("•", dotStart, dotEnd)
                            val result = textMeasurer.measure(
                                text = AnnotatedString("•"),
                                style = TextStyle(
                                    fontSize = dotFontSize,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                )
                            )
                            val dotW = result.size.width.toFloat()
                            val dotGap = (0.017f * canvasWidth).coerceIn(8f, 40f)
                            WordLayout(dotWord, result, Offset(dotIdx * (dotW + dotGap), 0f))
                        }
                        val dotH = dotLayouts.maxOfOrNull { it.textLayoutResult.size.height.toFloat() } ?: 0f
                        val totalDotsW = dotLayouts.lastOrNull()?.let { it.relativeOffset.x + it.textLayoutResult.size.width } ?: 0f
                        layouts.add(LineLayout(line, dotLayouts, currentY, dotH, totalDotsW, totalDotsW, true, isBg, line.oppositeAligned, false))
                        currentY += 0f // Interludes collapse when not active.
                        continue
                    }

                    // Standard lyric line layout.
                    val spaceWidth = textMeasurer.measure(
                        text = AnnotatedString(" "),
                        style = TextStyle(
                            fontSize = fontSize,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    ).size.width.toFloat()
                    val wordGap = spaceWidth

                    val measuredWords = line.words.mapIndexed { wIdx, word ->
                        val result = textMeasurer.measure(
                            text = AnnotatedString(word.text),
                            style = TextStyle(
                                fontSize = fontSize,
                                fontWeight = if (line.isSongwriter) FontWeight.Normal else FontWeight.Bold,
                                color = Color.White,
                                letterSpacing = (wIdx * 0.001f).sp
                            )
                        )
                        word to result
                    }

                    // Word-wrapping logic.
                    // If no duet, it uses a simple greedy wrap.
                    // If duet, it uses a balanced wrap to keep rows relatively equal in length.
                    val isSongwriterLine = line.isSongwriter
                    var R = 1
                    var currentW = 0f
                    val lineMaxWidth = if (isSongwriterLine) canvasWidth - (horizontalPadding * 2) else maxLineWidth
                    
                    for (i in measuredWords.indices) {
                        val w = measuredWords[i].second.size.width.toFloat()
                        val hspace = if (currentW > 0f && !measuredWords[i].first.isPartOfWord) wordGap else 0f
                        if (currentW + hspace + w > lineMaxWidth && currentW > 0f) {
                            R++
                            currentW = w
                        } else {
                            currentW += hspace + w
                        }
                    }

                    val numWords = measuredWords.size
                    val lineBreaks = mutableListOf<Int>()
                    
                    if (!hasDuet || isSongwriterLine) {
                        // Greedy wrap.
                        var currentLineW = 0f
                        var lastBreakCandidate = 0
                        lineBreaks.add(0)
                        
                        var i = 0
                        while (i < numWords) {
                            val wordW = measuredWords[i].second.size.width.toFloat()
                            val hspace = if (currentLineW > 0f && !measuredWords[i].first.isPartOfWord) wordGap else 0f
                            
                            val isForcedSyllable = i > 0 && measuredWords[i].first.isPartOfWord && !measuredWords[i - 1].first.text.endsWith("-")
                            if (!isForcedSyllable) {
                                lastBreakCandidate = i
                            }
                            
                            if (currentLineW + hspace + wordW > lineMaxWidth && currentLineW > 0f) {
                                val breakIdx = if (lastBreakCandidate > lineBreaks.last()) lastBreakCandidate else i
                                lineBreaks.add(breakIdx)
                                i = breakIdx
                                currentLineW = 0f
                            } else {
                                currentLineW += hspace + wordW
                                i++
                            }
                        }
                        lineBreaks.add(numWords)
                    } else {
                        // Balanced wrap using dynamic programming.
                        val dp = IntArray(numWords + 1) { Int.MAX_VALUE / 2 }
                        val breaks = IntArray(numWords + 1)
                        dp[0] = 0

                        val targetWordCount = numWords.toFloat() / R

                        for (i in 1..numWords) {
                            var w = 0f
                            var j = i - 1
                            while (j >= 0) {
                                val wordW = measuredWords[j].second.size.width.toFloat()
                                val hspace = if (j < i - 1 && !measuredWords[j + 1].first.isPartOfWord) wordGap else 0f
                                w += wordW + hspace
                                if (w > lineMaxWidth && i - j > 1) {
                                    break
                                }
                                
                                val isForcedSyllableBreak = j > 0 && measuredWords[j].first.isPartOfWord && !measuredWords[j - 1].first.text.endsWith("-")
                                
                                val wordsInLine = i - j
                                val variancePenalty = kotlin.math.abs(wordsInLine - targetWordCount)
                                val penalty = if (isForcedSyllableBreak) {
                                    Int.MAX_VALUE / 2
                                } else {
                                    (variancePenalty * 1000f).toInt() + (lineMaxWidth - w).toInt()
                                }

                                if (dp[j] + penalty < dp[i]) {
                                    dp[i] = dp[j] + penalty
                                    breaks[i] = j
                                }
                                j--
                            }
                        }

                        var curr = numWords
                        while (curr > 0) {
                            lineBreaks.add(0, curr)
                            curr = breaks[curr]
                        }
                        lineBreaks.add(0, 0)
                    }

                    // Assemble WordLayouts into rows.
                    val wordLayouts = mutableListOf<WordLayout>()
                    var rowY = 0f
                    var maxRowWidth = 0f
                    var rowHeight = 0f
                    val rowWidths = mutableMapOf<Float, Float>()

                    for (b in 0 until lineBreaks.size - 1) {
                        val startIdx = lineBreaks[b]
                        val endIdx = lineBreaks[b+1]
                        var rowX = 0f
                        rowHeight = 0f
                        for (idx in startIdx until endIdx) {
                            val (word, result) = measuredWords[idx]
                            val wordWidth = result.size.width.toFloat()
                            val actualGap = if (rowX > 0f && !word.isPartOfWord) wordGap else 0f
                            wordLayouts.add(WordLayout(word, result, Offset(rowX + actualGap, rowY)))
                            rowX += wordWidth + actualGap
                            rowHeight = maxOf(rowHeight, result.size.height.toFloat())
                        }
                        rowWidths[rowY] = rowX
                        maxRowWidth = maxOf(maxRowWidth, rowX)
                        if (b < lineBreaks.size - 2) {
                            rowY += rowHeight
                        }
                    }
                    
                    // Handle right-alignment for duet parts.
                    val isRightAligned = hasDuet && !line.oppositeAligned && !line.isSongwriter
                    if (isRightAligned) {
                        for (j in wordLayouts.indices) {
                            val wLayout = wordLayouts[j]
                            val rWidth = rowWidths[wLayout.relativeOffset.y] ?: maxRowWidth
                            val alignmentShift = maxRowWidth - rWidth
                            wordLayouts[j] = wLayout.copy(relativeOffset = Offset(wLayout.relativeOffset.x + alignmentShift, wLayout.relativeOffset.y))
                        }
                    }

                    val totalHeight = rowY + rowHeight
                    val totalWidth = maxRowWidth
                    
                    val drawY = if (isBg) currentY - 32f else if (line.isSongwriter) currentY + lineSpacing * 0.5f else currentY

                    layouts.add(LineLayout(line, wordLayouts, drawY, totalHeight, totalWidth, maxRowWidth, false, isBg, line.oppositeAligned, line.isSongwriter))
                    
                    val bottomY = drawY + totalHeight
                    currentY = maxOf(currentY, bottomY + (if (isBg) 32f else lineSpacing))
                }
                lineLayouts = layouts
            }
        }

        if (lineLayouts.isEmpty()) return@BoxWithConstraints

        var animStates by remember { mutableStateOf<List<LyricsAnimator.LineAnimState>>(emptyList()) }
        var dynamicYOffsets by remember { mutableStateOf<List<Float>>(emptyList()) }
        var animScrollY by remember { mutableFloatStateOf(0f) }

        // The high-frequency animation loop.
        LaunchedEffect(Unit) {
            while (true) {
                withFrameNanos { frameTimeNanos ->
                    val currentLayouts = lineLayoutsUpdated
                    val currentLines = linesUpdated
                    val currentTime = currentTimeMsUpdated

                    if (currentLayouts.isNotEmpty() && currentLines.isNotEmpty()) {
                        // 1. Step the animator for visual properties (scale, opacity, glow).
                        animStates = animator.animate(currentLines, currentTime, frameTimeNanos)

                        // 1.5 Calculate dynamic Y offsets based on interlude scales.
                        var accumulatedY = 0f
                        val newDynamicYOffsets = FloatArray(currentLayouts.size)
                        
                        for (i in currentLayouts.indices) {
                            val layout = currentLayouts[i]
                            val state = animStates.getOrNull(i)
                            
                            if (layout.isInterlude) {
                                val scale = state?.scale?.coerceIn(0f, 1f) ?: 0f
                                val padding = 64f * scale
                                val expansion = padding * 2f
                                
                                newDynamicYOffsets[i] = layout.yOffset + accumulatedY + padding
                                accumulatedY += expansion
                            } else {
                                newDynamicYOffsets[i] = layout.yOffset + accumulatedY
                            }
                        }
                        dynamicYOffsets = newDynamicYOffsets.toList()

                        // 2. Identify the active line and update the scroll target.
                        val activeIdx = currentLayouts.indices
                            .filter { !currentLayouts[it].isBackground }
                            .lastOrNull { currentLayouts[it].line.startMs <= currentTime }
                            ?.coerceAtLeast(0) ?: 0

                        if (activeIdx < currentLayouts.size) {
                            val activeLayout = currentLayouts[activeIdx]
                            val activeDynamicY = newDynamicYOffsets[activeIdx]
                            // The target scroll centers the active line vertically.
                            val targetY = -(activeDynamicY + activeLayout.height / 2f)
                            scrollSpring.setGoal(targetY)
                        }

                        // 3. Step the scroll spring and handle user overrides.
                        val dt = 0.016f
                        val totalContentHeight = (currentLayouts.lastOrNull()?.yOffset ?: 0f) + accumulatedY
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
                            // Clamp scroll and slowly decay user offset to return to auto-scroll.
                            if (totalScroll > maxScrollDown) {
                                userScrollOffset += (maxScrollDown - totalScroll) * 0.1f
                            } else if (totalScroll < maxScrollUp) {
                                userScrollOffset += (maxScrollUp - totalScroll) * 0.1f
                            } else if (isPlaying && timeSinceInteraction > 3000L && userScrollOffset != 0f) {
                                userScrollDecayTimer += dt
                                if (userScrollDecayTimer > 0.5f) {
                                    userScrollOffset *= 0.88f
                                    if (kotlin.math.abs(userScrollOffset) < 1f) userScrollOffset = 0f
                                }
                            } else if (!isPlaying) {
                                userScrollDecayTimer = 0f
                            }
                        } else {
                            userScrollDecayTimer = 0f
                            // Resistance when scrolling past boundaries.
                            if (totalScroll > maxScrollDown) {
                                userScrollOffset += (maxScrollDown - totalScroll) * 0.5f * dt
                            } else if (totalScroll < maxScrollUp) {
                                userScrollOffset += (maxScrollUp - totalScroll) * 0.5f * dt
                            }
                        }

                        animScrollY = actualSpringY + userScrollOffset
                    }
                }
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    // Interaction: Dragging.
                    detectDragGestures(
                        onDragStart = {
                            isUserScrolling = true
                            userScrollDecayTimer = 0f
                        },
                        onDragEnd = {
                            isUserScrolling = false
                            userScrollDecayTimer = 0f
                        },
                        onDragCancel = {
                            isUserScrolling = false
                            userScrollDecayTimer = 0f
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            userScrollOffset += dragAmount.y
                        }
                    )
                }
                .pointerInput(Unit) {
                    // Interaction: Tapping to seek.
                    detectTapGestures { tapOffset ->
                        val currentScrollY = animScrollY
                        val adjustedTapY = tapOffset.y - (centerY + currentScrollY)

                        for (i in lineLayouts.indices) {
                            val layout = lineLayouts[i]
                            val layoutDynamicY = dynamicYOffsets.getOrElse(i) { layout.yOffset }
                            if (adjustedTapY >= layoutDynamicY && adjustedTapY <= layoutDynamicY + layout.height) {
                                if (layout.isInterlude || layout.isSongwriter) continue
                                if (layout.line.words.isNotEmpty()) {
                                    onSeekWord(layout.line.startMs)
                                    userScrollOffset = 0f
                                    userScrollDecayTimer = 0f
                                    lastInteractionTimeMs = 0L
                                }
                                return@detectTapGestures
                            }
                        }
                    }
                }
        ) {
            val scrollOffset = centerY + animScrollY

            lineLayouts.forEachIndexed { lineIdx, layout ->
                val lineAnim = animStates.getOrNull(lineIdx) ?: return@forEachIndexed
                val dynamicY = dynamicYOffsets.getOrElse(lineIdx) { layout.yOffset }

                // Optimization: Don't draw invisible lines.
                if (lineAnim.opacity <= 0.01f) return@forEachIndexed

                // Optimization: Don't draw lines off-screen.
                val lineScreenY = scrollOffset + dynamicY
                if (lineScreenY < -layout.height * 3 || lineScreenY > canvasHeight + layout.height * 3) {
                    return@forEachIndexed
                }

                val lineStartX = getLineStartX(layout, size.width, horizontalPadding, hasDuet)

                // Render Instrumental Interlude (three dots).
                if (layout.isInterlude) {
                    val groupScale = lineAnim.scale.coerceIn(0f, 1f)
                    if (groupScale < 0.01f) return@forEachIndexed

                    val firstDot = layout.words.firstOrNull() ?: return@forEachIndexed
                    val lastDot  = layout.words.lastOrNull()  ?: return@forEachIndexed
                    val firstTextW = firstDot.textLayoutResult.size.width.toFloat()
                    val lastTextW  = lastDot.textLayoutResult.size.width.toFloat()

                    val dotGroupCentreX = lineStartX + firstDot.relativeOffset.x + firstTextW / 2f +
                        (lastDot.relativeOffset.x + lastTextW / 2f - firstDot.relativeOffset.x - firstTextW / 2f) / 2f
                    val dotGroupCentreY = dynamicY + scrollOffset

                    layout.words.forEachIndexed { dotIdx, wLayout ->
                        val dotAnim    = lineAnim.wordStates.getOrNull(dotIdx) ?: return@forEachIndexed
                        val dotOpacity = dotAnim.glow.coerceIn(0f, 1f)

                        val xPos  = lineStartX + wLayout.relativeOffset.x
                        val textW = wLayout.textLayoutResult.size.width.toFloat()
                        val textH = wLayout.textLayoutResult.size.height.toFloat()
                        val baseYPos = dotGroupCentreY - textH / 2f

                        val dotPivotX = xPos + textW / 2f
                        val dotPivotY = baseYPos + textH / 2f
                        val dotYShift = dotAnim.yOffset * textH

                        withTransform({
                            // Overall group scale.
                            scale(groupScale, groupScale, Offset(dotGroupCentreX, dotGroupCentreY))
                            // Individual dot bounce and scale.
                            scale(dotAnim.scale.coerceIn(0f, 1.5f), dotAnim.scale.coerceIn(0f, 1.5f), Offset(dotPivotX, dotPivotY))
                            translate(top = dotYShift)
                        }) {
                            drawText(
                                textLayoutResult = wLayout.textLayoutResult,
                                color = Color.White,
                                alpha = dotOpacity * lineAnim.opacity,
                                topLeft = Offset(xPos, baseYPos),
                            )
                        }
                    }
                    return@forEachIndexed
                }

                // Render standard lyric words.
                layout.words.forEachIndexed { wordIdx, wLayout ->
                    val wordAnim = lineAnim.wordStates.getOrNull(wordIdx) ?: return@forEachIndexed

                    val xPos = lineStartX + wLayout.relativeOffset.x
                    val yPos = dynamicY + wLayout.relativeOffset.y

                    val textWidth = wLayout.textLayoutResult.size.width.toFloat()
                    val textHeight = wLayout.textLayoutResult.size.height.toFloat()

                    val baseBright = LyricsAnimator.GRADIENT_ALPHA_BRIGHT
                    val baseDim = LyricsAnimator.GRADIENT_ALPHA_DIM

                    // Syllabic highlighting (LetterGroups).
                    if (wordAnim.isLetterGroup && wordAnim.letterStates.size == wLayout.word.text.length) {
                        val textLen = wLayout.word.text.length
                        for (li in 0 until textLen) {
                            val lState = wordAnim.letterStates[li]
                            val rect   = wLayout.textLayoutResult.getBoundingBox(li)

                            val lXPos   = xPos + rect.left
                            val lYPos   = yPos + rect.top
                            val lWidth  = rect.width
                            val lHeight = rect.height

                            val sLXPos   = lXPos
                            val sLYPos   = lYPos + scrollOffset
                            val sPivotX  = sLXPos + lWidth  / 2f
                            val sPivotY  = sLYPos + lHeight / 2f

                            val lYShift = lState.yOffset * textHeight * 2f

                            // Bloom effect for active letters.
                            val lGlowBlur    = 4f + 12f * lState.glow
                            val lGlowOpacity = (lState.glow * LyricsAnimator.LETTER_GLOW_MULTIPLIER_OPACITY).coerceIn(0f, 1f)
                            val lShadow = if (lGlowOpacity > 0.02f) Shadow(
                                color = Color.White.copy(alpha = lGlowOpacity * lineAnim.opacity),
                                blurRadius = lGlowBlur,
                            ) else null

                            val brightAlpha = (baseDim + (baseBright - baseDim) * wordAnim.activeAnimFactor) * lineAnim.opacity
                            val dimAlpha    = baseDim * lineAnim.opacity
                            val gradientFraction = ((lState.gradientPosition + 20f) / 120f).coerceIn(0f, 1f)

                            // Clip to the letter's bounding box and apply per-letter transforms.
                            clipRect(
                                left   = sLXPos - 2f,
                                top    = sLYPos - lHeight * 0.25f,
                                right  = sLXPos + lWidth + 2f,
                                bottom = sLYPos + lHeight * 1.25f,
                            ) {
                                withTransform({
                                    scale(lState.scale, lState.scale, Offset(sPivotX, sPivotY))
                                    translate(top = lYShift)
                                }) {
                                    // 1. Draw the basic dimmed word.
                                    drawText(
                                        textLayoutResult = wLayout.textLayoutResult,
                                        color = Color.White,
                                        alpha = dimAlpha.coerceIn(0f, 1f),
                                        topLeft = Offset(xPos, yPos + scrollOffset),
                                    )
                                    // 2. Draw the highlighted swept layer (active syllable).
                                    if (brightAlpha > dimAlpha + 0.01f && gradientFraction > 0.001f) {
                                        clipRect(
                                            left   = sLXPos - 1f,
                                            top    = sLYPos - lHeight * 0.25f,
                                            right  = sLXPos + lWidth * gradientFraction + 1f,
                                            bottom = sLYPos + lHeight * 1.25f,
                                        ) {
                                            drawText(
                                                textLayoutResult = wLayout.textLayoutResult,
                                                color = Color.White,
                                                alpha = (brightAlpha - dimAlpha).coerceIn(0f, 1f),
                                                shadow = lShadow,
                                                topLeft = Offset(xPos, yPos + scrollOffset),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Standard word-level highlighting.
                        val glowBlur = 4f + 2f * wordAnim.glow
                        val glowOpacity = (wordAnim.glow * 0.35f).coerceIn(0f, 1f)
                        val shadow = if (glowOpacity > 0.02f) {
                            Shadow(
                                color = Color.White.copy(alpha = glowOpacity * lineAnim.opacity),
                                blurRadius = glowBlur,
                            )
                        } else null

                        val wordScale = wordAnim.scale
                        val wordYShift = wordAnim.yOffset * textHeight

                        val pivotX = xPos + textWidth / 2f
                        val pivotY = yPos + textHeight / 2f

                        withTransform({
                            translate(top = scrollOffset)
                            scale(scaleX = wordScale, scaleY = wordScale, pivot = Offset(pivotX, pivotY))
                            translate(top = wordYShift)
                        }) {
                            val gradientFraction = ((wordAnim.gradientPosition + 20f) / 120f).coerceIn(0f, 1f)
                            val clipX = xPos + textWidth * gradientFraction

                            val brightAlpha = (baseDim + (baseBright - baseDim) * wordAnim.activeAnimFactor) * lineAnim.opacity
                            val dimAlpha = baseDim * lineAnim.opacity

                            // 1. Draw dimmed word.
                            drawText(
                                textLayoutResult = wLayout.textLayoutResult,
                                color = Color.White,
                                alpha = dimAlpha.coerceIn(0f, 1f),
                                topLeft = Offset(xPos, yPos),
                            )

                            // 2. Draw swept bright layer.
                            if (brightAlpha > dimAlpha + 0.01f && gradientFraction > 0.001f) {
                                clipRect(
                                    left = xPos - 2f,
                                    top = yPos - 2f,
                                    right = clipX,
                                    bottom = yPos + textHeight + 2f,
                                ) {
                                    drawText(
                                        textLayoutResult = wLayout.textLayoutResult,
                                        color = Color.White,
                                        alpha = (brightAlpha - dimAlpha).coerceIn(0f, 1f),
                                        shadow = shadow,
                                        topLeft = Offset(xPos, yPos),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Calculates the horizontal starting position of a line based on its alignment and the presence of duets.
 */
private fun getLineStartX(
    layout: LineLayout,
    canvasWidth: Float,
    horizontalPadding: Float,
    hasDuet: Boolean,
): Float {
    if (layout.isSongwriter) {
        return horizontalPadding
    }
    return if (!hasDuet || layout.oppositeAligned) {
        horizontalPadding
    } else {
        canvasWidth - horizontalPadding - layout.totalWidth
    }
}
