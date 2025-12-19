"""
SMTP 服务 - V4 版本增加过滤功能
"""
import asyncio
import re
from app.config import SMTP_HOST, SMTP_PORT, MAIL_DOMAIN
from app.services.mail_storage import MailStorageService
from app.services.log_service import LogService
from app.services.filter_service import FilterService
from app.db import get_db
from app.db import SessionLocal
from app.models import User
from app.utils.validators import is_valid_email, extract_username




class SMTPSession:
    """SMTP 会话状态"""
    def __init__(self):
        self.mail_from = None
        self.rcpt_to = []
        self.data_mode = False
        self.mail_data = []
        self.authenticated = False


class SMTPServer:
    """SMTP 服务器"""
    
    def __init__(self, host=SMTP_HOST, port=SMTP_PORT):
        self.host = host
        self.port = port
        self.server = None
    
    async def handle_client(self, reader, writer):
        """处理客户端连接"""
        addr = writer.get_extra_info('peername')
        client_addr = f"{addr[0]}:{addr[1]}"
        client_ip = addr[0]
        
        # 检查 IP 黑名单
        if FilterService.is_ip_blocked(client_ip):
            LogService.log_smtp(f"IP 被拒绝（黑名单）", client_addr)
            await self.send_response(writer, "554 IP address blocked")
            writer.close()
            await writer.wait_closed()
            return
        
        LogService.log_smtp(f"客户端连接", client_addr)
        
        # 创建会话
        session = SMTPSession()
        
        try:
            # 发送欢迎消息
            await self.send_response(writer, f"220 {MAIL_DOMAIN} SMTP Service Ready")
            
            while True:
                data = await reader.readline()
                if not data:
                    break
                
                message = data.decode('utf-8', errors='replace').strip()
                
                if not session.data_mode:
                    LogService.log_smtp(f"收到命令: {message}", client_addr)
                
                # 处理命令
                response = await self.handle_command(message, session, client_addr)
                
                if response:
                    await self.send_response(writer, response)
                
                # 如果是 QUIT 命令，断开连接
                if message.upper() == "QUIT":
                    break
        
        except Exception as e:
            LogService.log_smtp(f"错误: {e}", client_addr)
        finally:
            LogService.log_smtp(f"客户端断开", client_addr)
            writer.close()
            await writer.wait_closed()
    
    async def send_response(self, writer, response: str):
        """发送响应"""
        writer.write(f"{response}\r\n".encode('utf-8'))
        await writer.drain()
    
    async def handle_command(self, command: str, session: SMTPSession, client_addr: str) -> str:
        """处理 SMTP 命令"""
        
        # DATA 模式 - 接收邮件内容
        if session.data_mode:
            if command == ".":
                # 邮件结束标记
                session.data_mode = False
                mail_body = "\n".join(session.mail_data)
                
                # 解析邮件头和正文
                subject = self.extract_subject(mail_body)
                body = self.extract_body(mail_body)
                
                # 保存邮件（为每个收件人保存一份）
                for rcpt in session.rcpt_to:
                    try:
                        filepath = MailStorageService.save_mail(
                            to_addr=rcpt,
                            from_addr=session.mail_from,
                            subject=subject,
                            body=body
                        )
                        LogService.log_smtp(f"邮件已保存: {filepath}", client_addr)
                    except Exception as e:
                        LogService.log_smtp(f"保存邮件失败: {e}", client_addr)
                
                # 保存邮件到发件人的发件箱
                try:
                    sent_path = MailStorageService.save_sent_mail(
                        from_addr=session.mail_from,
                        to_addrs=session.rcpt_to,
                        subject=subject,
                        body=body
                    )
                    LogService.log_smtp(f"已发送邮件保存: {sent_path}", client_addr)
                except Exception as e:
                    LogService.log_smtp(f"保存已发送邮件失败: {e}", client_addr)

                # 重置会话
                session.mail_from = None
                session.rcpt_to = []
                session.mail_data = []
                
                return "250 OK: Message accepted for delivery"
            else:
                # 收集邮件数据
                session.mail_data.append(command)
                return None  # DATA 模式不返回响应
        
        # 普通命令模式
        cmd_upper = command.upper()
        
        # HELO / EHLO
        if cmd_upper.startswith("HELO") or cmd_upper.startswith("EHLO"):
            return f"250 {MAIL_DOMAIN} Hello"
        
        # MAIL FROM
        elif cmd_upper.startswith("MAIL FROM"):
            match = re.search(r'<(.+?)>', command, re.IGNORECASE)
            if match:
                from_addr = match.group(1)
                
                # 检查发件人是否在黑名单
                if FilterService.is_email_blocked(from_addr):
                    LogService.log_smtp(f"发件人被拒绝（黑名单）: {from_addr}", client_addr)
                    return "550 Sender address rejected"
                
                session.mail_from = from_addr
                LogService.log_smtp(f"发件人: {session.mail_from}", client_addr)
                return "250 OK"
            else:
                return "501 Syntax error in MAIL FROM"
        
        # RCPT TO
        elif cmd_upper.startswith("RCPT TO"):
            match = re.search(r'<(.+?)>', command, re.IGNORECASE)
            if match:
                rcpt = match.group(1)

                # 检查邮箱格式
                if not is_valid_email(rcpt):
                    LogService.log_smtp(f"收件人格式无效: {rcpt}", client_addr)
                    return "550 Invalid recipient address format"

                # 检查收件人是否存在
                db = SessionLocal()
                username = extract_username(rcpt)
                user = db.query(User).filter(User.username == username).first()
                db.close()

                if not user:
                    LogService.log_smtp(f"收件人不存在: {rcpt}", client_addr)
                    return "550 Recipient does not exist"

                # 检查收件人是否在黑名单
                if FilterService.is_email_blocked(rcpt):
                    LogService.log_smtp(f"收件人被拒绝（黑名单）: {rcpt}", client_addr)
                    return "550 Recipient address rejected"
                
                # 验证收件人是否存在（提取邮箱用户名部分）
                if '@' in rcpt:
                    username = rcpt.split('@')[0]
                else:
                    username = rcpt
                
                # 查询数据库验证用户是否存在
                db_gen = get_db()
                db = next(db_gen)
                try:
                    user = db.query(User).filter(User.username == username).first()
                    if not user:
                        LogService.log_smtp(f"收件人不存在: {rcpt}", client_addr)
                        return "550 5.1.1 User not found"
                finally:
                    db.close()
                    try:
                        next(db_gen)
                    except StopIteration:
                        pass
                
                session.rcpt_to.append(rcpt)
                LogService.log_smtp(f"收件人: {rcpt}", client_addr)
                return "250 OK"
            else:
                return "501 Syntax error in RCPT TO"
        
        # DATA
        elif cmd_upper == "DATA":
            if not session.mail_from:
                return "503 Bad sequence: MAIL FROM required"
            if not session.rcpt_to:
                return "503 Bad sequence: RCPT TO required"
            
            session.data_mode = True
            LogService.log_smtp("开始接收邮件数据", client_addr)
            return "354 Start mail input; end with <CRLF>.<CRLF>"
        
        # RSET
        elif cmd_upper == "RSET":
            session.mail_from = None
            session.rcpt_to = []
            session.mail_data = []
            session.data_mode = False
            return "250 OK"
        
        # NOOP
        elif cmd_upper == "NOOP":
            return "250 OK"
        
        # QUIT
        elif cmd_upper == "QUIT":
            return "221 Bye"
        
        # 未知命令
        else:
            return "500 Command not recognized"
    
    @staticmethod
    def extract_subject(mail_data: str) -> str:
        """提取邮件主题"""
        lines = mail_data.split("\n")
        for line in lines:
            if line.lower().startswith("subject:"):
                return line[8:].strip()
        return "(无主题)"
    
    @staticmethod
    def extract_body(mail_data: str) -> str:
        """提取邮件正文（去掉邮件头）"""
        # 查找空行，空行后是正文
        parts = mail_data.split("\n\n", 1)
        if len(parts) > 1:
            return parts[1]
        return mail_data
    
    async def start(self):
        """启动 SMTP 服务"""
        self.server = await asyncio.start_server(
            self.handle_client, self.host, self.port
        )
        LogService.log_system(f"SMTP 服务启动: {self.host}:{self.port}")
        async with self.server:
            await self.server.serve_forever()
