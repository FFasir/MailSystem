"""
数据库模型定义 - users、mails 表
"""
from sqlalchemy import Column, Integer, String, Text, DateTime
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
    created_at = Column(DateTime, default=datetime.utcnow)
    
    def __repr__(self):
        return f"<User {self.username}>"


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
