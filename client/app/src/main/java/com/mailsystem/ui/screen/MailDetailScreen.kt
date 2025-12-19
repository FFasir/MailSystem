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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MailDetailScreen(
    filename: String,  // 实际上是 mailId 的字符串形式
    onNavigateBack: () -> Unit,
    mailViewModel: MailViewModel = viewModel()
) {
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
    LaunchedEffect(mailContent) {
        if (mailContent.isNotEmpty()) {
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
            // 提取邮箱地址
            if (from.contains("<") && from.contains(">")) {
                from = from.substringAfter("<").substringBefore(">")
            }
            replyTo = from
            replySubject = if (subj.startsWith("Re:", ignoreCase = true)) subj else "Re: $subj"
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
                    // 快捷回复按钮
                    IconButton(onClick = { 
                        mailViewModel.loadTemplates()
                        showTemplateDialog = true 
                    }) {
                        Icon(Icons.Default.Send, "快捷回复")
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
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (error != null) {
                Text(
                    text = error ?: "加载失败",
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp),
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    Text(
                        text = filename,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    Divider()
                    
                    Text(
                        text = mailContent,
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
            onDismissRequest = { if (!isSending) showReplyConfirmDialog = false },
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
                    onClick = { showReplyConfirmDialog = false },
                    enabled = !isSending
                ) {
                    Text("取消")
                }
            }
        )
        
        // Handle sending logic
        if (isSending) {
            LaunchedEffect(Unit) {
                try {
                    mailViewModel.sendMail(replyTo, replySubject, replyContent)
                    showReplyConfirmDialog = false
                    // Optionally show success toast or message
                } catch (e: Exception) {
                    sendError = e.message ?: "发送失败"
                    isSending = false
                }
            }
        }
    }
}
