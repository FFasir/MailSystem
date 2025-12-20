"""
IMAP 客户端 - 从外部邮箱（如163）同步邮件到本地
"""
import imaplib
import email
from email.header import decode_header
from datetime import datetime
from app.config import (
    IMAP_HOST,
    IMAP_PORT,
    IMAP_USER,
    IMAP_PASS,
    IMAP_USE_SSL,
    IMAP_SYNC_ENABLED,
    IMAP_SYNC_TO_USER,
)
from app.services.log_service import LogService
from app.services.mail_storage import MailStorageService


class IMAPClient:
    """IMAP 客户端 - 用于从外部邮箱同步邮件"""

    def __init__(self):
        self.host = IMAP_HOST
        self.port = IMAP_PORT or 993
        self.user = IMAP_USER
        self.password = IMAP_PASS
        self.use_ssl = IMAP_USE_SSL

    def sync_new_mails(self) -> tuple[int, int]:
        """
        同步新邮件到本地用户收件箱
        
        Returns:
            (成功数, 失败数)
        """
        if not IMAP_SYNC_ENABLED:
            return 0, 0

        if not all([self.host, self.user, self.password]):
            LogService.log_system("IMAP同步: 配置不完整，跳过")
            return 0, 0

        success_count = 0
        fail_count = 0

        try:
            # 连接 IMAP 服务器
            if self.use_ssl:
                mail = imaplib.IMAP4_SSL(self.host, self.port)
            else:
                mail = imaplib.IMAP4(self.host, self.port)

            # 登录
            mail.login(self.user, self.password)
            LogService.log_system(f"IMAP登录成功: {self.user}@{self.host}")

            # 选择收件箱（必须在login后执行）
            status, _ = mail.select("INBOX")
            if status != "OK":
                LogService.log_system("IMAP选择收件箱失败")
                mail.logout()
                return 0, 0

            # 搜索未读邮件（UNSEEN）
            status, message_ids = mail.search(None, "UNSEEN")
            if status != "OK":
                LogService.log_system("IMAP搜索失败")
                mail.logout()
                return 0, 0

            # 获取邮件ID列表
            mail_ids = message_ids[0].split()
            if not mail_ids:
                LogService.log_system("IMAP同步: 无新邮件")
                mail.logout()
                return 0, 0

            LogService.log_system(f"IMAP同步: 发现 {len(mail_ids)} 封新邮件")

            # 处理每封邮件
            for mail_id in mail_ids:
                try:
                    # 获取邮件内容
                    status, msg_data = mail.fetch(mail_id, "(RFC822)")
                    if status != "OK":
                        fail_count += 1
                        continue

                    # 解析邮件
                    raw_email = msg_data[0][1]
                    msg = email.message_from_bytes(raw_email)

                    # 提取发件人
                    from_header = msg.get("From", "")
                    from_addr = self._decode_header(from_header)

                    # 提取主题
                    subject_header = msg.get("Subject", "")
                    subject = self._decode_header(subject_header)

                    # 提取正文
                    body = self._get_email_body(msg)

                    # 保存到本地用户收件箱
                    MailStorageService.save_mail(
                        username=IMAP_SYNC_TO_USER,
                        from_addr=from_addr,
                        subject=subject,
                        body=body
                    )

                    success_count += 1
                    LogService.log_system(
                        f"IMAP同步成功: {from_addr} -> {IMAP_SYNC_TO_USER}, 主题: {subject}"
                    )

                except Exception as e:
                    fail_count += 1
                    LogService.log_system(f"IMAP同步邮件失败: {e}")

            # 登出
            mail.logout()
            LogService.log_system(
                f"IMAP同步完成: 成功 {success_count}, 失败 {fail_count}"
            )

        except imaplib.IMAP4.error as e:
            LogService.log_system(f"IMAP连接/认证失败: {e}")
            return success_count, fail_count
        except Exception as e:
            LogService.log_system(f"IMAP同步异常: {e}")
            return success_count, fail_count

        return success_count, fail_count

    def _decode_header(self, header_value: str) -> str:
        """解码邮件头（处理编码）"""
        if not header_value:
            return ""
        
        decoded_parts = []
        for part, encoding in decode_header(header_value):
            if isinstance(part, bytes):
                try:
                    decoded_parts.append(part.decode(encoding or "utf-8", errors="ignore"))
                except Exception:
                    decoded_parts.append(part.decode("utf-8", errors="ignore"))
            else:
                decoded_parts.append(str(part))
        
        return "".join(decoded_parts)

    def _get_email_body(self, msg) -> str:
        """提取邮件正文（优先纯文本）"""
        body = ""
        
        if msg.is_multipart():
            for part in msg.walk():
                content_type = part.get_content_type()
                content_disposition = str(part.get("Content-Disposition", ""))

                # 跳过附件
                if "attachment" in content_disposition:
                    continue

                # 获取纯文本
                if content_type == "text/plain":
                    try:
                        payload = part.get_payload(decode=True)
                        charset = part.get_content_charset() or "utf-8"
                        body = payload.decode(charset, errors="ignore")
                        break
                    except Exception:
                        pass
                
                # 如果没有纯文本，尝试HTML
                if not body and content_type == "text/html":
                    try:
                        payload = part.get_payload(decode=True)
                        charset = part.get_content_charset() or "utf-8"
                        body = payload.decode(charset, errors="ignore")
                    except Exception:
                        pass
        else:
            # 非multipart邮件
            try:
                payload = msg.get_payload(decode=True)
                charset = msg.get_content_charset() or "utf-8"
                body = payload.decode(charset, errors="ignore")
            except Exception:
                body = str(msg.get_payload())

        return body.strip()
