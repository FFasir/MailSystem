"""
邮件路由 - 邮件列表、读取、删除、发送
"""
from fastapi import APIRouter, Depends, HTTPException, Header
from app.services.auth_service import AuthService
from app.services.mail_storage import MailStorageService
from app.services.smtp_client import SMTPClient
from app.schemas import MessageResponse, SendMailRequest, SaveDraftRequest, ReplyMailRequest
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

    # 验证收件人是否存在
    db = SessionLocal()
    username = extract_username(request.to_addr)
    user = db.query(User).filter(User.username == username).first()
    db.close()

    if not user:
        raise HTTPException(status_code=404, detail="收件人不存在")

    current_username = user_info.get("username")
    from_addr = f"{current_username}@{MAIL_DOMAIN}"

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

    # 创建 SMTP 客户端
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
