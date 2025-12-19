package com.mailsystem.data.repository

import com.mailsystem.data.api.RetrofitClient
import com.mailsystem.data.local.UserPreferences
import com.mailsystem.data.model.*
import com.mailsystem.data.protocol.SmtpClient
import com.mailsystem.data.protocol.Pop3Client
import kotlinx.coroutines.flow.first
import java.util.regex.Pattern  // 新增：导入正则工具类

class MailRepository(private val userPreferences: UserPreferences) {

    private val api = RetrofitClient.apiService
    private val smtpClient = SmtpClient()
    private val pop3Client = Pop3Client()

    // ========== 1. 统一邮件域名（替换localhost，和后端保持一致） ==========
    private val MAIL_DOMAIN = "mail.com"

    // ========== 2. 新增：邮箱格式校验函数（核心，复用至注册/登录/发邮件） ==========
    private fun isEmailValid(email: String): Boolean {
        if (email.isBlank()) return false
        // 标准邮箱正则：匹配 xxx@xxx.xxx 格式（可根据后端要求调整）
        val emailPattern = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$"
        val pattern = Pattern.compile(emailPattern)
        val matcher = pattern.matcher(email)
        return matcher.matches()
    }

    // 认证 - 注册（新增：校验username为合法邮箱）
    suspend fun register(username: String, password: String, role: String = "user"): Result<User> {
        /*
        // ========== 新增：注册前校验收username是否为合法邮箱 ==========
        if (!isEmailValid(username)) {
            return Result.failure(Exception("注册失败：用户名必须是合法邮箱格式（如：xxx@$MAIL_DOMAIN）"))
        }

         */

        return try {
            val response = api.register(RegisterRequest(username, password, role))
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val errorMsg = try {
                    response.errorBody()?.string() ?: response.message()
                } catch (e: Exception) {
                    response.message()
                }
                Result.failure(Exception("注册失败: $errorMsg"))
            }
        } catch (e: java.net.UnknownHostException) {
            Result.failure(Exception("无法连接到服务器，请检查网络设置"))
        } catch (e: java.net.SocketTimeoutException) {
            Result.failure(Exception("连接超时，请稍后重试"))
        } catch (e: Exception) {
            Result.failure(Exception("注册失败: ${e.message ?: "未知错误"}"))
        }
    }

    // 认证 - 登录（可选：校验收username为合法邮箱，提升体验）
    suspend fun login(username: String, password: String): Result<TokenResponse> {
        /*
        // ========== 新增：登录前校验收username是否为合法邮箱（可选，避免无效请求） ==========
        if (!isEmailValid(username)) {
            return Result.failure(Exception("登录失败：用户名必须是合法邮箱格式（如：xxx@$MAIL_DOMAIN）"))
        }

         */

        return try {
            val response = api.login(LoginRequest(username, password))
            if (response.isSuccessful && response.body() != null) {
                val tokenResponse = response.body()!!
                // 保存密码用于 POP3/SMTP 协议认证
                userPreferences.saveUserData(
                    tokenResponse.token,
                    tokenResponse.username,
                    tokenResponse.role,
                    tokenResponse.user_id,
                    password  // 保存密码
                )
                Result.success(tokenResponse)
            } else {
                val errorMsg = try {
                    response.errorBody()?.string() ?: response.message()
                } catch (e: Exception) {
                    response.message()
                }
                Result.failure(Exception("登录失败: $errorMsg"))
            }
        } catch (e: java.net.UnknownHostException) {
            Result.failure(Exception("无法连接到服务器，请检查网络设置"))
        } catch (e: java.net.SocketTimeoutException) {
            Result.failure(Exception("连接超时，请稍后重试"))
        } catch (e: Exception) {
            Result.failure(Exception("登录失败: ${e.message ?: "未知错误"}"))
        }
    }

    suspend fun logout(): Result<Unit> {
        return try {
            val token = userPreferences.token.first()
            if (token != null) {
                api.logout("Bearer $token")
            }
            userPreferences.clearUserData()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 邮件 - 通过 POP3 协议（无修改，保持原样）
    suspend fun getMailList(): Result<List<Mail>> {
        return try {
            val username = userPreferences.username.first() ?: return Result.failure(Exception("未登录，请先登录"))
            val password = userPreferences.password.first() ?: return Result.failure(Exception("未登录，请先登录"))

            // 使用 POP3 协议获取邮件列表
            val result = pop3Client.login(username, password)

            if (result.isSuccess) {
                val pop3Mails = result.getOrNull() ?: emptyList()
                // 转换为 Mail 对象
                val mails = pop3Mails.map { pop3Mail ->
                    Mail(
                        mailId = pop3Mail.id,
                        filename = "邮件 #${pop3Mail.id}",
                        size = pop3Mail.size
                    )
                }
                Result.success(mails)
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "获取邮件列表失败"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: java.net.UnknownHostException) {
            Result.failure(Exception("无法连接到POP3服务器，请检查网络设置"))
        } catch (e: java.net.SocketTimeoutException) {
            Result.failure(Exception("连接POP3服务器超时，请稍后重试"))
        } catch (e: Exception) {
            Result.failure(Exception("获取邮件失败: ${e.message ?: "未知错误"}"))
        }
    }

    suspend fun readMail(mailId: Int): Result<String> {
        return try {
            val username = userPreferences.username.first() ?: return Result.failure(Exception("未登录，请先登录"))
            val password = userPreferences.password.first() ?: return Result.failure(Exception("未登录，请先登录"))

            // 使用 POP3 协议读取邮件
            val result = pop3Client.retrieveMail(username, password, mailId)
            if (result.isSuccess) {
                result
            } else {
                Result.failure(Exception("读取邮件失败: ${result.exceptionOrNull()?.message ?: "未知错误"}"))
            }
        } catch (e: java.net.UnknownHostException) {
            Result.failure(Exception("无法连接到POP3服务器"))
        } catch (e: java.net.SocketTimeoutException) {
            Result.failure(Exception("连接超时，请稍后重试"))
        } catch (e: Exception) {
            Result.failure(Exception("读取邮件失败: ${e.message ?: "未知错误"}"))
        }
    }

    suspend fun deleteMail(mailId: Int): Result<Unit> {
        return try {
            val username = userPreferences.username.first() ?: return Result.failure(Exception("未登录，请先登录"))
            val password = userPreferences.password.first() ?: return Result.failure(Exception("未登录，请先登录"))

            // 使用 POP3 协议删除邮件
            val result = pop3Client.deleteMail(username, password, mailId)
            if (result.isSuccess) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("删除邮件失败: ${result.exceptionOrNull()?.message ?: "未知错误"}"))
            }
        } catch (e: java.net.UnknownHostException) {
            Result.failure(Exception("无法连接到POP3服务器"))
        } catch (e: java.net.SocketTimeoutException) {
            Result.failure(Exception("连接超时，请稍后重试"))
        } catch (e: Exception) {
            Result.failure(Exception("删除邮件失败: ${e.message ?: "未知错误"}"))
        }
    }

    suspend fun getSentMails(): Result<List<Mail>> {
        return try {
            val token = userPreferences.token.first() ?: return Result.failure(Exception("未登录"))
            val response = api.getSentMails("Bearer $token")
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.mails)
            } else {
                Result.failure(Exception(response.message()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun readSentMail(filename: String): Result<String> {
        return try {
            val token = userPreferences.token.first() ?: return Result.failure(Exception("未登录"))
            val response = api.readSentMail(filename, "Bearer $token")
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.content)
            } else {
                Result.failure(Exception(response.message()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 草稿箱功能
    suspend fun getDraftList(): Result<List<Mail>> {
        return try {
            val token = userPreferences.token.first() ?: return Result.failure(Exception("未登录"))
            val response = api.getDraftList("Bearer $token")
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.mails)
            } else {
                Result.failure(Exception(response.message()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun readDraft(filename: String): Result<String> {
        return try {
            val token = userPreferences.token.first() ?: return Result.failure(Exception("未登录"))
            val response = api.readDraft(filename, "Bearer $token")
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.content)
            } else {
                Result.failure(Exception(response.message()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveDraft(to: String, subject: String, body: String, filename: String? = null): Result<String> {
        return try {
            val token = userPreferences.token.first() ?: return Result.failure(Exception("未登录"))
            val response = api.saveDraft(
                SaveDraftRequest(to, subject, body, filename),
                "Bearer $token"
            )
            if (response.isSuccessful && response.body() != null && response.body()!!.success) {
                Result.success(response.body()!!.filename)
            } else {
                Result.failure(Exception(response.body()?.message ?: response.message()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteDraft(filename: String): Result<Unit> {
        return try {
            val token = userPreferences.token.first() ?: return Result.failure(Exception("未登录"))
            val response = api.deleteDraft(filename, "Bearer $token")
            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.body()?.message ?: response.message()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }


    // 管理员相关方法（无修改，保持原样）
    suspend fun getUsers(): Result<List<User>> {
        return try {
            val token = userPreferences.token.first() ?: return Result.failure(Exception("未登录，请先登录"))
            val response = api.getUsers("Bearer $token")
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val errorMsg = try {
                    response.errorBody()?.string() ?: response.message()
                } catch (e: Exception) {
                    response.message()
                }
                Result.failure(Exception("获取用户列表失败: $errorMsg"))
            }
        } catch (e: java.net.UnknownHostException) {
            Result.failure(Exception("无法连接到服务器"))
        } catch (e: Exception) {
            Result.failure(Exception("获取用户列表失败: ${e.message ?: "未知错误"}"))
        }
    }

    suspend fun deleteUser(userId: Int): Result<Unit> {
        return try {
            val token = userPreferences.token.first() ?: return Result.failure(Exception("未登录，请先登录"))
            val response = api.deleteUser(userId, "Bearer $token")
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                val errorMsg = try {
                    response.errorBody()?.string() ?: response.message()
                } catch (e: Exception) {
                    response.message()
                }
                Result.failure(Exception("删除用户失败: $errorMsg"))
            }
        } catch (e: java.net.UnknownHostException) {
            Result.failure(Exception("无法连接到服务器"))
        } catch (e: Exception) {
            Result.failure(Exception("删除用户失败: ${e.message ?: "未知错误"}"))
        }
    }

    // ===== 新增：管理员用户管理 =====
    suspend fun createUser(username: String, password: String, role: String = "user"): Result<User> {
        return try {
            val token = userPreferences.token.first() ?: return Result.failure(Exception("未登录，请先登录"))
            val response = api.createUser(CreateUserRequest(username, password, role), "Bearer $token")
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val errorMsg = try { response.errorBody()?.string() ?: response.message() } catch (e: Exception) { response.message() }
                Result.failure(Exception("创建用户失败: $errorMsg"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("创建用户失败: ${e.message ?: "未知错误"}"))
        }
    }

    suspend fun updateUserRole(userId: Int, role: String): Result<Unit> {
        if (role != "user" && role != "admin") return Result.failure(Exception("非法角色"))
        return try {
            val token = userPreferences.token.first() ?: return Result.failure(Exception("未登录，请先登录"))
            val response = api.updateUserRole(userId, UpdateUserRoleRequest(role), "Bearer $token")
            if (response.isSuccessful && (response.body()?.success == true)) Result.success(Unit)
            else Result.failure(Exception(response.body()?.message ?: response.message()))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun disableUser(userId: Int, reason: String? = null): Result<Unit> {
        return try {
            val token = userPreferences.token.first() ?: return Result.failure(Exception("未登录，请先登录"))
            val response = api.disableUser(userId, DisableUserRequest(reason), "Bearer $token")
            if (response.isSuccessful && (response.body()?.success == true)) Result.success(Unit)
            else Result.failure(Exception(response.body()?.message ?: response.message()))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun enableUser(userId: Int): Result<Unit> {
        return try {
            val token = userPreferences.token.first() ?: return Result.failure(Exception("未登录，请先登录"))
            val response = api.enableUser(userId, "Bearer $token")
            if (response.isSuccessful && (response.body()?.success == true)) Result.success(Unit)
            else Result.failure(Exception(response.body()?.message ?: response.message()))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun resetUserPassword(userId: Int, newPassword: String): Result<Unit> {
        return try {
            val token = userPreferences.token.first() ?: return Result.failure(Exception("未登录，请先登录"))
            val response = api.resetUserPassword(userId, ResetPasswordRequest(newPassword), "Bearer $token")
            if (response.isSuccessful && (response.body()?.success == true)) Result.success(Unit)
            else Result.failure(Exception(response.body()?.message ?: response.message()))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun forceLogoutUser(userId: Int): Result<Unit> {
        return try {
            val token = userPreferences.token.first() ?: return Result.failure(Exception("未登录，请先登录"))
            val response = api.forceLogoutUser(userId, "Bearer $token")
            if (response.isSuccessful && (response.body()?.success == true)) Result.success(Unit)
            else Result.failure(Exception(response.body()?.message ?: response.message()))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun broadcastMail(subject: String, body: String): Result<Unit> {
        return try {
            val token = userPreferences.token.first() ?: return Result.failure(Exception("未登录，请先登录"))
            val response = api.broadcastMail(
                BroadcastMailRequest(subject, body),
                "Bearer $token"
            )
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                val errorMsg = try {
                    response.errorBody()?.string() ?: response.message()
                } catch (e: Exception) {
                    response.message()
                }
                Result.failure(Exception("群发邮件失败: $errorMsg"))
            }
        } catch (e: java.net.UnknownHostException) {
            Result.failure(Exception("无法连接到服务器"))
        } catch (e: Exception) {
            Result.failure(Exception("群发邮件失败: ${e.message ?: "未知错误"}"))
        }
    }

    // 发送邮件 - 通过 SMTP 协议（核心修改：校验收件人+统一发件人域名）
    suspend fun sendMail(to: String, subject: String, content: String): Result<Unit> {
        return try {
            val username = userPreferences.username.first() ?: return Result.failure(Exception("未登录，请先登录"))

            // ========== 3. 新增：发送前校验收件人邮箱格式 ==========
            if (!isEmailValid(to)) {
                return Result.failure(Exception("发送失败：收件人邮箱格式无效（需填写 xxx@xxx.xxx 格式）"))
            }

            // ========== 4. 修改：发件人地址用 username + 统一域名（替换localhost） ==========
            val fromAddr = "$username@$MAIL_DOMAIN"

            // 使用 SMTP 协议发送邮件
            val result = smtpClient.sendMail(fromAddr, to, subject, content)

            if (result.isSuccess) {
                Result.success(Unit)
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "发送失败"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: java.net.UnknownHostException) {
            Result.failure(Exception("无法连接到SMTP服务器，请检查网络设置"))
        } catch (e: java.net.SocketTimeoutException) {
            Result.failure(Exception("连接SMTP服务器超时，请稍后重试"))
        } catch (e: Exception) {
            Result.failure(Exception("发送邮件失败: ${e.message ?: "未知错误"}"))
        }
    }

    // 管理员修改密码（无修改）
    suspend fun changeAdminPassword(oldPassword: String, newPassword: String): Result<Unit> {
        return try {
            val token = userPreferences.token.first() ?: return Result.failure(Exception("未登录"))
            val response = api.changeAdminPassword(ChangePasswordRequest(old_password = oldPassword, new_password = newPassword), "Bearer $token")
            if (response.isSuccessful && (response.body()?.success == true)) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.body()?.message ?: response.message()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // IP 黑名单（无修改）
    suspend fun getIpBlacklist(): Result<List<String>> {
        return try {
            val token = userPreferences.token.first() ?: return Result.failure(Exception("未登录"))
            val response = api.getIpBlacklist("Bearer $token")
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.ips)
            } else {
                Result.failure(Exception(response.message()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addIpToBlacklist(ip: String): Result<Unit> {
        return try {
            val token = userPreferences.token.first() ?: return Result.failure(Exception("未登录"))
            val response = api.addIpToBlacklist(IpBlacklistRequest(ip), "Bearer $token")
            if (response.isSuccessful && (response.body()?.success == true)) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.body()?.message ?: response.message()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun removeIpFromBlacklist(ip: String): Result<Unit> {
        return try {
            val token = userPreferences.token.first() ?: return Result.failure(Exception("未登录"))
            val response = api.removeIpFromBlacklist(ip, "Bearer $token")
            if (response.isSuccessful && (response.body()?.success == true)) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.body()?.message ?: response.message()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 邮箱黑名单（无修改）
    suspend fun getEmailBlacklist(): Result<List<String>> {
        return try {
            val token = userPreferences.token.first() ?: return Result.failure(Exception("未登录"))
            val response = api.getEmailBlacklist("Bearer $token")
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.emails)
            } else {
                Result.failure(Exception(response.message()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addEmailToBlacklist(email: String): Result<Unit> {
        // ========== 新增：添加邮箱黑名单前校验格式 ==========
        if (!isEmailValid(email)) {
            return Result.failure(Exception("添加失败：邮箱格式无效（需填写 xxx@xxx.xxx 格式）"))
        }

        return try {
            val token = userPreferences.token.first() ?: return Result.failure(Exception("未登录"))
            val response = api.addEmailToBlacklist(EmailBlacklistRequest(email), "Bearer $token")
            if (response.isSuccessful && (response.body()?.success == true)) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.body()?.message ?: response.message()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun removeEmailFromBlacklist(email: String): Result<Unit> {
        return try {
            val token = userPreferences.token.first() ?: return Result.failure(Exception("未登录"))
            val response = api.removeEmailFromBlacklist(email, "Bearer $token")
            if (response.isSuccessful && (response.body()?.success == true)) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.body()?.message ?: response.message()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun reloadFilters(): Result<Unit> {
        return try {
            val token = userPreferences.token.first() ?: return Result.failure(Exception("未登录"))
            val response = api.reloadFilters("Bearer $token")
            if (response.isSuccessful && (response.body()?.success == true)) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.body()?.message ?: response.message()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 普通用户修改密码（无修改）
    suspend fun changeMyPassword(oldPassword: String, newPassword: String): Result<Unit> {
        return try {
            val token = userPreferences.token.first() ?: return Result.failure(Exception("未登录"))
            val username = userPreferences.username.first() ?: return Result.failure(Exception("未登录"))
            val role = userPreferences.role.first() ?: "user"
            val userId = userPreferences.userIdFirst()
            val response = api.changeMyPassword(ChangePasswordRequest(old_password = oldPassword, new_password = newPassword), "Bearer $token")
            if (response.isSuccessful && (response.body()?.success == true)) {
                // 更新本地存储的密码，避免 SMTP/POP3 继续用旧密码
                userPreferences.saveUserData(
                    token = token,
                    username = username,
                    role = role,
                    userId = userId ?: 0,
                    password = newPassword
                )
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.body()?.message ?: response.message()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 管理员查看所有邮件（无修改）
    suspend fun getAllMails(): Result<List<AdminMailInfo>> {
        return try {
            val token = userPreferences.token.first() ?: return Result.failure(Exception("未登录"))
            val response = api.getAllMails("Bearer $token")
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.mails)
            } else {
                Result.failure(Exception(response.message()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getUserMail(username: String, filename: String): Result<String> {
        return try {
            val token = userPreferences.token.first() ?: return Result.failure(Exception("未登录"))
            val response = api.getUserMail(username, filename, "Bearer $token")
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.content)
            } else {
                Result.failure(Exception(response.message()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}