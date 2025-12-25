package com.mailsystem.data.repository

import com.mailsystem.data.api.RetrofitClient
import com.mailsystem.data.local.UserPreferences
import com.mailsystem.data.model.*
import com.mailsystem.data.protocol.SmtpClient
import com.mailsystem.data.protocol.Pop3Client
import kotlinx.coroutines.flow.first
import java.util.regex.Pattern  // 新增：导入正则工具类
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType

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

    // 忘记密码：请求短信验证码
    suspend fun requestPasswordResetCode(username: String): Result<String> {
        return try {
            val response = api.requestPasswordResetCode(PasswordResetCodeRequest(username))
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.message)
            } else {
                val errorMsg = try { response.errorBody()?.string() ?: response.message() } catch (e: Exception) { response.message() }
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "请求验证码失败"))
        }
    }

    // 忘记密码：确认验证码并重置密码
    suspend fun confirmPasswordReset(username: String, code: String, newPassword: String): Result<Unit> {
        return try {
            val response = api.confirmPasswordReset(PasswordResetConfirmRequest(username, code, newPassword))
            if (response.isSuccessful && response.body()?.success == true) {
                // 清理本地登录态（防止旧token继续使用）
                userPreferences.clearUserData()
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.body()?.message ?: response.message()))
            }
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "重置密码失败"))
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

    // 直接读取收件箱邮件（不检查发件箱）
    suspend fun readInboxMail(filename: String): Result<String> {
        return try {
            val token = userPreferences.token.first() ?: return Result.failure(Exception("未登录"))
            val response = api.readMail(filename, "Bearer $token")
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.content)
            } else {
                Result.failure(Exception("收件箱中不存在"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 通过文件名读取邮件（用于查看原邮件）
    suspend fun readMailByFilename(filename: String): Result<String> {
        return try {
            val token = userPreferences.token.first() ?: return Result.failure(Exception("未登录"))
            // 先尝试从收件箱读取
            val response = api.readMail(filename, "Bearer $token")
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.content)
            } else {
                // 如果收件箱没有，尝试从发件箱读取
                val sentResponse = api.readSentMail(filename, "Bearer $token")
                if (sentResponse.isSuccessful && sentResponse.body() != null) {
                    Result.success(sentResponse.body()!!.content)
                } else {
                    Result.failure(Exception("邮件不存在"))
                }
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
            val adminKey = userPreferences.getAdminKeyFirst()
            val response = api.getUsers("Bearer $token", adminKey)
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
            val adminKey = userPreferences.getAdminKeyFirst()
            val response = api.deleteUser(userId, "Bearer $token", adminKey)
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
            val adminKey = userPreferences.getAdminKeyFirst()
            val response = api.createUser(CreateUserRequest(username, password, role), "Bearer $token", adminKey)
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
            val adminKey = userPreferences.getAdminKeyFirst()
            val response = api.updateUserRole(userId, UpdateUserRoleRequest(role), "Bearer $token", adminKey)
            if (response.isSuccessful && (response.body()?.success == true)) Result.success(Unit)
            else Result.failure(Exception(response.body()?.message ?: response.message()))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun disableUser(userId: Int, reason: String? = null): Result<Unit> {
        return try {
            val token = userPreferences.token.first() ?: return Result.failure(Exception("未登录，请先登录"))
            val adminKey = userPreferences.getAdminKeyFirst()
            val response = api.disableUser(userId, DisableUserRequest(reason), "Bearer $token", adminKey)
            if (response.isSuccessful && (response.body()?.success == true)) Result.success(Unit)
            else Result.failure(Exception(response.body()?.message ?: response.message()))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun enableUser(userId: Int): Result<Unit> {
        return try {
            val token = userPreferences.token.first() ?: return Result.failure(Exception("未登录，请先登录"))
            val adminKey = userPreferences.getAdminKeyFirst()
            val response = api.enableUser(userId, "Bearer $token", adminKey)
            if (response.isSuccessful && (response.body()?.success == true)) Result.success(Unit)
            else Result.failure(Exception(response.body()?.message ?: response.message()))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun resetUserPassword(userId: Int, newPassword: String): Result<Unit> {
        return try {
            val token = userPreferences.token.first() ?: return Result.failure(Exception("未登录，请先登录"))
            val adminKey = userPreferences.getAdminKeyFirst()
            val response = api.resetUserPassword(userId, ResetPasswordRequest(newPassword), "Bearer $token", adminKey)
            if (response.isSuccessful && (response.body()?.success == true)) Result.success(Unit)
            else Result.failure(Exception(response.body()?.message ?: response.message()))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun forceLogoutUser(userId: Int): Result<Unit> {
        return try {
            val token = userPreferences.token.first() ?: return Result.failure(Exception("未登录，请先登录"))
            val adminKey = userPreferences.getAdminKeyFirst()
            val response = api.forceLogoutUser(userId, "Bearer $token", adminKey)
            if (response.isSuccessful && (response.body()?.success == true)) Result.success(Unit)
            else Result.failure(Exception(response.body()?.message ?: response.message()))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun broadcastMail(subject: String, body: String, userIds: List<Int>? = null): Result<Unit> {
        return try {
            val token = userPreferences.token.first() ?: return Result.failure(Exception("未登录，请先登录"))
            val adminKey = userPreferences.getAdminKeyFirst()
            val response = api.broadcastMail(
                BroadcastMailRequest(subject, body, "admin@localhost", userIds),
                "Bearer $token",
                adminKey
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

    // 回复邮件 - 通过HTTP API（因为需要传递回复关联信息）
    suspend fun replyMail(
        to: String,
        subject: String,
        content: String,
        replyToFilename: String,
        isPop3Mail: Boolean = false
    ): Result<Unit> {
        return try {
            val token = userPreferences.token.first() ?: return Result.failure(Exception("未登录，请先登录"))

            // 校验收件人邮箱格式
            if (!isEmailValid(to)) {
                return Result.failure(Exception("发送失败：收件人邮箱格式无效（需填写 xxx@xxx.xxx 格式）"))
            }

            val response = api.replyMail(
                ReplyMailRequest(to, subject, content, replyToFilename, isPop3Mail),
                "Bearer $token"
            )

            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(Unit)
            } else {
                val errorMsg = try {
                    response.errorBody()?.string()?.ifBlank { null }
                } catch (_: Exception) { null } ?: response.body()?.message ?: response.message()
                Result.failure(Exception("回复邮件失败: $errorMsg"))
            }
        } catch (e: java.net.UnknownHostException) {
            Result.failure(Exception("无法连接到服务器，请检查网络设置"))
        } catch (e: java.net.SocketTimeoutException) {
            Result.failure(Exception("连接服务器超时，请稍后重试"))
        } catch (e: Exception) {
            Result.failure(Exception("回复邮件失败: ${e.message ?: "未知错误"}"))
        }
    }

    // 获取原邮件主题
    suspend fun getOriginalMailSubject(inReplyTo: String): Result<String> {
        return try {
            val token = userPreferences.token.first() ?: return Result.failure(Exception("未登录"))
            val response = api.getOriginalMailSubject(inReplyTo, "Bearer $token")
            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(response.body()!!.subject)
            } else {
                Result.failure(Exception(response.body()?.subject ?: "获取失败"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 获取回复链
    suspend fun getReplyChain(filename: String): Result<List<com.mailsystem.data.model.ReplyChainItem>> {
        return try {
            val token = userPreferences.token.first() ?: return Result.failure(Exception("未登录"))
            val response = api.getReplyChain(filename, "Bearer $token")
            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(response.body()!!.chain)
            } else {
                Result.failure(Exception("获取回复链失败"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 发送邮件 - 通过后端 REST API（后端处理外部SMTP发送）
    suspend fun sendMail(to: String, subject: String, content: String, mailFilename: String?): Result<Unit> {
        return try {
            val token = userPreferences.token.first() ?: return Result.failure(Exception("未登录，请先登录"))

            // 校验收件人邮箱格式
            if (!isEmailValid(to)) {
                return Result.failure(Exception("发送失败：收件人邮箱格式无效（需填写 xxx@xxx.xxx 格式）"))
            }

            // 通过后端 REST API 发送邮件
            val request = SendMailRequest(
                to_addr = to,
                subject = subject,
                body = content,
                mail_filename = mailFilename
            )
            val response = api.sendMail(request, "Bearer $token")

            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(Unit)
            } else {
                val errorMsg = response.body()?.message ?: "发送失败"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(Exception("发送邮件失败: ${e.message ?: "未知错误"}"))
        }
    }

    // 管理员修改密码（无修改）
    suspend fun changeAdminPassword(oldPassword: String, newPassword: String): Result<Unit> {
        return try {
            val token = userPreferences.token.first() ?: return Result.failure(Exception("未登录"))
            val adminKey = userPreferences.getAdminKeyFirst()
            val response = api.changeAdminPassword(ChangePasswordRequest(old_password = oldPassword, new_password = newPassword), "Bearer $token", adminKey)
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
            val adminKey = userPreferences.getAdminKeyFirst()
            val response = api.getIpBlacklist("Bearer $token", adminKey)
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
            val adminKey = userPreferences.getAdminKeyFirst()
            val response = api.addIpToBlacklist(IpBlacklistRequest(ip), "Bearer $token", adminKey)
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
            val adminKey = userPreferences.getAdminKeyFirst()
            val response = api.removeIpFromBlacklist(ip, "Bearer $token", adminKey)
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
            val adminKey = userPreferences.getAdminKeyFirst()
            val response = api.getEmailBlacklist("Bearer $token", adminKey)
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
            val adminKey = userPreferences.getAdminKeyFirst()
            val response = api.addEmailToBlacklist(EmailBlacklistRequest(email), "Bearer $token", adminKey)
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
            val adminKey = userPreferences.getAdminKeyFirst()
            val response = api.removeEmailFromBlacklist(email, "Bearer $token", adminKey)
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
            val adminKey = userPreferences.getAdminKeyFirst()
            val response = api.reloadFilters("Bearer $token", adminKey)
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

    suspend fun getProfile(): Result<ProfileResponse> {
        return try {
            val token = userPreferences.token.first() ?: return Result.failure(Exception("未登录"))
            val response = api.getProfile("Bearer $token")
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(response.message()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateProfile(username: String?, phone: String?): Result<Unit> {
        return try {
            val token = userPreferences.token.first() ?: return Result.failure(Exception("未登录"))
            val response = api.updateProfile(UpdateProfileRequest(username, phone), "Bearer $token")
            if (response.isSuccessful && response.body()?.success == true) {
                // 同步本地用户名
                val currentRole = userPreferences.role.first() ?: "user"
                val currentUserId = userPreferences.userIdFirst() ?: 0
                val currentPassword = userPreferences.password.first() ?: ""
                val newUsername = username ?: (userPreferences.username.first() ?: "")
                userPreferences.saveUserData(token, newUsername, currentRole, currentUserId, currentPassword)
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.body()?.message ?: response.message()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 绑定手机号
    suspend fun bindPhone(phone: String): Result<Unit> {
        return try {
            val token = userPreferences.token.first() ?: return Result.failure(Exception("未登录"))
            val response = api.bindPhone(BindPhoneRequest(phone), "Bearer $token")
            if (response.isSuccessful && (response.body()?.success == true)) {
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
            val adminKey = userPreferences.getAdminKeyFirst()
            val response = api.getAllMails("Bearer $token", adminKey)
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
            val adminKey = userPreferences.getAdminKeyFirst()
            val response = api.getUserMail(username, filename, "Bearer $token", adminKey)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.content)
            } else {
                Result.failure(Exception(response.message()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 申诉功能
    suspend fun submitAppeal(username: String, password: String, reason: String): Result<String> {
        return try {
            val response = api.submitAppeal(AppealRequest(username, password, reason))
            if (response.isSuccessful && response.body() != null && response.body()!!.success) {
                Result.success(response.body()!!.message)
            } else {
                val errorMsg = try { response.errorBody()?.string() ?: response.message() } catch (e: Exception) { response.message() }
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAppeals(): Result<List<AppealResponse>> {
        return try {
            val token = userPreferences.token.first() ?: return Result.failure(Exception("未登录"))
            val response = api.getAppeals("Bearer $token")
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(response.message()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun approveAppeal(appealId: Int): Result<String> {
        return try {
            val token = userPreferences.token.first() ?: return Result.failure(Exception("未登录"))
            val response = api.approveAppeal(appealId, "Bearer $token")
            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(response.body()!!.message)
            } else {
                Result.failure(Exception(response.body()?.message ?: response.message()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun rejectAppeal(appealId: Int): Result<String> {
        return try {
            val token = userPreferences.token.first() ?: return Result.failure(Exception("未登录"))
            val response = api.rejectAppeal(appealId, "Bearer $token")
            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(response.body()!!.message)
            } else {
                Result.failure(Exception(response.body()?.message ?: response.message()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==================== 附件相关 ====================

    suspend fun uploadAttachment(mailFilename: String, fileName: String, fileContent: ByteArray): Result<String> {
        return try {
            val token = userPreferences.token.first() ?: return Result.failure(Exception("未登录"))

            // 检查大小（10MB）
            if (fileContent.size > 10 * 1024 * 1024) {
                return Result.failure(Exception("文件过大，最大限制10MB"))
            }

            // 创建 MultipartBody.Part
            val mediaType = "application/octet-stream".toMediaType()
            val body = fileContent.toRequestBody(mediaType)
            val part = MultipartBody.Part.createFormData("file", fileName, body)

            val response = api.uploadAttachment(mailFilename, part, "Bearer $token")

            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(response.body()!!.filename)
            } else {
                Result.failure(Exception(response.body()?.message ?: "上传失败"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("上传附件失败: ${e.message}"))
        }
    }

    suspend fun downloadAttachment(mailFilename: String, attachmentFilename: String): Result<ByteArray> {
        return try {
            val token = userPreferences.token.first() ?: return Result.failure(Exception("未登录"))
            
            val response = api.downloadAttachment(mailFilename, attachmentFilename, "Bearer $token")
            
            if (response.isSuccessful) {
                val bytes = response.body()?.bytes() ?: ByteArray(0)
                Result.success(bytes)
            } else {
                Result.failure(Exception("下载失败"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("下载附件失败: ${e.message}"))
        }
    }

    suspend fun getAttachments(mailFilename: String): Result<List<AttachmentInfo>> {
        return try {
            val token = userPreferences.token.first() ?: return Result.failure(Exception("未登录"))
            
            val response = api.getAttachments(mailFilename, "Bearer $token")
            
            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(response.body()!!.attachments)
            } else {
                Result.failure(Exception("获取附件列表失败"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("获取附件列表失败: ${e.message}"))
        }
    }

    // POP3: 根据序号获取真实文件名
    suspend fun getPop3Filename(index: Int): Result<String> {
        return try {
            val token = userPreferences.token.first() ?: return Result.failure(Exception("未登录"))
            val response = api.getPop3Filename(index, "Bearer $token")
            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(response.body()!!.filename)
            } else {
                Result.failure(Exception("获取文件名失败"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("获取文件名失败: ${e.message}"))
        }
    }
}