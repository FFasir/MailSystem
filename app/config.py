"""
配置文件 - 端口与数据库配置（可用环境变量覆盖）
"""
import os

# SMTP 配置
SMTP_HOST = os.getenv("SMTP_HOST", "0.0.0.0")
SMTP_PORT = int(os.getenv("SMTP_PORT", "2525"))

# POP3 配置
POP3_HOST = os.getenv("POP3_HOST", "0.0.0.0")
POP3_PORT = int(os.getenv("POP3_PORT", "8110"))

# 数据库配置
DATABASE_URL = os.getenv("DATABASE_URL", "sqlite:///./data/app.db")

# 邮件域名
MAIL_DOMAIN = os.getenv("MAIL_DOMAIN", "localhost")

# 短信配置
SMS_ENABLED = os.getenv("SMS_ENABLED", "true").lower() == "true"
SMS_PROVIDER = os.getenv("SMS_PROVIDER", "log")  # log/aliyun
SMS_CODE_TTL_SECONDS = int(os.getenv("SMS_CODE_TTL_SECONDS", "600"))  # 验证码有效期 10 分钟
SMS_RESEND_INTERVAL_SECONDS = int(os.getenv("SMS_RESEND_INTERVAL_SECONDS", "60"))  # 60 秒窗口
SMS_MAX_ATTEMPTS = int(os.getenv("SMS_MAX_ATTEMPTS", "5"))

# 阿里云短信配置
ALIYUN_ACCESS_KEY_ID = os.getenv("ALIYUN_ACCESS_KEY_ID")
ALIYUN_ACCESS_KEY_SECRET = os.getenv("ALIYUN_ACCESS_KEY_SECRET")
ALIYUN_SIGN_NAME = os.getenv("ALIYUN_SIGN_NAME")  # 短信签名
ALIYUN_TEMPLATE_CODE = os.getenv("ALIYUN_TEMPLATE_CODE")  # 模板ID（如 SMS_xxx）
ALIYUN_REGION_ID = os.getenv("ALIYUN_REGION_ID", "cn-hangzhou")
