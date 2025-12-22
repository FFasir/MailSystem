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
    def ensure_user_draftbox(username: str) -> Path:
        """确保用户草稿箱目录存在"""
        draft_dir = Path(MailStorageService.BASE_DIR) / username / "drafts"
        draft_dir.mkdir(parents=True, exist_ok=True)
        return draft_dir

    @staticmethod
    def save_draft(username: str, to_addr: str, subject: str, body: str, filename: str = None) -> str:
        """保存草稿"""
        draft_dir = MailStorageService.ensure_user_draftbox(username)
        
        if not filename:
            # 如果没有提供文件名，创建新的
            timestamp = datetime.now().strftime("%Y%m%d_%H%M%S_%f")
            filename = f"{timestamp}.txt"
        
        filepath = draft_dir / filename
        
        with open(filepath, "w", encoding="utf-8") as f:
            f.write(f"To: {to_addr}\n")
            f.write(f"Subject: {subject}\n")
            f.write(f"Date: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n")
            f.write("\n")
            f.write(body)
            
        return filename

    @staticmethod
    def list_drafts(username: str) -> list:
        """列出用户的所有草稿"""
        draft_dir = Path(MailStorageService.BASE_DIR) / username / "drafts"
        if not draft_dir.exists():
            return []
        
        drafts = []
        for draft_file in sorted(draft_dir.glob("*.txt"), reverse=True):
            if draft_file.is_file():
                # 读取草稿以获取主题和收件人（简单解析）
                subject = "(无主题)"
                to_addr = ""
                try:
                    with open(draft_file, "r", encoding="utf-8") as f:
                        lines = f.readlines()
                        for line in lines:
                            if line.startswith("Subject:"):
                                subject = line[8:].strip() or "(无主题)"
                            elif line.startswith("To:"):
                                to_addr = line[3:].strip()
                            if line.strip() == "": # End of headers
                                break
                except:
                    pass

                drafts.append({
                    "filename": draft_file.name,
                    "path": str(draft_file),
                    "size": draft_file.stat().st_size,
                    "created": datetime.fromtimestamp(draft_file.stat().st_ctime),
                    "subject": subject,
                    "to": to_addr
                })
        return drafts

    @staticmethod
    def read_draft(username: str, filename: str) -> str:
        """读取草稿内容"""
        filepath = Path(MailStorageService.BASE_DIR) / username / "drafts" / filename
        if not filepath.exists():
            return None
        
        with open(filepath, "r", encoding="utf-8") as f:
            return f.read()

    @staticmethod
    def delete_draft(username: str, filename: str) -> bool:
        """删除草稿"""
        filepath = Path(MailStorageService.BASE_DIR) / username / "drafts" / filename
        if filepath.exists():
            filepath.unlink()
            return True
        return False

    
    @staticmethod
    def ensure_user_sentbox(username: str) -> Path:
        """确保用户发件箱目录存在"""
        sent_dir = Path(MailStorageService.BASE_DIR) / username / "sent"
        sent_dir.mkdir(parents=True, exist_ok=True)
        return sent_dir
    @staticmethod
    def save_mail(to_addr: str, from_addr: str, subject: str, body: str, reply_to_filename: str = None, filename: str = None) -> str:
        """
        保存邮件到收件箱

        Args:
            to_addr: 收件人地址
            from_addr: 发件人地址
            subject: 邮件主题
            body: 邮件正文
            reply_to_filename: 可选，回复的原始邮件文件名（用于建立回复关联）
        """
        # 提取用户名（去掉 @domain 部分）
        username = to_addr.split("@")[0] if "@" in to_addr else to_addr

        # 确保用户邮箱目录存在
        user_dir = MailStorageService.ensure_user_mailbox(username)

        # 生成或使用指定文件名
        if filename:
            # 兼容传入不带后缀的情况
            if not filename.endswith(".txt"):
                filename = f"{filename}.txt"
        else:
            timestamp = datetime.now().strftime("%Y%m%d_%H%M%S_%f")
            filename = f"{timestamp}.txt"

        filepath = user_dir / filename

        # 写入邮件内容（标准RFC格式）
        with open(filepath, "w", encoding="utf-8") as f:
            f.write(f"From: {from_addr}\n")
            f.write(f"To: {to_addr}\n")
            f.write(f"Subject: {subject}\n")
            f.write(f"Date: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n")
            # 如果是回复邮件，添加回复关联头
            if reply_to_filename:
                f.write(f"In-Reply-To: {reply_to_filename}\n")
                f.write(f"References: {reply_to_filename}\n")
            f.write("\n")
            f.write(body)

        return str(filepath)

    @staticmethod
    def save_sent_mail(from_addr: str, to_addrs: list, subject: str, body: str, reply_to_filename: str = None, filename: str = None) -> str:
        """
        保存邮件到发件箱

        Args:
            from_addr: 发件人地址
            to_addrs: 收件人地址列表
            subject: 邮件主题
            body: 邮件正文
            reply_to_filename: 可选，回复的原始邮件文件名（用于建立回复关联）
        """
        # 提取发件人用户名
        username = from_addr.split("@")[0] if "@" in from_addr else from_addr

        # 确保发件箱目录存在
        sent_dir = MailStorageService.ensure_user_sentbox(username)

        # 生成或使用指定文件名
        if filename:
            if not filename.endswith(".txt"):
                filename = f"{filename}.txt"
        else:
            timestamp = datetime.now().strftime("%Y%m%d_%H%M%S_%f")
            filename = f"{timestamp}.txt"
        filepath = sent_dir / filename

        # 写入邮件内容
        to_str = ", ".join(to_addrs)
        with open(filepath, "w", encoding="utf-8") as f:
            f.write(f"From: {from_addr}\n")
            f.write(f"To: {to_str}\n")
            f.write(f"Subject: {subject}\n")
            f.write(f"Date: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n")
            # 如果是回复邮件，添加回复关联头
            if reply_to_filename:
                f.write(f"In-Reply-To: {reply_to_filename}\n")
                f.write(f"References: {reply_to_filename}\n")
            f.write("\n")
            f.write(body)

        return str(filepath)

    @staticmethod
    def list_user_mails(username: str) -> list:
        """列出用户的所有邮件（收件箱）"""
        user_dir = Path(MailStorageService.BASE_DIR) / username
        if not user_dir.exists():
            return []

        mails = []
        # 只列出根目录下的 .txt 文件，不递归（避免包含 sent 子目录）
        for mail_file in sorted(user_dir.glob("*.txt"), reverse=True):
            if mail_file.is_file():
                mails.append({
                    "filename": mail_file.name,
                    "path": str(mail_file),
                    "size": mail_file.stat().st_size,
                    "created": datetime.fromtimestamp(mail_file.stat().st_ctime)
                })
        return mails

    @staticmethod
    def list_sent_mails(username: str) -> list:
        """列出用户的所有已发送邮件"""
        sent_dir = Path(MailStorageService.BASE_DIR) / username / "sent"
        if not sent_dir.exists():
            return []

        mails = []
        for mail_file in sorted(sent_dir.glob("*.txt"), reverse=True):
            if mail_file.is_file():
                mails.append({
                    "filename": mail_file.name,
                    "path": str(mail_file),
                    "size": mail_file.stat().st_size,
                    "created": datetime.fromtimestamp(mail_file.stat().st_ctime)
                })
        return mails

    @staticmethod
    def read_mail(username: str, filename: str) -> str:
        """读取邮件内容（收件箱）"""
        filepath = Path(MailStorageService.BASE_DIR) / username / filename
        if not filepath.exists():
            return None

        with open(filepath, "r", encoding="utf-8") as f:
            return f.read()

    @staticmethod
    def read_sent_mail(username: str, filename: str) -> str:
        """读取已发送邮件内容"""
        filepath = Path(MailStorageService.BASE_DIR) / username / "sent" / filename
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

    @staticmethod
    def get_original_mail_subject(username: str, in_reply_to: str) -> str:
        """
        根据In-Reply-To获取原邮件的主题

        Args:
            username: 用户名
            in_reply_to: In-Reply-To的值（可能是文件名或POP3_MAIL_xxx格式）

        Returns:
            原邮件的主题，如果找不到则返回None
        """
        # 如果是POP3_MAIL_格式，无法直接获取（因为POP3邮件是动态的）
        if in_reply_to.startswith("POP3_MAIL_"):
            return None

        # 尝试从收件箱读取
        filepath = Path(MailStorageService.BASE_DIR) / username / in_reply_to
        if not filepath.exists():
            # 尝试从发件箱读取
            filepath = Path(MailStorageService.BASE_DIR) / username / "sent" / in_reply_to

        if not filepath.exists():
            return None

        try:
            with open(filepath, "r", encoding="utf-8") as f:
                content = f.read()
                lines = content.split("\n")
                for line in lines:
                    if line.startswith("Subject:"):
                        return line[8:].strip()
        except Exception:
            pass

        return None

    @staticmethod
    def get_reply_info(username: str, filename: str) -> dict:
        """
        获取邮件的回复关联信息

        Args:
            username: 用户名
            filename: 邮件文件名

        Returns:
            包含回复信息的字典，包括：
            - in_reply_to: 回复的原始邮件文件名（如果有）
            - is_reply: 是否是回复邮件
        """
        # 先尝试从收件箱读取
        filepath = Path(MailStorageService.BASE_DIR) / username / filename
        if not filepath.exists():
            # 尝试从发件箱读取
            filepath = Path(MailStorageService.BASE_DIR) / username / "sent" / filename

        if not filepath.exists():
            return {"in_reply_to": None, "is_reply": False}

        try:
            with open(filepath, "r", encoding="utf-8") as f:
                content = f.read()
                lines = content.split("\n")

                in_reply_to = None
                for line in lines:
                    if line.startswith("In-Reply-To:"):
                        in_reply_to = line[13:].strip()
                        break

                return {
                    "in_reply_to": in_reply_to,
                    "is_reply": in_reply_to is not None
                }
        except Exception:
            return {"in_reply_to": None, "is_reply": False}

    @staticmethod
    def get_attachment_dir(username: str, mail_filename: str) -> Path:
        """获取邮件的附件目录"""
        # 邮件文件名为 20251220_121857_613099.txt
        # 附件目录为 mailbox/username/attachments/20251220_121857_613099/
        mail_name = mail_filename.replace(".txt", "")
        attach_dir = Path(MailStorageService.BASE_DIR) / username / "attachments" / mail_name
        return attach_dir

    @staticmethod
    def ensure_attachment_dir(username: str, mail_filename: str) -> Path:
        """确保邮件的附件目录存在"""
        attach_dir = MailStorageService.get_attachment_dir(username, mail_filename)
        attach_dir.mkdir(parents=True, exist_ok=True)
        return attach_dir

    @staticmethod
    def save_attachment(username: str, mail_filename: str, file_content: bytes, original_filename: str) -> bool:
        """保存附件文件"""
        try:
            attach_dir = MailStorageService.ensure_attachment_dir(username, mail_filename)
            filepath = attach_dir / original_filename
            with open(filepath, "wb") as f:
                f.write(file_content)
            return True
        except Exception:
            return False

    @staticmethod
    def get_attachments(username: str, mail_filename: str) -> list:
        """获取邮件的所有附件信息"""
        attach_dir = MailStorageService.get_attachment_dir(username, mail_filename)
        if not attach_dir.exists():
            return []
        
        attachments = []
        for file in attach_dir.glob("*"):
            if file.is_file():
                attachments.append({
                    "filename": file.name,
                    "size": file.stat().st_size,
                    "content_type": "application/octet-stream"  # 默认二进制
                })
        return attachments

    @staticmethod
    def get_attachment_filepaths(username: str, mail_filename: str) -> list:
        """获取附件的完整路径信息列表"""
        attach_dir = MailStorageService.get_attachment_dir(username, mail_filename)
        if not attach_dir.exists():
            return []

        files = []
        for file in attach_dir.glob("*"):
            if file.is_file():
                files.append({
                    "file_path": str(file),
                    "filename": file.name
                })
        return files

    @staticmethod
    def copy_attachments(src_username: str, src_mail_filename: str, dst_username: str, dst_mail_filename: str) -> None:
        """拷贝附件到目标用户的附件目录"""
        from shutil import copy2

        src_dir = MailStorageService.get_attachment_dir(src_username, src_mail_filename)
        if not src_dir.exists():
            return

        dst_dir = MailStorageService.ensure_attachment_dir(dst_username, dst_mail_filename)
        for file in src_dir.glob("*"):
            if file.is_file():
                copy2(file, dst_dir / file.name)

    @staticmethod
    def read_attachment(username: str, mail_filename: str, attachment_filename: str) -> bytes:
        """读取附件文件"""
        attach_dir = MailStorageService.get_attachment_dir(username, mail_filename)
        filepath = attach_dir / attachment_filename
        
        if not filepath.exists():
            return None
        
        try:
            with open(filepath, "rb") as f:
                return f.read()
        except Exception:
            return None

    @staticmethod
    def delete_attachment(username: str, mail_filename: str, attachment_filename: str) -> bool:
        """删除附件"""
        try:
            attach_dir = MailStorageService.get_attachment_dir(username, mail_filename)
            filepath = attach_dir / attachment_filename
            
            if filepath.exists():
                filepath.unlink()
                return True
            return False
        except Exception:
            return False

    @staticmethod
    def find_recent_attachment_mail_filename(username: str, max_age_seconds: int = 180) -> str | None:
        """从附件目录中推断最近一次上传所对应的 mail_filename。

        用于兼容客户端未传 mail_filename 的情况，避免邮件文件名与附件目录不一致。
        """
        attach_root = Path(MailStorageService.BASE_DIR) / username / "attachments"
        if not attach_root.exists():
            return None

        now_ts = datetime.now().timestamp()
        candidates: list[Path] = [p for p in attach_root.glob("*") if p.is_dir()]
        if not candidates:
            return None

        # 取最近修改的附件目录
        latest = max(candidates, key=lambda p: p.stat().st_mtime)
        age = now_ts - latest.stat().st_mtime
        if age > max_age_seconds:
            return None

        # 目录名是去掉 .txt 的邮件名
        return f"{latest.name}.txt"

