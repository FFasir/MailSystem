"""
数据库引擎与表初始化
"""
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from app.models import Base
from app.config import DATABASE_URL
from sqlalchemy import text

# 创建数据库引擎
engine = create_engine(
    DATABASE_URL,
    connect_args={"check_same_thread": False} if "sqlite" in DATABASE_URL else {}
)

# 创建会话工厂
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)


def init_db():
    """初始化数据库表"""
    Base.metadata.create_all(bind=engine)
    print("数据库表初始化成功")
    # 轻量列保障：为现有 users 表补充缺失列（SQLite）
    try:
        if "sqlite" in DATABASE_URL:
            with engine.connect() as conn:
                cols = conn.execute(text("PRAGMA table_info(users)"))
                names = {row[1] for row in cols}
                if "is_disabled" not in names:
                    conn.execute(text("ALTER TABLE users ADD COLUMN is_disabled INTEGER DEFAULT 0"))
                    print("已为 users 表添加列 is_disabled")
                if "phone_number" not in names:
                    conn.execute(text("ALTER TABLE users ADD COLUMN phone_number TEXT"))
                    print("已为 users 表添加列 phone_number")
                if "email" not in names:
                    conn.execute(text("ALTER TABLE users ADD COLUMN email TEXT"))
                    print("已为 users 表添加列 email")
    except Exception as e:
        # 非致命：打印提示继续运行
        print(f"数据库列检查/升级时出现问题: {e}")


def get_db():
    """获取数据库会话"""
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
