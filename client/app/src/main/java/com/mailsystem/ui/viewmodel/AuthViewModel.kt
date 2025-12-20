package com.mailsystem.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mailsystem.data.local.UserPreferences
import com.mailsystem.data.repository.MailRepository
import com.mailsystem.data.model.ProfileResponse
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
    
    private val _appealMessage = MutableStateFlow<String?>(null)
    val appealMessage: StateFlow<String?> = _appealMessage

    private val _profileMessage = MutableStateFlow<String?>(null)
    val profileMessage: StateFlow<String?> = _profileMessage
    private val _profileError = MutableStateFlow<String?>(null)
    val profileError: StateFlow<String?> = _profileError

    private val _phoneMessage = MutableStateFlow<String?>(null)
    val phoneMessage: StateFlow<String?> = _phoneMessage
    private val _phoneError = MutableStateFlow<String?>(null)
    val phoneError: StateFlow<String?> = _phoneError

    // 忘记密码 - 状态
    private val _forgotCodeMessage = MutableStateFlow<String?>(null)
    val forgotCodeMessage: StateFlow<String?> = _forgotCodeMessage
    private val _forgotCodeError = MutableStateFlow<String?>(null)
    val forgotCodeError: StateFlow<String?> = _forgotCodeError

    private val _forgotResetMessage = MutableStateFlow<String?>(null)
    val forgotResetMessage: StateFlow<String?> = _forgotResetMessage
    private val _forgotResetError = MutableStateFlow<String?>(null)
    val forgotResetError: StateFlow<String?> = _forgotResetError

    private val _profile = MutableStateFlow<ProfileResponse?>(null)
    val profile: StateFlow<ProfileResponse?> = _profile
    
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

    fun submitAppeal(username: String, password: String, reason: String) {
        viewModelScope.launch {
            val result = repository.submitAppeal(username, password, reason)
            if (result.isSuccess) {
                _appealMessage.value = result.getOrNull()
            } else {
                _appealMessage.value = "申诉提交失败: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun clearAppealMessage() {
        _appealMessage.value = null
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

    fun loadProfile() {
        viewModelScope.launch {
            val result = repository.getProfile()
            if (result.isSuccess) {
                _profile.value = result.getOrNull()
            } else {
                _profileError.value = result.exceptionOrNull()?.message ?: "获取资料失败"
            }
        }
    }

    fun updateProfile(username: String?, phone: String?) {
        viewModelScope.launch {
            _profileMessage.value = null
            _profileError.value = null
            val result = repository.updateProfile(username, phone)
            if (result.isSuccess) {
                _profileMessage.value = "资料更新成功"
                // 重新拉取资料以同步UI
                loadProfile()
            } else {
                _profileError.value = result.exceptionOrNull()?.message ?: "资料更新失败"
            }
        }
    }

    fun bindPhone(phone: String) {
        viewModelScope.launch {
            _phoneMessage.value = null
            _phoneError.value = null
            val result = repository.bindPhone(phone)
            if (result.isSuccess) {
                _phoneMessage.value = "手机号绑定成功"
            } else {
                _phoneError.value = result.exceptionOrNull()?.message ?: "绑定失败"
            }
        }
    }

    fun clearPhoneTips() { _phoneMessage.value = null; _phoneError.value = null }

    // 忘记密码：请求验证码
    fun requestPasswordResetCode(username: String) {
        viewModelScope.launch {
            _forgotCodeMessage.value = null
            _forgotCodeError.value = null
            val result = repository.requestPasswordResetCode(username)
            if (result.isSuccess) {
                _forgotCodeMessage.value = result.getOrNull()
            } else {
                _forgotCodeError.value = result.exceptionOrNull()?.message
            }
        }
    }

    // 忘记密码：确认重置
    fun confirmPasswordReset(username: String, code: String, newPassword: String) {
        viewModelScope.launch {
            _forgotResetMessage.value = null
            _forgotResetError.value = null
            val result = repository.confirmPasswordReset(username, code, newPassword)
            if (result.isSuccess) {
                _forgotResetMessage.value = "密码重置成功，请重新登录"
            } else {
                _forgotResetError.value = result.exceptionOrNull()?.message
            }
        }
    }
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
