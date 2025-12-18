"""
日志服务 - SMTP/POP3 操作日志记录
"""
import os
from datetime import datetime
from pathlib import Path
from enum import Enum


class LogType(Enum):
    """日志类型"""
    SMTP = "smtp"
    POP3 = "pop3"
    SYSTEM = "system"


class LogService:
    """日志服务"""
    
    LOG_DIR = "logs"
    
    @staticmethod
    def ensure_log_dir():
        """确保日志目录存在"""
        Path(LogService.LOG_DIR).mkdir(parents=True, exist_ok=True)
    
    @staticmethod
    def log(log_type: LogType, message: str, client_addr: str = None):
        """
        记录日志
        
        Args:
            log_type: 日志类型
            message: 日志消息
            client_addr: 客户端地址
        """
        LogService.ensure_log_dir()
        
        timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        date_str = datetime.now().strftime("%Y-%m-%d")
        
        # 日志文件按日期分割
        log_file = Path(LogService.LOG_DIR) / f"{log_type.value}_{date_str}.log"
        
        # 构建日志行
        log_line = f"[{timestamp}]"
        if client_addr:
            log_line += f" [{client_addr}]"
        log_line += f" {message}\n"
        
        # 追加写入日志
        with open(log_file, "a", encoding="utf-8") as f:
            f.write(log_line)
        
        # 同时打印到控制台
        print(f"[{log_type.value.upper()}] {log_line.strip()}")
    
    @staticmethod
    def log_smtp(message: str, client_addr: str = None):
        """记录 SMTP 日志"""
        LogService.log(LogType.SMTP, message, client_addr)
    
    @staticmethod
    def log_pop3(message: str, client_addr: str = None):
        """记录 POP3 日志"""
        LogService.log(LogType.POP3, message, client_addr)
    
    @staticmethod
    def log_system(message: str):
        """记录系统日志"""
        LogService.log(LogType.SYSTEM, message)
