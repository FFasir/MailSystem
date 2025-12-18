"""
管理员路由 - 用户管理、群发邮件、过滤管理
"""
from fastapi import APIRouter, Depends, HTTPException, Header
from sqlalchemy.orm import Session
from app.db import get_db
from app.models import User
from app.schemas import UserResponse, MessageResponse
from app.services.auth_service import AuthService
from app.services.mail_storage import MailStorageService
from app.services.filter_service import FilterService
from pydantic import BaseModel
from typing import List

router = APIRouter(prefix="/admin", tags=["管理"])


def verify_admin_token(authorization: str = Header(None)) -> dict:
    """验证管理员权限"""
    if not authorization:
        raise HTTPException(status_code=401, detail="缺少认证令牌")
    
    token = authorization.replace("Bearer ", "")
    user_info = AuthService.verify_token(token)
    
    if not user_info:
        raise HTTPException(status_code=401, detail="Token 无效或已过期")
    
    if user_info.get("role") != "admin":
        raise HTTPException(status_code=403, detail="仅管理员可访问")
    
    return user_info


@router.get("/users", response_model=List[UserResponse])
async def get_users(
    db: Session = Depends(get_db),
    admin_info: dict = Depends(verify_admin_token)
):
    """获取所有用户列表（仅管理员）"""
    users = db.query(User).all()
    return users


class CreateUserRequest(BaseModel):
    username: str
    password: str
    role: str = "user"  # user/admin


@router.post("/users", response_model=UserResponse)
async def create_user(
    request: CreateUserRequest,
    db: Session = Depends(get_db),
    admin_info: dict = Depends(verify_admin_token)
):
    """创建用户（仅管理员）"""
    try:
        user = AuthService.register_user(db, request.username, request.password, request.role)
        return user
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))


@router.delete("/users/{user_id}", response_model=MessageResponse)
async def delete_user(
    user_id: int,
    db: Session = Depends(get_db),
    admin_info: dict = Depends(verify_admin_token)
):
    """删除用户（仅管理员）"""
    user = db.query(User).filter(User.id == user_id).first()
    
    if not user:
        raise HTTPException(status_code=404, detail=f"用户 ID {user_id} 不存在")
    
    db.delete(user)
    db.commit()
    
    return MessageResponse(success=True, message=f"用户 {user.username} 已删除")


class UpdateUserRoleRequest(BaseModel):
    role: str  # user 或 admin


@router.patch("/users/{user_id}/role", response_model=MessageResponse)
async def update_user_role(
    user_id: int,
    request: UpdateUserRoleRequest,
    db: Session = Depends(get_db),
    admin_info: dict = Depends(verify_admin_token)
):
    """授权/消权：更新用户角色（仅管理员）"""
    if request.role not in ("user", "admin"):
        raise HTTPException(status_code=400, detail="非法角色，仅支持 user/admin")
    user = db.query(User).filter(User.id == user_id).first()
    if not user:
        raise HTTPException(status_code=404, detail=f"用户 ID {user_id} 不存在")
    user.role = request.role
    db.commit()
    return MessageResponse(success=True, message=f"已将用户 {user.username} 角色设为 {request.role}")


class DisableUserRequest(BaseModel):
    reason: str | None = None


@router.post("/users/{user_id}/disable", response_model=MessageResponse)
async def disable_user(
    user_id: int,
    request: DisableUserRequest | None = None,
    db: Session = Depends(get_db),
    admin_info: dict = Depends(verify_admin_token)
):
    """禁用账号并强制下线（仅管理员）"""
    user = db.query(User).filter(User.id == user_id).first()
    if not user:
        raise HTTPException(status_code=404, detail=f"用户 ID {user_id} 不存在")
    user.is_disabled = 1
    db.commit()
    # 撤销所有 token
    AuthService.revoke_tokens_for_user(user_id)
    msg = f"用户 {user.username} 已禁用"
    if request and request.reason:
        msg += f"（原因：{request.reason}）"
    return MessageResponse(success=True, message=msg)


