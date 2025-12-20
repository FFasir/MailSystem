# 华为云标准邮箱系统部署指南

## 完整部署步骤

### 一、准备阶段

#### 1. 购买域名（华为云）
```
成本：¥50-100/年（用华为云额度）
推荐：mailsystem.com 或 yourdomain.com
```

#### 2. 购买ECS云服务器
```
规格：s6.medium (2核4GB)
系统：Ubuntu 20.04 LTS
存储：40-50GB
公网IP：自动分配
费用：用华为云额度
```

#### 3. 配置安全组（防火墙）
```
入站规则：
- TCP 22: SSH
- TCP 25: SMTP (接收)
- TCP 587: SMTP (发送)
- TCP 110: POP3
- TCP 143: IMAP
- TCP 993: IMAP+SSL
- TCP 995: POP3+SSL
- TCP 443: HTTPS
- TCP 8000: API服务器
```

---

### 二、DNS配置（华为云云解析）

#### 1. 基础记录
```
A 记录:
  主机记录: @
  记录值: 你的ECS公网IP
  TTL: 3600

A 记录:
  主机记录: mail
  记录值: 你的ECS公网IP
  TTL: 3600
```

#### 2. 邮件相关记录
```
MX 记录:
  主机记录: @
  记录值: mail.yourdomain.com
  优先级: 10
  TTL: 3600

SPF 记录:
  主机记录: @
  类型: TXT
  记录值: v=spf1 mx ~all
  TTL: 3600
```

#### 3. DKIM记录（生成后添加）
```
参考第三步中的DKIM生成
```

---

### 三、服务器部署

#### Step 1: 连接服务器
```bash
ssh root@你的公网IP
```

#### Step 2: 基础环境
```bash
# 更新系统
apt update && apt upgrade -y

# 安装必要工具
apt install -y python3 python3-pip python3-venv git
apt install -y build-essential libssl-dev libffi-dev
apt install -y supervisor  # 进程管理
apt install -y nginx       # Web服务器（可选）
```

#### Step 3: 申请SSL证书
```bash
# 安装certbot
apt install -y certbot

# 申请证书（替换域名）
certbot certonly --standalone -d yourdomain.com -d mail.yourdomain.com

# 证书位置：
# /etc/letsencrypt/live/yourdomain.com/
```

#### Step 4: 部署应用
```bash
# 创建目录
mkdir -p /opt/mailsystem
cd /opt/mailsystem

# 上传项目代码（3种方式选一）
# 方式A: Git克隆
git clone <你的项目> .

# 方式B: SCP上传
# 在本地运行: scp -r MailSystem1/* root@IP:/opt/mailsystem/

# 方式C: 手动上传

# 创建Python虚拟环境
python3 -m venv venv
source venv/bin/activate

# 安装依赖
pip install -r requirements.txt

# 创建必要目录
mkdir -p logs mailbox data filters
```

#### Step 5: 配置 .env
```bash
cat > /opt/mailsystem/.env <<'EOF'
# 数据库
DATABASE_URL=sqlite:///./data/app.db
MAIL_DOMAIN=yourdomain.com

# SMTP 配置（标准端口）
SMTP_HOST=0.0.0.0
SMTP_PORT=25
SMTP_USE_SSL=false

# POP3 配置
POP3_HOST=0.0.0.0
POP3_PORT=110

# IMAP 配置
IMAP_HOST=0.0.0.0
IMAP_PORT=143

# SSL证书（可选，用于SMTPS）
# SMTP_SSL_CERT=/etc/letsencrypt/live/yourdomain.com/fullchain.pem
# SMTP_SSL_KEY=/etc/letsencrypt/live/yourdomain.com/privkey.pem

# SMS（可禁用）
SMS_ENABLED=false

# IMAP同步（可禁用）
IMAP_SYNC_ENABLED=false
EOF
```

#### Step 6: 使用 Systemd 运行应用
```bash
# 创建服务文件
cat > /etc/systemd/system/mailsystem.service <<'EOF'
[Unit]
Description=Mail System API Server
After=network.target

[Service]
Type=simple
User=root
WorkingDirectory=/opt/mailsystem
Environment="PATH=/opt/mailsystem/venv/bin"
ExecStart=/opt/mailsystem/venv/bin/python3 -m uvicorn app.main:app --host 0.0.0.0 --port 8000
Restart=always
RestartSec=10
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
EOF

# 启动服务
systemctl daemon-reload
systemctl start mailsystem
systemctl enable mailsystem

# 查看状态
systemctl status mailsystem

# 查看日志
journalctl -u mailsystem -f
```

