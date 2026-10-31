package com.shadow.firetv.feature.main

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.shadow.firetv.R
import com.shadow.firetv.core.ui.components.LinearProgress
import com.shadow.firetv.core.ui.theme.BljadflixColors
import kotlinx.coroutines.launch

@Composable
fun SplashOverlay(progressPercent: Int, statusText: String, onAnimationDone: () -> Unit) {
    val logoScale = remember { Animatable(0.5f) }
    val logoAlpha = remember { Animatable(0f) }
    val textAlpha = remember { Animatable(0f) }
    val animationDone by rememberUpdatedState(onAnimationDone)

    LaunchedEffect(Unit) {
        launch { textAlpha.animateTo(1f, tween(700, delayMillis = 150)) }
        launch { logoAlpha.animateTo(1f, tween(1000)) }
        logoScale.animateTo(1.15f, tween(1000))
        animationDone()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(Color(0xFFD81414), Color(0xFF6E0000)), radius = 1800f))
    ) {
        Column(
            Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(R.drawable.ic_hammer_sickle),
                contentDescription = null,
                modifier = Modifier
                    .size(150.dp)
                    .scale(logoScale.value)
                    .alpha(logoAlpha.value)
            )
            Spacer(Modifier.height(28.dp))
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.displaySmall,
                color = BljadflixColors.TextPrimary,
                modifier = Modifier.alpha(textAlpha.value)
            )
            Spacer(Modifier.height(36.dp))
            Column(Modifier.width(450.dp)) {
                LinearProgress(
                    progress = progressPercent / 100f,
                    height = 10.dp,
                    color = BljadflixColors.Highlight,
                    trackColor = Color.Black.copy(alpha = 0.3f)
                )
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(statusText, style = MaterialTheme.typography.bodyMedium, color = BljadflixColors.TextPrimary.copy(alpha = 0.85f))
                    Text("$progressPercent%", style = MaterialTheme.typography.labelLarge, color = BljadflixColors.Highlight)
                }
            }
        }
        Text(
            stringResource(R.string.splash_skip_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = BljadflixColors.TextPrimary.copy(alpha = 0.7f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp)
        )
    }
}
