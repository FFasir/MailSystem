package com.mailsystem.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mailsystem.data.local.UserPreferences
import com.mailsystem.data.model.AdminMailInfo
import com.mailsystem.data.model.User
import com.mailsystem.data.repository.MailRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AdminViewModel(application: Application) : AndroidViewModel(application) {
    
    private val userPreferences = UserPreferences(application)
    private val repository = MailRepository(userPreferences)
    
    private val _users = MutableStateFlow<List<User>>(emptyList())
    val users: StateFlow<List<User>> = _users
    
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    private val _ipBlacklist = MutableStateFlow<List<String>>(emptyList())
    val ipBlacklist: StateFlow<List<String>> = _ipBlacklist

    private val _emailBlacklist = MutableStateFlow<List<String>>(emptyList())
    val emailBlacklist: StateFlow<List<String>> = _emailBlacklist
    
    fun loadUsers() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            val result = repository.getUsers()
            if (result.isSuccess) {
                _users.value = result.getOrNull() ?: emptyList()
            } else {
                _error.value = result.exceptionOrNull()?.message ?: "加载失败"
            }
            _loading.value = false
        }
    }
    
    fun deleteUser(userId: Int) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            val result = repository.deleteUser(userId)
            if (result.isSuccess) {
                _message.value = "用户删除成功"
                loadUsers()
            } else {
                _error.value = result.exceptionOrNull()?.message ?: "删除失败"
            }
            _loading.value = false
        }
    }

    // ===== 新增：管理员用户管理能力 =====
    fun createUser(username: String, password: String, role: String) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            val result = repository.createUser(username, password, role)
            if (result.isSuccess) {
                _message.value = "创建用户成功"
                loadUsers()
            } else {
                _error.value = result.exceptionOrNull()?.message ?: "创建失败"
            }
            _loading.value = false
        }
    }

    fun updateUserRole(userId: Int, role: String) {
        viewModelScope.launch {
            val result = repository.updateUserRole(userId, role)
            if (result.isSuccess) {
                _message.value = "角色更新成功"
                loadUsers()
            } else {
                _error.value = result.exceptionOrNull()?.message ?: "操作失败"
            }
        }
    }

    fun disableUser(userId: Int, reason: String? = null) {
        viewModelScope.launch {
            val result = repository.disableUser(userId, reason)
            if (result.isSuccess) {
                _message.value = "已禁用并强制下线"
                loadUsers()
            } else {
                _error.value = result.exceptionOrNull()?.message ?: "操作失败"
            }
        }
    }

    fun enableUser(userId: Int) {
        viewModelScope.launch {
            val result = repository.enableUser(userId)
            if (result.isSuccess) {
                _message.value = "已启用"
                loadUsers()
            } else {
                _error.value = result.exceptionOrNull()?.message ?: "操作失败"
            }
        }
    }

    fun resetUserPassword(userId: Int, newPassword: String) {
        viewModelScope.launch {
            val result = repository.resetUserPassword(userId, newPassword)
            if (result.isSuccess) {
                _message.value = "密码已重置并强制下线"
            } else {
                _error.value = result.exceptionOrNull()?.message ?: "操作失败"
            }
        }
    }

    fun forceLogout(userId: Int) {
        viewModelScope.launch {
            val result = repository.forceLogoutUser(userId)
            if (result.isSuccess) {
                _message.value = "已强制下线"
            } else {
                _error.value = result.exceptionOrNull()?.message ?: "操作失败"
            }
        }
    }
    
    fun broadcastMail(subject: String, body: String, userIds: List<Int>? = null) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            val result = repository.broadcastMail(subject, body, userIds)
            if (result.isSuccess) {
                _message.value = "群发邮件成功"
            } else {
                _error.value = result.exceptionOrNull()?.message ?: "群发失败"
            }
            _loading.value = false
        }
    }
    
    fun clearMessage() {
        _message.value = null
    }
    
    fun clearError() {
        _error.value = null
    }

    // 修改管理员密码
    fun changePassword(oldPassword: String, newPassword: String) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            val result = repository.changeAdminPassword(oldPassword, newPassword)
            if (result.isSuccess) {
                _message.value = "密码修改成功"
            } else {
                _error.value = result.exceptionOrNull()?.message ?: "密码修改失败"
            }
            _loading.value = false
        }
    }

    // 黑名单 - IP
    fun loadIpBlacklist() {
        viewModelScope.launch {
            val result = repository.getIpBlacklist()
            if (result.isSuccess) _ipBlacklist.value = result.getOrNull() ?: emptyList()
        }
    }

    fun addIp(ip: String) {
        viewModelScope.launch {
            val result = repository.addIpToBlacklist(ip)
            if (result.isSuccess) {
                _message.value = "已添加 IP 黑名单"
                loadIpBlacklist()
            } else {
                _error.value = result.exceptionOrNull()?.message ?: "添加失败"
            }
        }
    }

    fun removeIp(ip: String) {
        viewModelScope.launch {
            val result = repository.removeIpFromBlacklist(ip)
            if (result.isSuccess) {
                _message.value = "已移除 IP 黑名单"
                loadIpBlacklist()
            } else {
                _error.value = result.exceptionOrNull()?.message ?: "移除失败"
            }
        }
    }

    // 黑名单 - 邮箱
    fun loadEmailBlacklist() {
        viewModelScope.launch {
            val result = repository.getEmailBlacklist()
            if (result.isSuccess) _emailBlacklist.value = result.getOrNull() ?: emptyList()
        }
    }

    fun addEmail(email: String) {
        viewModelScope.launch {
            val result = repository.addEmailToBlacklist(email)
            if (result.isSuccess) {
                _message.value = "已添加邮箱黑名单"
                loadEmailBlacklist()
            } else {
                _error.value = result.exceptionOrNull()?.message ?: "添加失败"
            }
        }
    }

    fun removeEmail(email: String) {
        viewModelScope.launch {
            val result = repository.removeEmailFromBlacklist(email)
            if (result.isSuccess) {
                _message.value = "已移除邮箱黑名单"
                loadEmailBlacklist()
            } else {
                _error.value = result.exceptionOrNull()?.message ?: "移除失败"
            }
        }
    }

    fun reloadFilters() {
        viewModelScope.launch {
            val result = repository.reloadFilters()
            if (result.isSuccess) {
                _message.value = "过滤器已重新加载"
                loadIpBlacklist(); loadEmailBlacklist()
            } else {
                _error.value = result.exceptionOrNull()?.message ?: "重载失败"
            }
        }
    }

    // 管理员查看所有邮件
    private val _allMails = MutableStateFlow<List<AdminMailInfo>>(emptyList())
    val allMails: StateFlow<List<AdminMailInfo>> = _allMails

    private val _selectedMailContent = MutableStateFlow<String?>(null)
    val selectedMailContent: StateFlow<String?> = _selectedMailContent

    private val _appeals = MutableStateFlow<List<com.mailsystem.data.model.AppealResponse>>(emptyList())
    val appeals: StateFlow<List<com.mailsystem.data.model.AppealResponse>> = _appeals

    fun loadAllMails() {
        viewModelScope.launch {
            val result = repository.getAllMails()
            if (result.isSuccess) _allMails.value = result.getOrNull() ?: emptyList()
        }
    }

    fun viewMailContent(username: String, filename: String) {
        viewModelScope.launch {
            val result = repository.getUserMail(username, filename)
            if (result.isSuccess) {
                _selectedMailContent.value = result.getOrNull()
            } else {
                _error.value = result.exceptionOrNull()?.message ?: "加载失败"
            }
        }
    }

    fun clearMailContent() {
        _selectedMailContent.value = null
    }

    // 申诉管理
    fun loadAppeals() {
        viewModelScope.launch {
            val result = repository.getAppeals()
            if (result.isSuccess) {
                _appeals.value = result.getOrNull() ?: emptyList()
            } else {
                _error.value = result.exceptionOrNull()?.message ?: "加载申诉列表失败"
            }
        }
    }

    fun approveAppeal(appealId: Int) {
        viewModelScope.launch {
            val result = repository.approveAppeal(appealId)
            if (result.isSuccess) {
                _message.value = "申诉已通过，账号已启用"
                loadAppeals()
                loadUsers() // 刷新用户列表以反映状态变化
            } else {
                _error.value = result.exceptionOrNull()?.message ?: "操作失败"
            }
        }
    }

    fun rejectAppeal(appealId: Int) {
        viewModelScope.launch {
            val result = repository.rejectAppeal(appealId)
            if (result.isSuccess) {
                _message.value = "申诉已拒绝"
                loadAppeals()
            } else {
                _error.value = result.exceptionOrNull()?.message ?: "操作失败"
            }
        }
    }
}
