package com.finance.splitup.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import com.finance.splitup.ui.theme.SplitUpType

enum class TourHighlightArea {
    TOP_SUMMARY_CARD,
    BOTTOM_RIGHT_FAB,
    TOP_SEARCH_FILTERS,
    CENTER_GROUPS_LIST,
    SETTINGS_PREFERENCES
}

object TourManager {
    val boundsMap = mutableStateMapOf<TourHighlightArea, Rect>()

    fun reportBounds(area: TourHighlightArea, rect: Rect) {
        if (rect != Rect.Zero && rect.width > 0f && rect.height > 0f) {
            boundsMap[area] = rect
        }
    }

    fun getBounds(area: TourHighlightArea): Rect? {
        return boundsMap[area]
    }
}

fun Modifier.tourTarget(area: TourHighlightArea): Modifier = this.onGloballyPositioned { coordinates ->
    val bounds = coordinates.boundsInRoot()
    TourManager.reportBounds(area, bounds)
}

data class TourStepDetails(
    val stepIndex: Int,
    val title: String,
    val badgeLabel: String,
    val description: String,
    val icon: ImageVector,
    val highlightArea: TourHighlightArea,
    val cardAlignment: Alignment
)

@Composable
fun DemoTourOverlay(
    currentStep: Int,
    totalSteps: Int = 5,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit
) {
    val steps = remember {
        listOf(
            TourStepDetails(
                stepIndex = 0,
                title = "Dashboard & Net Worth",
                badgeLabel = "🎯 Net Worth Overview",
                description = "Track your net worth, income, and cash flow summary at a glance.",
                icon = Icons.Rounded.Dashboard,
                highlightArea = TourHighlightArea.TOP_SUMMARY_CARD,
                cardAlignment = Alignment.BottomCenter
            ),
            TourStepDetails(
                stepIndex = 1,
                title = "Quick Actions & AI Scanner",
                badgeLabel = "⚡ Floating Action Button",
                description = "Tap (+) anytime to log expenses, create groups, or scan receipts with AI.",
                icon = Icons.Rounded.Bolt,
                highlightArea = TourHighlightArea.BOTTOM_RIGHT_FAB,
                cardAlignment = Alignment.TopCenter
            ),
            TourStepDetails(
                stepIndex = 2,
                title = "Expense & Income Tracker",
                badgeLabel = "🔍 Personal Finances",
                description = "Search transactions instantly and filter by category to manage spending habits.",
                icon = Icons.Rounded.ReceiptLong,
                highlightArea = TourHighlightArea.TOP_SEARCH_FILTERS,
                cardAlignment = Alignment.BottomCenter
            ),
            TourStepDetails(
                stepIndex = 3,
                title = "Group Splitting & Settlements",
                badgeLabel = "👥 Shared Groups & Balances",
                description = "Manage shared expenses, view net balances, and calculate optimal debt settlements.",
                icon = Icons.Rounded.Groups,
                highlightArea = TourHighlightArea.CENTER_GROUPS_LIST,
                cardAlignment = Alignment.BottomCenter
            ),
            TourStepDetails(
                stepIndex = 4,
                title = "Preferences & Security",
                badgeLabel = "⚙️ Currency & PDF Export",
                description = "Customize currency, enable PIN security, and export PDF/CSV financial reports.",
                icon = Icons.Rounded.Security,
                highlightArea = TourHighlightArea.SETTINGS_PREFERENCES,
                cardAlignment = Alignment.BottomCenter
            )
        )
    }

    val infiniteTransition = rememberInfiniteTransition(label = "spotlight_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val density = LocalDensity.current

    val currentStepData = steps.getOrElse(currentStep) { steps.last() }
    val measuredBounds = TourManager.getBounds(currentStepData.highlightArea)

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // 1. Dynamic Canvas Darkened Overlay with True Measured Gap Cutout (BlendMode.Clear)
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                // Consume every tap on the scrim so the dimmed app content and the
                // bottom navigation bar stay inert while the tour is active.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {}
                .graphicsLayer { alpha = 0.99f }
        ) {
            drawRect(color = Color.Black.copy(alpha = 0.72f))

            if (measuredBounds != null) {
                with(density) {
                    val left = measuredBounds.left - 8.dp.toPx()
                    val top = measuredBounds.top - 8.dp.toPx()
                    val width = measuredBounds.width + 16.dp.toPx()
                    val height = measuredBounds.height + 16.dp.toPx()
                    val cornerRadius = CornerRadius(20.dp.toPx(), 20.dp.toPx())

                    if (currentStepData.highlightArea == TourHighlightArea.BOTTOM_RIGHT_FAB) {
                        val centerX = measuredBounds.center.x
                        val centerY = measuredBounds.center.y
                        val radius = (measuredBounds.width / 1.5f) * pulseScale

                        drawCircle(
                            color = Color.Transparent,
                            center = Offset(centerX, centerY),
                            radius = radius,
                            blendMode = BlendMode.Clear
                        )
                        drawCircle(
                            color = primaryColor.copy(alpha = pulseAlpha),
                            center = Offset(centerX, centerY),
                            radius = radius,
                            style = Stroke(width = 3.5.dp.toPx())
                        )
                    } else {
                        drawRoundRect(
                            color = Color.Transparent,
                            topLeft = Offset(left, top),
                            size = Size(width, height),
                            cornerRadius = cornerRadius,
                            blendMode = BlendMode.Clear
                        )
                        drawRoundRect(
                            brush = Brush.horizontalGradient(listOf(primaryColor, tertiaryColor)),
                            topLeft = Offset(left, top),
                            size = Size(width, height),
                            cornerRadius = cornerRadius,
                            style = Stroke(width = 3.dp.toPx())
                        )
                    }
                }
            }
        }

        // Animated Page Transitions for Badge and Tooltip Card
        AnimatedContent(
            targetState = currentStep,
            transitionSpec = {
                if (targetState > initialState) {
                    (slideInHorizontally(animationSpec = tween(350, easing = FastOutSlowInEasing)) { width -> width / 3 } +
                            fadeIn(animationSpec = tween(350)))
                        .togetherWith(
                            slideOutHorizontally(animationSpec = tween(350, easing = FastOutSlowInEasing)) { width -> -width / 3 } +
                                    fadeOut(animationSpec = tween(350))
                        )
                } else {
                    (slideInHorizontally(animationSpec = tween(350, easing = FastOutSlowInEasing)) { width -> -width / 3 } +
                            fadeIn(animationSpec = tween(350)))
                        .togetherWith(
                            slideOutHorizontally(animationSpec = tween(350, easing = FastOutSlowInEasing)) { width -> width / 3 } +
                                    fadeOut(animationSpec = tween(350))
                        )
                }
            },
            label = "tour_page_transition",
            modifier = Modifier.fillMaxSize()
        ) { targetStep ->
            val step = steps.getOrElse(targetStep) { steps.last() }
            val targetBounds = TourManager.getBounds(step.highlightArea)

            Box(modifier = Modifier.fillMaxSize()) {
                // 2. Dynamic Badge Indicator Label centered above measured target
                var badgeWidthPx by remember { mutableIntStateOf(0) }
                if (targetBounds != null) {
                    with(density) {
                        val badgeTopPx = (targetBounds.top - 42.dp.toPx()).coerceAtLeast(16.dp.toPx())
                        val screenWidthPx = LocalConfiguration.current.screenWidthDp.dp.toPx()
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = primaryColor,
                            modifier = Modifier
                                .onSizeChanged { badgeWidthPx = it.width }
                                .offset {
                                    val x = (targetBounds.center.x - badgeWidthPx / 2f)
                                        .coerceIn(16.dp.toPx(), (screenWidthPx - badgeWidthPx - 16.dp.toPx()).coerceAtLeast(16.dp.toPx()))
                                    IntOffset(x.roundToInt(), badgeTopPx.roundToInt())
                                }
                        ) {
                            Text(text = step.badgeLabel, color = MaterialTheme.colorScheme.onPrimary, style = SplitUpType.ChipLabel, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                        }
                    }
                }

                // 3. Persistent Tour Tooltip Card
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .padding(
                            top = if (step.cardAlignment == Alignment.TopCenter) 80.dp else 0.dp,
                            bottom = if (step.cardAlignment == Alignment.BottomCenter) 40.dp else 0.dp
                        ),
                    contentAlignment = step.cardAlignment
                ) {
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = "Step ${targetStep + 1} of $totalSteps",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                TextButton(
                                    onClick = onSkip,
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text("Skip Tour", color = MaterialTheme.colorScheme.onSurfaceVariant, style = SplitUpType.BodySmallAlt)
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = primaryColor.copy(alpha = 0.15f),
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            step.icon,
                                            contentDescription = null,
                                            tint = primaryColor,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = step.title, style = SplitUpType.CardTitle, color = MaterialTheme.colorScheme.onSurface)
                                    Spacer(modifier = Modifier.height(1.dp))
                                    Text(
                                        text = step.description,
                                        style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        lineHeight = 17.sp
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (targetStep > 0) {
                                    OutlinedButton(
                                        onClick = onBack,
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(vertical = 8.dp)
                                    ) {
                                        Text("Back", style = SplitUpType.BodySmallAlt)
                                    }
                                }
                                Button(
                                    onClick = onNext,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(vertical = 8.dp)
                                ) {
                                    Text(if (targetStep < totalSteps - 1) "Next ➔" else "Finish Tour 🚀", style = SplitUpType.BadgeLabel)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
