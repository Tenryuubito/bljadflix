package com.shadow.firetv.core.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme
import com.shadow.firetv.core.theme.AppTheme
import com.shadow.firetv.core.theme.ThemeManager

object BljadflixColors {
    val Surface = Color(0xFF151520)
    val SurfaceElevated = Color(0xFF1E1E2C)
    val SurfaceFocused = Color(0xFF2A2A3C)
    val Border = Color(0xFF2D2D42)
    val TextPrimary = Color(0xFFE8E8F0)
    val TextSecondary = Color(0xFFB5B5CA)
    val TextMuted = Color(0xFF7A7A94)
    val Success = Color(0xFF4CAF50)
    val Highlight = Color(0xFFFFD54F)
    val Error = Color(0xFFFF6B6B)
}

private fun backgroundColors(theme: AppTheme): Pair<Color, Color> = when (theme) {
    AppTheme.SOVIET_RED -> Color(0xFF2D0808) to Color(0xFF0A0202)
    AppTheme.CLASSIC_DARK -> Color(0xFF151515) to Color(0xFF050505)
    AppTheme.CYBERPUNK_BLUE -> Color(0xFF091A2E) to Color(0xFF020812)
    AppTheme.EMERALD_GREEN -> Color(0xFF082218) to Color(0xFF020C07)
    AppTheme.GOLD_ROYAL -> Color(0xFF2A1E08) to Color(0xFF0C0802)
}

fun themeAccentColor(theme: AppTheme): Color = when (theme) {
    AppTheme.CLASSIC_DARK -> Color(0xFF9A9AB0)
    else -> Color(android.graphics.Color.parseColor(theme.primaryColorHex))
}

private fun onPrimaryColor(theme: AppTheme): Color = when (theme) {
    AppTheme.SOVIET_RED, AppTheme.CYBERPUNK_BLUE -> Color.White
    else -> Color(0xFF0A0A10)
}

private val TvTypography = Typography(
    displaySmall = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.Bold, lineHeight = 46.sp),
    headlineMedium = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, lineHeight = 30.sp),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, lineHeight = 28.sp),
    titleMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp),
    titleSmall = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontSize = 18.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    labelLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp),
    labelMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, lineHeight = 18.sp)
)

@Composable
fun rememberSelectedTheme(): AppTheme {
    val themeManager = ThemeManager.getInstance(LocalContext.current)
    var theme by remember { mutableStateOf(themeManager.getSelectedTheme()) }
    LifecycleResumeEffect(Unit) {
        theme = themeManager.getSelectedTheme()
        onPauseOrDispose { }
    }
    return theme
}

@Composable
fun BljadflixTheme(theme: AppTheme = rememberSelectedTheme(), content: @Composable () -> Unit) {
    val (top, bottom) = backgroundColors(theme)
    val colorScheme = darkColorScheme(
        primary = themeAccentColor(theme),
        onPrimary = onPrimaryColor(theme),
        secondary = BljadflixColors.SurfaceFocused,
        onSecondary = BljadflixColors.TextPrimary,
        background = bottom,
        onBackground = BljadflixColors.TextPrimary,
        surface = BljadflixColors.Surface,
        onSurface = BljadflixColors.TextPrimary,
        surfaceVariant = BljadflixColors.SurfaceElevated,
        onSurfaceVariant = BljadflixColors.TextSecondary,
        inverseSurface = BljadflixColors.TextPrimary,
        inverseOnSurface = Color(0xFF0A0A10),
        border = BljadflixColors.Border,
        borderVariant = BljadflixColors.TextPrimary,
        error = BljadflixColors.Error,
        surfaceTint = top
    )
    MaterialTheme(colorScheme = colorScheme, typography = TvTypography, content = content)
}

@Composable
fun ScreenBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val top = MaterialTheme.colorScheme.surfaceTint
    val bottom = MaterialTheme.colorScheme.background
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(top, bottom), radius = 2200f)),
        content = content
    )
}
