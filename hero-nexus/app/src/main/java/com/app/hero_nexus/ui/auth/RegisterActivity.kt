package com.app.hero_nexus.ui.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.app.hero_nexus.HeroNexusApp
import com.app.hero_nexus.R
import com.app.hero_nexus.databinding.ActivityRegisterBinding
import com.app.hero_nexus.util.Resource
import com.app.hero_nexus.util.gone
import com.app.hero_nexus.util.visible

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding

    private val viewModel: AuthViewModel by viewModels {
        viewModelFactory {
            initializer { AuthViewModel((application as HeroNexusApp).userRepository) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.buttonRegister.setOnClickListener {
            val username = binding.inputUsername.text?.toString().orEmpty()
            val email = binding.inputEmail.text?.toString().orEmpty()
            val password = binding.inputPassword.text?.toString().orEmpty()
            val confirm = binding.inputConfirmPassword.text?.toString().orEmpty()

            if (username.isBlank() || email.isBlank() || password.isBlank() || confirm.isBlank()) {
                showError(getString(R.string.error_fields_required))
                return@setOnClickListener
            }
            if (password != confirm) {
                showError(getString(R.string.error_passwords_dont_match))
                return@setOnClickListener
            }
            viewModel.register(username, email, password)
        }

        binding.textGoLogin.setOnClickListener { finish() }

        viewModel.state.observe(this) { state ->
            when (state) {
                is Resource.Loading -> {
                    binding.progress.visible()
                    binding.buttonRegister.isEnabled = false
                    binding.textError.gone()
                }
                is Resource.Success -> {
                    binding.progress.gone()
                    Toast.makeText(this, R.string.register_success, Toast.LENGTH_SHORT).show()
                    startActivity(
                        Intent(this, com.app.hero_nexus.ui.collection.CollectionActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    )
                    finish()
                }
                is Resource.Error -> {
                    binding.progress.gone()
                    binding.buttonRegister.isEnabled = true
                    showError(state.message)
                }
                else -> Unit
            }
        }
    }

    private fun showError(message: String) {
        binding.textError.text = message
        binding.textError.visible()
    }
}
