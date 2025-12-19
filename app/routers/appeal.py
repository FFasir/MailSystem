"""
申诉路由 - 用户申诉、管理员处理申诉
"""
from fastapi import APIRouter, Depends, HTTPException, Header
from sqlalchemy.orm import Session
from app.db import get_db
from app.models import User, Appeal
from app.schemas import AppealRequest, AppealResponse, MessageResponse
from app.services.auth_service import AuthService
from typing import List

router = APIRouter(prefix="/appeal", tags=["申诉"])


def verify_admin_token(authorization: str = Header(None)) -> dict:
    """验证管理员权限（复用）"""
    if not authorization:
        raise HTTPException(status_code=401, detail="缺少认证令牌")
    
    token = authorization.replace("Bearer ", "")
    user_info = AuthService.verify_token(token)
    
    if not user_info:
        raise HTTPException(status_code=401, detail="Token 无效或已过期")
    
    if user_info.get("role") != "admin":
        raise HTTPException(status_code=403, detail="仅管理员可访问")
    
    return user_info


@router.post("/submit", response_model=MessageResponse)
async def submit_appeal(
    request: AppealRequest,
    db: Session = Depends(get_db)
):
    """用户提交申诉"""
    # 验证用户名和密码
    user = db.query(User).filter(User.username == request.username).first()
    
    if not user or not AuthService.verify_password(request.password, user.password):
        raise HTTPException(status_code=401, detail="用户名或密码错误")
    
    if user.is_disabled == 0:
        raise HTTPException(status_code=400, detail="账号未被禁用，无需申诉")
    
    # 检查是否已有待处理的申诉
    existing_appeal = db.query(Appeal).filter(
        Appeal.user_id == user.id,
        Appeal.status == "pending"
    ).first()
    
    if existing_appeal:
        # 更新申诉理由
        existing_appeal.reason = request.reason
        db.commit()
        return MessageResponse(success=True, message="已有待处理的申诉，理由已更新")
    
    # 创建新申诉
    appeal = Appeal(user_id=user.id, reason=request.reason)
    db.add(appeal)
    db.commit()
    
    return MessageResponse(success=True, message="申诉已提交，请等待管理员审核")


@router.get("/list", response_model=List[AppealResponse])
async def list_appeals(
    status: str = "pending",
    db: Session = Depends(get_db),
    admin_info: dict = Depends(verify_admin_token)
):
    """获取申诉列表（仅管理员）"""
    appeals = db.query(Appeal).filter(Appeal.status == status).all()
    
    # 填充 username
    result = []
    for appeal in appeals:
        result.append(AppealResponse(
            id=appeal.id,
            user_id=appeal.user_id,
            username=appeal.user.username,
            reason=appeal.reason,
            status=appeal.status,
            created_at=appeal.created_at
        ))
        
    return result


@router.post("/{appeal_id}/approve", response_model=MessageResponse)
async def approve_appeal(
    appeal_id: int,
    db: Session = Depends(get_db),
    admin_info: dict = Depends(verify_admin_token)
):
    """同意申诉（启用账号）"""
    appeal = db.query(Appeal).filter(Appeal.id == appeal_id).first()
    if not appeal:
        raise HTTPException(status_code=404, detail="申诉不存在")
    
    if appeal.status != "pending":
        raise HTTPException(status_code=400, detail=f"申诉状态为 {appeal.status}，无法操作")
    
    # 更新申诉状态
    appeal.status = "approved"
    
    # 启用用户
    user = db.query(User).filter(User.id == appeal.user_id).first()
    if user:
        user.is_disabled = 0
    
    db.commit()
    return MessageResponse(success=True, message=f"已同意申诉，用户 {user.username} 已启用")


@router.post("/{appeal_id}/reject", response_model=MessageResponse)
async def reject_appeal(
    appeal_id: int,
    db: Session = Depends(get_db),
    admin_info: dict = Depends(verify_admin_token)
):
    """拒绝申诉"""
    appeal = db.query(Appeal).filter(Appeal.id == appeal_id).first()
    if not appeal:
        raise HTTPException(status_code=404, detail="申诉不存在")
    
    if appeal.status != "pending":
        raise HTTPException(status_code=400, detail=f"申诉状态为 {appeal.status}，无法操作")
    
    # 更新申诉状态
    appeal.status = "rejected"
    db.commit()
    
    return MessageResponse(success=True, message="已拒绝申诉")
