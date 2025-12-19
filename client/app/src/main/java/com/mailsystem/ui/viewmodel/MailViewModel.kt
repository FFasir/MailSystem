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
    private val templateManager = com.mailsystem.data.local.ReplyTemplateManager(application)
    
    private val _mailList = MutableStateFlow<List<Mail>>(emptyList())
    val mailList: StateFlow<List<Mail>> = _mailList
    
    private val _sentMailList = MutableStateFlow<List<Mail>>(emptyList())
    val sentMailList: StateFlow<List<Mail>> = _sentMailList

    private val _draftList = MutableStateFlow<List<Mail>>(emptyList())
    val draftList: StateFlow<List<Mail>> = _draftList
    
    private val _templates = MutableStateFlow<List<com.mailsystem.data.model.ReplyTemplate>>(emptyList())
    val templates: StateFlow<List<com.mailsystem.data.model.ReplyTemplate>> = _templates
    
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

    fun loadSentMails() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            val result = repository.getSentMails()
            if (result.isSuccess) {
                _sentMailList.value = result.getOrNull() ?: emptyList()
            } else {
                _error.value = result.exceptionOrNull()?.message ?: "加载已发送邮件失败"
            }
            _loading.value = false
        }
    }

    fun loadDraftList() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            val result = repository.getDraftList()
            if (result.isSuccess) {
                _draftList.value = result.getOrNull() ?: emptyList()
            } else {
                _error.value = result.exceptionOrNull()?.message ?: "加载草稿箱失败"
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
    
    fun readSentMail(filename: String) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            val result = repository.readSentMail(filename)
            if (result.isSuccess) {
                _mailContent.value = result.getOrNull() ?: ""
            } else {
                _error.value = result.exceptionOrNull()?.message ?: "读取失败"
            }
            _loading.value = false
        }
    }

    fun readDraft(filename: String, onResult: (String, String, String) -> Unit = { _, _, _ -> }) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            val result = repository.readDraft(filename)
            if (result.isSuccess) {
                val rawContent = result.getOrNull() ?: ""
                _mailContent.value = rawContent
                
                // 解析草稿内容
                val lines = rawContent.lines()
                var to = ""
                var subject = ""
                var bodyStartIndex = 0
                
                for ((index, line) in lines.withIndex()) {
                    if (line.startsWith("To: ")) {
                        to = line.substring(4).trim()
                    } else if (line.startsWith("Subject: ")) {
                        subject = line.substring(9).trim()
                    } else if (line.trim().isEmpty()) {
                        bodyStartIndex = index + 1
                        break
                    }
                }
                
                val body = if (bodyStartIndex < lines.size) {
                    lines.subList(bodyStartIndex, lines.size).joinToString("\n")
                } else {
                    ""
                }
                
                onResult(to, subject, body)
            } else {
                _error.value = result.exceptionOrNull()?.message ?: "读取草稿失败"
            }
            _loading.value = false
        }
    }

    suspend fun saveDraft(to: String, subject: String, body: String, filename: String? = null): Result<String> {
        _loading.value = true
        _error.value = null
        val result = repository.saveDraft(to, subject, body, filename)
        if (!result.isSuccess) {
            _error.value = result.exceptionOrNull()?.message ?: "保存草稿失败"
        } else {
            // 如果保存成功，重新加载草稿列表
            loadDraftList()
        }
        _loading.value = false
        return result
    }

    fun deleteDraft(filename: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            val result = repository.deleteDraft(filename)
            if (result.isSuccess) {
                loadDraftList()
                onSuccess()
            } else {
                _error.value = result.exceptionOrNull()?.message ?: "删除草稿失败"
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

    fun loadTemplates() {
        viewModelScope.launch {
            _templates.value = templateManager.getTemplates()
        }
    }

    fun saveTemplate(title: String, content: String, category: String, id: String? = null) {
        viewModelScope.launch {
            val template = com.mailsystem.data.model.ReplyTemplate(
                id = id ?: java.util.UUID.randomUUID().toString(),
                title = title,
                content = content,
                category = category
            )
            templateManager.saveTemplate(template)
            loadTemplates()
        }
    }

    fun deleteTemplate(id: String) {
        viewModelScope.launch {
            templateManager.deleteTemplate(id)
            loadTemplates()
        }
    }
}
