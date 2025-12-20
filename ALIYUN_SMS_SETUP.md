# 阿里云短信配置指南

## 概述
本项目使用阿里云短信服务来发送忘记密码的验证码。本文档说明如何完成阿里云短信服务的配置。

---

## 第一步：申请阿里云账户和开通短信服务

### 1.1 注册阿里云账户
- 访问 [阿里云官网](https://www.aliyun.com/)
- 点击"免费注册"
- 按照提示完成账户注册和实名认证

### 1.2 开通短信服务
1. 登录阿里云控制台
2. 搜索"短信服务"或访问 [短信服务控制台](https://dysms.console.aliyun.com/)
3. 点击"开通短信服务"
4. 按照提示完成开通流程

---

## 第二步：创建访问凭证（AccessKey）

### 2.1 获取 AccessKeyId 和 AccessKeySecret
1. 登录阿里云控制台
2. 点击右上角头像 → "AccessKey 管理"
3. 点击"创建 AccessKey"
4. 创建后会显示：
   - **AccessKey ID**（用户名，记下来）
   - **AccessKey Secret**（密码，保管好，只显示一次）

### 2.2 在 .env 文件中配置
```dotenv
ALIYUN_ACCESS_KEY_ID=你的AccessKey_ID
ALIYUN_ACCESS_KEY_SECRET=你的AccessKey_Secret
```

---

## 第三步：申请短信签名

短信签名是显示在短信开头的公司名称或应用名称，格式如：`【阿里云】验证码：123456`

### 3.1 创建签名
1. 登录短信服务控制台
2. 左侧菜单 → "国内短信" → "签名管理"
3. 点击"创建签名"
4. 填写以下信息：
   - **签名名称**：你的应用或公司名称（如：MailSystem、我的邮件系统）
   - **签名用途**：选择合适的用途（通常选择"验证码"）
   - **签名来源**：选择合适的来源（个人建议选择"自用应用"）
   - **上传证明资料**：根据签名来源上传相应资料

### 3.2 等待审核
- 提交后需要等待审核（通常 2-4 小时）
- 审核通过后会获得签名名称（不带括号）

### 3.3 在 .env 文件中配置
```dotenv
ALIYUN_SIGN_NAME=MailSystem
```

> ⚠️ 签名名称不需要包含【】，系统会自动添加

---

## 第四步：申请短信模板

短信模板定义了短信的内容格式，必须包含 `${code}` 占位符用于填充验证码。

### 4.1 创建模板
1. 登录短信服务控制台
2. 左侧菜单 → "国内短信" → "模板管理"
3. 点击"创建模板"
4. 填写以下信息：
   - **模板名称**：如 "验证码模板"、"密码重置验证码"
   - **模板内容**：如 `您的验证码是：${code}，请勿泄露。有效期为10分钟。`
   - **模板说明**：如 "用于用户忘记密码时发送验证码"
   - **模板类型**：选择"验证码"

> ⚠️ 重要：模板中必须包含 `${code}` 占位符，系统会替换为实际的验证码

### 4.2 等待审核
- 提交后需要等待审核（通常 2-4 小时）
- 审核通过后会获得模板 ID（如 SMS_xxxxx）

### 4.3 在 .env 文件中配置
```dotenv
ALIYUN_TEMPLATE_CODE=SMS_xxxxx
```

> ⚠️ TEMPLATE_CODE 就是审核通过后的模板 ID

---

## 第五步：配置 .env 文件

完成上述步骤后，在 `.env` 文件中进行完整配置：

```dotenv
# 启用短信服务
SMS_ENABLED=true
SMS_PROVIDER=aliyun

# 阿里云凭证
ALIYUN_ACCESS_KEY_ID=xxxxxxxxxxxxxxxxxx
ALIYUN_ACCESS_KEY_SECRET=xxxxxxxxxxxxxxxxxx

# 短信签名和模板
ALIYUN_SIGN_NAME=MailSystem
ALIYUN_TEMPLATE_CODE=SMS_xxxxx

# 区域（通常为 cn-hangzhou）
ALIYUN_REGION_ID=cn-hangzhou

# 验证码配置
SMS_CODE_TTL_SECONDS=600          # 验证码有效期：600秒（10分钟）
SMS_RESEND_INTERVAL_SECONDS=60    # 重新发送间隔：60秒
SMS_MAX_ATTEMPTS=5                # 最多发送次数：5次
```

---

## 第六步：后端代码配置

代码已经配置好，只需确保 `app/config.py` 中有以下配置：

```python
# SMS 配置
SMS_ENABLED = os.getenv("SMS_ENABLED", "true").lower() == "true"
SMS_PROVIDER = os.getenv("SMS_PROVIDER", "log")  # log 或 aliyun

# 阿里云短信配置
ALIYUN_ACCESS_KEY_ID = os.getenv("ALIYUN_ACCESS_KEY_ID")
ALIYUN_ACCESS_KEY_SECRET = os.getenv("ALIYUN_ACCESS_KEY_SECRET")
ALIYUN_SIGN_NAME = os.getenv("ALIYUN_SIGN_NAME")
ALIYUN_TEMPLATE_CODE = os.getenv("ALIYUN_TEMPLATE_CODE")
ALIYUN_REGION_ID = os.getenv("ALIYUN_REGION_ID", "cn-hangzhou")
```

---

## 测试

### 通过 API 测试
1. 启动后端服务：`uvicorn app.main:app --reload`
2. 使用以下 API 测试：

```bash
# 请求发送验证码
POST /auth/send-code
{
    "phone_number": "13800000000"
}

# 验证验证码
POST /auth/verify-code
{
    "phone_number": "13800000000",
    "code": "123456"
}
```

### 通过应用程序测试
1. 启动 Android 应用
2. 进入"忘记密码"页面
3. 输入手机号，点击"获取验证码"
4. 检查手机是否收到验证码短信

---

## 常见问题排查

### Q1: 提示 "AccessKey 无效"
**原因**：AccessKey ID 或 Secret 配置错误  
**解决**：
- 确认 .env 中的 AccessKey 与阿里云控制台中的一致
- 重新启动后端服务使新配置生效

### Q2: 提示 "签名不存在" 或 "模板不存在"
**原因**：签名或模板未审核通过，或名称配置错误  
**解决**：
- 登录短信服务控制台检查签名和模板是否已通过审核
- 确认 `ALIYUN_SIGN_NAME` 和 `ALIYUN_TEMPLATE_CODE` 配置正确（不要包含多余空格）

### Q3: 短信发送失败但没有错误提示
**原因**：后端日志中应该有详细错误  
**解决**：
- 查看 uvicorn 终端的日志输出
- 确保 SMS_PROVIDER 设置为 "aliyun"
- 确保 SMS_ENABLED 设置为 "true"

### Q4: 开发中想要测试但不想扣费
**方案**：
- 在 .env 中将 `SMS_PROVIDER` 改为 `log`，这样会将短信内容输出到日志而不是实际发送
- 测试完成后再改回 `aliyun`

```dotenv
# 开发环境：使用日志而不是真实发送
SMS_PROVIDER=log

# 生产环境：使用阿里云真实发送
SMS_PROVIDER=aliyun
```

---

## 费用说明

- 阿里云短信服务需要按照实际发送量计费
- 建议在推送到生产环境前，先申请阿里云的体验套餐或查看最新的定价策略
- 开发环境可使用 `SMS_PROVIDER=log` 来避免费用

---

## 参考资源

- [阿里云短信服务官方文档](https://help.aliyun.com/product/44282.html)
- [短信模板审核标准](https://help.aliyun.com/document_detail/108269.html)
- [Python SDK 使用指南](https://help.aliyun.com/document_detail/140556.html)

---

## 总结步骤

| 步骤 | 操作 | 输出 |
|-----|------|------|
| 1 | 注册阿里云 + 开通短信服务 | 账户激活 |
| 2 | 创建 AccessKey | AccessKey ID + Secret |
| 3 | 申请签名 + 等待审核 | 签名名称 |
| 4 | 申请模板 + 等待审核 | 模板 ID（SMS_xxxxx）|
| 5 | 配置 .env 文件 | SMS 配置完成 |
| 6 | 测试 | 手机收到验证码 |

完成以上步骤后，你的邮件系统就能通过阿里云发送短信验证码了！🎉
