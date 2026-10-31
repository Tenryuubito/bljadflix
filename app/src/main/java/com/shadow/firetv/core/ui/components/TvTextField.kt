package com.shadow.firetv.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.shadow.firetv.core.ui.theme.BljadflixColors

@Composable
fun TvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    imeAction: ImeAction = ImeAction.Done,
    leadingIcon: Painter? = null,
    onImeAction: () -> Unit = {}
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var fieldFocused by remember { mutableStateOf(false) }
    val fieldFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val shape = RoundedCornerShape(12.dp)
    val primary = MaterialTheme.colorScheme.primary

    LaunchedEffect(editing) {
        if (editing) {
            fieldFocus.requestFocus()
            keyboard?.show()
        }
    }

    Surface(
        onClick = { editing = true },
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = BljadflixColors.SurfaceElevated,
            focusedContainerColor = BljadflixColors.SurfaceFocused,
            contentColor = BljadflixColors.TextPrimary,
            focusedContentColor = BljadflixColors.TextPrimary
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(
                BorderStroke(if (fieldFocused) 3.dp else 1.5.dp, if (fieldFocused) primary else BljadflixColors.Border),
                shape = shape
            ),
            focusedBorder = Border(BorderStroke(3.dp, primary), shape = shape)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingIcon != null) {
                Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(22.dp), tint = BljadflixColors.TextSecondary)
                Spacer(Modifier.width(12.dp))
            }
            Box(Modifier.fillMaxWidth()) {
                if (value.isEmpty()) {
                    Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = BljadflixColors.TextMuted)
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = BljadflixColors.TextPrimary),
                    cursorBrush = SolidColor(primary),
                    visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (isPassword) KeyboardType.Password else KeyboardType.Text,
                        imeAction = imeAction
                    ),
                    keyboardActions = KeyboardActions(onAny = {
                        keyboard?.hide()
                        editing = false
                        onImeAction()
                    }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(fieldFocus)
                        .focusProperties { canFocus = editing }
                        .onFocusChanged {
                            fieldFocused = it.isFocused
                            if (!it.isFocused) editing = false
                        }
                )
            }
        }
    }
}
