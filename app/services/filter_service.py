"""
过滤服务 - IP 黑名单和邮箱地址过滤
"""
from pathlib import Path
from typing import Set
import re


class FilterService:
    """过滤服务"""
    
    FILTER_DIR = "filters"
    IP_BLACKLIST_FILE = "ip_blacklist.txt"
    EMAIL_BLACKLIST_FILE = "email_blacklist.txt"
    
    # 内存缓存
    _ip_blacklist: Set[str] = None
    _email_blacklist: Set[str] = None
    
    @staticmethod
    def ensure_filter_dir():
        """确保过滤器目录存在"""
        Path(FilterService.FILTER_DIR).mkdir(parents=True, exist_ok=True)
    
    @staticmethod
    def load_ip_blacklist() -> Set[str]:
        """加载 IP 黑名单"""
        FilterService.ensure_filter_dir()
        filepath = Path(FilterService.FILTER_DIR) / FilterService.IP_BLACKLIST_FILE
        
        if not filepath.exists():
            return set()
        
        with open(filepath, 'r', encoding='utf-8') as f:
            return {line.strip() for line in f if line.strip() and not line.startswith('#')}
    
    @staticmethod
    def load_email_blacklist() -> Set[str]:
        """加载邮箱黑名单"""
        FilterService.ensure_filter_dir()
        filepath = Path(FilterService.FILTER_DIR) / FilterService.EMAIL_BLACKLIST_FILE
        
        if not filepath.exists():
            return set()
        
        with open(filepath, 'r', encoding='utf-8') as f:
            return {line.strip().lower() for line in f if line.strip() and not line.startswith('#')}
    
    @staticmethod
    def get_ip_blacklist() -> Set[str]:
        """获取 IP 黑名单（带缓存）"""
        if FilterService._ip_blacklist is None:
            FilterService._ip_blacklist = FilterService.load_ip_blacklist()
        return FilterService._ip_blacklist
    
    @staticmethod
    def get_email_blacklist() -> Set[str]:
        """获取邮箱黑名单（带缓存）"""
        if FilterService._email_blacklist is None:
            FilterService._email_blacklist = FilterService.load_email_blacklist()
        return FilterService._email_blacklist
    
    @staticmethod
    def is_ip_blocked(ip: str) -> bool:
        """检查 IP 是否被屏蔽"""
        blacklist = FilterService.get_ip_blacklist()
        return ip in blacklist
    
    @staticmethod
    def is_email_blocked(email: str) -> bool:
        """检查邮箱地址是否被屏蔽"""
        blacklist = FilterService.get_email_blacklist()
        email_lower = email.lower()
        
        # 精确匹配
        if email_lower in blacklist:
            return True
        
        # 域名匹配（如 @spam.com）
        for blocked in blacklist:
            if blocked.startswith('@') and email_lower.endswith(blocked):
                return True
        
        return False
    
    @staticmethod
    def add_ip_to_blacklist(ip: str) -> bool:
        """添加 IP 到黑名单"""
        FilterService.ensure_filter_dir()
        filepath = Path(FilterService.FILTER_DIR) / FilterService.IP_BLACKLIST_FILE
        
        # 验证 IP 格式
        if not re.match(r'^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$', ip):
            return False
        
        # 读取现有黑名单
        blacklist = FilterService.load_ip_blacklist()
        
        if ip in blacklist:
            return False  # 已存在
        
        # 追加写入
        with open(filepath, 'a', encoding='utf-8') as f:
            f.write(f"{ip}\n")
        
        # 更新缓存
        FilterService._ip_blacklist = None
        return True
    
    @staticmethod
    def add_email_to_blacklist(email: str) -> bool:
        """添加邮箱到黑名单"""
        FilterService.ensure_filter_dir()
        filepath = Path(FilterService.FILTER_DIR) / FilterService.EMAIL_BLACKLIST_FILE
        
        email_lower = email.lower()
        
        # 读取现有黑名单
        blacklist = FilterService.load_email_blacklist()
        
        if email_lower in blacklist:
            return False  # 已存在
        
        # 追加写入
        with open(filepath, 'a', encoding='utf-8') as f:
            f.write(f"{email_lower}\n")
        
        # 更新缓存
        FilterService._email_blacklist = None
        return True
    
    @staticmethod
    def remove_ip_from_blacklist(ip: str) -> bool:
        """从黑名单移除 IP"""
        FilterService.ensure_filter_dir()
        filepath = Path(FilterService.FILTER_DIR) / FilterService.IP_BLACKLIST_FILE
        
        if not filepath.exists():
            return False
        
        # 读取所有行
        with open(filepath, 'r', encoding='utf-8') as f:
            lines = f.readlines()
        
        # 过滤掉要删除的 IP
        new_lines = [line for line in lines if line.strip() != ip]
        
        if len(new_lines) == len(lines):
            return False  # 未找到
        
        # 写回
        with open(filepath, 'w', encoding='utf-8') as f:
            f.writelines(new_lines)
        
        # 更新缓存
        FilterService._ip_blacklist = None
        return True
    
    @staticmethod
    def remove_email_from_blacklist(email: str) -> bool:
        """从黑名单移除邮箱"""
        FilterService.ensure_filter_dir()
        filepath = Path(FilterService.FILTER_DIR) / FilterService.EMAIL_BLACKLIST_FILE
        
        if not filepath.exists():
            return False
        
        email_lower = email.lower()
        
        # 读取所有行
        with open(filepath, 'r', encoding='utf-8') as f:
            lines = f.readlines()
        
        # 过滤掉要删除的邮箱
        new_lines = [line for line in lines if line.strip().lower() != email_lower]
        
        if len(new_lines) == len(lines):
            return False  # 未找到
        
        # 写回
        with open(filepath, 'w', encoding='utf-8') as f:
            f.writelines(new_lines)
        
        # 更新缓存
        FilterService._email_blacklist = None
        return True
    
    @staticmethod
    def reload_filters():
        """重新加载过滤器（清除缓存）"""
        FilterService._ip_blacklist = None
        FilterService._email_blacklist = None
