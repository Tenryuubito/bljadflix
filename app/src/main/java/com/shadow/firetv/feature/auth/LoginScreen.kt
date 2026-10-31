package com.shadow.firetv.feature.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.shadow.firetv.R
import com.shadow.firetv.core.ui.components.LoadingIndicator
import com.shadow.firetv.core.ui.components.TvTextField
import com.shadow.firetv.core.ui.theme.BljadflixColors
import com.shadow.firetv.core.ui.theme.ScreenBackground

@Composable
fun LoginScreen(
    state: LoginUiState,
    kickoutMessage: String?,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    val usernameFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { usernameFocus.requestFocus() }

    ScreenBackground {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .width(520.dp)
                .background(BljadflixColors.Surface.copy(alpha = 0.92f), RoundedCornerShape(24.dp))
                .border(BorderStroke(1.dp, BljadflixColors.Border), RoundedCornerShape(24.dp))
                .padding(horizontal = 40.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(R.drawable.ic_hammer_sickle),
                contentDescription = stringResource(R.string.app_name),
                modifier = Modifier.size(56.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.login_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = BljadflixColors.TextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(28.dp))
            TvTextField(
                value = state.usernameInput,
                onValueChange = onUsernameChange,
                placeholder = stringResource(R.string.login_username),
                imeAction = ImeAction.Next,
                modifier = Modifier.focusRequester(usernameFocus)
            )
            Spacer(Modifier.height(14.dp))
            TvTextField(
                value = state.passwordInput,
                onValueChange = onPasswordChange,
                placeholder = stringResource(R.string.login_password),
                isPassword = true,
                onImeAction = onSubmit
            )

            val message = state.errorMessage ?: kickoutMessage
            if (message != null) {
                Spacer(Modifier.height(16.dp))
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = BljadflixColors.Error,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onSubmit,
                enabled = !state.isLoading,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    focusedContainerColor = MaterialTheme.colorScheme.primary,
                    focusedContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                border = ButtonDefaults.border(
                    focusedBorder = Border(BorderStroke(3.dp, BljadflixColors.TextPrimary))
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (state.isLoading) {
                        LoadingIndicator(size = 20.dp, color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(12.dp))
                    }
                    Text(stringResource(R.string.login_submit), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
