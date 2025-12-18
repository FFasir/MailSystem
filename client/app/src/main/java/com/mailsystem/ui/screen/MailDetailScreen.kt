package com.mailsystem.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
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
    
    var showDeleteDialog by remember { mutableStateOf(false) }
    val mailId = filename.toIntOrNull() ?: 1
    
    LaunchedEffect(mailId) {
        mailViewModel.readMail(mailId)
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
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, "删除")
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
    
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("确认删除") },
            text = { Text("确定要删除这封邮件吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        mailViewModel.deleteMail(mailId) {
                            showDeleteDialog = false
                            onNavigateBack()
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
}
