package com.mailsystem.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mailsystem.data.model.User
import com.mailsystem.ui.viewmodel.AdminViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    onNavigateBack: () -> Unit,
    adminViewModel: AdminViewModel = viewModel()
) {
    val users by adminViewModel.users.collectAsState()
    val loading by adminViewModel.loading.collectAsState()
    val error by adminViewModel.error.collectAsState()
    val message by adminViewModel.message.collectAsState()
    val ipList by adminViewModel.ipBlacklist.collectAsState()
    val emailList by adminViewModel.emailBlacklist.collectAsState()
    
    var showBroadcastDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf<User?>(null) }
    var showResetPwdFor by remember { mutableStateOf<User?>(null) }
    
    val allMails by adminViewModel.allMails.collectAsState()
    val selectedMailContent by adminViewModel.selectedMailContent.collectAsState()
    var showMailDetailDialog by remember { mutableStateOf(false) }
    
    val appeals by adminViewModel.appeals.collectAsState()
    
    LaunchedEffect(Unit) {
        adminViewModel.loadUsers()
        adminViewModel.loadIpBlacklist()
        adminViewModel.loadEmailBlacklist()
        adminViewModel.loadAllMails()
        adminViewModel.loadAppeals()
    }
    
    LaunchedEffect(message) {
        message?.let {
            adminViewModel.clearMessage()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("管理面板") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { adminViewModel.loadUsers() }) {
                        Icon(Icons.Default.Refresh, "刷新")
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
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = error ?: "加载失败",
                        color = MaterialTheme.colorScheme.error
                    )
                    Button(
                        onClick = { adminViewModel.loadUsers() },
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text("重试")
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 所有用户邮件
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("所有邮件（共 ${allMails.size} 封）", style = MaterialTheme.typography.titleMedium)
                                if (allMails.isEmpty()) {
                                    Text("暂无邮件", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        allMails.take(10).forEach { mail ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { 
                                                        adminViewModel.viewMailContent(mail.username, mail.filename)
                                                        showMailDetailDialog = true 
                                                    }
                                                    .padding(vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(Modifier.weight(1f)) {
                                                    Text("${mail.username} - ${mail.filename}", style = MaterialTheme.typography.bodySmall)
                                                    Text("${mail.size} 字节", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                            if (mail != allMails.take(10).last()) {
                                                Divider()
                                            }
                                        }
                                    }
                                    if (allMails.size > 10) {
                                        Spacer(Modifier.height(8.dp))
                                        Text("显示前 10 封，共 ${allMails.size} 封", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                    
                    // 操作区：创建用户、群发、重载过滤器
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("管理员操作", style = MaterialTheme.typography.titleMedium)
                                // 创建用户表单
                                var newUsername by remember { mutableStateOf("") }
                                var newPassword by remember { mutableStateOf("") }
                                var roleExpanded by remember { mutableStateOf(false) }
                                var newRole by remember { mutableStateOf("user") }
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = newUsername,
                                        onValueChange = { newUsername = it },
                                        label = { Text("新用户邮箱/用户名") },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = newPassword,
                                        onValueChange = { newPassword = it },
                                        label = { Text("初始密码") },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    ExposedDropdownMenuBox(
                                        expanded = roleExpanded,
                                        onExpandedChange = { roleExpanded = it }
                                    ) {
                                        OutlinedTextField(
                                            value = if (newRole == "admin") "管理员" else "普通用户",
                                            onValueChange = {},
                                            readOnly = true,
                                            label = { Text("角色") },
                                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = roleExpanded) },
                                            modifier = Modifier.menuAnchor().fillMaxWidth()
                                        )
                                        ExposedDropdownMenu(expanded = roleExpanded, onDismissRequest = { roleExpanded = false }) {
                                            DropdownMenuItem(text = { Text("普通用户") }, onClick = { newRole = "user"; roleExpanded = false })
                                            DropdownMenuItem(text = { Text("管理员") }, onClick = { newRole = "admin"; roleExpanded = false })
                                        }
                                    }
                                    Button(onClick = {
                                        if (newUsername.isNotBlank() && newPassword.isNotBlank()) {
                                            adminViewModel.createUser(newUsername, newPassword, newRole)
                                            newUsername = ""; newPassword = ""; newRole = "user"
                                        }
                                    }) { Text("创建用户") }
                                }

                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { showBroadcastDialog = true }) { Text("群发邮件") }
                                    OutlinedButton(onClick = { adminViewModel.reloadFilters() }) { Text("重载过滤器") }
                                }
                            }
                        }
                    }

                    // 黑名单管理 - IP
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("IP 黑名单", style = MaterialTheme.typography.titleMedium)
                                var ipInput by remember { mutableStateOf("") }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = ipInput,
                                        onValueChange = { ipInput = it },
                                        label = { Text("新增 IP") },
                                        modifier = Modifier.weight(1f)
                                    )
                                    Button(onClick = {
                                        if (ipInput.isNotBlank()) {
                                            adminViewModel.addIp(ipInput)
                                            ipInput = ""
                                        }
                                    }) { Text("添加") }
                                }
                                if (ipList.isEmpty()) {
                                    Text("暂无 IP 黑名单", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else {
                                    ipList.forEach { ip ->
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Text(ip)
                                            TextButton(onClick = { adminViewModel.removeIp(ip) }) { Text("移除") }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 黑名单管理 - 邮箱
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("邮箱黑名单", style = MaterialTheme.typography.titleMedium)
                                var emailInput by remember { mutableStateOf("") }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = emailInput,
                                        onValueChange = { emailInput = it },
                                        label = { Text("新增邮箱") },
                                        modifier = Modifier.weight(1f)
                                    )
                                    Button(onClick = {
                                        if (emailInput.isNotBlank()) {
                                            adminViewModel.addEmail(emailInput)
                                            emailInput = ""
                                        }
                                    }) { Text("添加") }
                                }
                                if (emailList.isEmpty()) {
                                    Text("暂无邮箱黑名单", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else {
                                    emailList.forEach { email ->
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Text(email)
                                            TextButton(onClick = { adminViewModel.removeEmail(email) }) { Text("移除") }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 申诉管理
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("账号申诉 (${appeals.size})", style = MaterialTheme.typography.titleMedium)
                                if (appeals.isEmpty()) {
                                    Text("暂无待处理申诉", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else {
                                    appeals.forEach { appeal ->
                                        Card(
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(12.dp),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text("用户: ${appeal.username}", style = MaterialTheme.typography.titleSmall)
                                                Text("理由: ${appeal.reason}", style = MaterialTheme.typography.bodyMedium)
                                                Text("时间: ${appeal.created_at}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.End,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    TextButton(
                                                        onClick = { adminViewModel.rejectAppeal(appeal.id) },
                                                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                                    ) {
                                                        Text("拒绝")
                                                    }
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Button(
                                                        onClick = { adminViewModel.approveAppeal(appeal.id) }
                                                    ) {
                                                        Text("通过")
                                                    }
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }
                                }
                            }
                        }
                    }

                    // 用户列表
                    items(users) { user ->
                        UserItem(
                            user = user,
                            onDelete = { showDeleteDialog = user },
                            onToggleRole = {
                                val newRole = if (user.role == "admin") "user" else "admin"
                                adminViewModel.updateUserRole(user.id, newRole)
                            },
                            onDisable = { adminViewModel.disableUser(user.id) },
                            onEnable = { adminViewModel.enableUser(user.id) },
                            onResetPassword = { showResetPwdFor = user },
                            onForceLogout = { adminViewModel.forceLogout(user.id) }
                        )
                    }
                }
            }
        }
    }
    
    // 群发邮件对话框
    if (showBroadcastDialog) {
        BroadcastDialog(
            onDismiss = { showBroadcastDialog = false },
            onConfirm = { subject, body ->
                adminViewModel.broadcastMail(subject, body)
                showBroadcastDialog = false
            }
        )
    }
    
    // 删除用户对话框
    showDeleteDialog?.let { user ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("确认删除") },
            text = { Text("确定要删除用户 ${user.username} 吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        adminViewModel.deleteUser(user.id)
                        showDeleteDialog = null
                    }
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("取消")
                }
            }
        )
    }

    // 重置密码对话框
    showResetPwdFor?.let { user ->
        var newPwd by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showResetPwdFor = null },
            title = { Text("重置密码") },
            text = {
                Column { OutlinedTextField(value = newPwd, onValueChange = { newPwd = it }, label = { Text("新密码") }) }
            },
            confirmButton = {
                TextButton(onClick = { adminViewModel.resetUserPassword(user.id, newPwd); showResetPwdFor = null }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showResetPwdFor = null }) { Text("取消") } }
        )
    }
    
    // 邮件详情对话框
    if (showMailDetailDialog && selectedMailContent != null) {
        AlertDialog(
            onDismissRequest = { showMailDetailDialog = false; adminViewModel.clearMailContent() },
            title = { Text("邮件内容") },
            text = {
                LazyColumn {
                    item {
                        Text(selectedMailContent ?: "", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMailDetailDialog = false; adminViewModel.clearMailContent() }) {
                    Text("关闭")
                }
            }
        )
    }
    
    // 显示消息
    message?.let {
        Snackbar(
            modifier = Modifier.padding(16.dp),
            action = {
                TextButton(onClick = { adminViewModel.clearMessage() }) {
                    Text("关闭")
                }
            }
        ) {
            Text(it)
        }
    }
}

@Composable
fun UserItem(
    user: User,
    onDelete: () -> Unit,
    onToggleRole: () -> Unit,
    onDisable: () -> Unit,
    onEnable: () -> Unit,
    onResetPassword: () -> Unit,
    onForceLogout: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user.username,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "角色: ${if (user.role == "admin") "管理员" else "普通用户"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "ID: ${user.id}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (user.is_disabled == 1) "状态: 已禁用" else "状态: 正常",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (user.is_disabled == 1) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onToggleRole) { Text(if (user.role == "admin") "设为普通" else "设为管理员") }
                    if (user.is_disabled == 1) {
                        Button(onClick = onEnable) { Text("启用") }
                    } else {
                        OutlinedButton(onClick = onDisable) { Text("禁用") }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onResetPassword) { Text("重置密码") }
                    OutlinedButton(onClick = onForceLogout) { Text("强制下线") }
                    IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "删除", tint = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
}

@Composable
fun BroadcastDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var subject by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("群发邮件") },
        text = {
            Column {
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text("主题") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text("正文") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    maxLines = 5
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(subject, body) },
                enabled = subject.isNotBlank() && body.isNotBlank()
            ) {
                Text("发送")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
