package com.mailsystem.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mailsystem.data.local.UserPreferences
import com.mailsystem.data.model.Mail
import com.mailsystem.data.repository.MailRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MailViewModel(application: Application) : AndroidViewModel(application) {

    val userPreferences = UserPreferences(application)
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

    suspend fun sendMail(to: String, subject: String, content: String, mailFilename: String?) {
        _loading.value = true
        _error.value = null
        val result = repository.sendMail(to, subject, content, mailFilename)
        if (!result.isSuccess) {
            _error.value = result.exceptionOrNull()?.message ?: "发送失败"
            throw result.exceptionOrNull() ?: Exception("发送失败")
        }
        _loading.value = false
    }

    // 回复邮件
    suspend fun replyMail(
        to: String,
        subject: String,
        content: String,
        replyToFilename: String,
        isPop3Mail: Boolean = false
    ) {
        _loading.value = true
        _error.value = null
        val result = repository.replyMail(to, subject, content, replyToFilename, isPop3Mail)
        if (!result.isSuccess) {
            _error.value = result.exceptionOrNull()?.message ?: "回复失败"
            _loading.value = false
            throw result.exceptionOrNull() ?: Exception("回复失败")
        }
        _loading.value = false
    }

    // 获取原邮件主题
    suspend fun getOriginalMailSubject(inReplyTo: String): String? {
        val result = repository.getOriginalMailSubject(inReplyTo)
        return if (result.isSuccess) {
            result.getOrNull()
        } else {
            null
        }
    }

    // POP3：获取真实文件名（用于附件加载）
    suspend fun getPop3Filename(index: Int): String? {
        val result = repository.getPop3Filename(index)
        return if (result.isSuccess) result.getOrNull() else null
    }

    // 获取POP3原邮件主题（通过mailId），去掉"Re: "前缀
    suspend fun getPop3OriginalMailSubject(mailId: Int): String? {
        val result = repository.readMail(mailId)
        if (result.isSuccess) {
            val content = result.getOrNull() ?: return null
            // 从邮件内容中提取Subject
            val lines = content.lines()
            for (line in lines) {
                if (line.startsWith("Subject:", ignoreCase = true)) {
                    var subject = line.substringAfter(":").trim()
                    // 去掉"Re: "前缀（如果有），返回原始主题
                    if (subject.startsWith("Re:", ignoreCase = true)) {
                        subject = subject.substringAfter(":").trim()
                    }
                    return subject
                }
                if (line.isEmpty()) break
            }
        }
        return null
    }

    // 获取原邮件内容（用于查看）
    private val _originalMailContent = MutableStateFlow<String>("")
    val originalMailContent: StateFlow<String> = _originalMailContent

    // 回复链（用于显示完整对话）
    private val _replyChain = MutableStateFlow<List<com.mailsystem.data.model.ReplyChainItem>>(emptyList())
    val replyChain: StateFlow<List<com.mailsystem.data.model.ReplyChainItem>> = _replyChain

    fun loadOriginalMail(inReplyTo: String, isPop3Mail: Boolean, pop3MailId: Int? = null, currentMailSubject: String? = null) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            _originalMailContent.value = ""  // 清空之前的内容
            try {
                if (isPop3Mail && pop3MailId != null) {
                    // 当In-Reply-To是POP3_MAIL_xxx格式时，原邮件可能不在POP3中
                    // 先尝试通过文件名从收件箱和发件箱查找（如果inReplyTo包含文件名信息）
                    // 如果inReplyTo是POP3_MAIL_xxx格式，先尝试通过POP3读取，然后验证

                    // 确定期望的原邮件主题（去掉"Re: "前缀）
                    var expectedSubject = currentMailSubject ?: ""
                    if (expectedSubject.startsWith("Re:", ignoreCase = true)) {
                        expectedSubject = expectedSubject.substringAfter(":").trim()
                    }

                    // 验证邮件是否是原邮件（不是回复邮件）
                    fun isOriginalMail(content: String): Boolean {
                        if (content.isEmpty()) return false
                        val lines = content.lines()
                        var hasInReplyTo = false
                        var subject = ""
                        var hasReferences = false
                        for (line in lines) {
                            if (line.startsWith("In-Reply-To:", ignoreCase = true)) {
                                hasInReplyTo = true
                                break  // 一旦发现有In-Reply-To，就不是原邮件
                            }
                            if (line.startsWith("References:", ignoreCase = true)) {
                                hasReferences = true
                            }
                            if (line.startsWith("Subject:", ignoreCase = true)) {
                                subject = line.substringAfter(":").trim()
                            }
                            if (line.isEmpty()) break
                        }
                        // 原邮件必须没有In-Reply-To头和References头
                        if (hasInReplyTo || hasReferences) {
                            return false
                        }
                        // 如果提供了期望主题，验证主题是否匹配
                        if (expectedSubject.isNotEmpty()) {
                            // 主题应该完全匹配（不包含"Re: "）
                            return subject.equals(expectedSubject, ignoreCase = true) &&
                                   !subject.startsWith("Re:", ignoreCase = true)
                        }
                        // 如果没有期望主题，只要没有In-Reply-To和References就认为是原邮件
                        return true
                    }

                    // 先尝试通过POP3协议读取
                    val pop3Result = repository.readMail(pop3MailId)
                    var foundOriginal = false

                    if (pop3Result.isSuccess) {
                        val pop3Content = pop3Result.getOrNull() ?: ""
                        if (isOriginalMail(pop3Content)) {
                            _originalMailContent.value = pop3Content
                            foundOriginal = true
                        }
                    }

                    // 如果POP3读取的不是原邮件，尝试从收件箱和发件箱查找
                    if (!foundOriginal) {
                        // 尝试从收件箱和发件箱读取（通过文件名，如果inReplyTo包含文件名）
                        // 但inReplyTo是POP3_MAIL_xxx格式，没有文件名信息
                        // 所以需要通过主题从发件箱和收件箱查找

                        val currentUsername = userPreferences.username.first() ?: ""
                        val currentUserEmail = if (currentUsername.isNotEmpty()) "$currentUsername@mail.com" else ""

                        // 先尝试从发件箱查找（原邮件可能是用户发送的）
                        val sentMailsResult = repository.getSentMails()
                        if (sentMailsResult.isSuccess && expectedSubject.isNotEmpty()) {
                            val sentMails = sentMailsResult.getOrNull() ?: emptyList()
                            for (mail in sentMails) {
                                val sentContentResult = repository.readSentMail(mail.filename)
                                if (sentContentResult.isSuccess) {
                                    val sentContent = sentContentResult.getOrNull() ?: ""
                                    if (isOriginalMail(sentContent)) {
                                        _originalMailContent.value = sentContent
                                        foundOriginal = true
                                        break
                                    }
                                }
                            }
                        }

                        // 如果发件箱没找到，尝试从收件箱查找（原邮件可能是别人发送的）
                        if (!foundOriginal) {
                            // 从收件箱查找需要获取所有邮件，但收件箱邮件是通过POP3协议获取的
                            // 所以我们需要通过POP3协议获取邮件列表，然后查找匹配的邮件
                            val mailListResult = repository.getMailList()
                            if (mailListResult.isSuccess && expectedSubject.isNotEmpty()) {
                                val mails = mailListResult.getOrNull() ?: emptyList()
                                for (mail in mails) {
                                    // 跳过当前邮件（回复邮件本身）
                                    if (mail.mailId == pop3MailId) continue

                                    val mailContentResult = repository.readMail(mail.mailId)
                                    if (mailContentResult.isSuccess) {
                                        val mailContent = mailContentResult.getOrNull() ?: ""
                                        if (isOriginalMail(mailContent)) {
                                            _originalMailContent.value = mailContent
                                            foundOriginal = true
                                            break
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (!foundOriginal) {
                        _error.value = "无法加载原邮件: 邮件不存在或不是原邮件"
                    }
                } else {
                    // 普通邮件，通过文件名获取原邮件
                    // 确定期望的原邮件主题（去掉"Re: "前缀）
                    var expectedSubject = currentMailSubject ?: ""
                    if (expectedSubject.startsWith("Re:", ignoreCase = true)) {
                        expectedSubject = expectedSubject.substringAfter(":").trim()
                    }

                    // 验证邮件是否是原邮件（不是回复邮件）
                    fun isOriginalMail(content: String): Boolean {
                        val lines = content.lines()
                        var hasInReplyTo = false
                        var subject = ""
                        for (line in lines) {
                            if (line.startsWith("In-Reply-To:", ignoreCase = true)) {
                                hasInReplyTo = true
                                break  // 一旦发现有In-Reply-To，就不是原邮件
                            }
                            if (line.startsWith("Subject:", ignoreCase = true)) {
                                subject = line.substringAfter(":").trim()
                            }
                            if (line.isEmpty()) break
                        }
                        // 原邮件必须没有In-Reply-To头
                        if (hasInReplyTo) {
                            return false
                        }
                        // 如果提供了期望主题，验证主题是否匹配
                        if (expectedSubject.isNotEmpty()) {
                            // 主题应该完全匹配（不包含"Re: "）
                            return subject.equals(expectedSubject, ignoreCase = true)
                        }
                        return true  // 如果没有期望主题，只要没有In-Reply-To就认为是原邮件
                    }

                    // 尝试从发件箱和收件箱读取，找到真正的原邮件
                    // 分别检查收件箱和发件箱
                    val inboxResult = repository.readInboxMail(inReplyTo)
                    val sentResult = repository.readSentMail(inReplyTo)

                    var foundOriginal = false
                    var candidateContent: String? = null
                    var candidateIsOriginal = false

                    // 先检查收件箱的邮件
                    if (inboxResult.isSuccess) {
                        val inboxContent = inboxResult.getOrNull() ?: ""
                        val isOriginal = isOriginalMail(inboxContent)
                        if (isOriginal) {
                            _originalMailContent.value = inboxContent
                            foundOriginal = true
                        } else {
                            // 保存作为候选（如果找不到更好的）
                            candidateContent = inboxContent
                            candidateIsOriginal = isOriginal
                        }
                    }

                    // 如果收件箱没有找到，检查发件箱的邮件
                    if (!foundOriginal && sentResult.isSuccess) {
                        val sentContent = sentResult.getOrNull() ?: ""
                        val isOriginal = isOriginalMail(sentContent)
                        if (isOriginal) {
                            _originalMailContent.value = sentContent
                            foundOriginal = true
                        } else if (candidateContent == null || !candidateIsOriginal) {
                            // 如果收件箱的邮件也不是原邮件，优先使用发件箱的邮件
                            candidateContent = sentContent
                            candidateIsOriginal = isOriginal
                        }
                    }

                    // 如果都没找到真正的原邮件，使用候选内容或报错
                    if (!foundOriginal) {
                        if (candidateContent != null && candidateIsOriginal) {
                            _originalMailContent.value = candidateContent
                        } else if (candidateContent != null) {
                            // 如果候选内容不是原邮件，但仍然使用它（可能是同名文件）
                            _originalMailContent.value = candidateContent
                        } else {
                            _error.value = "无法加载原邮件: 邮件不存在或不是原邮件"
                        }
                    }
                }
            } catch (e: Exception) {
                _error.value = "加载原邮件失败: ${e.message}"
            }
            _loading.value = false
        }
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

    // 加载回复链（用于显示完整对话）
    fun loadReplyChain(filename: String) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            _replyChain.value = emptyList()
            try {
                val result = repository.getReplyChain(filename)
                if (result.isSuccess) {
                    _replyChain.value = result.getOrNull() ?: emptyList()
                } else {
                    _error.value = result.exceptionOrNull()?.message ?: "获取回复链失败"
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "获取回复链失败"
            } finally {
                _loading.value = false
            }
        }
    }
    
    // ========== 新增：附件管理方法 ==========
    fun uploadAttachment(mailFilename: String, fileName: String, fileContent: ByteArray) {
        viewModelScope.launch {
            try {
                val result = repository.uploadAttachment(mailFilename, fileName, fileContent)
                if (result.isFailure) {
                    _error.value = result.exceptionOrNull()?.message ?: "上传失败"
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "上传失败"
            }
        }
    }
    
    suspend fun downloadAttachment(mailFilename: String, attachmentFilename: String): Result<ByteArray> {
        return repository.downloadAttachment(mailFilename, attachmentFilename)
    }
    
    suspend fun getAttachments(mailFilename: String): Result<List<com.mailsystem.data.model.AttachmentInfo>> {
        return repository.getAttachments(mailFilename).let { result ->
            if (result.isSuccess) {
                Result.success(result.getOrNull() ?: emptyList())
            } else {
                Result.failure(result.exceptionOrNull() ?: Exception("获取附件失败"))
            }
        }
    }
    
    private val _downloadedFile = MutableStateFlow<ByteArray?>(null)
    val downloadedFile: StateFlow<ByteArray?> = _downloadedFile
}
