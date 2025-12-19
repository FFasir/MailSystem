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
    val from_addr: String = "admin@localhost"
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

/*
// 补充MessageResponse（如果没有的话）
data class MessageResponse(
    val success: Boolean,
    val message: String
)

 */
