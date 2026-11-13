package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CinemaTab
import com.example.ui.theme.PlanetBorderSubtle
import com.example.ui.theme.PlanetRose
import com.example.ui.theme.PlanetRoseLight
import com.example.ui.theme.PlanetSpacing
import com.example.ui.theme.PlanetSurfaceGlass
import com.example.ui.theme.PlanetTextMuted
import com.example.ui.theme.PlanetTextPrimary

private fun tabIcons(tab: CinemaTab, selected: Boolean): ImageVector {
    return when (tab) {
        CinemaTab.SCHEDULE -> if (selected) Icons.Filled.CalendarMonth else Icons.Outlined.CalendarMonth
        CinemaTab.TIMELINE -> if (selected) Icons.Filled.Timeline else Icons.Outlined.Timeline
        CinemaTab.HALLS_MAP -> if (selected) Icons.Filled.GridView else Icons.Outlined.GridView
        CinemaTab.DAILY_STATS -> if (selected) Icons.Filled.BarChart else Icons.Outlined.BarChart
    }
}

@Composable
fun CinemaNavigationBar(
    activeTab: CinemaTab,
    onTabSelected: (CinemaTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalDivider(thickness = 1.dp, color = PlanetBorderSubtle)
        NavigationBar(
            containerColor = PlanetSurfaceGlass,
            contentColor = PlanetTextPrimary,
            tonalElevation = 0.dp,
            windowInsets = WindowInsets.navigationBars
        ) {
            CinemaTab.entries.forEach { tab ->
                val isSelected = activeTab == tab
                NavigationBarItem(
                    selected = isSelected,
                    onClick = { onTabSelected(tab) },
                    icon = {
                        Icon(
                            imageVector = tabIcons(tab, isSelected),
                            contentDescription = tab.title,
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    label = {
                        Text(
                            text = tab.title,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 11.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = PlanetRoseLight,
                        selectedTextColor = PlanetTextPrimary,
                        indicatorColor = PlanetRose.copy(alpha = 0.18f),
                        unselectedIconColor = PlanetTextMuted,
                        unselectedTextColor = PlanetTextMuted
                    ),
                    modifier = Modifier.testTag("tab_${tab.name}")
                )
            }
        }
    }
}

@Composable
fun CinemaNavigationRail(
    activeTab: CinemaTab,
    onTabSelected: (CinemaTab) -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.layout.Row(modifier = modifier.fillMaxHeight()) {
        NavigationRail(
            containerColor = PlanetSurfaceGlass,
            contentColor = PlanetTextPrimary,
            windowInsets = WindowInsets(0, 0, 0, 0)
        ) {
            Spacer(modifier = Modifier.height(PlanetSpacing.md))
            CinemaTab.entries.forEach { tab ->
                val isSelected = activeTab == tab
                NavigationRailItem(
                    selected = isSelected,
                    onClick = { onTabSelected(tab) },
                    icon = {
                        Icon(
                            imageVector = tabIcons(tab, isSelected),
                            contentDescription = tab.title,
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    label = {
                        Text(
                            text = tab.title,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    colors = NavigationRailItemDefaults.colors(
                        selectedIconColor = PlanetRoseLight,
                        selectedTextColor = PlanetTextPrimary,
                        indicatorColor = PlanetRose.copy(alpha = 0.18f),
                        unselectedIconColor = PlanetTextMuted,
                        unselectedTextColor = PlanetTextMuted
                    ),
                    modifier = Modifier
                        .padding(vertical = PlanetSpacing.xs)
                        .testTag("tab_${tab.name}")
                )
            }
        }
        VerticalDivider(thickness = 1.dp, color = PlanetBorderSubtle)
    }
}
