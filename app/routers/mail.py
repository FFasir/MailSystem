"""
邮件路由 - 邮件列表、读取、删除、发送、附件管理
"""
from fastapi import APIRouter, Depends, HTTPException, Header, UploadFile, File
from fastapi.responses import FileResponse
from app.services.auth_service import AuthService
from app.services.mail_storage import MailStorageService
from app.services.smtp_client import SMTPClient
from app.schemas import MessageResponse, SendMailRequest, SaveDraftRequest, ReplyMailRequest
from app.config import MAIL_DOMAIN
from typing import List
from app.utils.validators import is_valid_email, extract_username
from app.db import SessionLocal
from app.models import User
import os

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
    """读取邮件内容及附件"""
    username = user_info.get("username")
    content = MailStorageService.read_mail(username, filename)

    if not content:
        raise HTTPException(status_code=404, detail="邮件不存在")

    # 获取附件信息
    attachments = MailStorageService.get_attachments(username, filename)

    return {
        "success": True,
        "filename": filename,
        "content": content,
        "attachments": attachments
    }


@router.get("/sent/read/{filename}")
async def read_sent_mail(filename: str, user_info: dict = Depends(verify_user_token)):
    """读取已发送邮件内容及附件"""
    username = user_info.get("username")
    content = MailStorageService.read_sent_mail(username, filename)

    if not content:
        raise HTTPException(status_code=404, detail="邮件不存在")

    # 获取附件信息
    attachments = MailStorageService.get_attachments(username, filename)

    return {
        "success": True,
        "filename": filename,
        "content": content,
        "attachments": attachments
    }


@router.get("/original-subject")
async def get_original_mail_subject(
    in_reply_to: str,
    user_info: dict = Depends(verify_user_token)
):
    """根据In-Reply-To获取原邮件的主题"""
    username = user_info.get("username")
    subject = MailStorageService.get_original_mail_subject(username, in_reply_to)

    return {
        "success": True,
        "subject": subject if subject else "未知邮件"
    }

