package com.shadow.firetv.feature.auth

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.shadow.firetv.MainActivity
import com.shadow.firetv.core.ui.theme.BljadflixTheme
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private val viewModel: LoginViewModel by viewModels { LoginViewModel.Factory() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val kickoutMessage = intent.getStringExtra("KICKOUT_MESSAGE")?.takeIf { it.isNotEmpty() }

        setContent {
            BljadflixTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                LoginScreen(
                    state = state,
                    kickoutMessage = kickoutMessage,
                    onUsernameChange = viewModel::onUsernameChanged,
                    onPasswordChange = viewModel::onPasswordChanged,
                    onSubmit = { viewModel.submitLogin(context = this) }
                )
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is LoginEvent.NavigateToMain -> {
                            startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                            finish()
                        }
                    }
                }
            }
        }
    }
}
