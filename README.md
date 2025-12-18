# 项目开发需求说明（可直接给开发者）

## 一、项目背景与目标

本项目为**计算机网络课程设计**，目标是：

- 设计并实现一个**基于 SMTP / POP3 协议的邮件服务系统**
- 包含 **邮件服务器端** 与 **Android 移动客户端**
- 重点考察：
  - 应用层协议（SMTP / POP3）
  - C/S 架构
  - Socket 网络编程
  - 客户端与服务器协作

------

## 二、技术选型（已确定，不可随意更改）

### 1️⃣ 后端（服务器端）

- **语言**：Python
- **Web 框架**：FastAPI
- **协议实现**：
  - SMTP：Python socket / asyncio（需自行实现基础协议流程）
  - POP3：Python socket / asyncio（需自行实现基础协议流程）
- **数据库**：MySQL 或 SQLite
- **运行环境**：Linux / Windows 均可

⚠ FastAPI **不直接实现 SMTP / POP3**，仅用于管理与业务接口。

------

### 2️⃣ 客户端（必须是 Android）

- **平台**：Android
- **语言**：Kotlin
- **客户端类型**：
  - 普通用户端
  - 管理员端
- **通信方式**：
  - HTTP（调用 FastAPI 接口）
  - 不直接实现 SMTP / POP3 协议

------

## 三、系统整体架构（必须遵守）

```
Android 客户端（Kotlin）
        ↓ HTTP
FastAPI 业务服务
        ↓
SMTP / POP3 服务模块（Python Socket）
        ↓
邮件 / 用户数据存储（DB + 文件）
```

------

## 四、功能需求（明确划分，按模块开发）

### （一）邮件服务器端功能

#### 1️⃣ 用户与权限管理

- 用户注册
- 用户登录
- 普通用户 / 管理员角色区分
- 管理员：
  - 查看用户列表
  - 删除 / 禁用用户
  - 修改管理员密码

------

#### 2️⃣ 邮件功能（核心）

- **SMTP**
  - 接收客户端发送的邮件
  - 保存邮件内容
- **POP3**
  - 用户认证
  - 邮件列表查询
  - 邮件内容获取
  - 删除邮件

⚠ 只需实现**最小可用子集协议命令**，不要求 RFC 全覆盖。

------

#### 3️⃣ 管理功能

- 群发邮件（管理员）
- 邮件地址过滤
- IP 地址过滤
- 服务器参数配置：
  - SMTP 端口（默认 25）
  - POP3 端口（默认 110）
  - 邮件域名
- 日志记录：
  - SMTP 日志
  - POP3 日志

------

### （二）Android 客户端功能

#### 1️⃣ 普通用户端

- 注册 / 登录
- 收件箱查看
- 阅读邮件
- 发送邮件
- 删除邮件
- 修改个人信息（密码等）

------

#### 2️⃣ 管理员端

- 群发邮件
- 查看用户信息
- 删除用户

------

## 五、非功能性要求（不要忽略）

- 系统结构清晰，模块解耦
- SMTP / POP3 协议逻辑独立
- 日志可用于说明书截图
- 功能**可演示、可截图**
- 不要求高并发、不要求安全加固

------

## 六、明确不做的事情（防止跑偏）

- ❌ 不接入真实邮件服务器（如 Gmail）
- ❌ 不调用系统自带 SMTP / POP3 服务
- ❌ 不只做 Web 客户端
- ❌ 不省略 Android 客户端

------

## 七、最低验收标准（保过线）

- Android 客户端能：
  - 登录
  - 发信
  - 收信
- SMTP / POP3：
  - 至少各 5 个基本命令可用
- 管理员：
  - 能群发
  - 能删用户
- 说明书中：
  - 有架构图
  - 有协议流程图
  - 有功能截图

---

## V0 启动说明（工程骨架）

本仓库已提供最小可运行骨架，涵盖 FastAPI 服务、SQLite 表初始化（`users`、`mails`）与 SMTP/POP3 占位监听。

### 依赖安装

```powershell
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
```

### 启动

```powershell
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

启动后：
- FastAPI：默认 `http://127.0.0.1:8000`
- 健康检查：`http://127.0.0.1:8000/health`
- SMTP 占位：`0.0.0.0:2525`（`SMTP_HOST` / `SMTP_PORT` 可改）
- POP3 占位：`0.0.0.0:8110`（`POP3_HOST` / `POP3_PORT` 可改）
- 数据库：`sqlite:///./data/app.db`（`DATABASE_URL` 可改）

### 验证

```powershell
telnet 127.0.0.1 2525   # SMTP 占位
telnet 127.0.0.1 8110   # POP3 占位
```

进入后可输入任意命令（如 `HELO` / `USER`），会返回占位响应；`QUIT` 关闭连接。

### 目录结构（关键文件）

```
app/
  main.py           # FastAPI 入口，启动时同时拉起 SMTP/POP3 占位服务
  config.py         # 端口与数据库配置（可用环境变量覆盖）
  db.py             # SQLite 引擎与表初始化
  models.py         # users、mails 表定义
  routers/health.py # /health 路由
  services/         # smtp_server.py、pop3_server.py 占位监听
```