#### Step 7: 测试服务
```bash
# 测试API是否运行
curl http://localhost:8000/health

# 测试SMTP端口是否开放
telnet localhost 25

# 查看进程
ps aux | grep uvicorn
```

---

### 四、客户端配置修改

#### 1. 修改后端API地址
文件：`client/app/src/main/java/com/mailsystem/data/api/RetrofitClient.kt`

```kotlin
private const val BASE_URL = "http://你的公网IP:8000/"
// 或
private const val BASE_URL = "http://yourdomain.com:8000/"
```

#### 2. 修改SMTP/POP3地址
文件：`client/app/src/main/java/com/mailsystem/data/protocol/SmtpClient.kt`

```kotlin
private val host: String = "你的公网IP",
private val port: Int = 25
```

文件：`client/app/src/main/java/com/mailsystem/data/protocol/Pop3Client.kt`

```kotlin
private val host: String = "你的公网IP",
private val port: Int = 110
```

#### 3. 重新编译APK
```
Android Studio -> Build -> Build Bundle(s) / APK(s)
```

---

### 五、功能验证

#### 测试本地邮件发送
```bash
# 创建用户
curl -X POST http://localhost:8000/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"user1@yourdomain.com","password":"123456"}'

curl -X POST http://localhost:8000/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"user2@yourdomain.com","password":"123456"}'

# 登录获取token
TOKEN=$(curl -s -X POST http://localhost:8000/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"user1@yourdomain.com","password":"123456"}' | jq '.access_token')

# 发送邮件（本地）
curl -X POST http://localhost:8000/mail/send \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "to_addr":"user2@yourdomain.com",
    "subject":"测试邮件",
    "body":"这是一封测试邮件"
  }'
```

#### 测试外部邮件接收
```bash
# 从你的qq邮箱或163邮箱发送邮件到 user1@yourdomain.com
# 检查是否收到（通过POP3或API）
```

---

### 六、故障排查

#### SMTP端口无法连接
```bash
# 检查防火墙
sudo ufw status
sudo ufw allow 25/tcp

# 检查应用是否运行
systemctl status mailsystem

# 检查端口是否监听
netstat -tlnp | grep 25
```

#### DNS问题
```bash
# 验证MX记录
nslookup -type=MX yourdomain.com

# 验证A记录
nslookup yourdomain.com

# 测试邮件可达性
telnet yourdomain.com 25
```

#### 邮件被当作垃圾邮件
```
需要生成DKIM签名（后续步骤）
```

---

### 七、可选优化

#### 1. DKIM署名（防止垃圾邮件）
```bash
# 生成DKIM密钥
openssl genrsa -out /etc/dkim/yourdomain.key 2048
openssl rsa -in /etc/dkim/yourdomain.key -pubout -out /etc/dkim/yourdomain.pub

# 在DNS中添加DKIM记录
# 类型：TXT
# 名称：default._domainkey
# 值：(公钥内容)
```

#### 2. Nginx反向代理（可选）
```bash
# 配置Nginx用于HTTPS和负载均衡
cat > /etc/nginx/sites-available/mailsystem <<'EOF'
server {
    listen 443 ssl http2;
    server_name yourdomain.com mail.yourdomain.com;

    ssl_certificate /etc/letsencrypt/live/yourdomain.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/yourdomain.com/privkey.pem;

    location / {
        proxy_pass http://localhost:8000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
EOF

systemctl reload nginx
```

#### 3. 监控和日志
```bash
# 查看应用日志
tail -f /opt/mailsystem/logs/system_*.log

# 使用supervisor进程监控
apt install -y supervisor
```

---

## 成本总结

| 项目 | 成本 | 说明 |
|------|------|------|
| 域名 | ¥50-100/年 | 用华为云额度 |
| ECS | 免费 | 用华为云额度 |
| SSL证书 | 免费 | Let's Encrypt |
| **总计** | **¥50-100/年** | 完全可用的邮箱系统 |

---

## 验收清单

- [ ] 域名已购买
- [ ] ECS实例已创建
- [ ] DNS记录已配置
- [ ] SSL证书已申请
- [ ] 项目已部署到服务器
- [ ] systemd服务已启动
- [ ] API可以正常访问
- [ ] SMTP端口25开放
- [ ] 本地邮件可以发送接收
- [ ] 客户端可以连接到服务器
- [ ] 外部邮件可以发送（如果配置）

---

## 后续步骤

1. 注册真实用户账号
2. 邀请朋友使用你的邮箱系统
3. 配置反垃圾邮件规则
4. 设置邮箱配额和限制
5. 实现WebUI管理面板（可选）

---

需要帮助的地方请告诉我！
