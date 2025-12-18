"""
认证服务 - 用户注册、登录、Token 管理
"""
import hashlib
import uuid
from typing import Optional
from sqlalchemy.orm import Session
from app.models import User
from app.schemas import TokenResponse


class AuthService:
    """认证服务类"""
    
    # 简单的内存 Token 存储 (生产环境应用数据库)
    tokens = {}
    
    @staticmethod
    def hash_password(password: str) -> str:
        """密码哈希"""
        return hashlib.sha256(password.encode()).hexdigest()
    
    @staticmethod
    def verify_password(password: str, hashed: str) -> bool:
        """验证密码"""
        return AuthService.hash_password(password) == hashed
    
    @staticmethod
    def register_user(db: Session, username: str, password: str, role: str = "user") -> User:
        """注册用户"""
        # 检查用户是否已存在
        existing_user = db.query(User).filter(User.username == username).first()
        if existing_user:
            raise ValueError(f"用户 {username} 已存在")
        
        # 创建新用户
        hashed_password = AuthService.hash_password(password)
        user = User(username=username, password=hashed_password, role=role)
        db.add(user)
        db.commit()
        db.refresh(user)
        return user
    
    @staticmethod
    def login_user(db: Session, username: str, password: str) -> TokenResponse:
        """用户登录"""
        user = db.query(User).filter(User.username == username).first()
        
        if not user or not AuthService.verify_password(password, user.password):
            raise ValueError("用户名或密码错误")
        if getattr(user, "is_disabled", 0) == 1:
            raise ValueError("账号已被禁用，请联系管理员")
        
        # 生成 Token
        token = str(uuid.uuid4())
        AuthService.tokens[token] = {
            "user_id": user.id,
            "username": user.username,
            "role": user.role
        }
        
        return TokenResponse(
            token=token,
            user_id=user.id,
            username=user.username,
            role=user.role
        )
    
    @staticmethod
    def verify_token(token: str) -> Optional[dict]:
        """验证 Token"""
        return AuthService.tokens.get(token)
    
    @staticmethod
    def logout_user(token: str):
        """用户登出"""
        if token in AuthService.tokens:
            del AuthService.tokens[token]

    @staticmethod
    def revoke_tokens_for_user(user_id: int):
        """撤销指定用户的所有 Token（强制下线）"""
        to_delete = [t for t, info in AuthService.tokens.items() if info.get("user_id") == user_id]
        for t in to_delete:
            del AuthService.tokens[t]
