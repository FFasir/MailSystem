package com.mailsystem.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mailsystem.ui.viewmodel.AuthViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    viewModel: AuthViewModel = viewModel()
) {
    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    val msg by viewModel.profileMessage.collectAsState()
    val err by viewModel.profileError.collectAsState()

    LaunchedEffect(msg, err) {
        // 若需要可在一定时间后清除提示
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("修改密码") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = oldPassword,
                onValueChange = { oldPassword = it },
                label = { Text("旧密码") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = newPassword,
                onValueChange = { newPassword = it },
                label = { Text("新密码") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = { viewModel.changeMyPassword(oldPassword, newPassword) },
                enabled = oldPassword.isNotBlank() && newPassword.isNotBlank()
            ) {
                Text("保存")
            }
            msg?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}
