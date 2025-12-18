# Android 客户端测试指南

## 1. 前置条件

### 启动后端服务器
```bash
cd c:\Users\shao\Desktop\MailSystem
python -m app.main
```

服务器将监听：
- SMTP: `0.0.0.0:2525`
- POP3: `0.0.0.0:8110`
- HTTP API: `0.0.0.0:8000`

### 创建测试用户
使用 PowerShell 注册测试用户：
```powershell
$body = @{
    username = "testuser"
    password = "password123"
} | ConvertTo-Json

Invoke-WebRequest -Uri "http://localhost:8000/auth/register" -Method POST -Body $body -ContentType "application/json"
```

## 2. Android 配置

### 模拟器网络配置
- Android 模拟器访问宿主机使用 IP: `10.0.2.2`
- SmtpClient 连接: `10.0.2.2:2525`
- Pop3Client 连接: `10.0.2.2:8110`
- HTTP API: `10.0.2.2:8000`

### 真机网络配置
如果使用真机测试，需要：
1. 确保手机和电脑在同一局域网
2. 找到电脑的局域网 IP（例如 192.168.1.100）
3. 修改 RetrofitClient.kt 和协议客户端的地址

## 3. 功能测试

### 3.1 用户认证
1. 启动应用，进入登录界面
2. 输入用户名 `testuser` 和密码 `password123`
3. 点击登录，验证是否成功进入收件箱

### 3.2 发送邮件 (SMTP)
1. 在收件箱点击"写邮件"按钮
2. 填写：
   - 收件人：`admin1@mail.com` 或 `user1@mail.com`
   - 主题：测试邮件
   - 正文：这是一封测试邮件
3. 点击发送
4. 观察 logcat 日志中的 SMTP 通信过程
5. 检查后端 `logs/smtp.log` 查看 SMTP 命令序列

### 3.3 接收邮件 (POP3)
1. 使用另一个账户发送邮件给当前用户
2. 在收件箱点击刷新按钮
3. 验证邮件列表是否更新
4. 观察 logcat 日志中的 POP3 通信过程
5. 检查后端 `logs/pop3.log` 查看 POP3 命令序列

### 3.4 查看邮件
1. 点击收件箱中的任一邮件
2. 验证是否正确显示发件人、主题、日期和正文
3. 观察 POP3 RETR 命令的执行

### 3.5 删除邮件
1. 在邮件详情页点击删除按钮
2. 确认删除对话框
3. 验证邮件是否从列表中移除
4. 观察 POP3 DELE 命令的执行

## 4. 调试方法

### Android Studio Logcat
查看关键日志标签：
```
SmtpClient
Pop3Client
MailRepository
MailViewModel
```

### 后端日志
查看协议交互详情：
```bash
# SMTP 日志
cat logs/smtp.log

# POP3 日志
cat logs/pop3.log
```

### 邮件文件
检查邮件是否正确存储：
```bash
ls mailbox/testuser/
cat mailbox/testuser/20251214_*.txt
```

## 5. 常见问题

### 连接失败
- **问题**: 无法连接到服务器
- **解决**: 
  1. 确认后端服务器已启动
  2. 检查防火墙是否允许端口 2525、8110、8000
  3. 模拟器使用 `10.0.2.2`，真机使用局域网 IP

### 登录失败
- **问题**: POP3 认证失败
- **解决**:
  1. 确认用户名密码正确
  2. 检查 UserPreferences 是否正确存储密码
  3. 查看 pop3.log 中的认证日志

### 邮件发送失败
- **问题**: SMTP 发送失败
- **解决**:
  1. 检查收件人地址格式（必须是 `username@mail.com`）
  2. 查看 smtp.log 中的错误信息
  3. 验证发件人是否在黑名单中

### 邮件列表为空
- **问题**: 收件箱没有邮件
- **解决**:
  1. 检查 `mailbox/{username}/` 目录是否有文件
  2. 查看 pop3.log 中的 LIST 命令响应
  3. 确认 POP3 认证成功

## 6. 协议交互示例

### SMTP 发送流程
```
客户端: HELO mail.com
服务器: 250 Hello mail.com
客户端: MAIL FROM:<testuser@mail.com>
服务器: 250 OK
客户端: RCPT TO:<admin1@mail.com>
服务器: 250 OK
客户端: DATA
服务器: 354 Start mail input
客户端: From: testuser@mail.com
客户端: To: admin1@mail.com
客户端: Subject: Test
客户端: 
客户端: This is a test.
客户端: .
服务器: 250 OK
客户端: QUIT
服务器: 221 Bye
```

### POP3 接收流程
```
客户端: USER testuser
服务器: +OK User accepted
客户端: PASS password123
服务器: +OK Mailbox locked and ready
客户端: STAT
服务器: +OK 3 1500
客户端: LIST
服务器: +OK 3 messages (1500 octets)
服务器: 1 500
服务器: 2 600
服务器: 3 400
服务器: .
客户端: RETR 1
服务器: +OK 500 octets
服务器: [邮件内容]
服务器: .
客户端: DELE 1
服务器: +OK Message deleted
客户端: QUIT
服务器: +OK Bye
```

## 7. APK 构建

### Debug 版本
```bash
cd client
./gradlew assembleDebug
```
输出: `app/build/outputs/apk/debug/app-debug.apk`

### Release 版本
```bash
./gradlew assembleRelease
```
输出: `app/build/outputs/apk/release/app-release-unsigned.apk`

注意：Release 版本需要签名才能安装。
