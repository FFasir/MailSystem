package com.mailsystem.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mailsystem.data.protocol.SmtpClient
import com.mailsystem.ui.viewmodel.MailViewModel
import kotlinx.coroutines.launch
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import java.text.SimpleDateFormat
import java.util.*

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
    
    // ========== 新增：附件管理 ==========
    var selectedFiles by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var isUploadingAttachments by remember { mutableStateOf(false) }
    var uploadProgress by remember { mutableStateOf(0) }
    var fileUploadError by remember { mutableStateOf("") }
    
    val context = LocalContext.current
    
    // 文件选择器
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            if (selectedFiles.size < 5) {
                selectedFiles = selectedFiles + it
            } else {
                fileUploadError = "最多只能添加5个附件"
            }
        }
    }

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
                    // ========== 新增：附件选择区域 ==========
                    // 附件按钮
                    Button(
                        onClick = { filePickerLauncher.launch("*/*") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        enabled = !isSending && !isUploadingAttachments && selectedFiles.size < 5
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "添加附件", modifier = Modifier.padding(end = 8.dp))
                        Text("添加附件(${selectedFiles.size}/5)")
                    }
                    
                    // 显示文件上传错误
                    if (fileUploadError.isNotBlank()) {
                        Text(
                            text = fileUploadError,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    
                    // 显示已选择的附件
                    if (selectedFiles.isNotEmpty()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline,
                                    RoundedCornerShape(8.dp)
                                ),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("已选择附件:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(8.dp))
                                selectedFiles.forEachIndexed { index, uri ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = uri.lastPathSegment ?: "文件$index",
                                            fontSize = 12.sp,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = {
                                                selectedFiles = selectedFiles.filterIndexed { i, _ -> i != index }
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "移除", modifier = Modifier.size(16.dp))
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
    
    // 发送邮件逻辑（包括附件上传）
    LaunchedEffect(isSending) {
        if (isSending) {
            scope.launch {
                try {
                    // 生成邮件文件名（客户端与服务端统一用于附件关联）
                    val mailFilename = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault()).format(Date()) + ".txt"

                    // 第1步：如果有附件，先上传附件（确保服务端发送时能复制/附带）
                    if (selectedFiles.isNotEmpty()) {
                        isUploadingAttachments = true

                        selectedFiles.forEachIndexed { index, uri ->
                            try {
                                // 读取文件内容
                                val fileInputStream = context.contentResolver.openInputStream(uri)
                                val fileContent = fileInputStream?.readBytes() ?: byteArrayOf()
                                fileInputStream?.close()
                                
                                // 获取文件名
                                val fileName = if (uri.scheme == "content") {
                                    val cursor = context.contentResolver.query(uri, arrayOf(android.provider.MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)
                                    cursor?.use {
                                        if (it.moveToFirst()) {
                                            it.getString(it.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.DISPLAY_NAME))
                                        } else {
                                            "attachment_${index + 1}"
                                        }
                                    } ?: "attachment_${index + 1}"
                                } else {
                                    uri.lastPathSegment ?: "attachment_${index + 1}"
                                }
                                
                                // 上传附件
                                uploadProgress = ((index + 1) * 100) / selectedFiles.size
                                mailViewModel.uploadAttachment(mailFilename, fileName, fileContent)
                            } catch (e: Exception) {
                                fileUploadError = "上传文件失败: ${e.message}"
                            }
                        }
                        isUploadingAttachments = false
                    }

                    // 第2步：发送邮件（携带统一的邮件文件名）
                    mailViewModel.sendMail(recipient, subject, content, mailFilename)
                    
                    // 第3步：删除草稿（如果存在）
                    if (currentDraftFilename != null) {
                        try {
                            mailViewModel.deleteDraft(currentDraftFilename!!)
                        } catch (e: Exception) {
                            // 忽略删除错误
                        }
                    }
                    
                    // 成功
                    isSending = false
                    selectedFiles = emptyList()  // 清空已选文件
                    uploadProgress = 0
                    fileUploadError = ""
                    showSuccessDialog = true
                } catch (e: Exception) {
                    isSending = false
                    isUploadingAttachments = false
                    errorMessage = e.message ?: "发送失败"
                    showErrorDialog = true
                }
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

    // 保存草稿成功对话框
    if (showSaveSuccessDialog) {
        AlertDialog(
            onDismissRequest = { showSaveSuccessDialog = false },
            title = { Text("保存成功", fontSize = 18.sp) },
            text = { Text("草稿已保存", fontSize = 14.sp) },
            confirmButton = {
                Button(
                    onClick = { showSaveSuccessDialog = false },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("确定")
                }
            }
        )
    }

    // 退出确认对话框（询问是否存草稿）
    if (showSaveDraftDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDraftDialog = false },
            title = { Text("保存草稿？", fontSize = 18.sp) },
            text = { Text("是否将未发送的邮件保存到草稿箱？", fontSize = 14.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        showSaveDraftDialog = false
                        // 执行保存并退出
                        isSaving = true
                        scope.launch {
                            try {
                                val result = mailViewModel.saveDraft(recipient, subject, content, currentDraftFilename)
                                if (result.isSuccess) {
                                    onBack() // 保存成功后退出
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
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showSaveDraftDialog = false
                        onBack() // 不保存直接退出
                    }
                ) {
                    Text("不保存")
                }
            }
        )
    }
}
