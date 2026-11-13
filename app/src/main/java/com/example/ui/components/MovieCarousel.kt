package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocalActivity
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.example.data.model.MovieSummary
import com.example.ui.theme.PlanetBorderSubtle
import com.example.ui.theme.PlanetCardBg
import com.example.ui.theme.PlanetDarkBg
import com.example.ui.theme.PlanetMotion
import com.example.ui.theme.PlanetRadius
import com.example.ui.theme.PlanetRose
import com.example.ui.theme.PlanetRoseLight
import com.example.ui.theme.PlanetSpacing
import com.example.ui.theme.PlanetTextMuted
import com.example.ui.theme.rememberReducedMotion

@Composable
fun MovieCarousel(
    movies: List<MovieSummary>,
    selectedMovieTitle: String?,
    onMovieClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(PlanetSpacing.sm)
    ) {
        SectionHeaderRow(
            title = "סרטים מובילים היום",
            subtitle = if (selectedMovieTitle != null) "לחיצה נוספת תבטל את הסינון" else "לחץ על פוסטר לסינון",
            icon = Icons.Filled.LocalActivity,
            iconTint = PlanetRoseLight
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 1.dp)
        ) {
            items(movies, key = { it.title }) { movie ->
                val isSelected = movie.title == selectedMovieTitle
                MovieChipItem(
                    movie = movie,
                    isSelected = isSelected,
                    onClick = { onMovieClick(movie.title) }
                )
            }
        }
    }
}

@Composable
private fun MovieChipItem(
    movie: MovieSummary,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val reducedMotion = rememberReducedMotion()
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) PlanetRose else PlanetBorderSubtle,
        animationSpec = PlanetMotion.standardTween(PlanetMotion.fastMs, reducedMotion),
        label = "movie_chip_border"
    )
    val borderWidth = if (isSelected) 2.dp else 1.dp

    Column(
        modifier = Modifier
            .width(106.dp)
            .clip(RoundedCornerShape(PlanetRadius.md))
            .clickable(onClick = onClick)
            .testTag("movie_chip_${movie.title}")
    ) {
        Box(
            modifier = Modifier
                .width(106.dp)
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(PlanetRadius.md))
                .border(borderWidth, borderColor, RoundedCornerShape(PlanetRadius.md))
                .background(PlanetCardBg),
            contentAlignment = Alignment.BottomCenter
        ) {
            SubcomposeAsyncImage(
                model = movie.posterUrl,
                contentDescription = movie.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = {
                    ShimmerPosterBox(reducedMotion = reducedMotion)
                },
                error = {
                    ArtisticPosterFallback(movie = movie)
                }
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.28f),
                                Color.Black.copy(alpha = 0.88f)
                            )
                        )
                    )
            )

            Text(
                text = movie.title,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                ),
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 7.dp)
            )

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(PlanetRose),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "נבחר",
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "${movie.genre} • ${movie.screeningCount} הקרנות",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
            color = if (isSelected) PlanetRoseLight else PlanetTextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
    }
}

@Composable
private fun ShimmerPosterBox(reducedMotion: Boolean) {
    val alpha = if (reducedMotion) {
        0.5f
    } else {
        val transition = rememberInfiniteTransition(label = "shimmer")
        val animated by transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 0.7f,
            animationSpec = infiniteRepeatable(
                animation = tween(900, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "shimmer_alpha"
        )
        animated
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PlanetCardBg.copy(alpha = alpha)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Movie,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.2f),
            modifier = Modifier.size(30.dp)
        )
    }
}

@Composable
private fun ArtisticPosterFallback(movie: MovieSummary) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        movie.primaryColor.copy(alpha = 0.75f),
                        PlanetDarkBg
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Movie,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(18.dp)
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = movie.genre,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = Color.White.copy(alpha = 0.9f),
                    maxLines = 1
                )
            }
        }
    }
}
