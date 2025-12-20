"""
SMTP 客户端 - 支持本地SMTP与外部SMTP(如163/QQ)发送，支持附件
"""
import asyncio
import smtplib
from email.message import EmailMessage
from pathlib import Path
from app.config import (
    SMTP_HOST,
    SMTP_PORT,
    SMTP_USER,
    SMTP_PASS,
    SMTP_USE_SSL,
    SMTP_USE_STARTTLS,
    MAIL_DOMAIN,
)
from app.services.log_service import LogService


class SMTPClient:
    """SMTP 客户端"""
    
    def __init__(self, host=None, port=SMTP_PORT):
        # 默认使用配置中的 SMTP_HOST/PORT
        if host is None:
            host = SMTP_HOST
        # 本地占位服务时，将 0.0.0.0 作为 127.0.0.1 进行连接
        if host == "0.0.0.0":
            host = "127.0.0.1"
        self.host = host
        self.port = port

    async def send_mail(self, from_addr: str, to_addr: str, subject: str, body: str, reply_to_filename: str = None, attachments: list = None) -> bool:
        """
        通过 SMTP 协议发送邮件

        Args:
            from_addr: 发件人地址
            to_addr: 收件人地址
            subject: 邮件主题
            body: 邮件正文
            reply_to_filename: 可选，回复的原始邮件文件名
            attachments: 可选，附件列表 [{"file_path": "path/to/file", "filename": "original_name.txt"}, ...]

        Returns:
            是否发送成功
        """
        try:
            # 如果配置了外部SMTP用户/密码，则使用带认证的发送
            if SMTP_USER and SMTP_PASS:
                msg = EmailMessage()
                # 为兼容外部服务，强制使用认证账户作为From
                msg["From"] = SMTP_USER
                msg["To"] = to_addr
                msg["Subject"] = subject
                if reply_to_filename:
                    msg["In-Reply-To"] = reply_to_filename
                    msg["References"] = reply_to_filename
                msg.set_content(body)

                # 添加附件
                if attachments:
                    for att in attachments:
                        try:
                            with open(att["file_path"], "rb") as f:
                                content = f.read()
                                msg.add_attachment(
                                    content,
                                    maintype="application",
                                    subtype="octet-stream",
                                    filename=att["filename"]
                                )
                        except Exception as e:
                            LogService.log_system(f"添加附件失败: {att['filename']}, 错误: {e}")

                if SMTP_USE_SSL:
                    with smtplib.SMTP_SSL(self.host, self.port) as server:
                        server.login(SMTP_USER, SMTP_PASS)
                        server.send_message(msg)
                else:
                    with smtplib.SMTP(self.host, self.port) as server:
                        server.ehlo()
                        if SMTP_USE_STARTTLS:
                            server.starttls()
                            server.ehlo()
                        server.login(SMTP_USER, SMTP_PASS)
                        server.send_message(msg)

                LogService.log_system(f"外部SMTP发送成功: {SMTP_USER} -> {to_addr}")
                return True

            # 否则回退到本地占位SMTP（无认证，不支持附件）
            reader, writer = await asyncio.open_connection(self.host, self.port)
            response = await reader.readline()
            LogService.log_system(f"本地SMTP连接: {response.decode().strip()}")
            if not response.startswith(b"220"):
                return False

            await self._send_command(writer, reader, f"HELO {MAIL_DOMAIN}\r\n")
            await self._send_command(writer, reader, f"MAIL FROM:<{from_addr}>\r\n")
            await self._send_command(writer, reader, f"RCPT TO:<{to_addr}>\r\n")
            await self._send_command(writer, reader, "DATA\r\n", expected_code=b"354")
            mail_content = (
                f"From: {from_addr}\r\nTo: {to_addr}\r\nSubject: {subject}\r\n"
            )
            if reply_to_filename:
                mail_content += (
                    f"In-Reply-To: {reply_to_filename}\r\nReferences: {reply_to_filename}\r\n"
                )
            mail_content += "\r\n" + body + "\r\n.\r\n"
            writer.write(mail_content.encode("utf-8"))
            await writer.drain()
            response = await reader.readline()
            if not response.startswith(b"250"):
                return False
            writer.write(b"QUIT\r\n")
            await writer.drain()
            await reader.readline()
            writer.close()
            await writer.wait_closed()
            LogService.log_system(f"本地SMTP发送成功: {from_addr} -> {to_addr}")
            return True

        except Exception as e:
            LogService.log_system(f"SMTP 客户端发送失败: {e}")
            return False

    async def _send_command(self, writer, reader, command: str, expected_code: bytes = b"250"):
        writer.write(command.encode("utf-8"))
        await writer.drain()
        response = await reader.readline()
        LogService.log_system(f"SMTP 命令: {command.strip()} -> {response.decode().strip()}")
        if not response.startswith(expected_code):
            raise Exception(
                f"SMTP 命令失败: {command.strip()}, 响应: {response.decode().strip()}"
            )
