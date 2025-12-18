package com.mailsystem.data.api

import com.mailsystem.data.model.*
import retrofit2.Response
import retrofit2.http.*

interface ApiService {
    
    // 认证
    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<User>
    
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<TokenResponse>
    
    @POST("auth/logout")
    suspend fun logout(@Header("Authorization") token: String): Response<MessageResponse>
    
    // 邮件功能已改为直接使用 SMTP/POP3 协议，不再通过 HTTP API
    
    // 管理员
    @GET("admin/users")
    suspend fun getUsers(@Header("Authorization") token: String): Response<List<User>>
    
    @DELETE("admin/users/{userId}")
    suspend fun deleteUser(
        @Path("userId") userId: Int,
        @Header("Authorization") token: String
    ): Response<MessageResponse>
    
    @POST("admin/broadcast")
    suspend fun broadcastMail(
        @Body request: BroadcastMailRequest,
        @Header("Authorization") token: String
    ): Response<MessageResponse>
    
    // 修改管理员密码
    @POST("admin/change-password")
    suspend fun changeAdminPassword(
        @Body request: ChangePasswordRequest,
        @Header("Authorization") token: String
    ): Response<MessageResponse>

    // ===== 管理员用户管理 - 新增 =====
    @POST("admin/users")
    suspend fun createUser(
        @Body request: CreateUserRequest,
        @Header("Authorization") token: String
    ): Response<User>

    @PATCH("admin/users/{userId}/role")
    suspend fun updateUserRole(
        @Path("userId") userId: Int,
        @Body request: UpdateUserRoleRequest,
        @Header("Authorization") token: String
    ): Response<MessageResponse>

    @POST("admin/users/{userId}/disable")
    suspend fun disableUser(
        @Path("userId") userId: Int,
        @Body request: DisableUserRequest?,
        @Header("Authorization") token: String
    ): Response<MessageResponse>

    @POST("admin/users/{userId}/enable")
    suspend fun enableUser(
        @Path("userId") userId: Int,
        @Header("Authorization") token: String
    ): Response<MessageResponse>

    @POST("admin/users/{userId}/reset-password")
    suspend fun resetUserPassword(
        @Path("userId") userId: Int,
        @Body request: ResetPasswordRequest,
        @Header("Authorization") token: String
    ): Response<MessageResponse>

    @POST("admin/users/{userId}/logout")
    suspend fun forceLogoutUser(
        @Path("userId") userId: Int,
        @Header("Authorization") token: String
    ): Response<MessageResponse>

    // IP 黑名单
    @GET("admin/ip-blacklist")
    suspend fun getIpBlacklist(
        @Header("Authorization") token: String
    ): Response<IpBlacklistResponse>

    @POST("admin/ip-blacklist")
    suspend fun addIpToBlacklist(
        @Body request: IpBlacklistRequest,
        @Header("Authorization") token: String
    ): Response<MessageResponse>

    @DELETE("admin/ip-blacklist/{ip}")
    suspend fun removeIpFromBlacklist(
        @Path("ip") ip: String,
        @Header("Authorization") token: String
    ): Response<MessageResponse>

    // 邮箱黑名单
    @GET("admin/email-blacklist")
    suspend fun getEmailBlacklist(
        @Header("Authorization") token: String
    ): Response<EmailBlacklistResponse>

    @POST("admin/email-blacklist")
    suspend fun addEmailToBlacklist(
        @Body request: EmailBlacklistRequest,
        @Header("Authorization") token: String
    ): Response<MessageResponse>

    @DELETE("admin/email-blacklist/{email}")
    suspend fun removeEmailFromBlacklist(
        @Path("email") email: String,
        @Header("Authorization") token: String
    ): Response<MessageResponse>

    // 重新加载过滤器
    @POST("admin/reload-filters")
    suspend fun reloadFilters(
        @Header("Authorization") token: String
    ): Response<MessageResponse>

    // 管理员查看所有邮件
    @GET("admin/mails")
    suspend fun getAllMails(
        @Header("Authorization") token: String
    ): Response<AllMailsResponse>

    @GET("admin/mails/{username}/{filename}")
    suspend fun getUserMail(
        @Path("username") username: String,
        @Path("filename") filename: String,
        @Header("Authorization") token: String
    ): Response<MailContentResponse>

    // 用户修改密码
    @POST("auth/change-password")
    suspend fun changeMyPassword(
        @Body request: ChangePasswordRequest,
        @Header("Authorization") token: String
    ): Response<MessageResponse>

    // sendMail 已移除 - Android 直接使用 SMTP 协议发送邮件
}
