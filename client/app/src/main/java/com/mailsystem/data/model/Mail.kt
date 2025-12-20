package com.mailsystem.data.model

data class Mail(
    val mailId: Int,           // POP3 邮件 ID
    val filename: String,      // 显示用的文件名
    val path: String = "",     // 路径（POP3 模式下不需要）
    val size: Int,             // 邮件大小（字节）
    val created: String = ""   // 创建时间
)

data class MailListResponse(
    val success: Boolean,
    val count: Int,
    val mails: List<Mail>
)

data class MailContentResponse(
    val success: Boolean,
    val filename: String,
    val content: String
)

// SendMailRequest 不再需要 - Android 直接使用 SMTP 协议发送邮件

data class BroadcastMailRequest(
    val subject: String,
    val body: String,
    val from_addr: String = "admin@localhost",
    val user_ids: List<Int>? = null  // 可选的用户ID列表，如果为空或null则发送给所有用户
)


// 新增发送邮件请求模型
data class SendMailRequest(
    val to_addr: String,  // 对应后端的to_addr字段
    val subject: String,
    val body: String
)

data class SaveDraftRequest(
    val to_addr: String,
    val subject: String,
    val body: String,
    val filename: String? = null
)

data class SaveDraftResponse(
    val success: Boolean,
    val message: String,
    val filename: String
)

// 回复邮件请求模型
data class ReplyMailRequest(
    val to_addr: String,  // 收件人地址
    val subject: String,  // 邮件主题
    val body: String,     // 邮件正文
    val reply_to_filename: String,  // 回复的原始邮件文件名或mailId
    val is_pop3_mail: Boolean = false  // 是否是POP3邮件（通过mailId标识）
)

// 原邮件主题响应
data class OriginalMailSubjectResponse(
    val success: Boolean,
    val subject: String
)

// 回复链项
data class ReplyChainItem(
    val filename: String,
    val content: String
)

// 回复链响应
data class ReplyChainResponse(
    val success: Boolean,
    val chain: List<ReplyChainItem>
)

/*
// 补充MessageResponse（如果没有的话）
data class MessageResponse(
    val success: Boolean,
    val message: String
)

 */
