import re

def is_valid_email(email: str) -> bool:
    """验证邮箱格式是否有效"""
    email_pattern = r'^[a-zA-Z0-9_.+-]+@[a-zA-Z0-9-]+\.[a-zA-Z0-9-.]+$'
    return re.match(email_pattern, email) is not None

def extract_username(email: str) -> str:
    """从邮箱地址提取用户名"""
    if '@' in email:
        return email.split('@')[0]
    return email