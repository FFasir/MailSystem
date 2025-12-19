package com.mailsystem.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mailsystem.data.protocol.SmtpClient  // 新增：导入SMTP客户端
import com.mailsystem.ui.viewmodel.MailViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeScreen(
    onBack: () -> Unit,
    onSent: () -> Unit,
    initialTo: String = "",
    initialSubject: String = "",
    initialContent: String = "",
    draftFilename: String? = null,
    mailViewModel: MailViewModel = viewModel()
) {
    var recipient by remember { mutableStateOf(initialTo) }
    var subject by remember { mutableStateOf(initialSubject) }
    var content by remember { mutableStateOf(initialContent) }
    var isSending by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var showErrorDialog by remember { mutableStateOf(false) }
    var showSaveDraftDialog by remember { mutableStateOf(false) }
    var showSaveSuccessDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var currentDraftFilename by remember { mutableStateOf(draftFilename) }

    // Check if content has changed from initial values
    val hasChanges by derivedStateOf {
        recipient != initialTo || subject != initialSubject || content != initialContent
    }

    // ========== 新增：SMTP客户端实例（用于格式校验） ==========
    val smtpClient = remember { SmtpClient() }
    // ========== 新增：实时校验收件人格式 ==========
    val isRecipientValid by derivedStateOf {
        smtpClient.isEmailValid(recipient)
    }

    val scope = rememberCoroutineScope()

    // Save Draft Logic
    val saveDraft = {
        isSaving = true
        scope.launch {
            try {
                val result = mailViewModel.saveDraft(recipient, subject, content, currentDraftFilename)
                if (result.isSuccess) {
                    currentDraftFilename = result.getOrNull()
                    showSaveSuccessDialog = true
                } else {
                    errorMessage = result.exceptionOrNull()?.message ?: "保存草稿失败"
                    showErrorDialog = true
                }
            } catch (e: Exception) {
                errorMessage = e.message ?: "保存草稿失败"
                showErrorDialog = true
            } finally {
                isSaving = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (currentDraftFilename != null) "编辑草稿" else "写邮件") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (hasChanges && !showSuccessDialog) {
                            showSaveDraftDialog = true
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.Default.ArrowBack, "返回")
                    }
                },
                actions = {
                    // Save Draft Button
                    TextButton(
                        onClick = { saveDraft() },
                        enabled = !isSending && !isSaving
                    ) {
                        Text("存草稿")
                    }

                    IconButton(
                        onClick = {
                            if (recipient.isNotBlank() && subject.isNotBlank() && content.isNotBlank()) {
                                isSending = true
                            }
                        },
                        // ========== 修改：发送按钮启用条件 → 格式正确+内容非空 ==========
                        enabled = !isSending && isRecipientValid && subject.isNotBlank() && content.isNotBlank()
                        //enabled = !isSending && recipient.isNotBlank() && subject.isNotBlank() && content.isNotBlank()
                    ) {
                        Icon(Icons.Default.Send, "发送")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // 邮件表单卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    // 收件人
                    OutlinedTextField(
                        value = recipient,
                        onValueChange = { recipient = it },
                        label = { Text("收件人") },
                        placeholder = { Text("username@example.com") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        shape = RoundedCornerShape(8.dp),
                        enabled = !isSending,
                        singleLine = true,
                        // ========== 新增：格式错误时标红 + 显示错误提示 ==========
                        isError = recipient.isNotBlank() && !isRecipientValid,
                        supportingText = {
                            if (recipient.isNotBlank() && !isRecipientValid) {
                                Text(
                                    text = "请输入有效的邮箱格式（如：xxx@xxx.xxx）",
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    )
                    
                    // 主题
                    OutlinedTextField(
                        value = subject,
                        onValueChange = { subject = it },
                        label = { Text("主题") },
                        placeholder = { Text("请输入邮件主题") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        shape = RoundedCornerShape(8.dp),
                        enabled = !isSending,
                        singleLine = true
                    )
                    
                    // 内容
                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it },
                        label = { Text("内容") },
                        placeholder = { Text("请输入邮件内容") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .padding(bottom = 16.dp),
                        shape = RoundedCornerShape(8.dp),
                        enabled = !isSending,
                        maxLines = 12
                    )
                    
                    if (isSending) {
                        Spacer(modifier = Modifier.height(12.dp))
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
    
    // 发送邮件逻辑
    LaunchedEffect(isSending) {
        if (isSending) {
            try {
                mailViewModel.sendMail(recipient, subject, content)
                // If sent successfully, delete the draft if it exists
                if (currentDraftFilename != null) {
                    try {
                        mailViewModel.deleteDraft(currentDraftFilename!!)
                    } catch (e: Exception) {
                        // Ignore deletion error, sending was successful
                    }
                }
                isSending = false
                showSuccessDialog = true
            } catch (e: Exception) {
                isSending = false
                errorMessage = e.message ?: "发送失败"
                showErrorDialog = true
            }
        }
    }
    
    // 成功对话框
    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("发送成功", fontSize = 18.sp) },
            text = { Text("邮件已成功发送给 $recipient", fontSize = 14.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        showSuccessDialog = false
                        onSent()
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("确定")
                }
            }
        )
    }
    
    // 错误对话框
    if (showErrorDialog) {
        AlertDialog(
            onDismissRequest = { showErrorDialog = false },
            title = { Text("发送失败", fontSize = 18.sp) },
            text = { Text(errorMessage, fontSize = 14.sp) },
            confirmButton = {
                Button(
                    onClick = { showErrorDialog = false },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("确定")
                }
            }
        )
    }
}
