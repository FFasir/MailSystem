"""
FastAPI 入口，启动时同时拉起 SMTP/POP3 占位服务
"""
import asyncio
from contextlib import asynccontextmanager
from fastapi import FastAPI
from app.db import init_db
from app.services.smtp_server import SMTPServer
from app.services.pop3_server import POP3Server
from app.routers import health, auth, admin, mail


# 全局任务存储
smtp_task = None
pop3_task = None


@asynccontextmanager
async def lifespan(app: FastAPI):
    """应用生命周期管理"""
    global smtp_task, pop3_task
    
    # 启动时的操作
    print("=" * 50)
    print("邮件系统启动中...")
    print("=" * 50)
    
    # 初始化数据库
    init_db()
    
    # 启动 SMTP 和 POP3 服务
    smtp_server = SMTPServer()
    pop3_server = POP3Server()
    
    smtp_task = asyncio.create_task(smtp_server.start())
    pop3_task = asyncio.create_task(pop3_server.start())
    
    print("=" * 50)
    print("邮件系统启动完成！")
    print("=" * 50)
    
    yield
    
    # 关闭时的操作
    print("\n关闭邮件系统...")
    smtp_task.cancel()
    pop3_task.cancel()
    try:
        await smtp_task
    except asyncio.CancelledError:
        pass
    try:
        await pop3_task
    except asyncio.CancelledError:
        pass
    print("邮件系统已关闭")


# 创建 FastAPI 应用
app = FastAPI(
    title="邮件系统 API",
    description="基于 SMTP/POP3 的邮件服务系统",
    version="V4.0.0",
    lifespan=lifespan
)

# 注册路由
app.include_router(health.router)
app.include_router(auth.router)
app.include_router(admin.router)
app.include_router(mail.router)


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(
        app,
        host="127.0.0.1",
        port=8000,
        reload=True
    )
