package com.mailsystem.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mailsystem.data.model.Mail
import com.mailsystem.ui.viewmodel.AuthViewModel
import com.mailsystem.ui.viewmodel.MailViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    onMailClick: (String) -> Unit,
    onLogout: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToAdmin: () -> Unit,
    onCompose: () -> Unit,
    mailViewModel: MailViewModel = viewModel(),
    authViewModel: AuthViewModel = viewModel()
) {
    val mailList by mailViewModel.mailList.collectAsState()
    val loading by mailViewModel.loading.collectAsState()
    val error by mailViewModel.error.collectAsState()
    val role by authViewModel.role.collectAsState(initial = "user")
    val username by authViewModel.username.collectAsState(initial = "")
    
    LaunchedEffect(Unit) {
        mailViewModel.loadMailList()
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("收件箱 - $username") },
                actions = {
                    IconButton(onClick = { mailViewModel.loadMailList() }) {
                        Icon(Icons.Default.Refresh, "刷新")
                    }
                    if (role == "admin") {
                        IconButton(onClick = onNavigateToAdmin) {
                            Icon(Icons.Default.Settings, "管理")
                        }
                    }
                    IconButton(onClick = onNavigateToProfile) {
                        Icon(Icons.Default.AccountCircle, "个人资料")
                    }
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Default.ExitToApp, "登出")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCompose,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, "写邮件")
            }
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
                Card(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = error ?: "加载失败",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        Button(
                            onClick = { mailViewModel.loadMailList() },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("重试")
                        }
                    }
                }
            } else if (mailList.isEmpty()) {
                Card(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        text = "暂无邮件",
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(24.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(mailList) { mail ->
                        MailItem(mail = mail, onClick = { onMailClick(mail.mailId.toString()) })
                    }
                }
            }
        }
    }
}

@Composable
fun MailItem(mail: Mail, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = mail.filename,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${mail.size} 字节",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = mail.created,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
