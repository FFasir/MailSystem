"""
邮件存储服务 - 邮件文件保存与管理
"""
import os
from datetime import datetime
from pathlib import Path
from app.config import MAIL_DOMAIN


class MailStorageService:
    """邮件存储服务"""
    
    BASE_DIR = "mailbox"
    
    @staticmethod
    def ensure_user_mailbox(username: str) -> Path:
        """确保用户邮箱目录存在"""
        user_dir = Path(MailStorageService.BASE_DIR) / username
        user_dir.mkdir(parents=True, exist_ok=True)
        return user_dir
    
    @staticmethod
    def save_mail(to_addr: str, from_addr: str, subject: str, body: str) -> str:
        """
        保存邮件到文件系统
        
        Args:
            to_addr: 收件人地址
            from_addr: 发件人地址
            subject: 主题
            body: 邮件正文
            
        Returns:
            保存的文件路径
        """
        # 提取用户名（去掉 @domain 部分）
        username = to_addr.split("@")[0] if "@" in to_addr else to_addr
        
        # 确保用户邮箱目录存在
        user_dir = MailStorageService.ensure_user_mailbox(username)
        
        # 生成文件名（时间戳）
        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S_%f")
        filename = f"{timestamp}.txt"
        filepath = user_dir / filename
        
        # 写入邮件内容（标准RFC格式）
        with open(filepath, "w", encoding="utf-8") as f:
            f.write(f"From: {from_addr}\n")
            f.write(f"To: {to_addr}\n")
            f.write(f"Subject: {subject}\n")
            f.write(f"Date: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n")
            f.write("\n")
            f.write(body)
        
        return str(filepath)
    
    @staticmethod
    def list_user_mails(username: str) -> list:
        """列出用户的所有邮件"""
        user_dir = Path(MailStorageService.BASE_DIR) / username
        if not user_dir.exists():
            return []
        
        mails = []
        for mail_file in sorted(user_dir.glob("*.txt"), reverse=True):
            mails.append({
                "filename": mail_file.name,
                "path": str(mail_file),
                "size": mail_file.stat().st_size,
                "created": datetime.fromtimestamp(mail_file.stat().st_ctime)
            })
        return mails
    
    @staticmethod
    def read_mail(username: str, filename: str) -> str:
        """读取邮件内容"""
        filepath = Path(MailStorageService.BASE_DIR) / username / filename
        if not filepath.exists():
            return None
        
        with open(filepath, "r", encoding="utf-8") as f:
            return f.read()
    
    @staticmethod
    def delete_mail(username: str, filename: str) -> bool:
        """删除邮件"""
        filepath = Path(MailStorageService.BASE_DIR) / username / filename
        if filepath.exists():
            filepath.unlink()
            return True
        return False
