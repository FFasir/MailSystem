"""
数据库模型定义 - users、mails 表
"""
from sqlalchemy import Column, Integer, String, Text, DateTime, ForeignKey
from sqlalchemy.orm import relationship
from sqlalchemy.ext.declarative import declarative_base
from datetime import datetime

Base = declarative_base()


class User(Base):
    """用户表"""
    __tablename__ = "users"
    
    id = Column(Integer, primary_key=True, index=True)
    username = Column(String(100), unique=True, index=True, nullable=False)
    password = Column(String(255), nullable=False)
    role = Column(String(20), default="user")  # user 或 admin
    is_disabled = Column(Integer, default=0)  # 0 启用, 1 禁用
    phone_number = Column(String(20), unique=True, index=True, nullable=True)  # 绑定手机号
    email = Column(String(255), unique=True, index=True, nullable=True)  # 绑定邮箱
    created_at = Column(DateTime, default=datetime.utcnow)
    
    appeals = relationship("Appeal", back_populates="user")

    def __repr__(self):
        return f"<User {self.username}>"


class Appeal(Base):
    """申诉表"""
    __tablename__ = "appeals"
    
    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, ForeignKey("users.id"))
    reason = Column(String(500), nullable=False)
    status = Column(String(20), default="pending")  # pending, approved, rejected
    created_at = Column(DateTime, default=datetime.utcnow)
    
    user = relationship("User", back_populates="appeals")

    def __repr__(self):
        return f"<Appeal user={self.user_id} status={self.status}>"



class Mail(Base):
    """邮件表"""
    __tablename__ = "mails"
    
    id = Column(Integer, primary_key=True, index=True)
    from_addr = Column(String(255), nullable=False)
    to_addr = Column(String(255), nullable=False)
    subject = Column(String(500))
    body = Column(Text)
    file_path = Column(String(500))  # 邮件存储文件路径
    created_at = Column(DateTime, default=datetime.utcnow)
    is_deleted = Column(Integer, default=0)  # 0 未删除, 1 已删除
    
    def __repr__(self):
        return f"<Mail from={self.from_addr} to={self.to_addr}>"


class PasswordResetCode(Base):
    """密码重置验证码表（短信）"""
    __tablename__ = "password_reset_codes"

    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, ForeignKey("users.id"), nullable=False)
    code = Column(String(10), nullable=False)
    expires_at = Column(DateTime, nullable=False)
    used = Column(Integer, default=0)  # 0 未使用, 1 已使用
    delivery_channel = Column(String(20), default="sms")
    delivered_to = Column(String(50))  # 发送到的号码或地址
    attempts = Column(Integer, default=0)
    created_at = Column(DateTime, default=datetime.utcnow)

    user = relationship("User")

    def __repr__(self):
        return f"<PasswordResetCode user={self.user_id} code={self.code} used={self.used}>"
