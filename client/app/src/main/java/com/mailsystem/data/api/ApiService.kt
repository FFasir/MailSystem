package com.mailsystem.data.api

import com.mailsystem.data.model.*
import okhttp3.MultipartBody
import okhttp3.ResponseBody
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
    
    // 新增：已发送邮件（通过 REST API 获取，因为 POP3 不支持已发送文件夹）
    @GET("mail/sent/list")
    suspend fun getSentMails(@Header("Authorization") token: String): Response<MailListResponse>
    
    @GET("mail/read/{filename}")
    suspend fun readMail(
        @Path("filename") filename: String,
        @Header("Authorization") token: String
    ): Response<MailContentResponse>

    @GET("mail/sent/read/{filename}")
    suspend fun readSentMail(
        @Path("filename") filename: String,
        @Header("Authorization") token: String
    ): Response<MailContentResponse>

    // 草稿箱功能
    @GET("mail/draft/list")
    suspend fun getDraftList(@Header("Authorization") token: String): Response<MailListResponse>

    @GET("mail/draft/read/{filename}")
    suspend fun readDraft(
        @Path("filename") filename: String,
        @Header("Authorization") token: String
    ): Response<MailContentResponse>

    @POST("mail/draft/save")
    suspend fun saveDraft(
        @Body request: SaveDraftRequest,
        @Header("Authorization") token: String
    ): Response<SaveDraftResponse>

    @DELETE("mail/draft/delete/{filename}")
    suspend fun deleteDraft(
        @Path("filename") filename: String,
        @Header("Authorization") token: String
    ): Response<MessageResponse>

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

    @POST("auth/bind-phone")
    suspend fun bindPhone(
        @Body request: BindPhoneRequest,
        @Header("Authorization") token: String
    ): Response<MessageResponse>

    @GET("auth/profile")
    suspend fun getProfile(
        @Header("Authorization") token: String
    ): Response<ProfileResponse>

    @PATCH("auth/profile")
    suspend fun updateProfile(
        @Body request: UpdateProfileRequest,
        @Header("Authorization") token: String
    ): Response<MessageResponse>

    // 忘记密码 - 发送验证码
    @POST("auth/forgot-password/request")
    suspend fun requestPasswordResetCode(
        @Body request: PasswordResetCodeRequest
    ): Response<MessageResponse>

    // 忘记密码 - 重置密码
    @POST("auth/forgot-password/reset")
    suspend fun confirmPasswordReset(
        @Body request: PasswordResetConfirmRequest
    ): Response<MessageResponse>

    // 申诉
    @POST("appeal/submit")
    suspend fun submitAppeal(@Body request: AppealRequest): Response<MessageResponse>

    @GET("appeal/list")
    suspend fun getAppeals(@Header("Authorization") token: String): Response<List<AppealResponse>>

    @POST("appeal/{appealId}/approve")
    suspend fun approveAppeal(
        @Path("appealId") appealId: Int,
        @Header("Authorization") token: String
    ): Response<MessageResponse>

    @POST("appeal/{appealId}/reject")
    suspend fun rejectAppeal(
        @Path("appealId") appealId: Int,
        @Header("Authorization") token: String
    ): Response<MessageResponse>

    // 发送邮件 - 通过后端 REST API（后端会处理外部SMTP）
    @POST("mail/send")
    suspend fun sendMail(
        @Body request: SendMailRequest,
        @Header("Authorization") token: String
    ): Response<MessageResponse>

    // 回复邮件API
    @POST("mail/reply")
    suspend fun replyMail(
        @Body request: ReplyMailRequest,
        @Header("Authorization") token: String
    ): Response<MessageResponse>

    // 获取原邮件主题
    @GET("mail/original-subject")
    suspend fun getOriginalMailSubject(
        @Query("in_reply_to") inReplyTo: String,
        @Header("Authorization") token: String
    ): Response<OriginalMailSubjectResponse>

    // 获取回复链
    @GET("mail/reply-chain/{filename}")
    suspend fun getReplyChain(
        @Path("filename") filename: String,
        @Header("Authorization") token: String
    ): Response<ReplyChainResponse>

    // ==================== 附件相关 API ====================

    @Multipart
    @POST("mail/attachment/upload/{mail_filename}")
    suspend fun uploadAttachment(
        @Path("mail_filename") mailFilename: String,
        @Part file: MultipartBody.Part,
        @Header("Authorization") token: String
    ): Response<UploadAttachmentResponse>

    @GET("mail/attachment/{mail_filename}/{attachment_filename}")
    suspend fun downloadAttachment(
        @Path("mail_filename") mailFilename: String,
        @Path("attachment_filename") attachmentFilename: String,
        @Header("Authorization") token: String
    ): Response<ResponseBody>

    @GET("mail/attachments/{mail_filename}")
    suspend fun getAttachments(
        @Path("mail_filename") mailFilename: String,
        @Header("Authorization") token: String
    ): Response<AttachmentsResponse>
}
