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
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mailsystem.data.model.Mail
import com.mailsystem.ui.viewmodel.AuthViewModel
import com.mailsystem.ui.viewmodel.MailViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    onMailClick: (String) -> Unit,
    onLogout: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToAdmin: () -> Unit,
    onCompose: (String?) -> Unit,
    mailViewModel: MailViewModel = viewModel(),
    authViewModel: AuthViewModel = viewModel()
) {
    val mailList by mailViewModel.mailList.collectAsState()
    val sentMailList by mailViewModel.sentMailList.collectAsState()
    val draftList by mailViewModel.draftList.collectAsState()
    val loading by mailViewModel.loading.collectAsState()
    val error by mailViewModel.error.collectAsState()
    val role by authViewModel.role.collectAsState(initial = "user")
    val username by authViewModel.username.collectAsState(initial = "")
    
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var selectedBox by remember { mutableStateOf("Inbox") } // "Inbox", "Sent", or "Drafts"
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        mailViewModel.loadMailList()
    }
    
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "邮件系统",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.titleLarge
                )
                Divider()
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Email, null) },
                    label = { Text("收件箱") },
                    selected = selectedBox == "Inbox",
                    onClick = {
                        selectedBox = "Inbox"
                        scope.launch { drawerState.close() }
                        mailViewModel.loadMailList()
                        searchQuery = ""
                        isSearchActive = false
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Send, null) },
                    label = { Text("已发送") },
                    selected = selectedBox == "Sent",
                    onClick = {
                        selectedBox = "Sent"
                        scope.launch { drawerState.close() }
                        mailViewModel.loadSentMails()
                        searchQuery = ""
                        isSearchActive = false
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Edit, null) },
                    label = { Text("草稿箱") },
                    selected = selectedBox == "Drafts",
                    onClick = {
                        selectedBox = "Drafts"
                        scope.launch { drawerState.close() }
                        mailViewModel.loadDraftList()
                        searchQuery = ""
                        isSearchActive = false
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
            }
        },
        content = {
            Scaffold(
                topBar = {
                    if (isSearchActive) {
                        SearchBar(
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            onClose = { 
                                isSearchActive = false 
                                searchQuery = ""
                            }
                        )
                    } else {
                        TopAppBar(
                            title = { Text(if (selectedBox == "Inbox") "收件箱 - $username" else "已发送 - $username") },
                            navigationIcon = {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(Icons.Default.Menu, "菜单")
                                }
                            },
                            actions = {
                                IconButton(onClick = { isSearchActive = true }) {
                                    Icon(Icons.Default.Search, "搜索")
                                }
                                IconButton(onClick = { 
                                    if (selectedBox == "Inbox") mailViewModel.loadMailList() else mailViewModel.loadSentMails()
                                }) {
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
                    }
                },
                floatingActionButton = {
                    FloatingActionButton(
                        onClick = { onCompose(null) },
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
                    val currentList = when (selectedBox) {
                        "Inbox" -> mailList
                        "Sent" -> sentMailList
                        "Drafts" -> draftList
                        else -> emptyList()
                    }
                    val filteredList = if (searchQuery.isEmpty()) {
                        currentList
                    } else {
                        currentList.filter { 
                            it.filename.contains(searchQuery, ignoreCase = true) || 
                            it.created.contains(searchQuery, ignoreCase = true)
                        }
                    }

                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else if (error != null) {
                        ErrorView(
                            error = error!!, 
                            onRetry = { 
                                if (selectedBox == "Inbox") mailViewModel.loadMailList() else mailViewModel.loadSentMails() 
                            },
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else if (filteredList.isEmpty()) {
                        EmptyView(
                            message = if (searchQuery.isNotEmpty()) "未找到相关邮件" else "暂无邮件",
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(filteredList) { mail ->
                                MailItem(
                                    mail = mail,
                                    onClick = {
                                        if (selectedBox == "Drafts") {
                                            onCompose(mail.filename)
                                        } else {
                                            val id = if (selectedBox == "Inbox") mail.mailId.toString() else mail.filename
                                            onMailClick(id)
                                        }
                                    },
                                    onDelete = if (selectedBox == "Drafts") {
                                        {
                                            mailViewModel.deleteDraft(mail.filename)
                                        }
                                    } else null
                                )
                            }
                        }
                    }
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit
) {
    TopAppBar(
        title = {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("搜索邮件...") },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth()
            )
        },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, "关闭搜索")
            }
        }
    )
}

@Composable
fun ErrorView(error: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.padding(16.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = error,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Button(
                onClick = onRetry,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("重试")
            }
        }
    }
}

@Composable
fun EmptyView(message: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.padding(32.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Text(
            text = message,
            modifier = Modifier
                .padding(24.dp),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
fun MailItem(mail: Mail, onClick: () -> Unit, onDelete: (() -> Unit)? = null) {
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = mail.filename,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
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
            
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}