@router.get("/reply-chain/{filename}")
async def get_reply_chain(
    filename: str,
    user_info: dict = Depends(verify_user_token)
):
    """获取邮件的完整回复链（从原邮件到当前邮件的所有邮件）"""
    username = user_info.get("username")
    chain = MailStorageService.get_reply_chain(username, filename)

    return {
        "success": True,
        "chain": chain
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
    发送邮件
    - 内部邮箱 (mail.com): 直接保存到接收者邮箱目录
    - 外部邮箱: 通过外部 SMTP 发送
    """
    from app.services.log_service import LogService
    
    # 验证收件人邮箱格式
    if not is_valid_email(request.to_addr):
        raise HTTPException(status_code=400, detail="收件人邮箱格式无效")

    # 允许发送到外部邮箱：仅当收件人为本域时才校验存在性
    db = SessionLocal()
    username = extract_username(request.to_addr)
    domain = request.to_addr.split("@")[-1].lower() if "@" in request.to_addr else ""
    user = None
    if domain == MAIL_DOMAIN:
        user = db.query(User).filter(User.username == username).first()
        if not user:
            db.close()
            raise HTTPException(status_code=404, detail="收件人不存在")
    db.close()

    from app.config import SMTP_USER
    sender_username = user_info.get("username")
    
    # 根据收件人域名选择发送方式
    if domain == MAIL_DOMAIN:
        # 内部邮箱：直接保存到接收者邮箱目录
        from_addr = f"{sender_username}@{MAIL_DOMAIN}"
        
        # 直接使用 MailStorageService 保存邮件
        filepath = MailStorageService.save_mail(
            to_addr=request.to_addr,
            from_addr=from_addr,
            subject=request.subject,
            body=request.body
        )
        
        LogService.log_system(f"邮件已保存: {from_addr} -> {request.to_addr}")
        
        return MessageResponse(
            success=True,
            message=f"邮件已发送到 {request.to_addr}"
        )
    else:
        # 外部邮箱：使用 SMTP 发送
        from_addr = SMTP_USER or f"{sender_username}@{MAIL_DOMAIN}"
        
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


@router.post("/reply", response_model=MessageResponse)
async def reply_mail(
    request: ReplyMailRequest,
    user_info: dict = Depends(verify_user_token)
):
    """
    回复邮件（通过 SMTP 协议）
    支持回复收件箱邮件（通过文件名）和POP3邮件（通过mailId）
    """
    # 验证收件人邮箱格式
    if not is_valid_email(request.to_addr):
        raise HTTPException(status_code=400, detail="收件人邮箱格式无效")

    # 允许回复到外部邮箱：仅当本域地址才校验存在性
    db = SessionLocal()
    username = extract_username(request.to_addr)
    domain = request.to_addr.split("@")[-1].lower() if "@" in request.to_addr else ""
    user = None
    if domain == MAIL_DOMAIN:
        user = db.query(User).filter(User.username == username).first()
        if not user:
            db.close()
            raise HTTPException(status_code=404, detail="收件人不存在")
    db.close()

    from app.config import SMTP_USER
    current_username = user_info.get("username")
    
    # 验证不能回复自己的邮件
    to_username = extract_username(request.to_addr)
    if to_username == current_username:
        raise HTTPException(status_code=400, detail="不能回复自己的邮件")

    # 确定回复关联的文件名，并获取原邮件的主题（保持原主题，不添加"Re: "）
    reply_to_filename = None
    original_subject = request.subject  # 默认使用请求中的主题

    if request.is_pop3_mail:
        # POP3邮件：mailId作为标识，需要转换为文件名格式
        # 由于POP3邮件是动态的，我们使用mailId作为标识
        reply_to_filename = f"POP3_MAIL_{request.reply_to_filename}"
        # 对于POP3邮件，无法直接获取原邮件主题，使用请求中的主题（应该已经是原主题）
    else:
        # 收件箱邮件：直接使用文件名
        reply_to_filename = request.reply_to_filename
        # 尝试获取原邮件的主题（去掉"Re: "前缀）
        original_subject = MailStorageService.get_original_mail_subject(current_username, reply_to_filename)
        if original_subject:
            # 如果找到了原主题，使用原主题（不添加"Re: "）
            pass  # original_subject已经是去掉"Re: "的主题
        else:
            # 如果找不到，使用请求中的主题（去掉"Re: "前缀）
            original_subject = request.subject
            if original_subject.startswith("Re:"):
                original_subject = original_subject[3:].strip()

    # 根据收件人域名选择发送方式
    if domain == MAIL_DOMAIN:
        # 内部邮箱：直接保存到接收者邮箱
        from_addr = f"{current_username}@{MAIL_DOMAIN}"
        
        filepath = MailStorageService.save_mail(
            to_addr=request.to_addr,
            from_addr=from_addr,
            subject=original_subject,
            body=request.body,
            reply_to_filename=reply_to_filename
        )
        
        return MessageResponse(
            success=True,
            message=f"回复邮件已发送到 {request.to_addr}"
        )
    else:
        # 外部邮箱：使用 SMTP 发送回复邮件
        from_addr = SMTP_USER or f"{current_username}@{MAIL_DOMAIN}"
        smtp_client = SMTPClient()

        # 通过 SMTP 协议发送回复邮件（使用原主题，不添加"Re: "）
        success = await smtp_client.send_mail(
            from_addr=from_addr,
            to_addr=request.to_addr,
            subject=original_subject,  # 使用原主题，保持与原邮件一致
            body=request.body,
            reply_to_filename=reply_to_filename
        )

        if not success:
            raise HTTPException(status_code=500, detail="回复邮件发送失败")

        return MessageResponse(
            success=True,
            message=f"回复邮件已通过 SMTP 协议发送到 {request.to_addr}"
        )


# ==================== 附件相关 API ====================

@router.post("/attachment/upload/{mail_filename}")
async def upload_attachment(
    mail_filename: str,
    file: UploadFile = File(...),
    user_info: dict = Depends(verify_user_token)
):
    """上传邮件附件（在发送邮件前上传，保存到临时目录）"""
    username = user_info.get("username")
    
    # 限制文件大小：10MB
    MAX_FILE_SIZE = 10 * 1024 * 1024  # 10MB
    content = await file.read()
    
    if len(content) > MAX_FILE_SIZE:
        raise HTTPException(status_code=413, detail=f"文件过大，最大限制 {MAX_FILE_SIZE / 1024 / 1024}MB")
    
    # 保存附件
    try:
        success = MailStorageService.save_attachment(
            username=username,
            mail_filename=mail_filename,
            file_content=content,
            original_filename=file.filename
        )
        
        if not success:
            raise Exception("保存失败")
        
        return {
            "success": True,
            "message": "附件已上传",
            "filename": file.filename,
            "size": len(content)
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"附件上传失败: {str(e)}")


@router.get("/attachment/{mail_filename}/{attachment_filename}")
async def download_attachment(
    mail_filename: str,
    attachment_filename: str,
    user_info: dict = Depends(verify_user_token)
):
    """下载邮件附件"""
    username = user_info.get("username")
    
    # 读取附件
    content = MailStorageService.read_attachment(
        username=username,
        mail_filename=mail_filename,
        attachment_filename=attachment_filename
    )
    
    if not content:
        raise HTTPException(status_code=404, detail="附件不存在")
    
    # 返回文件
    attach_dir = MailStorageService.get_attachment_dir(username, mail_filename)
    filepath = attach_dir / attachment_filename
    
    return FileResponse(
        path=filepath,
        filename=attachment_filename,
        media_type="application/octet-stream"
    )


@router.get("/attachments/{mail_filename}")
async def get_attachments(
    mail_filename: str,
    user_info: dict = Depends(verify_user_token)
):
    """获取邮件的所有附件信息"""
    username = user_info.get("username")
    
    attachments = MailStorageService.get_attachments(
        username=username,
        mail_filename=mail_filename
    )
    
    return {
        "success": True,
        "filename": mail_filename,
        "attachments": attachments,
        "count": len(attachments)
    }

