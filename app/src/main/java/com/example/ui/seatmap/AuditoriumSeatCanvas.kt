package com.example.ui.seatmap

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.RoomType
import com.example.ui.theme.PlanetBorder
import com.example.ui.theme.PlanetBorderSubtle
import com.example.ui.theme.PlanetCardBg
import com.example.ui.theme.PlanetDarkBg
import com.example.ui.theme.PlanetGold
import com.example.ui.theme.PlanetImaxSkyLight
import com.example.ui.theme.PlanetMotion
import com.example.ui.theme.PlanetRadius
import com.example.ui.theme.PlanetSpacing
import com.example.ui.theme.PlanetSurface
import com.example.ui.theme.PlanetTextMuted
import com.example.ui.theme.PlanetTextPrimary
import com.example.ui.theme.PlanetTextSecondary
import com.example.ui.theme.rememberReducedMotion
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Interactive pinch-to-zoom, pan, double-tap, and seat-selectable auditorium layout viewport.
 * Preserves physical (cx, cy) coordinate geometry independent of RTL layout direction while
 * keeping all labels in Hebrew RTL.
 */
@Composable
fun AuditoriumSeatCanvas(
    model: AuditoriumSeatMapUiModel,
    selectedSeatId: String?,
    onSeatSelected: (AuditoriumSeatUiItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val reducedMotion = rememberReducedMotion()
    var zoomScale by remember(model.screeningId) { mutableFloatStateOf(1f) }
    var panOffset by remember(model.screeningId) { mutableStateOf(Offset.Zero) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(PlanetRadius.lg))
            .background(PlanetDarkBg)
            .border(1.dp, PlanetBorderSubtle, RoundedCornerShape(PlanetRadius.lg))
            .testTag("auditorium_seat_canvas"),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // Top Zoom & Orientation Control Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(PlanetSurface)
                .border(0.5.dp, PlanetBorderSubtle)
                .padding(horizontal = PlanetSpacing.sm, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "גרור להזזה • צבוט או לחץ +/- לזום",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = PlanetTextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(
                    onClick = {
                        val next = (zoomScale - 0.4f).coerceIn(1f, 3.6f)
                        zoomScale = next
                        if (next <= 1.01f) panOffset = Offset.Zero
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .size(48.dp)
                        .testTag("seat_map_zoom_out")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Remove,
                        contentDescription = "הקטן תצוגה",
                        tint = PlanetTextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Text(
                    text = "${(zoomScale * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontFeatureSettings = "tnum"
                    ),
                    color = PlanetTextSecondary,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                IconButton(
                    onClick = {
                        zoomScale = (zoomScale + 0.4f).coerceIn(1f, 3.6f)
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .size(48.dp)
                        .testTag("seat_map_zoom_in")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "הגדל תצוגה",
                        tint = PlanetTextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = {
                        zoomScale = 1f
                        panOffset = Offset.Zero
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .size(48.dp)
                        .testTag("seat_map_zoom_reset")
                ) {
                    Icon(
                        imageVector = Icons.Filled.CenterFocusStrong,
                        contentDescription = "אפס מיקום וזום",
                        tint = PlanetImaxSkyLight,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Interactive Auditorium Viewport
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(380.dp)
                .clipToBounds()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF04070D), PlanetDarkBg, Color(0xFF0A101E))
                    )
                )
                .pointerInput(model.screeningId) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = (zoomScale * zoom).coerceIn(1f, 3.6f)
                        val maxPanX = (size.width * (newScale - 1f)) * 0.55f
                        val maxPanY = (size.height * (newScale - 1f)) * 0.55f + 60f
                        val rawOffset = if (newScale <= 1.02f) {
                            Offset.Zero
                        } else {
                            Offset(
                                x = (panOffset.x + pan.x).coerceIn(-maxPanX, maxPanX),
                                y = (panOffset.y + pan.y).coerceIn(-maxPanY, maxPanY)
                            )
                        }
                        zoomScale = newScale
                        panOffset = rawOffset
                    }
                }
                .pointerInput(model.screeningId) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (zoomScale > 1.2f) {
                                zoomScale = 1f
                                panOffset = Offset.Zero
                            } else {
                                zoomScale = 2f
                            }
                        }
                    )
                }
        ) {
            val density = LocalDensity.current
            val viewportWidthPx = with(density) { maxWidth.toPx() }
            val viewportHeightPx = with(density) { maxHeight.toPx() }
            val rowMarginPx = with(density) { 28.dp.toPx() }
            val screenHeaderPx = with(density) { 46.dp.toPx() }
            val bottomPadPx = with(density) { 16.dp.toPx() }

            val seatAreaWidthPx = (viewportWidthPx - rowMarginPx * 2f).coerceAtLeast(120f)
            val seatAreaHeightPx = (viewportHeightPx - screenHeaderPx - bottomPadPx).coerceAtLeast(120f)

            val desiredSeatHeightPx = seatAreaWidthPx / model.aspectRatio.coerceIn(0.65f, 2.2f)
            val mapBoxWidthPx = seatAreaWidthPx
            val mapBoxHeightPx = desiredSeatHeightPx.coerceIn(
                minimumValue = seatAreaHeightPx * 0.72f,
                maximumValue = seatAreaHeightPx
            )

            val rawCellStepPx = min(
                mapBoxWidthPx * model.minHorizontalSpacingNorm,
                mapBoxHeightPx * model.minVerticalSpacingNorm
            )
            val minSeatPx = with(density) { 12.dp.toPx() }
            val maxSeatPx = with(density) { 34.dp.toPx() }
            val seatSizePx = (rawCellStepPx * 0.82f).coerceIn(minSeatPx, maxSeatPx)
            val seatSizeDp = with(density) { seatSizePx.toDp() }
            val showSeatTextInside = seatSizePx >= with(density) { 14.dp.toPx() }

            // Center the transformed auditorium world cleanly inside the viewport
            val mapTopOffsetPx = screenHeaderPx + ((seatAreaHeightPx - mapBoxHeightPx) / 2f).coerceAtLeast(0f)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = zoomScale
                        scaleY = zoomScale
                        translationX = panOffset.x
                        translationY = panOffset.y
                    }
            ) {
                // Physical-coordinate canvas container (never mirrored by RTL)
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // 1. Curved Screen Banner at Front of Auditorium
                        CinemaScreenArc(
                            roomType = model.roomType,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                                .padding(horizontal = 24.dp, vertical = 4.dp)
                                .testTag("seat_map_screen_banner")
                        )

                        // 2. Row Labels on Right & Left margins for effortless scanning
                        model.rowLabels.forEach { rowLabel ->
                            val yPx = (mapTopOffsetPx + rowLabel.normalizedY * mapBoxHeightPx - with(density) { 9.dp.toPx() }).roundToInt()
                            Text(
                                text = rowLabel.label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFeatureSettings = "tnum"
                                ),
                                color = PlanetTextMuted,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                modifier = Modifier
                                    .width(24.dp)
                                    .absoluteOffset { IntOffset(x = 2, y = yPx) }
                                    .testTag("seat_row_label_left_${rowLabel.id}")
                            )
                            Text(
                                text = rowLabel.label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFeatureSettings = "tnum"
                                ),
                                color = PlanetTextMuted,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                modifier = Modifier
                                    .width(24.dp)
                                    .absoluteOffset {
                                        IntOffset(
                                            x = (viewportWidthPx - with(density) { 26.dp.toPx() }).roundToInt(),
                                            y = yPx
                                        )
                                    }
                                    .testTag("seat_row_label_right_${rowLabel.id}")
                            )
                        }

                        // 3. Individual Seats positioned by normalized real (cx, cy) or row-grid coordinates
                        model.seats.forEach { seat ->
                            val centerX = rowMarginPx + seat.normalizedX * mapBoxWidthPx
                            val centerY = mapTopOffsetPx + seat.normalizedY * mapBoxHeightPx
                            val leftPx = (centerX - seatSizePx / 2f).roundToInt()
                            val topPx = (centerY - seatSizePx / 2f).roundToInt()

                            SeatCell(
                                seat = seat,
                                isSelected = seat.id == selectedSeatId,
                                seatSizeDp = seatSizeDp,
                                showNumberInside = showSeatTextInside,
                                reducedMotion = reducedMotion,
                                onClick = { onSeatSelected(seat) },
                                modifier = Modifier.absoluteOffset { IntOffset(x = leftPx, y = topPx) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CinemaScreenArc(
    roomType: RoomType,
    modifier: Modifier = Modifier
) {
    val accent = roomType.badgeColor
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val startX = w * 0.08f
            val endX = w * 0.92f
            val arcPath = Path().apply {
                moveTo(startX, h * 0.78f)
                quadraticTo(w * 0.5f, h * 0.14f, endX, h * 0.78f)
            }
            // Subtle projector glow behind screen
            drawPath(
                path = arcPath,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        accent.copy(alpha = 0.35f),
                        Color.White.copy(alpha = 0.5f),
                        accent.copy(alpha = 0.35f),
                        Color.Transparent
                    )
                ),
                style = Stroke(width = 7f, cap = StrokeCap.Round)
            )
            drawPath(
                path = arcPath,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        accent.copy(alpha = 0.3f),
                        accent,
                        Color.White,
                        accent,
                        accent.copy(alpha = 0.3f)
                    )
                ),
                style = Stroke(width = 3f, cap = StrokeCap.Round)
            )
        }
        Text(
            text = "מסך הקולנוע • ${roomType.label}",
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            ),
            color = PlanetTextSecondary,
            modifier = Modifier.padding(top = 12.dp)
        )
    }
}

