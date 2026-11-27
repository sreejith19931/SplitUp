package com.finance.splitup.ui.components

import android.content.res.Configuration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finance.splitup.ui.theme.*

enum class LogoStyle {
    REFINED_CARD_SPLITUP,    // Finalized Option 19: Ultra-refined split credit card & receipt wordmark with microchip & contactless waves
    ANIMATED_LAUNCH,         // Animated card expansion sequence (panels expand from center dotted line, details fade in)
    MINIMAL,                 // Minimal card symbol
    HEADER_WITH_TEXT,        // Brand header (platinum card logo mark without text)
    CIRCULAR_NAVY_PLATINUM   // Circular navy platinum emblem badge
}

/**
 * Finalized Platinum Card Logo Suite for SplitUp (Option 19).
 * Clean, human-crafted FinTech branding representing split bills, cards, and secure settlement.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun SplitUpLogo(
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    style: LogoStyle = LogoStyle.REFINED_CARD_SPLITUP,
    subtitle: String? = "SMART EXPENSE SPLITTING"
) {
    when (style) {
        LogoStyle.REFINED_CARD_SPLITUP, LogoStyle.MINIMAL -> {
            RefinedCardSplitUpWordmark(modifier = modifier)
        }
        LogoStyle.ANIMATED_LAUNCH -> {
            AnimatedSplitUpLaunchLogo(modifier = modifier)
        }
        LogoStyle.HEADER_WITH_TEXT -> {
            RefinedCardSplitUpWordmark(modifier = modifier)
        }
        LogoStyle.CIRCULAR_NAVY_PLATINUM -> {
            CircularNavyPlatinumLogo(modifier = modifier, size = if (size == 64.dp) 256.dp else size)
        }
    }
}

/**
 * Option 19: "The Refined Platinum Card SplitUp"
 * An ultra-polished FinTech wordmark integrating the full "SplitUp" name across split payment card halves
 * featuring a detailed gold microchip, contactless payment waves, and a glowing cyan-dotted perforated receipt tear line.
 */
@Composable
fun RefinedCardSplitUpWordmark(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .semantics { contentDescription = "SplitUp Logo" }
            .shadow(elevation = 6.dp, shape = RoundedCornerShape(10.dp), clip = false),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = 160.dp)
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // Left side: "Split" + Gold Microchip + Security Hologram (Cobalt Card Half)
            Surface(
                color = LogoPrimaryBlue, // Rich Cobalt Blue
                shape = RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp),
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 0.5.dp)
                    .fillMaxHeight()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Microchip
                        Box(
                            modifier = Modifier
                                .size(14.dp, 10.dp)
                                .background(LogoGold, RoundedCornerShape(2.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp, 6.dp)
                                    .background(LogoChipTextAmber, RoundedCornerShape(1f))
                            )
                        }
                        // Security Hologram Square
                        Box(
                            modifier = Modifier
                                .size(8.dp, 10.dp)
                                .background(LogoLightBlue.copy(alpha = 0.8f), RoundedCornerShape(2.dp))
                        )
                    }
                    Text(
                        text = "Split",
                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            letterSpacing = (-0.5).sp
                        )
                    )
                }
            }

            // Right side: "Up" + Contactless Waves (Emerald Card Half)
            Surface(
                color = LogoEmeraldTeal, // Deep Emerald Teal
                shape = RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp),
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 0.5.dp)
                    .fillMaxHeight()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End
                ) {
                    // Contactless payment waves icon representation (multiple ))) arcs)
                    Box(
                        modifier = Modifier.size(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(16.dp)) {
                            val strokeWidth = 1.5f
                            // Arc 1 (inner)
                            drawArc(
                                color = Color.White.copy(alpha = 0.9f),
                                startAngle = -50f,
                                sweepAngle = 100f,
                                useCenter = false,
                                topLeft = Offset(size.width * 0.3f, size.height * 0.2f),
                                size = Size(size.width * 0.4f, size.height * 0.6f),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
                            )
                            // Arc 2 (outer)
                            drawArc(
                                color = Color.White.copy(alpha = 0.7f),
                                startAngle = -50f,
                                sweepAngle = 100f,
                                useCenter = false,
                                topLeft = Offset(size.width * 0.1f, size.height * 0.05f),
                                size = Size(size.width * 0.8f, size.height * 0.9f),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
                            )
                        }
                    }
                    Text(
                        text = "Up",
                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            letterSpacing = (-0.5).sp
                        )
                    )
                }
            }
        }

        // Center Perforated Receipt Seam Line with cyan dots
        Column(
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.height(44.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            repeat(7) {
                Box(
                    modifier = Modifier
                        .size(2.5.dp)
                        .background(LogoCyan, CircleShape)
                )
            }
        }
    }
}

/**
 * Animated Card Expansion Launch Logo Sequence:
 * Left and right card panels expand outward from the center perforated receipt tear line,
 * followed by the microchip, hologram, and contactless payment symbol fading in smoothly.
 * Exact same width and layout as the standard app logo mark.
 */
