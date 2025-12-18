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
