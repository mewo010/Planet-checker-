package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CinemaTab
import com.example.ui.theme.PlanetBorderSubtle
import com.example.ui.theme.PlanetDarkBg
import com.example.ui.theme.PlanetRose
import com.example.ui.theme.PlanetSurfaceGlass
import com.example.ui.theme.PlanetTextSecondary

@Composable
fun CinemaNavigationBar(
    activeTab: CinemaTab,
    onTabSelected: (CinemaTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(PlanetSurfaceGlass)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(PlanetDarkBg)
                .border(1.dp, PlanetBorderSubtle, RoundedCornerShape(12.dp))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CinemaTab.values().forEach { tab ->
                val isSelected = activeTab == tab

                val icon: ImageVector = when (tab) {
                    CinemaTab.SCHEDULE -> Icons.Filled.CalendarMonth
                    CinemaTab.TIMELINE -> Icons.Filled.Timeline
                    CinemaTab.HALLS_MAP -> Icons.Filled.GridOn
                    CinemaTab.DAILY_STATS -> Icons.Filled.BarChart
                }

                val targetBg = if (isSelected) PlanetRose else Color.Transparent
                val animatedBg by animateColorAsState(
                    targetValue = targetBg,
                    animationSpec = tween(250, easing = FastOutSlowInEasing),
                    label = "tab_bg"
                )

                val targetContentColor = if (isSelected) Color.White else PlanetTextSecondary
                val animatedContentColor by animateColorAsState(
                    targetValue = targetContentColor,
                    animationSpec = tween(200),
                    label = "tab_content_color"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(animatedBg)
                        .clickable(onClick = { onTabSelected(tab) })
                        .testTag("tab_${tab.name}"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = tab.title,
                            tint = animatedContentColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = tab.title,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 11.sp
                            ),
                            color = animatedContentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