@Composable
fun AnimatedSplitUpLaunchLogo(
    modifier: Modifier = Modifier,
    onAnimationFinished: () -> Unit = {}
) {
    var isStarted by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isStarted) 1f else 0.2f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    )

    val expandProgress by animateFloatAsState(
        targetValue = if (isStarted) 1f else 0f,
        animationSpec = tween(durationMillis = 700, delayMillis = 100)
    )

    val contentAlpha by animateFloatAsState(
        targetValue = if (isStarted) 1f else 0f,
        animationSpec = tween(durationMillis = 500, delayMillis = 200)
    )

    LaunchedEffect(Unit) {
        isStarted = true
        kotlinx.coroutines.delay(1200L)
        onAnimationFinished()
    }

    Box(
        modifier = modifier
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.height(68.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // Left side panel (expands from center rightwards)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(if (expandProgress == 0f) 0.001f else expandProgress)
            ) {
                Surface(
                    color = LogoPrimaryBlue,
                    shape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(end = 0.5.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 8.dp)
                            .graphicsLayer(alpha = contentAlpha),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Microchip
                            Box(
                                modifier = Modifier
                                    .size(16.dp, 12.dp)
                                    .background(LogoGold, RoundedCornerShape(2.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp, 8.dp)
                                        .background(LogoChipTextAmber, RoundedCornerShape(1f))
                                )
                            }
                            // Hologram
                            Box(
                                modifier = Modifier
                                    .size(10.dp, 12.dp)
                                    .background(LogoLightBlue.copy(alpha = 0.8f), RoundedCornerShape(2.dp))
                            )
                        }
                        Text(
                            text = "Split",
                            style = androidx.compose.material3.MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                letterSpacing = (-0.5).sp
                            )
                        )
                    }
                }
            }

            // Right side panel (expands from center leftwards)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(if (expandProgress == 0f) 0.001f else expandProgress)
            ) {
                Surface(
                    color = LogoEmeraldTeal,
                    shape = RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 0.5.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 8.dp)
                            .graphicsLayer(alpha = contentAlpha),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.End
                    ) {
                        Box(
                            modifier = Modifier.size(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.size(16.dp)) {
                                val strokeWidth = 1.5f
                                drawArc(
                                    color = Color.White.copy(alpha = 0.9f),
                                    startAngle = -50f,
                                    sweepAngle = 100f,
                                    useCenter = false,
                                    topLeft = Offset(size.width * 0.3f, size.height * 0.2f),
                                    size = Size(size.width * 0.4f, size.height * 0.6f),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
                                )
                                drawArc(
                                    color = Color.White.copy(alpha = 0.7f),
                                    startAngle = -50f,
                                    sweepAngle = 100f,
                                    useCenter = false,
                                    topLeft = Offset(size.width * 0.1f, size.height * 0.05f),
                                    size = Size(size.width * 0.8f, size.height * 0.9f),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
                                )
                            }
                        }
                        Text(
                            text = "Up",
                            style = androidx.compose.material3.MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                letterSpacing = (-0.5).sp
                            )
                        )
                    }
                }
            }
        }

        // Center Perforated Receipt Split Line (Anchor)
        Column(
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.height(44.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            repeat(7) {
                Box(
                    modifier = Modifier
                        .size(2.5.dp)
                        .background(LogoCyan, CircleShape)
                )
            }
        }
    }
}

@Preview(name = "Platinum Card Logo - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun PreviewPlatinumCardLogoDark() {
    SplitUpTheme(dynamicColor = false) {
        Surface(color = DarkSlateBackground, modifier = Modifier.padding(24.dp)) {
            SplitUpLogo(style = LogoStyle.REFINED_CARD_SPLITUP)
        }
    }
}

@Composable
fun CircularNavyPlatinumLogo(
    modifier: Modifier = Modifier,
    size: Dp = 256.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .background(DarkSlateBackground, CircleShape)
            .border(
                width = 3.dp,
                brush = Brush.linearGradient(
                    colors = listOf(LogoCyan, LogoLightBlue, LogoPrimaryBlue)
                ),
                shape = CircleShape
            )
            .padding(14.dp),
        contentAlignment = Alignment.Center
    ) {
        RefinedCardSplitUpWordmark(
            modifier = Modifier
                .height(84.dp)
                .width(160.dp)
        )
    }
}

@Preview(name = "Circular Navy Platinum Logo")
@Composable
fun PreviewCircularNavyPlatinumLogo() {
    SplitUpTheme(dynamicColor = false) {
        CircularNavyPlatinumLogo(size = 256.dp)
    }
}

@Preview(name = "Animated Card Expansion Logo", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun PreviewAnimatedLaunchLogo() {
    SplitUpTheme(dynamicColor = false) {
        Surface(color = DarkSlateBackground, modifier = Modifier.padding(24.dp)) {
            SplitUpLogo(style = LogoStyle.ANIMATED_LAUNCH)
        }
    }
}