@router.post("/users/{user_id}/enable", response_model=MessageResponse)
async def enable_user(
    user_id: int,
    db: Session = Depends(get_db),
    admin_info: dict = Depends(verify_admin_token)
):
    """启用账号（仅管理员）"""
    user = db.query(User).filter(User.id == user_id).first()
    if not user:
        raise HTTPException(status_code=404, detail=f"用户 ID {user_id} 不存在")
    user.is_disabled = 0
    db.commit()
    return MessageResponse(success=True, message=f"用户 {user.username} 已启用")


class ResetPasswordRequest(BaseModel):
    new_password: str


@router.post("/users/{user_id}/reset-password", response_model=MessageResponse)
async def reset_password(
    user_id: int,
    request: ResetPasswordRequest,
    db: Session = Depends(get_db),
    admin_info: dict = Depends(verify_admin_token)
):
    """重置用户密码（仅管理员）"""
    user = db.query(User).filter(User.id == user_id).first()
    if not user:
        raise HTTPException(status_code=404, detail=f"用户 ID {user_id} 不存在")
    user.password = AuthService.hash_password(request.new_password)
    db.commit()
    # 可选：重置密码后强制下线
    AuthService.revoke_tokens_for_user(user_id)
    return MessageResponse(success=True, message=f"用户 {user.username} 的密码已重置并强制下线")


@router.post("/users/{user_id}/logout", response_model=MessageResponse)
async def force_logout(
    user_id: int,
    db: Session = Depends(get_db),
    admin_info: dict = Depends(verify_admin_token)
):
    """强制用户下线（撤销其所有Token，仅管理员）"""
    user = db.query(User).filter(User.id == user_id).first()
    if not user:
        raise HTTPException(status_code=404, detail=f"用户 ID {user_id} 不存在")
    AuthService.revoke_tokens_for_user(user_id)
    return MessageResponse(success=True, message=f"用户 {user.username} 已被强制下线")


@router.get("/users/{user_id}", response_model=UserResponse)
async def get_user(
    user_id: int,
    db: Session = Depends(get_db),
    admin_info: dict = Depends(verify_admin_token)
):
    """获取单个用户信息（仅管理员）"""
    user = db.query(User).filter(User.id == user_id).first()
    
    if not user:
        raise HTTPException(status_code=404, detail=f"用户 ID {user_id} 不存在")
    
    return user


# ============ 群发邮件 ============

class BroadcastMailRequest(BaseModel):
    """群发邮件请求"""
    subject: str
    body: str
    from_addr: str = "admin@localhost"


@router.post("/broadcast", response_model=MessageResponse)
async def broadcast_mail(
    request: BroadcastMailRequest,
    db: Session = Depends(get_db),
    admin_info: dict = Depends(verify_admin_token)
):
    """群发邮件给所有用户（仅管理员）"""
    users = db.query(User).all()
    
    if not users:
        raise HTTPException(status_code=404, detail="没有找到任何用户")
    
    success_count = 0
    for user in users:
        try:
            to_addr = f"{user.username}@localhost"
            MailStorageService.save_mail(
                to_addr=to_addr,
                from_addr=request.from_addr,
                subject=request.subject,
                body=request.body
            )
            success_count += 1
        except Exception as e:
            print(f"群发邮件失败 {user.username}: {e}")
    
    return MessageResponse(
        success=True,
        message=f"群发成功：已发送 {success_count}/{len(users)} 封邮件"
    )


# ============ 修改密码 ============

class ChangePasswordRequest(BaseModel):
    """修改密码请求"""
    old_password: str
    new_password: str


@router.post("/change-password", response_model=MessageResponse)
async def change_password(
    request: ChangePasswordRequest,
    db: Session = Depends(get_db),
    admin_info: dict = Depends(verify_admin_token)
):
    """管理员修改自己的密码"""
    user = db.query(User).filter(User.id == admin_info['user_id']).first()
    
    if not user:
        raise HTTPException(status_code=404, detail="用户不存在")
    
    # 验证旧密码
    if not AuthService.verify_password(request.old_password, user.password):
        raise HTTPException(status_code=400, detail="旧密码错误")
    
    # 更新密码
    user.password = AuthService.hash_password(request.new_password)
    db.commit()
    
    return MessageResponse(success=True, message="密码修改成功")


# ============ IP 黑名单管理 ============

class IPBlacklistRequest(BaseModel):
    """IP 黑名单请求"""
    ip: str


