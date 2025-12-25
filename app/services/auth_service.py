"""
认证服务 - 用户注册、登录、Token 管理（bcrypt + JWT）
"""
import uuid
from typing import Optional
from sqlalchemy.orm import Session
from app.models import User
from app.schemas import TokenResponse
import bcrypt
import jwt
from datetime import datetime, timedelta, timezone
from app.config import (
    TOKEN_SECRET,
    TOKEN_EXPIRE_MINUTES,
    TOKEN_ALGORITHM,
    LOGIN_MAX_ATTEMPTS,
    LOGIN_LOCKOUT_MINUTES,
    LOGIN_COOLDOWN_SECONDS,
)


class AuthService:
    """认证服务类"""
    
    # 兼容历史：不再使用内存 Token 存储；JWT 为无状态令牌
    tokens = {}
    # 登录失败计数与锁定状态（内存级，按用户名）
    login_attempts: dict[str, dict] = {}
    
    @staticmethod
    def hash_password(password: str) -> str:
        """使用 bcrypt 进行密码哈希"""
        if isinstance(password, str):
            password = password.encode("utf-8")
        salt = bcrypt.gensalt()
        return bcrypt.hashpw(password, salt).decode("utf-8")
    
    @staticmethod
    def verify_password(password: str, hashed: str) -> bool:
        """使用 bcrypt 验证密码"""
        try:
            return bcrypt.checkpw(password.encode("utf-8"), hashed.encode("utf-8"))
        except Exception:
            return False
    
    @staticmethod
    def register_user(db: Session, username: str, password: str, role: str = "user", phone_number: str | None = None, email: str | None = None) -> User:
        """注册用户"""
        # 检查用户是否已存在
        existing_user = db.query(User).filter(User.username == username).first()
        if existing_user:
            raise ValueError(f"用户 {username} 已存在")
        if phone_number:
            existing_phone = db.query(User).filter(User.phone_number == phone_number).first()
            if existing_phone:
                raise ValueError("该手机号已被绑定到其他账户")
        if email:
            existing_email = db.query(User).filter(User.email == email).first()
            if existing_email:
                raise ValueError("该邮箱已被绑定到其他账户")
        
        # 创建新用户
        hashed_password = AuthService.hash_password(password)
        user = User(username=username, password=hashed_password, role=role, phone_number=phone_number, email=email)
        db.add(user)
        db.commit()
        db.refresh(user)
        return user
    
    @staticmethod
    def login_user(db: Session, username: str, password: str) -> TokenResponse:
        """用户登录：颁发带过期与签名的 JWT"""
        # 登录前校验：锁定与冷却
        now = datetime.now(timezone.utc)
        attempt = AuthService.login_attempts.get(username)
        if attempt:
            locked_until = attempt.get("locked_until")
            last_failed = attempt.get("last_failed")
            if locked_until and locked_until > now:
                # 账户短期锁定
                remaining = int((locked_until - now).total_seconds())
                raise ValueError(f"账户暂时锁定，请 {remaining} 秒后重试")
            if last_failed and (now - last_failed).total_seconds() < LOGIN_COOLDOWN_SECONDS:
                # 冷却期内拒绝频繁尝试
                remaining = int(LOGIN_COOLDOWN_SECONDS - (now - last_failed).total_seconds())
                raise ValueError(f"冷却中，请 {remaining} 秒后重试")
        user = db.query(User).filter(User.username == username).first()
        
        if not user or not AuthService.verify_password(password, user.password):
            # 记录失败并可能触发锁定
            data = AuthService.login_attempts.get(username, {"count": 0, "last_failed": None, "locked_until": None})
            data["count"] = int(data.get("count", 0)) + 1
            data["last_failed"] = now
            if data["count"] >= LOGIN_MAX_ATTEMPTS:
                data["locked_until"] = now + timedelta(minutes=LOGIN_LOCKOUT_MINUTES)
                data["count"] = 0  # 锁定后计数清零
            AuthService.login_attempts[username] = data
            raise ValueError("用户名或密码错误")
        if getattr(user, "is_disabled", 0) == 1:
            raise ValueError("账号已被禁用，请联系管理员")

        now = datetime.now(timezone.utc)
        exp = now + timedelta(minutes=TOKEN_EXPIRE_MINUTES)
        payload = {
            "sub": str(user.id),
            "username": user.username,
            "role": user.role,
            "iat": int(now.timestamp()),
            "exp": int(exp.timestamp()),
        }
        token = jwt.encode(payload, TOKEN_SECRET, algorithm=TOKEN_ALGORITHM)
        
        # 登录成功：清除失败记录
        if username in AuthService.login_attempts:
            AuthService.login_attempts.pop(username, None)

        return TokenResponse(
            token=token,
            user_id=user.id,
            username=user.username,
            role=user.role
        )
    
    @staticmethod
    def verify_token(token: str) -> Optional[dict]:
        """验证 JWT Token：签名、过期与用户状态"""
        try:
            decoded = jwt.decode(token, TOKEN_SECRET, algorithms=[TOKEN_ALGORITHM])
            user_id = int(decoded.get("sub"))
            username = decoded.get("username")
            role = decoded.get("role")

            # 验证用户状态（禁用/删除）
            from app.db import SessionLocal
            db = SessionLocal()
            try:
                user = db.query(User).filter(User.id == user_id).first()
                if not user or user.is_disabled == 1:
                    return None
            finally:
                db.close()

            return {"user_id": user_id, "username": username, "role": role}
        except jwt.ExpiredSignatureError:
            return None
        except Exception:
            return None
    
    @staticmethod
    def logout_user(token: str):
        """用户登出（JWT 无状态，服务端无需存储）"""
        return

    @staticmethod
    def revoke_tokens_for_user(user_id: int):
        """撤销指定用户的所有 Token（占位，JWT建议结合禁用或版本字段实现）"""
        # 简化：配合管理员“禁用账号”实现服务端拒绝；如需软下线，可采用用户token版本方案。
        return

    @staticmethod
    def update_tokens_username(user_id: int, new_username: str):
        """JWT 无状态，不需更新服务端缓存。保留占位以兼容调用。"""
        return

    @staticmethod
    def update_phone_number(db: Session, user_id: int, phone_number: str) -> User:
        """为用户绑定或更新手机号，保证唯一性"""
        if phone_number:
            exists = db.query(User).filter(User.phone_number == phone_number, User.id != user_id).first()
            if exists:
                raise ValueError("该手机号已被其他账户绑定")

        user = db.query(User).filter(User.id == user_id).first()
        if not user:
            raise ValueError("用户不存在")

        user.phone_number = phone_number
        db.commit()
        db.refresh(user)
        return user

    @staticmethod
    def update_profile(db: Session, user_id: int, username: str | None, phone_number: str | None) -> User:
        """更新用户名与手机号（可选），保证唯一性并同步 token 内存。"""
        user = db.query(User).filter(User.id == user_id).first()
        if not user:
            raise ValueError("用户不存在")

        if username and username != user.username:
            exists = db.query(User).filter(User.username == username).first()
            if exists:
                raise ValueError("该用户名已被占用")
            user.username = username

        if phone_number and phone_number != user.phone_number:
            exists_phone = db.query(User).filter(User.phone_number == phone_number, User.id != user_id).first()
            if exists_phone:
                raise ValueError("该手机号已被其他账户绑定")
            user.phone_number = phone_number

        db.commit()
        db.refresh(user)

        # 同步内存 token 信息（避免强制重登，但建议前端刷新用户信息）
        AuthService.update_tokens_username(user_id, user.username)
        return user
