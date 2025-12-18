package com.mailsystem.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mailsystem.data.local.UserPreferences
import com.mailsystem.data.model.Mail
import com.mailsystem.data.repository.MailRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MailViewModel(application: Application) : AndroidViewModel(application) {
    
    private val userPreferences = UserPreferences(application)
    private val repository = MailRepository(userPreferences)
    
    private val _mailList = MutableStateFlow<List<Mail>>(emptyList())
    val mailList: StateFlow<List<Mail>> = _mailList
    
    private val _mailContent = MutableStateFlow("")
    val mailContent: StateFlow<String> = _mailContent
    
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    
    fun loadMailList() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            val result = repository.getMailList()
            if (result.isSuccess) {
                _mailList.value = result.getOrNull() ?: emptyList()
            } else {
                _error.value = result.exceptionOrNull()?.message ?: "加载失败"
            }
            _loading.value = false
        }
    }
    
    fun readMail(mailId: Int) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            val result = repository.readMail(mailId)
            if (result.isSuccess) {
                _mailContent.value = result.getOrNull() ?: ""
            } else {
                _error.value = result.exceptionOrNull()?.message ?: "读取失败（POP3 协议）"
            }
            _loading.value = false
        }
    }
    
    fun deleteMail(mailId: Int, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            val result = repository.deleteMail(mailId)
            if (result.isSuccess) {
                loadMailList()
                onSuccess()
            } else {
                _error.value = result.exceptionOrNull()?.message ?: "删除失败（POP3 协议）"
            }
            _loading.value = false
        }
    }
    
    fun clearError() {
        _error.value = null
    }
    
    suspend fun sendMail(to: String, subject: String, content: String) {
        _loading.value = true
        _error.value = null
        val result = repository.sendMail(to, subject, content)
        if (!result.isSuccess) {
            _error.value = result.exceptionOrNull()?.message ?: "发送失败"
            throw result.exceptionOrNull() ?: Exception("发送失败")
        }
        _loading.value = false
    }
}
