"""
POP3 服务 - V3 版本实现完整协议逻辑
"""
import asyncio
from pathlib import Path
from app.config import POP3_HOST, POP3_PORT
from app.services.mail_storage import MailStorageService
from app.services.log_service import LogService
from app.db import SessionLocal
from app.models import User
from app.services.auth_service import AuthService


class POP3Session:
    """POP3 会话状态"""
    def __init__(self):
        self.username = None
        self.authenticated = False
        self.mails = []
        self.deleted_mails = set()  # 标记为删除的邮件


class POP3Server:
    """POP3 服务器"""
    
    def __init__(self, host=POP3_HOST, port=POP3_PORT):
        self.host = host
        self.port = port
        self.server = None
    
    async def handle_client(self, reader, writer):
        """处理客户端连接"""
        addr = writer.get_extra_info('peername')
        client_addr = f"{addr[0]}:{addr[1]}"
        
        LogService.log_pop3(f"客户端连接", client_addr)
        
        # 创建会话
        session = POP3Session()
        
        try:
            # 发送欢迎消息
            await self.send_response(writer, "+OK POP3 server ready")
            
            while True:
                data = await reader.readline()
                if not data:
                    break
                
                message = data.decode('utf-8', errors='replace').strip()
                LogService.log_pop3(f"收到命令: {message}", client_addr)
                
                # 处理命令
                response = await self.handle_command(message, session, client_addr)
                
                if response:
                    await self.send_response(writer, response)
                
                # 如果是 QUIT 命令，处理删除并断开
                if message.upper() == "QUIT":
                    break
        
        except Exception as e:
            LogService.log_pop3(f"错误: {e}", client_addr)
        finally:
            LogService.log_pop3(f"客户端断开", client_addr)
            writer.close()
            await writer.wait_closed()
    
    async def send_response(self, writer, response: str):
        """发送响应"""
        writer.write(f"{response}\r\n".encode('utf-8'))
        await writer.drain()
    
    async def handle_command(self, command: str, session: POP3Session, client_addr: str) -> str:
        """处理 POP3 命令"""
        
        parts = command.split(None, 1)
        if not parts:
            return "-ERR Bad command"
        
        cmd = parts[0].upper()
        args = parts[1] if len(parts) > 1 else ""
        
        # USER - 用户名
        if cmd == "USER":
            if not args:
                return "-ERR Missing username"
            
            session.username = args
            session.authenticated = False
            LogService.log_pop3(f"用户名: {args}", client_addr)
            return "+OK User name accepted"
        
        # PASS - 密码
        elif cmd == "PASS":
            if not session.username:
                return "-ERR No username given"
            
            if not args:
                return "-ERR Missing password"
            
            # 验证用户名和密码
            db = SessionLocal()
            try:
                user = db.query(User).filter(User.username == session.username).first()
                
                if not user or not AuthService.verify_password(args, user.password):
                    LogService.log_pop3(f"认证失败: {session.username}", client_addr)
                    return "-ERR Authentication failed"
                
                # 认证成功，加载邮件列表
                session.authenticated = True
                session.mails = MailStorageService.list_user_mails(session.username)
                session.deleted_mails = set()
                
                LogService.log_pop3(f"认证成功: {session.username}, 邮件数: {len(session.mails)}", client_addr)
                return f"+OK Mailbox locked and ready, {len(session.mails)} messages"
            
            finally:
                db.close()
        
        # 以下命令需要认证
        if not session.authenticated:
            return "-ERR Not authenticated"
        
        # STAT - 邮箱状态
        if cmd == "STAT":
            mail_count = len(session.mails) - len(session.deleted_mails)
            total_size = sum(m['size'] for i, m in enumerate(session.mails) if i not in session.deleted_mails)
            return f"+OK {mail_count} {total_size}"
        
        # LIST - 邮件列表
        elif cmd == "LIST":
            if args:
                # LIST 指定邮件
                try:
                    msg_num = int(args) - 1  # 转为 0 索引
                    if msg_num < 0 or msg_num >= len(session.mails):
                        return "-ERR No such message"
                    if msg_num in session.deleted_mails:
                        return "-ERR Message deleted"
                    
                    mail = session.mails[msg_num]
                    return f"+OK {msg_num + 1} {mail['size']}"
                except ValueError:
                    return "-ERR Invalid message number"
            else:
                # LIST 所有邮件
                mail_count = len(session.mails) - len(session.deleted_mails)
                total_size = sum(m['size'] for i, m in enumerate(session.mails) if i not in session.deleted_mails)
                
                response = f"+OK {mail_count} messages ({total_size} octets)\r\n"
                for i, mail in enumerate(session.mails):
                    if i not in session.deleted_mails:
                        response += f"{i + 1} {mail['size']}\r\n"
                response += "."
                return response
        
        # RETR - 获取邮件内容
        elif cmd == "RETR":
            if not args:
                return "-ERR Missing message number"
            
            try:
                msg_num = int(args) - 1
                if msg_num < 0 or msg_num >= len(session.mails):
                    return "-ERR No such message"
                if msg_num in session.deleted_mails:
                    return "-ERR Message deleted"
                
                mail = session.mails[msg_num]
                content = MailStorageService.read_mail(session.username, mail['filename'])
                
                if not content:
                    return "-ERR Cannot read message"
                
                LogService.log_pop3(f"读取邮件: {mail['filename']}", client_addr)
                
                response = f"+OK {mail['size']} octets\r\n"
                # 为前端附件加载提供稳定的真实文件名标识（不依赖序号/排序）
                response += f"X-Mail-Filename: {mail['filename']}\r\n"
                response += content + "\r\n."
                return response
            
            except ValueError:
                return "-ERR Invalid message number"
        
        # DELE - 删除邮件
        elif cmd == "DELE":
            if not args:
                return "-ERR Missing message number"
            
            try:
                msg_num = int(args) - 1
                if msg_num < 0 or msg_num >= len(session.mails):
                    return "-ERR No such message"
                if msg_num in session.deleted_mails:
                    return "-ERR Message already deleted"
                
                session.deleted_mails.add(msg_num)
                LogService.log_pop3(f"标记删除邮件: {msg_num + 1}", client_addr)
                return f"+OK Message {msg_num + 1} deleted"
            
            except ValueError:
                return "-ERR Invalid message number"
        
        # RSET - 重置（取消删除标记）
        elif cmd == "RSET":
            deleted_count = len(session.deleted_mails)
            session.deleted_mails = set()
            return f"+OK {deleted_count} messages undeleted"
        
        # NOOP - 空操作
        elif cmd == "NOOP":
            return "+OK"
        
        # QUIT - 退出
        elif cmd == "QUIT":
            # 执行真正的删除
            deleted_count = 0
            for msg_num in session.deleted_mails:
                mail = session.mails[msg_num]
                if MailStorageService.delete_mail(session.username, mail['filename']):
                    deleted_count += 1
                    LogService.log_pop3(f"删除邮件: {mail['filename']}", client_addr)
            
            return f"+OK POP3 server signing off ({deleted_count} messages deleted)"
        
        # 未知命令
        else:
            return "-ERR Command not recognized"
    
    async def start(self):
        """启动 POP3 服务"""
        self.server = await asyncio.start_server(
            self.handle_client, self.host, self.port
        )
        LogService.log_system(f"POP3 服务启动: {self.host}:{self.port}")
        async with self.server:
            await self.server.serve_forever()
