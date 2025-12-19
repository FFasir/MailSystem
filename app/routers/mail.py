"""
邮件路由 - 邮件列表、读取、删除、发送
"""
from fastapi import APIRouter, Depends, HTTPException, Header
from app.services.auth_service import AuthService
from app.services.mail_storage import MailStorageService
from app.services.smtp_client import SMTPClient
from app.schemas import MessageResponse, SendMailRequest, SaveDraftRequest
from app.config import MAIL_DOMAIN
from typing import List
from app.utils.validators import is_valid_email, extract_username
from app.db import SessionLocal
from app.models import User

router = APIRouter(prefix="/mail", tags=["邮件"])


def verify_user_token(authorization: str = Header(None)) -> dict:
    """验证用户 Token"""
    if not authorization:
        raise HTTPException(status_code=401, detail="缺少认证令牌")
    
    token = authorization.replace("Bearer ", "")
    user_info = AuthService.verify_token(token)
    
    if not user_info:
        raise HTTPException(status_code=401, detail="Token 无效或已过期")
    
    return user_info

# ... (imports)

@router.get("/draft/list")
async def list_drafts(user_info: dict = Depends(verify_user_token)):
    """获取当前用户的草稿列表"""
    username = user_info.get("username")
    drafts = MailStorageService.list_drafts(username)
    
    return {
        "success": True,
        "count": len(drafts),
        "mails": drafts
    }


@router.get("/draft/read/{filename}")
async def read_draft(filename: str, user_info: dict = Depends(verify_user_token)):
    """读取草稿内容"""
    username = user_info.get("username")
    content = MailStorageService.read_draft(username, filename)
    
    if not content:
        raise HTTPException(status_code=404, detail="草稿不存在")
    
    return {
        "success": True,
        "filename": filename,
        "content": content
    }


@router.post("/draft/save")
async def save_draft(
    request: SaveDraftRequest,
    user_info: dict = Depends(verify_user_token)
):
    """保存草稿"""
    username = user_info.get("username")
    filename = MailStorageService.save_draft(
        username=username,
        to_addr=request.to_addr,
        subject=request.subject,
        body=request.body,
        filename=request.filename
    )
    
    return {
        "success": True,
        "message": "草稿已保存",
        "filename": filename
    }


@router.delete("/draft/delete/{filename}", response_model=MessageResponse)
async def delete_draft(filename: str, user_info: dict = Depends(verify_user_token)):
    """删除草稿"""
    username = user_info.get("username")
    success = MailStorageService.delete_draft(username, filename)
    
    if not success:
        raise HTTPException(status_code=404, detail="草稿不存在")
    
    return MessageResponse(success=True, message=f"草稿 {filename} 已删除")

@router.get("/list")
async def list_mails(user_info: dict = Depends(verify_user_token)):
    """获取当前用户的邮件列表"""
    username = user_info.get("username")
    mails = MailStorageService.list_user_mails(username)
    
    return {
        "success": True,
        "count": len(mails),
        "mails": mails
    }


@router.get("/sent/list")
async def list_sent_mails(user_info: dict = Depends(verify_user_token)):
    """获取当前用户的已发送邮件列表"""
    username = user_info.get("username")
    mails = MailStorageService.list_sent_mails(username)
    
    return {
        "success": True,
        "count": len(mails),
        "mails": mails
    }


@router.get("/read/{filename}")
async def read_mail(filename: str, user_info: dict = Depends(verify_user_token)):
    """读取邮件内容"""
    username = user_info.get("username")
    content = MailStorageService.read_mail(username, filename)
    
    if not content:
        raise HTTPException(status_code=404, detail="邮件不存在")
    
    return {
        "success": True,
        "filename": filename,
        "content": content
    }


@router.get("/sent/read/{filename}")
async def read_sent_mail(filename: str, user_info: dict = Depends(verify_user_token)):
    """读取已发送邮件内容"""
    username = user_info.get("username")
    content = MailStorageService.read_sent_mail(username, filename)
    
    if not content:
        raise HTTPException(status_code=404, detail="邮件不存在")
    
    return {
        "success": True,
        "filename": filename,
        "content": content
    }


@router.delete("/delete/{filename}", response_model=MessageResponse)
async def delete_mail(filename: str, user_info: dict = Depends(verify_user_token)):
    """删除邮件"""
    username = user_info.get("username")
    success = MailStorageService.delete_mail(username, filename)
    
    if not success:
        raise HTTPException(status_code=404, detail="邮件不存在")
    
    return MessageResponse(success=True, message=f"邮件 {filename} 已删除")


@router.post("/send", response_model=MessageResponse)
async def send_mail(
    request: SendMailRequest,
    user_info: dict = Depends(verify_user_token)
):
    """
    发送邮件（通过 SMTP 协议）
    Android 客户端调用此接口，后端通过 SMTP 协议发送到本地 SMTP 服务器
    """
    # 验证收件人邮箱格式
    if not is_valid_email(request.to_addr):
        raise HTTPException(status_code=400, detail="收件人邮箱格式无效")

    # 验证收件人是否存在
    db = SessionLocal()
    username = extract_username(request.to_addr)
    user = db.query(User).filter(User.username == username).first()
    db.close()

    if not user:
        raise HTTPException(status_code=404, detail="收件人不存在")

    username = user_info.get("username")
    from_addr = f"{username}@{MAIL_DOMAIN}"


    # 创建 SMTP 客户端
    smtp_client = SMTPClient()
    
    # 通过 SMTP 协议发送邮件
    success = await smtp_client.send_mail(
        from_addr=from_addr,
        to_addr=request.to_addr,
        subject=request.subject,
        body=request.body
    )
    
    if not success:
        raise HTTPException(status_code=500, detail="邮件发送失败")
    
    return MessageResponse(
        success=True,
        message=f"邮件已通过 SMTP 协议发送到 {request.to_addr}"
    )
