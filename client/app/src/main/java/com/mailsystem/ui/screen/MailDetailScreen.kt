package com.mailsystem.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mailsystem.data.model.ReplyTemplate
import com.mailsystem.ui.viewmodel.MailViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MailDetailScreen(
    filename: String,  // 实际上是 mailId 的字符串形式
    onNavigateBack: () -> Unit,
    onNavigateToMail: (String) -> Unit,  // 导航到指定邮件
    mailViewModel: MailViewModel = viewModel()
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val mailContent by mailViewModel.mailContent.collectAsState()
    val loading by mailViewModel.loading.collectAsState()
    val error by mailViewModel.error.collectAsState()
    val templates by mailViewModel.templates.collectAsState()

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showTemplateDialog by remember { mutableStateOf(false) }
    var showEditTemplateDialog by remember { mutableStateOf(false) }
    var showReplyConfirmDialog by remember { mutableStateOf(false) }
    var selectedTemplate by remember { mutableStateOf<ReplyTemplate?>(null) }
    var templateToEdit by remember { mutableStateOf<ReplyTemplate?>(null) } // For editing existing or new

    // Reply context
    var replyTo by remember { mutableStateOf("") }
    var replySubject by remember { mutableStateOf("") }
    var replyContent by remember { mutableStateOf("") }

    val mailId = filename.toIntOrNull()

    LaunchedEffect(filename) {
        if (mailId != null) {
            mailViewModel.readMail(mailId)
        } else {
            // If filename is not an integer, assume it's a sent mail filename
            mailViewModel.readSentMail(filename)
        }
    }

    // 解析邮件头获取回复信息
    var isReplyMail by remember { mutableStateOf(false) }
    var replyToMailInfo by remember { mutableStateOf<String?>(null) }
    var originalMailSubject by remember { mutableStateOf<String?>(null) }
    var isFromSentBox by remember { mutableStateOf(false) }  // 是否是发件箱邮件
    var showOriginalMailDialog by remember { mutableStateOf(false) }  // 显示原邮件弹窗
    var originalMailId by remember { mutableStateOf<Int?>(null) }  // POP3原邮件ID
    var originalMailFilename by remember { mutableStateOf<String?>(null) }  // 原邮件文件名

    LaunchedEffect(mailContent) {
        if (mailContent.isNotEmpty()) {
            val lines = mailContent.lines()
            var from = ""
            var subj = ""
            var inReplyTo: String? = null
            for (line in lines) {
                if (line.startsWith("From: ", ignoreCase = true)) {
                    from = line.substringAfter(":").trim()
                } else if (line.startsWith("Subject: ", ignoreCase = true)) {
                    subj = line.substringAfter(":").trim()
                } else if (line.startsWith("In-Reply-To: ", ignoreCase = true)) {
                    inReplyTo = line.substringAfter(":").trim()
                    isReplyMail = true
                }
                if (line.isEmpty()) break
            }
            // 提取邮箱地址
            if (from.contains("<") && from.contains(">")) {
                from = from.substringAfter("<").substringBefore(">")
            }
            // 如果域名是localhost，转换为mail.com（与系统配置保持一致）
            if (from.endsWith("@localhost")) {
                from = from.replace("@localhost", "@mail.com")
            }
            replyTo = from
            // 回复时保持原邮件主题（不添加"Re: "），需要获取原邮件主题
            replySubject = subj  // 临时设置，后续会从原邮件获取
            replyToMailInfo = inReplyTo

            // 判断是否是发件箱邮件（mailId为null表示是已发送邮件）
            isFromSentBox = (mailId == null)

            // 如果是回复邮件，获取原邮件主题（用于回复时保持原主题）
            if (inReplyTo != null && !inReplyTo.startsWith("POP3_MAIL_")) {
                // 普通邮件，通过文件名获取主题
                originalMailFilename = inReplyTo
                scope.launch {
                    val subject = mailViewModel.getOriginalMailSubject(inReplyTo)
                    // 获取原邮件主题（去掉"Re: "前缀），用于回复
                    val originalSubject = subject ?: subj
                    // 去掉"Re: "前缀（如果有）
                    val cleanSubject = if (originalSubject.startsWith("Re:", ignoreCase = true)) {
                        originalSubject.substringAfter(":").trim()
                    } else {
                        originalSubject
                    }
                    originalMailSubject = cleanSubject
                    replySubject = cleanSubject  // 回复时使用原主题，不添加"Re: "
                }
            } else if (inReplyTo != null && inReplyTo.startsWith("POP3_MAIL_")) {
                // POP3邮件，通过mailId获取主题
                val pop3MailId = inReplyTo.substringAfter("POP3_MAIL_").toIntOrNull()
                if (pop3MailId != null) {
                    originalMailId = pop3MailId
                    scope.launch {
                        val subject = mailViewModel.getPop3OriginalMailSubject(pop3MailId)
                        // 获取原邮件主题（去掉"Re: "前缀），用于回复
                        val originalSubject = subject ?: subj
                        // 去掉"Re: "前缀（如果有）
                        val cleanSubject = if (originalSubject.startsWith("Re:", ignoreCase = true)) {
                            originalSubject.substringAfter(":").trim()
                        } else {
                            originalSubject
                        }
                        originalMailSubject = cleanSubject
                        replySubject = cleanSubject  // 回复时使用原主题，不添加"Re: "
                    }
                } else {
                    originalMailSubject = inReplyTo
                    // 去掉"Re: "前缀（如果有）
                    val cleanSubject = if (subj.startsWith("Re:", ignoreCase = true)) {
                        subj.substringAfter(":").trim()
                    } else {
                        subj
                    }
                    replySubject = cleanSubject
                }
            } else {
                // 不是回复邮件，回复时使用当前主题
                replySubject = subj
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("邮件详情") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "返回")
                    }
                },
                actions = {
                    // 回复按钮 - 只在收件箱邮件中显示（不显示在发件箱中）
                    if (!isFromSentBox) {
                        IconButton(onClick = {
                            showReplyConfirmDialog = true
                        }) {
                            Icon(Icons.Default.Send, "回复")
                        }
                        // 快捷回复按钮（模板）
                        IconButton(onClick = {
                            mailViewModel.loadTemplates()
                            showTemplateDialog = true
                        }) {
                            Icon(Icons.Default.Edit, "快捷回复模板")
                        }
                    }
                    // Only show delete if it's an Inbox mail (mailId != null)
                    if (mailId != null) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, "删除")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Snackbar用于显示成功提示
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (error != null && mailContent.isEmpty()) {
                // 只在邮件内容为空时显示错误（避免对话框错误影响邮件显示）
                Text(
                    text = error ?: "加载失败",
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp),
                    color = MaterialTheme.colorScheme.error
                )
            } else if (mailContent.isNotEmpty() || error == null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    // 显示回复关系提示
                    if (isReplyMail && replyToMailInfo != null) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp)
                                .clickable {
                                    // 点击后查看原邮件内容（不显示对话）
                                    var currentSubject = ""
                                    val lines = mailContent.lines()
                                    for (line in lines) {
                                        if (line.startsWith("Subject:", ignoreCase = true)) {
                                            currentSubject = line.substringAfter(":").trim()
                                            break
                                        }
                                        if (line.isEmpty()) break
                                    }

                                    if (mailId == null) {
                                        // 已发送邮件，使用文件名获取原邮件
                                        val mailFilename = originalMailFilename ?: replyToMailInfo
                                        if (mailFilename != null && !mailFilename.startsWith("POP3_MAIL_")) {
                                            mailViewModel.loadOriginalMail(
                                                mailFilename,
                                                isPop3Mail = false,
                                                currentMailSubject = currentSubject
                                            )
                                        } else {
                                            // 如果是POP3邮件，尝试使用replyToMailInfo
                                            val replyToInfo = replyToMailInfo
                                            if (replyToInfo != null && replyToInfo.startsWith("POP3_MAIL_")) {
                                                val pop3MailId = replyToInfo.substringAfter("POP3_MAIL_").toIntOrNull()
                                                if (pop3MailId != null) {
                                                    mailViewModel.loadOriginalMail(
                                                        replyToInfo,
                                                        isPop3Mail = true,
                                                        pop3MailId = pop3MailId,
                                                        currentMailSubject = currentSubject
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        // POP3邮件，使用原邮件加载逻辑
                                        if (originalMailId != null) {
                                            val replyToInfo = replyToMailInfo
                                            if (replyToInfo != null) {
                                                mailViewModel.loadOriginalMail(
                                                    replyToInfo,
                                                    isPop3Mail = true,
                                                    pop3MailId = originalMailId,
                                                    currentMailSubject = currentSubject
                                                )
                                            }
                                        } else if (originalMailFilename != null) {
                                            mailViewModel.loadOriginalMail(
                                                originalMailFilename!!,
                                                isPop3Mail = false,
                                                currentMailSubject = currentSubject
                                            )
                                        }
                                    }
                                    showOriginalMailDialog = true
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Send,
                                    contentDescription = "回复",
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "这是对以下邮件的回复（点击查看）",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "原邮件: ${originalMailSubject ?: replyToMailInfo}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        text = filename,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Divider()

                    // 显示邮件内容，过滤掉 In-Reply-To 和 References 头
                    val filteredContent = remember(mailContent) {
                        mailContent.lines().filterNot { line ->
                            line.startsWith("In-Reply-To:", ignoreCase = true) ||
                            line.startsWith("References:", ignoreCase = true)
                        }.joinToString("\n")
                    }

                    Text(
                        text = filteredContent,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            }
        }
    }

    // 删除确认对话框
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("确认删除") },
            text = { Text("确定要删除这封邮件吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        mailId?.let { id ->
                            mailViewModel.deleteMail(id) {
                                showDeleteDialog = false
                                onNavigateBack()
                            }
                        }
                    }
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 模板选择对话框
    if (showTemplateDialog) {
        Dialog(onDismissRequest = { showTemplateDialog = false }) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "选择快捷回复模板",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    LazyColumn(
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                            items(templates) { template ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedTemplate = template
                                        replyContent = template.content
                                        showTemplateDialog = false
                                        // 如果还没有设置回复信息，先设置
                                        if (replyTo.isBlank()) {
                                            // 从邮件内容中提取发件人
                                            val lines = mailContent.lines()
                                            var from = ""
                                            var subj = ""
                                            for (line in lines) {
                                                if (line.startsWith("From: ", ignoreCase = true)) {
                                                    from = line.substringAfter(":").trim()
                                                } else if (line.startsWith("Subject: ", ignoreCase = true)) {
                                                    subj = line.substringAfter(":").trim()
                                                }
                                                if (line.isEmpty()) break
                                            }
                                            if (from.contains("<") && from.contains(">")) {
                                                from = from.substringAfter("<").substringBefore(">")
                                            }
                                            // 如果域名是localhost，转换为mail.com（与系统配置保持一致）
                                            if (from.endsWith("@localhost")) {
                                                from = from.replace("@localhost", "@mail.com")
                                            }
                                            replyTo = from
                                            replySubject = if (subj.startsWith("Re:", ignoreCase = true)) subj else "Re: $subj"
                                        }
                                        showReplyConfirmDialog = true
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = template.title, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        text = "[${template.category}] ${template.content}",
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = {
                                    templateToEdit = template
                                    showTemplateDialog = false
                                    showEditTemplateDialog = true
                                }) {
                                    Icon(Icons.Default.Edit, "编辑", modifier = Modifier.size(20.dp))
                                }
                            }
                            Divider()
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            templateToEdit = null // New template
                            showTemplateDialog = false
                            showEditTemplateDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("新建模板")
                    }
                }
            }
        }
    }

    // 编辑/新建模板对话框
    if (showEditTemplateDialog) {
        var title by remember { mutableStateOf(templateToEdit?.title ?: "") }
        var content by remember { mutableStateOf(templateToEdit?.content ?: "") }
        var category by remember { mutableStateOf(templateToEdit?.category ?: "通用") }

        AlertDialog(
            onDismissRequest = { showEditTemplateDialog = false },
            title = { Text(if (templateToEdit == null) "新建模板" else "编辑模板") },
            text = {
                Column {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("标题") },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = { Text("分类") },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it },
                        label = { Text("内容") },
                        modifier = Modifier.fillMaxWidth().height(100.dp),
                        maxLines = 5
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (title.isNotBlank() && content.isNotBlank()) {
                            mailViewModel.saveTemplate(title, content, category, templateToEdit?.id)
                            showEditTemplateDialog = false
                            showTemplateDialog = true // Return to selection
                        }
                    }
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                Row {
                    if (templateToEdit != null) {
                        TextButton(
                            onClick = {
                                templateToEdit?.let { mailViewModel.deleteTemplate(it.id) }
                                showEditTemplateDialog = false
                                showTemplateDialog = true
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("删除")
                        }
                    }
                    TextButton(onClick = {
                        showEditTemplateDialog = false
                        showTemplateDialog = true
                    }) {
                        Text("取消")
                    }
                }
            }
        )
    }

    // 回复确认对话框
    if (showReplyConfirmDialog) {
        var isSending by remember { mutableStateOf(false) }
        var sendError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = {
                if (!isSending) {
                    showReplyConfirmDialog = false
                    sendError = null  // 清除对话框错误状态
                    isSending = false  // 重置发送状态
                    // 不清除ViewModel的错误，避免影响邮件内容显示
                }
            },
            title = { Text("确认回复") },
            text = {
                Column {
                    Text("收件人: $replyTo", style = MaterialTheme.typography.bodySmall)
                    Text("主题: $replySubject", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = replyContent,
                        onValueChange = { replyContent = it },
                        label = { Text("回复内容") },
                        modifier = Modifier.fillMaxWidth().height(150.dp)
                    )
                    if (sendError != null) {
                        Text(
                            text = sendError!!,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    if (isSending) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isSending = true
                        sendError = null
                        // Use coroutine scope to call suspend function
                        // But since we are in Composable, we can't launch directly easily without scope
                        // We can use LaunchedEffect or a scope from composition
                    },
                    enabled = !isSending && replyTo.isNotBlank()
                ) {
                    Text("发送")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        // 只清除对话框相关状态，不影响邮件内容
                        showReplyConfirmDialog = false
                        sendError = null
                        isSending = false
                        // 不清除ViewModel的错误，因为可能影响邮件显示
                    },
                    enabled = !isSending
                ) {
                    Text("取消")
                }
            }
        )

        // Handle sending logic - 使用key来确保LaunchedEffect只在isSending变为true时触发一次
        LaunchedEffect(isSending) {
            if (isSending) {
                try {
                    // 使用回复邮件功能，传递原邮件标识
                    val replyToFilename = if (mailId != null) {
                        // POP3邮件，使用mailId
                        mailId.toString()
                    } else {
                        // 已发送邮件，使用文件名
                        filename
                    }
                    mailViewModel.replyMail(
                        replyTo,
                        replySubject,
                        replyContent,
                        replyToFilename,
                        isPop3Mail = (mailId != null)
                    )
                    showReplyConfirmDialog = false
                    sendError = null
                    isSending = false
                    // 显示成功提示
                    scope.launch {
                        snackbarHostState.showSnackbar("已发送！")
                    }
                } catch (e: Exception) {
                    sendError = e.message ?: "回复失败"
                    isSending = false
                }
            }
        }
    }

    // 原邮件查看弹窗（只显示原邮件内容，不显示对话）
    if (showOriginalMailDialog) {
        val originalContent by mailViewModel.originalMailContent.collectAsState()
        val originalLoading by mailViewModel.loading.collectAsState()
        val originalError by mailViewModel.error.collectAsState()

        Dialog(onDismissRequest = { showOriginalMailDialog = false }) {
            Surface(
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.9f),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // 标题栏
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { showOriginalMailDialog = false }) {
                            Icon(Icons.Default.ArrowBack, "返回")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "原邮件",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                    Divider()

                    // 内容区域
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        if (originalLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.align(Alignment.Center)
                            )
                        } else if (originalError != null && originalContent.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = originalError ?: "加载失败",
                                    color = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(onClick = { showOriginalMailDialog = false }) {
                                    Text("关闭")
                                }
                            }
                        } else if (originalContent.isNotEmpty()) {
                            // 只显示原邮件内容（过滤掉 In-Reply-To 和 References）
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(16.dp)
                            ) {
                                val filteredContent = originalContent.lines().filterNot { line ->
                                    line.startsWith("In-Reply-To:", ignoreCase = true) ||
                                    line.startsWith("References:", ignoreCase = true)
                                }.joinToString("\n")

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp)
                                    ) {
                                        Text(
                                            text = filteredContent,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
