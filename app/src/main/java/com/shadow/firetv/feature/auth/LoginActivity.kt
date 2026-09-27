package com.shadow.firetv.feature.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.shadow.firetv.MainActivity
import com.shadow.firetv.R
import com.shadow.firetv.core.theme.ThemeManager
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var etUsername: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnSubmit: Button
    private lateinit var tvLoginError: TextView
    private lateinit var progressLogin: ProgressBar

    private val viewModel: LoginViewModel by viewModels { LoginViewModel.Factory() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.getInstance(this).applyTheme(this)
        setContentView(R.layout.activity_login)

        etUsername = findViewById(R.id.et_username)
        etPassword = findViewById(R.id.et_password)
        btnSubmit = findViewById(R.id.btn_submit)
        tvLoginError = findViewById(R.id.tv_login_error)
        progressLogin = findViewById(R.id.progress_login)

        val kickoutMessage = intent.getStringExtra("KICKOUT_MESSAGE")
        if (!kickoutMessage.isNullOrEmpty()) {
            tvLoginError.visibility = View.VISIBLE
            tvLoginError.text = kickoutMessage
        }

        setupUIControls()
        observeViewModel()
    }

    private fun setupUIControls() {
        setupFocusEffect(etUsername)
        setupFocusEffect(etPassword)
        setupFocusEffect(btnSubmit)

        etPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_UNSPECIFIED) {
                performLogin()
                true
            } else {
                false
            }
        }

        btnSubmit.setOnClickListener {
            performLogin()
        }

        etUsername.requestFocus()
    }

    private fun performLogin() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(currentFocus?.windowToken ?: etUsername.windowToken, 0)
        val username = etUsername.text.toString()
        val password = etPassword.text.toString()
        viewModel.submitLogin(username = username, password = password, context = this)
    }

    private fun setupFocusEffect(view: View) {
        view.setOnFocusChangeListener { v, hasFocus ->
            val interpolator = FastOutSlowInInterpolator()
            if (hasFocus) {
                v.animate().scaleX(1.03f).scaleY(1.03f).setInterpolator(interpolator).setDuration(120).start()
            } else {
                v.animate().scaleX(1.0f).scaleY(1.0f).setInterpolator(interpolator).setDuration(120).start()
            }
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        renderState(state)
                    }
                }
                launch {
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

    private fun renderState(state: LoginUiState) {
        progressLogin.visibility = if (state.isLoading) View.VISIBLE else View.GONE
        btnSubmit.isEnabled = !state.isLoading

        if (state.errorMessage != null) {
            tvLoginError.visibility = View.VISIBLE
            tvLoginError.text = state.errorMessage
        } else if (intent.getStringExtra("KICKOUT_MESSAGE").isNullOrEmpty()) {
            tvLoginError.visibility = View.GONE
        }
    }
}
