"""
短信服务
- 默认: 写日志 (provider=log)
- 阿里云: 使用短信服务网关发送验证码
"""
import json
import os
import random
from datetime import datetime
from typing import Optional

from app.config import (
    SMS_ENABLED,
    SMS_PROVIDER,
    ALIYUN_ACCESS_KEY_ID,
    ALIYUN_ACCESS_KEY_SECRET,
    ALIYUN_SIGN_NAME,
    ALIYUN_TEMPLATE_CODE,
    ALIYUN_REGION_ID,
)

LOG_DIR = os.path.join(os.getcwd(), "logs")
LOG_FILE = os.path.join(LOG_DIR, "sms.log")


def ensure_log_dir():
    try:
        os.makedirs(LOG_DIR, exist_ok=True)
    except Exception:
        pass


def generate_code(length: int = 6) -> str:
    return "".join(str(random.randint(0, 9)) for _ in range(length))


def mask_phone(phone: str) -> str:
    if not phone:
        return ""
    return f"***{phone[-4:]}" if len(phone) >= 4 else "***"


def _log_sms(phone: str, content: str) -> bool:
    ensure_log_dir()
    line = f"[{datetime.utcnow().isoformat()}] provider={SMS_PROVIDER} to={phone} content={content}\n"
    try:
        with open(LOG_FILE, "a", encoding="utf-8") as f:
            f.write(line)
        print(line.strip())
    except Exception:
        return False
    return True


def _extract_code(content: str) -> Optional[str]:
    digits = "".join(ch for ch in content if ch.isdigit())
    if not digits:
        return None
    return digits[-6:] if len(digits) >= 4 else digits


def _send_sms_aliyun(phone: str, content: str) -> bool:
    try:
        from aliyunsdkcore.client import AcsClient
        from aliyunsdkdysmsapi.request.v20170525 import SendSmsRequest
    except Exception as e:  # pragma: no cover - 环境缺依赖时返回 False
        print(f"Aliyun SDK import failed: {e}")
        return False

    # 凭据检查
    if not all([ALIYUN_ACCESS_KEY_ID, ALIYUN_ACCESS_KEY_SECRET, ALIYUN_SIGN_NAME, ALIYUN_TEMPLATE_CODE]):
        print("Aliyun SMS config missing, fallback to log")
        return False

    client = AcsClient(ALIYUN_ACCESS_KEY_ID, ALIYUN_ACCESS_KEY_SECRET, ALIYUN_REGION_ID)

    code = _extract_code(content) or content
    params = {"code": code}

    request = SendSmsRequest()
    request.set_accept_format("json")
    request.set_SignName(ALIYUN_SIGN_NAME)
    request.set_TemplateCode(ALIYUN_TEMPLATE_CODE)
    request.set_TemplateParam(json.dumps(params))
    request.set_PhoneNumbers(phone)

    try:
        response = client.do_action_with_exception(request)
        payload = json.loads(response)
        ok = payload.get("Code") == "OK"
        if not ok:
            print(f"Aliyun SMS failed: {payload}")
        return ok
    except Exception as e:
        print(f"Aliyun SMS exception: {e}")
        return False


def send_sms(phone: str, content: str) -> bool:
    """发送短信，按配置选择通道，成功返回 True。"""
    if not SMS_ENABLED:
        return False

    if SMS_PROVIDER == "aliyun":
        sent = _send_sms_aliyun(phone, content)
        if sent:
            return True
        # 如果阿里云失败，继续写日志便于排查
        return _log_sms(phone, f"[aliyun-fallback] {content}")

    # 默认: 写日志
    return _log_sms(phone, content)
