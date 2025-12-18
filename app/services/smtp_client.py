"""
SMTP 客户端 - 用于通过 SMTP 协议发送邮件
"""
import socket
import asyncio
from app.config import SMTP_HOST, SMTP_PORT, MAIL_DOMAIN
from app.services.log_service import LogService


class SMTPClient:
    """SMTP 客户端"""
    
    def __init__(self, host=SMTP_HOST, port=SMTP_PORT):
        self.host = host
        self.port = port
    
    async def send_mail(self, from_addr: str, to_addr: str, subject: str, body: str) -> bool:
        """
        通过 SMTP 协议发送邮件
        
        Args:
            from_addr: 发件人地址
            to_addr: 收件人地址
            subject: 邮件主题
            body: 邮件正文
            
        Returns:
            是否发送成功
        """
        try:
            # 连接到 SMTP 服务器
            reader, writer = await asyncio.open_connection(self.host, self.port)
            
            # 读取欢迎消息
            response = await reader.readline()
            LogService.log_system(f"SMTP 客户端连接: {response.decode().strip()}")
            
            if not response.startswith(b"220"):
                return False
            
            # 发送 HELO
            await self._send_command(writer, reader, f"HELO {MAIL_DOMAIN}\r\n")
            
            # 发送 MAIL FROM
            await self._send_command(writer, reader, f"MAIL FROM:<{from_addr}>\r\n")
            
            # 发送 RCPT TO
            await self._send_command(writer, reader, f"RCPT TO:<{to_addr}>\r\n")
            
            # 发送 DATA
            await self._send_command(writer, reader, "DATA\r\n", expected_code=b"354")
            
            # 发送邮件内容
            mail_content = f"From: {from_addr}\r\n"
            mail_content += f"To: {to_addr}\r\n"
            mail_content += f"Subject: {subject}\r\n"
            mail_content += "\r\n"
            mail_content += body
            mail_content += "\r\n.\r\n"
            
            writer.write(mail_content.encode('utf-8'))
            await writer.drain()
            
            response = await reader.readline()
            LogService.log_system(f"SMTP 数据发送完成: {response.decode().strip()}")
            
            if not response.startswith(b"250"):
                return False
            
            # 发送 QUIT
            writer.write(b"QUIT\r\n")
            await writer.drain()
            await reader.readline()
            
            # 关闭连接
            writer.close()
            await writer.wait_closed()
            
            LogService.log_system(f"邮件发送成功: {from_addr} -> {to_addr}")
            return True
            
        except Exception as e:
            LogService.log_system(f"SMTP 客户端发送失败: {e}")
            return False
    
    async def _send_command(self, writer, reader, command: str, expected_code: bytes = b"250"):
        """发送 SMTP 命令并验证响应"""
        writer.write(command.encode('utf-8'))
        await writer.drain()
        
        response = await reader.readline()
        LogService.log_system(f"SMTP 命令: {command.strip()} -> {response.decode().strip()}")
        
        if not response.startswith(expected_code):
            raise Exception(f"SMTP 命令失败: {command.strip()}, 响应: {response.decode().strip()}")
