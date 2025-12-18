"""
认证路由 - 注册、登录
"""
from fastapi import APIRouter, Depends, HTTPException, Header
from sqlalchemy.orm import Session
from app.db import get_db
from app.schemas import UserRegisterRequest, UserLoginRequest, UserResponse, TokenResponse, MessageResponse
from app.services.auth_service import AuthService
from pydantic import BaseModel

router = APIRouter(prefix="/auth", tags=["认证"])


@router.post("/register", response_model=UserResponse)
async def register(
    request: UserRegisterRequest,
    db: Session = Depends(get_db)
):
    """用户注册"""
    try:
        user = AuthService.register_user(db, request.username, request.password, request.role)
        return user
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))


@router.post("/login", response_model=TokenResponse)
async def login(
    request: UserLoginRequest,
    db: Session = Depends(get_db)
):
    """用户登录"""
    try:
        token_response = AuthService.login_user(db, request.username, request.password)
        return token_response
    except ValueError as e:
        raise HTTPException(status_code=401, detail=str(e))


@router.post("/logout", response_model=MessageResponse)
async def logout(authorization: str = Header(None)):
    """用户登出"""
    if not authorization:
        raise HTTPException(status_code=401, detail="缺少认证令牌")
    
    token = authorization.replace("Bearer ", "")
    AuthService.logout_user(token)
    
    return MessageResponse(success=True, message="登出成功")


class ChangePasswordRequest(BaseModel):
    old_password: str
    new_password: str


@router.post("/change-password", response_model=MessageResponse)
async def change_password(
    request: ChangePasswordRequest,
    authorization: str = Header(None),
    db: Session = Depends(get_db)
):
    """用户修改自己的密码（登录用户）"""
    if not authorization:
        raise HTTPException(status_code=401, detail="缺少认证令牌")
    token = authorization.replace("Bearer ", "")
    user_info = AuthService.verify_token(token)
    if not user_info:
        raise HTTPException(status_code=401, detail="Token 无效或已过期")

    user_id = user_info.get("user_id")
    from app.models import User
    user = db.query(User).filter(User.id == user_id).first()
    if not user:
        raise HTTPException(status_code=404, detail="用户不存在")

    if not AuthService.verify_password(request.old_password, user.password):
        raise HTTPException(status_code=400, detail="旧密码错误")

    user.password = AuthService.hash_password(request.new_password)
    db.commit()

    return MessageResponse(success=True, message="密码修改成功")
