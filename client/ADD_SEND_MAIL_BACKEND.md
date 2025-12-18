# 后端添加发送邮件 API

需要在后端 `app/routers/mail.py` 添加发送邮件接口：

```python
from pydantic import BaseModel

class SendMailRequest(BaseModel):
    to: str  # 收件人
    subject: str  # 主题
    content: str  # 内容

@router.post("/send")
async def send_mail(
    request: SendMailRequest,
    current_user: dict = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """发送邮件"""
    try:
        from_email = f"{current_user['username']}@{config.DOMAIN}"
        
        # 构造邮件内容
        mail_data = f"""From: {from_email}
To: {request.to}
Subject: {request.subject}

{request.content}
"""
        
        # 直接保存到收件人邮箱（因为是内部系统）
        to_username = request.to.split('@')[0]
        
        # 检查收件人是否存在
        recipient = db.query(User).filter(User.username == to_username).first()
        if not recipient:
            raise HTTPException(status_code=404, detail="收件人不存在")
        
        # 保存邮件
        mail_storage = MailStorageService()
        filename = mail_storage.save_mail(to_username, mail_data)
        
        # 记录日志
        log_service = LogService()
        log_service.log(f"User {current_user['username']} sent mail to {request.to}")
        
        return {"message": "邮件发送成功", "filename": filename}
        
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
```

在后端项目根目录运行此命令来重启服务器以加载新接口。
