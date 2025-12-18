package com.mailsystem.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mailsystem.data.local.UserPreferences
import com.mailsystem.data.repository.MailRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    
    private val userPreferences = UserPreferences(application)
    private val repository = MailRepository(userPreferences)
    
    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState
    
    private val _registerState = MutableStateFlow<RegisterState>(RegisterState.Idle)
    val registerState: StateFlow<RegisterState> = _registerState
    
    val username = userPreferences.username
    val role = userPreferences.role
    
    private val _profileMessage = MutableStateFlow<String?>(null)
    val profileMessage: StateFlow<String?> = _profileMessage
    private val _profileError = MutableStateFlow<String?>(null)
    val profileError: StateFlow<String?> = _profileError
    
    fun login(username: String, password: String) {
        viewModelScope.launch {
            _loginState.value = LoginState.Loading
            val result = repository.login(username, password)
            _loginState.value = if (result.isSuccess) {
                LoginState.Success(result.getOrNull()!!.role)
            } else {
                LoginState.Error(result.exceptionOrNull()?.message ?: "登录失败")
            }
        }
    }
    
    fun register(username: String, password: String, role: String = "user") {
        viewModelScope.launch {
            _registerState.value = RegisterState.Loading
            val result = repository.register(username, password, role)
            _registerState.value = if (result.isSuccess) {
                RegisterState.Success
            } else {
                RegisterState.Error(result.exceptionOrNull()?.message ?: "注册失败")
            }
        }
    }
    
    fun logout() {
        viewModelScope.launch {
            repository.logout()
            _loginState.value = LoginState.Idle
        }
    }
    
    fun resetLoginState() {
        _loginState.value = LoginState.Idle
    }
    
    fun resetRegisterState() {
        _registerState.value = RegisterState.Idle
    }
    
    fun changeMyPassword(oldPassword: String, newPassword: String) {
        viewModelScope.launch {
            val result = repository.changeMyPassword(oldPassword, newPassword)
            if (result.isSuccess) {
                _profileMessage.value = "密码修改成功"
            } else {
                _profileError.value = result.exceptionOrNull()?.message ?: "密码修改失败"
            }
        }
    }
    
    fun clearProfileTips() { _profileMessage.value = null; _profileError.value = null }
}

sealed class LoginState {
    object Idle : LoginState()
    object Loading : LoginState()
    data class Success(val role: String) : LoginState()
    data class Error(val message: String) : LoginState()
}

sealed class RegisterState {
    object Idle : RegisterState()
    object Loading : RegisterState()
    object Success : RegisterState()
    data class Error(val message: String) : RegisterState()
}
