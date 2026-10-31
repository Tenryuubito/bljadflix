package com.shadow.firetv.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.shadow.firetv.core.ui.theme.BljadflixColors

data class DialogAction(val label: String, val isPrimary: Boolean = false, val onClick: () -> Unit)

@Composable
fun TvDialog(
    title: String,
    actions: List<DialogAction>,
    onDismiss: () -> Unit,
    message: String? = null
) {
    val firstAction = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(560.dp)
                .background(BljadflixColors.Surface, RoundedCornerShape(20.dp))
                .border(BorderStroke(1.dp, BljadflixColors.Border), RoundedCornerShape(20.dp))
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = BljadflixColors.TextPrimary)
            if (message != null) {
                Text(message, style = MaterialTheme.typography.bodyLarge, color = BljadflixColors.TextSecondary)
            }
            Spacer(Modifier.height(8.dp))
            actions.forEachIndexed { index, action ->
                TvActionButton(
                    text = action.label,
                    onClick = action.onClick,
                    isPrimary = action.isPrimary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (index == 0) Modifier.focusRequester(firstAction) else Modifier)
                )
            }
        }
    }
    LaunchedEffect(Unit) { firstAction.requestFocus() }
}
