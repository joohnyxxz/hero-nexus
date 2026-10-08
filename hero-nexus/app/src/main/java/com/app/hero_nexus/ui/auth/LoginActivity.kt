package com.app.hero_nexus.ui.auth

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.app.hero_nexus.HeroNexusApp
import com.app.hero_nexus.R
import com.app.hero_nexus.databinding.ActivityLoginBinding
import com.app.hero_nexus.ui.collection.CollectionActivity
import com.app.hero_nexus.util.Constants
import com.app.hero_nexus.util.Resource
import com.app.hero_nexus.util.gone
import com.app.hero_nexus.util.visible
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    private val viewModel: AuthViewModel by viewModels {
        viewModelFactory {
            initializer { AuthViewModel((application as HeroNexusApp).userRepository) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadThematicBackground()

        binding.buttonLogin.setOnClickListener {
            val email = binding.inputEmail.text?.toString().orEmpty()
            val password = binding.inputPassword.text?.toString().orEmpty()
            if (email.isBlank() || password.isBlank()) {
                showError(getString(R.string.error_fields_required))
                return@setOnClickListener
            }
            viewModel.login(email, password)
        }

        binding.textGoRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        viewModel.state.observe(this) { state ->
            when (state) {
                is Resource.Loading -> {
                    binding.progress.visible()
                    binding.buttonLogin.isEnabled = false
                    binding.textError.gone()
                }
                is Resource.Success -> {
                    binding.progress.gone()
                    startActivity(
                        Intent(this, CollectionActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    )
                    finish()
                }
                is Resource.Error -> {
                    binding.progress.gone()
                    binding.buttonLogin.isEnabled = true
                    showError(state.message)
                }
                else -> Unit
            }
        }
    }

    private fun loadThematicBackground() {
        lifecycleScope.launch {
            val app = application as HeroNexusApp
            val url = app.backgroundRepository.findBackgroundUrl(Constants.PEXELS_LOGIN_BG_QUERY)
            if (url != null) {
                Glide.with(this@LoginActivity).load(url).into(binding.imageLoginBackground)
            }
        }
    }

    private fun showError(message: String) {
        binding.textError.text = message
        binding.textError.visible()
    }
}
