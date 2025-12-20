"""
认证路由 - 注册、登录
"""
from fastapi import APIRouter, Depends, HTTPException, Header
from sqlalchemy.orm import Session
from app.db import get_db
from app.schemas import (
    UserRegisterRequest,
    UserLoginRequest,
    UserResponse,
    TokenResponse,
    MessageResponse,
    PasswordResetCodeRequest,
    PasswordResetConfirmRequest,
    BindPhoneRequest,
    ProfileResponse,
    UpdateProfileRequest,
)
from app.services.auth_service import AuthService
from app.services.password_reset_service import PasswordResetService
from pydantic import BaseModel

router = APIRouter(prefix="/auth", tags=["认证"])


@router.post("/register", response_model=UserResponse)
async def register(
    request: UserRegisterRequest,
    db: Session = Depends(get_db)
):
    """用户注册"""
    try:
        # 若未提供 email，且 username 看起来像邮箱，可自动作为 email 绑定
        auto_email = None
        try:
            from pydantic import EmailStr
            auto_email = str(EmailStr(request.username))
        except Exception:
            auto_email = None

        user = AuthService.register_user(
            db,
            request.username,
            request.password,
            request.role,
            phone_number=request.phone_number,
            email=(str(request.email) if request.email is not None else auto_email),
        )
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


@router.post("/forgot-password/request", response_model=MessageResponse)
async def forgot_password_request(
    request: PasswordResetCodeRequest,
    db: Session = Depends(get_db)
):
    """请求发送短信验证码用于重置密码"""
    try:
        result = PasswordResetService.request_code(db, request.username)
        return MessageResponse(success=result.get("success", False), message="验证码已发送", data={"sent_to": result.get("sent_to")})
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))


@router.post("/forgot-password/reset", response_model=MessageResponse)
async def forgot_password_reset(
    request: PasswordResetConfirmRequest,
    db: Session = Depends(get_db)
):
    """校验验证码并重置密码"""
    try:
        PasswordResetService.confirm_reset(db, request.username, request.code, request.new_password)
        return MessageResponse(success=True, message="密码重置成功")
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))


@router.post("/bind-phone", response_model=MessageResponse)
async def bind_phone(
    request: BindPhoneRequest,
    authorization: str = Header(None),
    db: Session = Depends(get_db)
):
    """登录用户绑定或更新手机号"""
    if not authorization:
        raise HTTPException(status_code=401, detail="缺少认证令牌")
    token = authorization.replace("Bearer ", "")
    user_info = AuthService.verify_token(token)
    if not user_info:
        raise HTTPException(status_code=401, detail="Token 无效或已过期")

    user_id = user_info.get("user_id")
    try:
        AuthService.update_phone_number(db, user_id, request.phone_number)
        return MessageResponse(success=True, message="手机号绑定成功")
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))


@router.get("/profile", response_model=ProfileResponse)
async def get_profile(
    authorization: str = Header(None),
    db: Session = Depends(get_db)
):
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
    return user


@router.patch("/profile", response_model=MessageResponse)
async def update_profile(
    request: UpdateProfileRequest,
    authorization: str = Header(None),
    db: Session = Depends(get_db)
):
    if not authorization:
        raise HTTPException(status_code=401, detail="缺少认证令牌")
    token = authorization.replace("Bearer ", "")
    user_info = AuthService.verify_token(token)
    if not user_info:
        raise HTTPException(status_code=401, detail="Token 无效或已过期")
    user_id = user_info.get("user_id")
    try:
        AuthService.update_profile(db, user_id, request.username, request.phone_number)
        return MessageResponse(success=True, message="资料更新成功")
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))
