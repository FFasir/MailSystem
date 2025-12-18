# 邮件系统开发任务清单（AI 开发 + 分版本验收）

## 总体技术前提（每一版都默认）

- 后端：**Python + FastAPI**
- 协议：**SMTP / POP3（socket 实现）**
- 客户端：**Android（Kotlin）**
- 数据库：SQLite（先简单）
- 邮件存储：文件系统（.txt / .eml）

------

# ✅ V0 — 项目骨架 & 能跑（地基版）

### 🎯 目标

**不实现任何业务，只保证工程结构 + 服务能启动**

### 开发任务

- 创建 FastAPI 项目结构
- 配置基础路由：
  - `/health`
- 初始化数据库（users、mails 表）
- 创建 SMTP / POP3 端口监听空服务（仅 accept）

### 验收标准

- FastAPI 启动无报错
- 能访问 `/health`
- SMTP / POP3 端口能 `telnet` 连上

### 给 AI 的指令示例

> 用 FastAPI 搭一个最小项目，包含 SMTP/POP3 socket 监听，占位即可，不实现逻辑。

------

# ✅ V1 — 用户系统 + HTTP 管理接口（可截图）

### 🎯 目标

**先做“管理功能”，别急着做协议**

### 开发任务

#### 后端

- 用户表：
  - id, username, password, role
- 接口：
  - POST `/auth/register`
  - POST `/auth/login`
  - GET `/admin/users`
  - DELETE `/admin/users/{id}`
- 简单 token（不用 JWT 也行）

#### 客户端

- Android：
  - 登录页
  - 注册页
  - 用户列表页（管理员）

### 验收标准

- 能注册 / 登录
- 管理员能看到用户列表
- 能删除用户
- 数据库变化可验证

------

# ✅ V2 — SMTP 收信 + 邮件落盘（核心协议 1）

### 🎯 目标

**真正“实现协议”的开始**

### SMTP 实现（最小集）

- 支持命令：
  - HELO / EHLO
  - MAIL FROM
  - RCPT TO
  - DATA
  - QUIT
- 邮件保存为：
  - `mailbox/{username}/xxx.txt`

### 开发任务

- SMTP socket 服务
- 邮件存储模块
- SMTP 日志

### 验收方式（关键）

```
telnet localhost 25
```

手工发一封邮件成功

### 验收标准

- 邮件文件成功生成
- SMTP 日志可查看
- FastAPI 无异常

------

# ✅ V3 — POP3 收信 + Android 收件箱（核心协议 2）

### 🎯 目标

**能“收邮件”= 项目成型**

### POP3 实现（最小集）

- USER
- PASS
- LIST
- RETR
- DELE
- QUIT

### 开发任务

- POP3 socket 服务
- 邮件索引管理
- FastAPI 封装 POP3 调用接口

### Android

- 收件箱列表
- 阅读邮件
- 删除邮件

### 验收标准

- Android 能看到邮件列表
- 能点开邮件内容
- 删除后服务器文件消失

------

# ✅ V4 — 管理功能 + 群发 + 过滤（加分项）

### 🎯 目标

**满足“课程说明书”所有文字要求**

### 开发任务

#### 管理功能

- 群发邮件接口
- IP 黑名单
- 邮箱地址过滤
- 管理员修改密码

#### 系统增强

- 配置文件（端口、域名）
- 日志模块

### 验收标准

- 管理员能群发
- 非法 IP 被拒绝
- 日志可截图

------

# 🚨 必须停在这里（不要继续）

- 不做 HTTPS
- 不做并发优化
- 不做真正邮件 RFC

------

## 📦 最终交付清单（你要的）

- ✅ Android APK
- ✅ FastAPI 项目代码
- ✅ SMTP / POP3 协议代码
- ✅ 数据库文件
- ✅ 实验说明书截图素材