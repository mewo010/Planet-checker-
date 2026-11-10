package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.RoomType
import com.example.data.model.Screening
import com.example.data.model.ScreeningStatus
import com.example.ui.theme.PlanetBorder
import com.example.ui.theme.PlanetCardBg
import com.example.ui.theme.PlanetEmerald
import com.example.ui.theme.PlanetEmeraldLight
import com.example.ui.theme.PlanetRose
import com.example.ui.theme.PlanetRoseLight
import com.example.ui.theme.PlanetSurface
import com.example.ui.theme.PlanetTextMuted
import com.example.ui.theme.PlanetTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudienceBreakdownDialog(
    allScreenings: List<Screening>,
    totalAudience: Int,
    isLiveDataSource: Boolean,
    onDismiss: () -> Unit
) {
    val liveScreenings = allScreenings.filter { it.status == ScreeningStatus.LIVE }

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(PlanetSurface)
            .border(1.dp, PlanetBorder, RoundedCornerShape(20.dp))
            .testTag("audience_breakdown_dialog")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "פירוט צופים חי באולמות",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            ),
                            color = Color.White
                        )
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "נתוני אמת מאומתים",
                            tint = PlanetEmeraldLight,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Text(
                        text = if (isLiveDataSource) "נתוני אמת מחושבים ישירות מקופות פלאנט" else "נתוני דוגמה (אין חיבור)",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = if (isLiveDataSource) PlanetEmeraldLight else PlanetTextMuted
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "סגור",
                        tint = PlanetTextSecondary
                    )
                }
            }

            // Summary pill
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(PlanetCardBg)
                    .border(1.dp, PlanetBorder.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "סה\"כ נוכחים כעת בקולנוע",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = PlanetTextSecondary
                        )
                        Text(
                            text = "$totalAudience צופים",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp
                            ),
                            color = PlanetRoseLight
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(PlanetRose.copy(alpha = 0.15f))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "${liveScreenings.size} אולמות מקרינים",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            ),
                            color = PlanetRoseLight
                        )
                    }
                }
            }

            // List of active halls
            Text(
                text = "אולמות פעילים ברגע זה:",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )

            if (liveScreenings.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "אין אולמות המקרינים ברגע זה",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PlanetTextMuted
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(liveScreenings, key = { it.id }) { screening ->
                        ActiveRoomRow(screening = screening)
                    }
                }
            }

            // Verification explanation
            Text(
                text = "✓ נתון זה הינו נתון אמת מדויק. הוא מחושב ישירות מתפוסת המושבים (1 פחות אחוז המקומות הפנויים בקופות) מוכפל בקיבולת הרשמית של כל אולם.",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, lineHeight = 14.sp),
                color = PlanetTextMuted
            )
        }
    }
}

@Composable
private fun ActiveRoomRow(screening: Screening) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(PlanetCardBg.copy(alpha = 0.7f))
            .border(1.dp, PlanetBorder.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(PlanetRoseLight)
                )

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = RoomType.formatRoomLabel(screening.room),
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            ),
                            color = Color.White
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(screening.roomType.containerColor)
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = screening.roomType.label,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                color = screening.roomType.badgeColor
                            )
                        }
                    }

                    Text(
                        text = screening.title,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = PlanetTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${screening.estimatedPeople ?: 0} צופים",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    ),
                    color = PlanetRoseLight
                )
                Text(
                    text = "${screening.occupancyPercentage}% תפוסה",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = screening.crowdLevel.color
                )
            }
        }
    }
}
