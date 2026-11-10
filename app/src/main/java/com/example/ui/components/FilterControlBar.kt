package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.SortOption
import com.example.ui.theme.PlanetBorder
import com.example.ui.theme.PlanetCardBg
import com.example.ui.theme.PlanetDarkBg
import com.example.ui.theme.PlanetImaxSky
import com.example.ui.theme.PlanetImaxSkyLight
import com.example.ui.theme.PlanetRose
import com.example.ui.theme.PlanetRoseLight
import com.example.ui.theme.PlanetSurface
import com.example.ui.theme.PlanetTextMuted
import com.example.ui.theme.PlanetTextSecondary

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

    val filterCategories = listOf(
        FilterCategory("ALL", "הכל"),
        FilterCategory("LIVE", "מוקרן כעת", PlanetRoseLight),
        FilterCategory("IMAX", "IMAX", PlanetImaxSkyLight),
        FilterCategory("VIP", "VIP", Color(0xFFFACC15)),
        FilterCategory("4DX", "4DX", Color(0xFFA78BFA)),
        FilterCategory("ScreenX", "ScreenX", Color(0xFF34D399)),
        FilterCategory("2D", "2D", Color(0xFFCBD5E1))
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(PlanetSurface)
            .border(1.dp, PlanetBorder.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Search & Sort row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Search Input
            TextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = {
                    Text(
                        text = "חיפוש לפי שם סרט או מספר אולם…",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                        color = PlanetTextMuted
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = "חיפוש",
                        tint = PlanetTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { onSearchQueryChange("") },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Clear,
                                contentDescription = "נקה חיפוש",
                                tint = PlanetTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = PlanetCardBg,
                    unfocusedContainerColor = PlanetCardBg,
                    disabledContainerColor = PlanetCardBg,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedIndicatorColor = PlanetRose,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("search_input")
            )

            // Sort Dropdown Button
            Box {
                Box(
                    modifier = Modifier
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(PlanetCardBg)
                        .border(1.dp, PlanetBorder, RoundedCornerShape(12.dp))
                        .clickable { sortMenuExpanded = true }
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Sort,
                            contentDescription = null,
                            tint = PlanetTextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = sortBy.title,
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                            color = Color.White
                        )
                        Icon(
                            imageVector = Icons.Filled.ArrowDropDown,
                            contentDescription = null,
                            tint = PlanetTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                DropdownMenu(
                    expanded = sortMenuExpanded,
                    onDismissRequest = { sortMenuExpanded = false },
                    modifier = Modifier.background(PlanetCardBg)
                ) {
                    SortOption.values().forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = option.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (sortBy == option) PlanetRoseLight else Color.White
                                )
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

        // Horizontal filter buttons row
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 1.dp)
        ) {
            items(filterCategories, key = { it.key }) { category ->
                val isActive = activeFilter == category.key
                val count = countsByType[category.key] ?: 0

                val bg = if (isActive) PlanetRose else PlanetCardBg
                val contentColor = if (isActive) Color.White else (category.badgeColor.takeIf { it != Color.Unspecified } ?: PlanetTextSecondary)
                val border = if (isActive) PlanetRose else PlanetBorder.copy(alpha = 0.7f)

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(bg)
                        .border(1.dp, border, RoundedCornerShape(10.dp))
                        .clickable { onFilterChange(category.key) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("filter_${category.key}")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = category.label,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 12.sp
                            ),
                            color = contentColor
                        )
                        Text(
                            text = "($count)",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp
                            ),
                            color = if (isActive) Color.White.copy(alpha = 0.8f) else PlanetTextMuted
                        )
                    }
                }
            }
        }

        // Show ended toggle row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Switch(
                    checked = showEnded,
                    onCheckedChange = onShowEndedChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = PlanetRose,
                        uncheckedThumbColor = PlanetTextSecondary,
                        uncheckedTrackColor = PlanetCardBg
                    ),
                    modifier = Modifier.size(width = 44.dp, height = 24.dp)
                )
                Text(
                    text = "הצג הקרנות שהסתיימו",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = if (showEnded) Color.White else PlanetTextSecondary
                )
            }

            if (searchQuery.isNotEmpty()) {
                Text(
                    text = "תוצאות סינון",
                    style = MaterialTheme.typography.labelSmall,
                    color = PlanetRoseLight
                )
            }
        }
    }
}
