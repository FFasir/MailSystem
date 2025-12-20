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
    var phone by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    val msg by viewModel.profileMessage.collectAsState()
    val err by viewModel.profileError.collectAsState()
    val phoneMsg by viewModel.phoneMessage.collectAsState()
    val phoneErr by viewModel.phoneError.collectAsState()
    val profile by viewModel.profile.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadProfile()
    }

    LaunchedEffect(profile) {
        profile?.let {
            username = it.username
            phone = it.phone_number ?: ""
        }
    }

    LaunchedEffect(msg, err) {
        // 若需要可在一定时间后清除提示
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("个人资料") },
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
            Text("个人资料", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("用户名") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("手机号") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = { viewModel.updateProfile(username, phone) },
                enabled = username.isNotBlank()
            ) { Text("保存资料") }
            phoneMsg?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            phoneErr?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Spacer(modifier = Modifier.height(24.dp))
            Text("修改密码", style = MaterialTheme.typography.titleMedium)
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
            ) { Text("保存密码") }
            msg?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}
