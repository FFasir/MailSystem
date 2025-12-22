package com.mailsystem.data.model

data class User(
    val id: Int,
    val username: String,
    val role: String,
    val created_at: String,
    val is_disabled: Int = 0
)

data class LoginRequest(
    val username: String,
    val password: String
)

data class RegisterRequest(
    val username: String,
    val password: String,
    val role: String = "user"
)

data class TokenResponse(
    val token: String,
    val user_id: Int,
    val username: String,
    val role: String
)

data class MessageResponse(
    val success: Boolean,
    val message: String
)

// 管理员修改密码
data class ChangePasswordRequest(
    val old_password: String,
    val new_password: String
)

// 管理员用户管理新增
data class CreateUserRequest(
    val username: String,
    val password: String,
    val role: String = "user"
)

data class UpdateUserRoleRequest(
    val role: String
)

data class DisableUserRequest(
    val reason: String? = null
)

data class ResetPasswordRequest(
    val new_password: String
)

data class BindPhoneRequest(
    val phone_number: String
)

data class ProfileResponse(
    val id: Int,
    val username: String,
    val role: String,
    val is_disabled: Int,
    val created_at: String,
    val phone_number: String?,
    val email: String?
)

data class UpdateProfileRequest(
    val username: String?,
    val phone_number: String?
)

// 忘记密码 - 请求验证码
data class PasswordResetCodeRequest(
    val username: String
)

// 忘记密码 - 确认重置
data class PasswordResetConfirmRequest(
    val username: String,
    val code: String,
    val new_password: String
)

// 黑名单数据模型
data class IpBlacklistRequest(
    val ip: String
)

data class EmailBlacklistRequest(
    val email: String
)

data class IpBlacklistResponse(
    val success: Boolean,
    val count: Int,
    val ips: List<String>
)

data class EmailBlacklistResponse(
    val success: Boolean,
    val count: Int,
    val emails: List<String>
)

// 管理员查看所有邮件
data class AdminMailInfo(
    val username: String,
    val filename: String,
    val size: Int,
    val created: Double
)

data class AllMailsResponse(
    val success: Boolean,
    val count: Int,
    val mails: List<AdminMailInfo>
)