@router.get("/ip-blacklist")
async def get_ip_blacklist(admin_info: dict = Depends(verify_admin_token)):
    """获取 IP 黑名单"""
    blacklist = list(FilterService.get_ip_blacklist())
    return {"success": True, "count": len(blacklist), "ips": blacklist}


@router.post("/ip-blacklist", response_model=MessageResponse)
async def add_ip_blacklist(
    request: IPBlacklistRequest,
    admin_info: dict = Depends(verify_admin_token)
):
    """添加 IP 到黑名单"""
    if FilterService.add_ip_to_blacklist(request.ip):
        return MessageResponse(success=True, message=f"IP {request.ip} 已加入黑名单")
    else:
        raise HTTPException(status_code=400, detail="IP 格式错误或已存在")


@router.delete("/ip-blacklist/{ip}", response_model=MessageResponse)
async def remove_ip_blacklist(
    ip: str,
    admin_info: dict = Depends(verify_admin_token)
):
    """从黑名单移除 IP"""
    if FilterService.remove_ip_from_blacklist(ip):
        return MessageResponse(success=True, message=f"IP {ip} 已从黑名单移除")
    else:
        raise HTTPException(status_code=404, detail="IP 不在黑名单中")


# ============ 邮箱黑名单管理 ============

class EmailBlacklistRequest(BaseModel):
    """邮箱黑名单请求"""
    email: str


@router.get("/email-blacklist")
async def get_email_blacklist(admin_info: dict = Depends(verify_admin_token)):
    """获取邮箱黑名单"""
    blacklist = list(FilterService.get_email_blacklist())
    return {"success": True, "count": len(blacklist), "emails": blacklist}


@router.post("/email-blacklist", response_model=MessageResponse)
async def add_email_blacklist(
    request: EmailBlacklistRequest,
    admin_info: dict = Depends(verify_admin_token)
):
    """添加邮箱到黑名单"""
    if FilterService.add_email_to_blacklist(request.email):
        return MessageResponse(success=True, message=f"邮箱 {request.email} 已加入黑名单")
    else:
        raise HTTPException(status_code=400, detail="邮箱已存在黑名单中")


@router.delete("/email-blacklist/{email}", response_model=MessageResponse)
async def remove_email_blacklist(
    email: str,
    admin_info: dict = Depends(verify_admin_token)
):
    """从黑名单移除邮箱"""
    if FilterService.remove_email_from_blacklist(email):
        return MessageResponse(success=True, message=f"邮箱 {email} 已从黑名单移除")
    else:
        raise HTTPException(status_code=404, detail="邮箱不在黑名单中")


@router.post("/reload-filters", response_model=MessageResponse)
async def reload_filters(admin_info: dict = Depends(verify_admin_token)):
    """重新加载过滤器"""
    FilterService.reload_filters()
    return MessageResponse(success=True, message="过滤器已重新加载")


# ============ 管理员查看所有邮件 ============

class MailInfo(BaseModel):
    """邮件信息"""
    username: str
    filename: str
    size: int
    created: str


@router.get("/mails")
async def get_all_mails(admin_info: dict = Depends(verify_admin_token)):
    """获取所有用户的邮件列表（仅管理员）"""
    from pathlib import Path
    
    all_mails = []
    mailbox_dir = Path("mailbox")
    
    if not mailbox_dir.exists():
        return {"success": True, "count": 0, "mails": []}
    
    for user_dir in mailbox_dir.iterdir():
        if user_dir.is_dir():
            username = user_dir.name
            for mail_file in sorted(user_dir.glob("*.txt"), reverse=True):
                from datetime import datetime
                created_time = datetime.fromtimestamp(mail_file.stat().st_ctime).strftime("%Y-%m-%d %H:%M:%S")
                all_mails.append({
                    "username": username,
                    "filename": mail_file.name,
                    "size": mail_file.stat().st_size,
                    "created": created_time
                })
    
    return {"success": True, "count": len(all_mails), "mails": all_mails}


@router.get("/mails/{username}/{filename}")
async def get_user_mail(
    username: str,
    filename: str,
    admin_info: dict = Depends(verify_admin_token)
):
    """查看指定用户的邮件内容（仅管理员）"""
    content = MailStorageService.read_mail(username, filename)
    
    if not content:
        raise HTTPException(status_code=404, detail="邮件不存在")
    
    return {"success": True, "content": content}
