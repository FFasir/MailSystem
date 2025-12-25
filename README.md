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

---

## 管理员操作指南

本项目对管理员接口增加了更严格的校验：除要求管理员账号登录外，后端可选启用 `X-Admin-Key` 头进行二次校验。以下是从环境配置到前端使用的完整流程。

### 1. 服务端配置（必读）

- 环境变量（在启动 FastAPI 前设置）：
  - `TOKEN_SECRET`：JWT 签名密钥（强随机，必填）
  - `TOKEN_EXPIRE_MINUTES`：JWT 过期分钟数（可选，默认如配置文件）
  - `TOKEN_ALGORITHM`：JWT 算法（可选，默认 HS256）
  - `ADMIN_ACCESS_KEY`：管理员额外访问密钥（可选，强烈推荐配置）。配置后，所有 `/admin/*` 接口将要求请求头携带 `X-Admin-Key: <该值>`。

> 注意：`ADMIN_ACCESS_KEY` 未配置时，管理员接口只校验角色；配置后，必须同时满足“管理员角色 + 正确的 X-Admin-Key”。

### 2. 初始化管理员账号

有两种方式创建管理员：

- 方式 A（首次引导）：
  1) 先设置环境变量 `ADMIN_ACCESS_KEY` 并启动后端。
  2) 通过移动端“注册”页勾选“注册为管理员”创建首个管理员账号并登录。

- 方式 B（已有管理员后）：
  1) 使用已登录的管理员进入“管理面板”。
  2) 在“管理员操作”卡片中填写新用户信息，角色选择“管理员”，点击“创建用户”。

> 可选的更安全策略：禁止任何人在注册页创建管理员，仅允许通过管理员后台创建。若需要，我们可以将注册页隐藏“注册为管理员”选项，并在后端限制 `/auth/register` 仅接受 `role=user`。

### 3. 客户端（Android）使用步骤

- 登录为管理员后进入“收件箱”页面，右上角齿轮图标进入“管理面板”。
- 在“管理面板”顶部可以看到“管理员密钥”卡片：
  - 粘贴服务端配置的 `ADMIN_ACCESS_KEY`，点击“保存”。
  - 保存后，客户端会把该密钥持久化，后续所有 `/admin/*` 请求会自动携带 `X-Admin-Key` 头。

> 代码位置：
> - 管理界面 UI：`client/app/src/main/java/com/mailsystem/ui/screen/AdminScreen.kt`
> - 保存密钥逻辑：`client/app/src/main/java/com/mailsystem/ui/viewmodel/AdminViewModel.kt` 的 `setAdminKey()`
> - 本地持久化：`client/app/src/main/java/com/mailsystem/data/local/UserPreferences.kt`
> - 接口调用：`client/app/src/main/java/com/mailsystem/data/repository/MailRepository.kt`（所有 admin 请求会读出并传入 `X-Admin-Key`）

### 4. 管理员常用操作

- 用户管理：
  - 查看用户列表
  - 创建用户（可选管理员角色）
  - 更新角色（普通/管理员）
  - 禁用/启用账号（禁用会强制下线）
  - 重置用户密码（可选强制下线）
  - 强制用户下线
  - 删除用户

- 群发邮件：
  - 在“管理员操作”中选择全部或部分用户发送系统通知。

- 黑名单管理：
  - IP 黑名单：添加/移除，命中将被拒绝
  - 邮箱黑名单：添加/移除，命中会被过滤
  - 重新加载过滤器：应用最新黑名单文件

- 邮件查看（仅管理员）：
  - 浏览所有用户的邮件列表
  - 查看任意邮件内容（用于排查问题）

- 申诉处理：
  - 查看用户申诉列表
  - 通过/拒绝申诉

### 5. 接口头与返回

- 所有管理员接口必须带：
  - `Authorization: Bearer <JWT>`（管理员账号登录后获得）
  - `X-Admin-Key: <ADMIN_ACCESS_KEY>`（当服务端配置了该项时必需）

- 普通接口只需 `Authorization`：
  - 登录之后客户端自动携带，用于访问用户资料、收件箱、草稿、附件等。

### 6. 忘记密码与安全策略

- 忘记密码只能在绑定手机号后进行：
  - 登录→个人资料→绑定手机号；
  - 登录页“忘记密码”→申请验证码→重置密码。
- 认证：采用 bcrypt 密码哈希 + JWT（含签名与过期），过期需重新登录。
 - 登录失败保护：
   - 连续登录失败将触发短期锁定，默认锁定 `LOGIN_LOCKOUT_MINUTES` 分钟；
   - 每次失败后有 `LOGIN_COOLDOWN_SECONDS` 秒的冷却时间，期间拒绝再次尝试；
   - 可用环境变量调整：`LOGIN_MAX_ATTEMPTS`、`LOGIN_LOCKOUT_MINUTES`、`LOGIN_COOLDOWN_SECONDS`。

### 7. 常见故障排查

- 管理面板操作返回 403：
  - 确认当前登录账号角色为管理员；
  - 若后端已配置 `ADMIN_ACCESS_KEY`，确保在“管理员密钥”卡片中正确保存密钥；
  - 重启客户端后重试（确保密钥已持久化）。

- 管理面板入口看不到“齿轮”图标：
  - 只有管理员角色的登录用户会显示入口；
  - 使用管理员账号重新登录。

- 忘记密码拒绝：
  - 需要先绑定手机号；若后端短信网关未配置，也会失败。

---

如需将“注册页允许选择管理员”改为“仅后台创建管理员”，请提交 Issue 或直接联系维护者，我们可以为你切换这一策略并更新文档。