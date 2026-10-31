package com.shadow.firetv.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.shadow.firetv.core.ui.theme.BljadflixColors

@Composable
fun TvActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false,
    icon: Painter? = null,
    iconTint: Color? = null,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null
) {
    val shape = RoundedCornerShape(12.dp)
    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val container = if (isPrimary) primary else BljadflixColors.SurfaceElevated
    val content = if (isPrimary) onPrimary else BljadflixColors.TextPrimary
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = ButtonDefaults.shape(shape),
        scale = ButtonDefaults.scale(focusedScale = 1.04f),
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 14.dp),
        colors = ButtonDefaults.colors(
            containerColor = container,
            contentColor = content,
            focusedContainerColor = if (isPrimary) primary else BljadflixColors.TextPrimary,
            focusedContentColor = if (isPrimary) onPrimary else BljadflixColors.Surface,
            disabledContainerColor = container.copy(alpha = 0.5f),
            disabledContentColor = content.copy(alpha = 0.6f)
        ),
        border = ButtonDefaults.border(
            border = Border(BorderStroke(1.dp, if (isPrimary) primary else BljadflixColors.Border), shape = shape),
            focusedBorder = Border(BorderStroke(3.dp, BljadflixColors.TextPrimary), shape = shape)
        )
    ) {
        Row(horizontalArrangement = Arrangement.Start, verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                if (iconTint != null) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = iconTint)
                } else {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
            }
            Text(text, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (trailing != null) {
                Spacer(Modifier.width(12.dp))
                trailing()
            }
        }
    }
}
