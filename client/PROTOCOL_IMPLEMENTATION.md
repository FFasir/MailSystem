# Android 客户端协议实现说明

## 协议架构

本 Android 客户端**直接实现了 SMTP 和 POP3 协议**，真正体现了"基于 SMTP/POP3 的邮件客户端"。

### 实现方式

```
┌─────────────────────────────────────────┐
│          Android 客户端                  │
├─────────────────────────────────────────┤
│  认证功能    →  HTTP REST API           │
│  用户管理    →  HTTP REST API           │
│  发送邮件    →  SMTP 协议（Socket）     │
│  收取邮件    →  POP3 协议（Socket）     │
│  删除邮件    →  POP3 协议（Socket）     │
└─────────────────────────────────────────┘
```

## 协议实现细节

### SMTP 客户端（发送邮件）

文件：`data/protocol/SmtpClient.kt`

**实现的命令：**
1. 连接并读取欢迎消息（220）
2. HELO - 握手
3. MAIL FROM - 设置发件人
4. RCPT TO - 设置收件人
5. DATA - 发送邮件内容
6. QUIT - 断开连接

**使用流程：**
```kotlin
val smtpClient = SmtpClient(host = "10.0.2.2", port = 2525)
val result = smtpClient.sendMail(
    fromAddr = "user1@localhost",
    toAddr = "user2@localhost",
    subject = "测试邮件",
    body = "这是邮件正文"
)
```

### POP3 客户端（收取邮件）

文件：`data/protocol/Pop3Client.kt`

**实现的命令：**
1. 连接并读取欢迎消息（+OK）
2. USER - 发送用户名
3. PASS - 发送密码
4. LIST - 获取邮件列表
5. RETR - 获取邮件内容
6. DELE - 标记删除邮件
7. QUIT - 执行删除并断开

**使用流程：**
```kotlin
val pop3Client = Pop3Client(host = "10.0.2.2", port = 8110)

// 获取邮件列表
val mailList = pop3Client.login(username, password)

// 读取邮件
val content = pop3Client.retrieveMail(username, password, mailId = 1)

// 删除邮件
val result = pop3Client.deleteMail(username, password, mailId = 1)
```

## 网络配置

### 模拟器访问本机

使用 `10.0.2.2` 作为主机地址，这是 Android 模拟器访问宿主机的特殊 IP。

```kotlin
// SmtpClient.kt
private val host: String = "10.0.2.2"
private val port: Int = 2525

// Pop3Client.kt
private val host: String = "10.0.2.2"
private val port: Int = 8110
```

### 真机测试

修改为局域网 IP：

```kotlin
private val host: String = "192.168.1.100"  // 替换为你的电脑 IP
```

## 数据流向

### 发送邮件
```
Android App
    ↓ (调用 SmtpClient.sendMail)
    ↓ Socket 连接到 10.0.2.2:2525
SMTP 服务器（Python）
    ↓ 接收 SMTP 命令
    ↓ 解析邮件内容
邮件存储（mailbox/用户名/*.txt）
```

### 收取邮件
```
Android App
    ↓ (调用 Pop3Client.login)
    ↓ Socket 连接到 10.0.2.2:8110
POP3 服务器（Python）
    ↓ 认证用户
    ↓ 返回邮件列表
    ↓ (调用 Pop3Client.retrieveMail)
    ↓ 返回邮件内容
Android App 显示邮件
```

## 课程设计要点

### ✅ 协议理解
- **真正实现了 SMTP/POP3 协议**
- 不是简单调用 HTTP API
- 展示了对应用层协议的深入理解

### ✅ Socket 编程
- 使用 Java Socket 进行网络通信
- 处理输入输出流
- 实现协议状态机

### ✅ C/S 架构
- 客户端主动发起连接
- 服务器被动监听
- 请求-响应模式

## 说明书截图建议

1. **代码截图**：
   - SmtpClient.kt 的 sendMail 方法
   - Pop3Client.kt 的 login 方法
   - 展示 Socket 连接和命令发送

2. **协议交互日志**：
   - 后端 logs/smtp_*.log
   - 后端 logs/pop3_*.log
   - 展示完整的协议交互过程

3. **应用界面**：
   - 发送邮件界面
   - 收件箱列表
   - 邮件详情

4. **架构图**：
   - 展示 Android → SMTP/POP3 → 服务器的数据流
   - 标注协议命令

## 与 HTTP API 的对比

| 功能 | 实现方式 | 原因 |
|------|----------|------|
| 登录/注册 | HTTP API | 需要 Token 管理，HTTP 更方便 |
| 用户管理 | HTTP API | 管理功能，HTTP RESTful 更适合 |
| **发送邮件** | **SMTP 协议** | **核心功能，体现协议实现** |
| **收取邮件** | **POP3 协议** | **核心功能，体现协议实现** |
| **删除邮件** | **POP3 协议** | **核心功能，体现协议实现** |

这种混合方式既满足了课程对协议实现的要求，又保持了应用的实用性。