@Composable
private fun SeatCell(
    seat: AuditoriumSeatUiItem,
    isSelected: Boolean,
    seatSizeDp: androidx.compose.ui.unit.Dp,
    showNumberInside: Boolean,
    reducedMotion: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val visual = seat.visualStatus
    val animDuration = if (reducedMotion) 0 else PlanetMotion.mediumMs

    val animatedFill by animateColorAsState(
        targetValue = visual.fillColor,
        animationSpec = tween(durationMillis = animDuration),
        label = "seat_fill_${seat.id}"
    )
    val animatedBorder by animateColorAsState(
        targetValue = if (isSelected) Color.White else visual.borderColor,
        animationSpec = tween(durationMillis = animDuration),
        label = "seat_border_${seat.id}"
    )

    val seatShape = RoundedCornerShape(
        topStart = 6.dp,
        topEnd = 6.dp,
        bottomStart = 3.dp,
        bottomEnd = 3.dp
    )

    val displayGlyph = when (visual) {
        SeatVisualStatus.BROKEN -> "!"
        SeatVisualStatus.BLOCKED_OR_UNAVAILABLE -> "×"
        SeatVisualStatus.UNKNOWN -> if (seat.seatNumber.length <= 2 && showNumberInside) "${seat.seatNumber}?" else "?"
        else -> seat.seatNumber
    }

    val accessibilityText = "שורה ${seat.rowNumber}, מושב ${seat.seatNumber}, סטטוס: ${seat.exactStatusLabel()}"

    Box(
        modifier = modifier
            .size(seatSizeDp)
            .clip(seatShape)
            .background(animatedFill)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = animatedBorder,
                shape = seatShape
            )
            .semantics { contentDescription = accessibilityText }
            .clickable(
                role = Role.Button,
                onClickLabel = accessibilityText,
                onClick = onClick
            )
            .testTag("seat_${seat.id}"),
        contentAlignment = Alignment.Center
    ) {
        if (showNumberInside) {
            val fontSize = when {
                seatSizeDp >= 26.dp -> 10.sp
                seatSizeDp >= 18.dp -> 8.5.sp
                else -> 7.sp
            }
            Text(
                text = displayGlyph,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = fontSize,
                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                    fontFeatureSettings = "tnum"
                ),
                color = visual.contentColor,
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.Center
            )
        }
        // Subtle cinema seat armrest/headrest accent bar at top of seat
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(0.66f)
                .height(2.dp)
                .background(
                    if (isSelected) PlanetGold else visual.borderColor.copy(alpha = 0.7f),
                    RoundedCornerShape(1.dp)
                )
        )
    }
}
