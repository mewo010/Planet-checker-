package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.SortOption
import com.example.ui.theme.Planet4dxPurpleLight
import com.example.ui.theme.PlanetBorder
import com.example.ui.theme.PlanetBorderSubtle
import com.example.ui.theme.PlanetCardBg
import com.example.ui.theme.PlanetDarkBg
import com.example.ui.theme.PlanetEmeraldLight
import com.example.ui.theme.PlanetGold
import com.example.ui.theme.PlanetImaxSkyLight
import com.example.ui.theme.PlanetMotion
import com.example.ui.theme.PlanetRadius
import com.example.ui.theme.PlanetRose
import com.example.ui.theme.PlanetRoseLight
import com.example.ui.theme.PlanetSpacing
import com.example.ui.theme.PlanetSurface
import com.example.ui.theme.PlanetTextMuted
import com.example.ui.theme.PlanetTextPrimary
import com.example.ui.theme.PlanetTextSecondary
import com.example.ui.theme.rememberReducedMotion

private data class FilterCategory(
    val key: String,
    val label: String,
    val badgeColor: Color = Color.Unspecified
)

@Composable
fun FilterControlBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    activeFilter: String,
    onFilterChange: (String) -> Unit,
    countsByType: Map<String, Int>,
    sortBy: SortOption,
    onSortChange: (SortOption) -> Unit,
    showEnded: Boolean,
    onShowEndedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }
    val reducedMotion = rememberReducedMotion()

    val filterCategories = remember {
        listOf(
            FilterCategory("ALL", "הכל"),
            FilterCategory("LIVE", "מוקרן כעת", PlanetRoseLight),
            FilterCategory("IMAX", "IMAX", PlanetImaxSkyLight),
            FilterCategory("VIP", "VIP", PlanetGold),
            FilterCategory("4DX", "4DX", Planet4dxPurpleLight),
            FilterCategory("ScreenX", "ScreenX", PlanetEmeraldLight),
            FilterCategory("2D", "2D", PlanetTextSecondary)
        )
    }

    DashboardCard(
        contentPadding = PaddingValues(PlanetSpacing.md),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(PlanetSpacing.sm)) {
            // Search & Sort row (48dp min height prevents text clipping)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(PlanetSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(PlanetRadius.md))
                        .background(PlanetDarkBg.copy(alpha = 0.85f))
                        .border(
                            1.dp,
                            if (searchQuery.isNotEmpty()) PlanetRose.copy(alpha = 0.6f) else PlanetBorderSubtle,
                            RoundedCornerShape(PlanetRadius.md)
                        )
                        .padding(start = PlanetSpacing.md, end = PlanetSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(PlanetSpacing.sm)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = "חיפוש",
                        tint = PlanetTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "חיפוש סרט או מספר אולם…",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                color = PlanetTextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = PlanetTextPrimary,
                                fontSize = 13.sp
                            ),
                            cursorBrush = SolidColor(PlanetRoseLight),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("search_input")
                        )
                    }

                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { onSearchQueryChange("") },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Clear,
                                contentDescription = "נקה חיפוש",
                                tint = PlanetTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Sort Dropdown Button (48dp min touch target)
                Box {
                    Row(
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .clip(RoundedCornerShape(PlanetRadius.md))
                            .background(PlanetDarkBg.copy(alpha = 0.85f))
                            .border(1.dp, PlanetBorderSubtle, RoundedCornerShape(PlanetRadius.md))
                            .clickable(onClick = { sortMenuExpanded = true })
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Sort,
                            contentDescription = "מיון",
                            tint = PlanetTextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = sortBy.title,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Medium,
                                fontSize = 12.sp
                            ),
                            color = PlanetTextPrimary,
                            maxLines = 1
                        )
                        Icon(
                            imageVector = Icons.Filled.ArrowDropDown,
                            contentDescription = null,
                            tint = PlanetTextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = sortMenuExpanded,
                        onDismissRequest = { sortMenuExpanded = false },
                        modifier = Modifier
                            .background(PlanetCardBg)
                            .border(1.dp, PlanetBorder, RoundedCornerShape(PlanetRadius.sm))
                    ) {
                        SortOption.entries.forEach { option ->
                            val isSelected = sortBy == option
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = option.title,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            ),
                                            color = if (isSelected) PlanetRoseLight else PlanetTextPrimary
                                        )
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Filled.Check,
                                                contentDescription = null,
                                                tint = PlanetRoseLight,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    onSortChange(option)
                                    sortMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Horizontal filter chips with smooth color animation
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(horizontal = 1.dp)
            ) {
                items(filterCategories, key = { it.key }) { category ->
                    val isActive = activeFilter == category.key
                    val count = countsByType[category.key] ?: 0

                    val bg by animateColorAsState(
                        targetValue = if (isActive) PlanetRose else PlanetDarkBg.copy(alpha = 0.75f),
                        animationSpec = PlanetMotion.standardTween(PlanetMotion.fastMs, reducedMotion),
                        label = "filter_bg"
                    )
                    val border by animateColorAsState(
                        targetValue = if (isActive) PlanetRoseLight else PlanetBorderSubtle,
                        animationSpec = PlanetMotion.standardTween(PlanetMotion.fastMs, reducedMotion),
                        label = "filter_border"
                    )
                    val contentColor = if (isActive) {
                        Color.White
                    } else {
                        category.badgeColor.takeIf { it != Color.Unspecified } ?: PlanetTextSecondary
                    }

                    Row(
                        modifier = Modifier
                            .defaultMinSize(minHeight = 36.dp)
                            .clip(RoundedCornerShape(PlanetRadius.sm))
                            .background(bg)
                            .border(1.dp, border, RoundedCornerShape(PlanetRadius.sm))
                            .clickable(onClick = { onFilterChange(category.key) })
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = category.label,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 12.sp
                            ),
                            color = contentColor,
                            maxLines = 1
                        )
                        Text(
                            text = "$count",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = if (isActive) Color.White.copy(alpha = 0.9f) else PlanetTextMuted
                        )
                    }
                }
            }

            // Show ended toggle row (full row clickable with standard Switch)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .clip(RoundedCornerShape(PlanetRadius.sm))
                    .clickable { onShowEndedChange(!showEnded) }
                    .padding(horizontal = PlanetSpacing.xs),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(PlanetSpacing.sm)
                ) {
                    Switch(
                        checked = showEnded,
                        onCheckedChange = onShowEndedChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = PlanetRose,
                            uncheckedThumbColor = PlanetTextMuted,
                            uncheckedTrackColor = PlanetDarkBg,
                            uncheckedBorderColor = PlanetBorderSubtle
                        )
                    )
                    Text(
                        text = "הצג הקרנות שהסתיימו",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = if (showEnded) PlanetTextPrimary else PlanetTextSecondary
                    )
                }

                if (searchQuery.isNotEmpty()) {
                    StatusPill(
                        text = "חיפוש פעיל",
                        textColor = PlanetRoseLight,
                        containerColor = PlanetRose.copy(alpha = 0.14f)
                    )
                }
            }
        }
    }
}
