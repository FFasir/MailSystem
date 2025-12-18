"""
/health 路由
"""
from fastapi import APIRouter

router = APIRouter()


@router.get("/health")
async def health_check():
    """健康检查端点"""
    return {
        "status": "ok",
        "message": "邮件系统服务正常运行"
    }
