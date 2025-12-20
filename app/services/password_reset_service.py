"""
密码重置服务 - 生成、发送短信验证码并重置密码。
"""
from datetime import datetime, timedelta
from sqlalchemy.orm import Session
from sqlalchemy import and_
from app.models import User, PasswordResetCode
from app.services.sms_service import generate_code, send_sms, mask_phone
from app.services.auth_service import AuthService
from app.config import SMS_CODE_TTL_SECONDS, SMS_MAX_ATTEMPTS


class PasswordResetService:
    @staticmethod
    def request_code(db: Session, username: str) -> dict:
        """请求发送重置密码短信验证码。"""
        user = db.query(User).filter(User.username == username).first()
        if not user:
            # 避免暴露用户存在性，统一返回成功
            return {"success": True, "sent_to": "***"}
        if not user.phone_number:
            raise ValueError("该用户未绑定手机号，无法发送验证码")

        code = generate_code(6)
        expires = datetime.utcnow() + timedelta(seconds=SMS_CODE_TTL_SECONDS)
        record = PasswordResetCode(
            user_id=user.id,
            code=code,
            expires_at=expires,
            used=0,
            delivery_channel="sms",
            delivered_to=user.phone_number,
            attempts=0,
        )
        db.add(record)
        db.commit()

        content = f"您本次重置密码验证码为：{code}，{SMS_CODE_TTL_SECONDS//60}分钟内有效。"
        sent = send_sms(user.phone_number, content)
        if not sent:
            raise ValueError("短信发送失败，请稍后重试")

        return {"success": True, "sent_to": mask_phone(user.phone_number)}

    @staticmethod
    def confirm_reset(db: Session, username: str, code: str, new_password: str) -> dict:
        """校验验证码并重置密码。"""
        user = db.query(User).filter(User.username == username).first()
        if not user:
            raise ValueError("用户不存在")
        now = datetime.utcnow()
        rec = db.query(PasswordResetCode).filter(
            and_(
                PasswordResetCode.user_id == user.id,
                PasswordResetCode.code == code,
                PasswordResetCode.used == 0,
                PasswordResetCode.expires_at > now,
            )
        ).order_by(PasswordResetCode.created_at.desc()).first()
        if not rec:
            raise ValueError("验证码无效或已过期")

        # 次数控制
        if rec.attempts >= SMS_MAX_ATTEMPTS:
            raise ValueError("验证码尝试次数过多，请重新获取")
        rec.attempts += 1

        # 验证成功，更新密码并标记验证码为已使用
        user.password = AuthService.hash_password(new_password)
        rec.used = 1
        db.commit()

        # 撤销该用户已有的登录令牌
        AuthService.revoke_tokens_for_user(user.id)
        return {"success": True}
