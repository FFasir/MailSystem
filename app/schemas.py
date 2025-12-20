"""
Pydantic 数据模型 - 请求/响应数据结构
"""
from pydantic import BaseModel
from typing import Optional
from datetime import datetime


class UserRegisterRequest(BaseModel):
    """用户注册请求"""
    username: str
    password: str
    role: str = "user"  # 默认为普通用户


class UserLoginRequest(BaseModel):
    """用户登录请求"""
    username: str
    password: str


class UserResponse(BaseModel):
    """用户响应"""
    id: int
    username: str
    role: str
    is_disabled: int
    created_at: datetime

    class Config:
        from_attributes = True


class TokenResponse(BaseModel):
    """Token 响应"""
    token: str
    user_id: int
    username: str
    role: str


class MessageResponse(BaseModel):
    """通用消息响应"""
    success: bool
    message: str
    data: Optional[dict] = None


class SendMailRequest(BaseModel):
    """发送邮件请求"""
    to_addr: str
    subject: str
    body: str

class ReplyMailRequest(BaseModel):
    """回复邮件请求"""
    to_addr: str
    subject: str
    body: str
    reply_to_filename: str  # 回复的原始邮件文件名（收件箱邮件）或mailId（POP3邮件）
    is_pop3_mail: bool = False  # 是否是POP3邮件（通过mailId标识）

class SaveDraftRequest(BaseModel):
    """保存草稿请求"""
    to_addr: str
    subject: str
    body: str
    filename: Optional[str] = None


class AppealRequest(BaseModel):
    """提交申诉请求"""
    username: str
    password: str
    reason: str


class AppealResponse(BaseModel):
    """申诉响应"""
    id: int
    user_id: int
    username: str
    reason: str
    status: str
    created_at: datetime

    class Config:
        from_attributes = True
