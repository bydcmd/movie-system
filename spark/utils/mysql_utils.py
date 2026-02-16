#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
MySQL 工具模块 - 用于Spark任务中操作MySQL
注意: 这不是Spark自带的，是自定义工具类

功能:
1. 批量更新 (用于回写计算结果到MySQL)
2. 数据清理 (清理当日旧数据)
3. 数据查询 (验证结果)

使用示例:
    from utils.mysql_utils import MySQLHelper
    
    mysql = MySQLHelper('jdbc:mysql://localhost:3306/movie_db', 'root', 'password')
    mysql.batch_update('movies', 'score', score_data)
"""

import pymysql
from typing import List, Tuple, Dict, Any
import logging

# 配置日志
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)


class MySQLHelper:
    """MySQL操作辅助类"""
    
    def __init__(self, host: str, port: int, user: str, password: str, database: str):
        """
        初始化MySQL连接配置
        
        Args:
            host: MySQL主机地址
            port: 端口号
            user: 用户名
            password: 密码
            database: 数据库名
        """
        self.host = host
        self.port = port
        self.user = user
        self.password = password
        self.database = database
        
    @classmethod
    def from_jdbc_url(cls, jdbc_url: str, user: str, password: str) -> 'MySQLHelper':
        """
        从JDBC URL解析连接信息
        
        Args:
            jdbc_url: 如 jdbc:mysql://localhost:3306/movie_db?useSSL=false
            user: 用户名
            password: 密码
            
        Returns:
            MySQLHelper实例
        """
        # 解析 jdbc:mysql://host:port/database?params
        url_part = jdbc_url.replace('jdbc:mysql://', '')
        host_port_db = url_part.split('?')[0]
        
        if ':' in host_port_db:
            host, port_db = host_port_db.split(':', 1)
            if '/' in port_db:
                port, database = port_db.split('/', 1)
            else:
                port = 3306
                database = port_db
        else:
            host = host_port_db.split('/')[0]
            port = 3306
            database = host_port_db.split('/')[1] if '/' in host_port_db else ''
            
        return cls(host, int(port), user, password, database)
    
    def _get_connection(self):
        """获取数据库连接"""
        return pymysql.connect(
            host=self.host,
            port=self.port,
            user=self.user,
            password=self.password,
            database=self.database,
            charset='utf8mb4',
            autocommit=False
        )
    
    def execute_query(self, sql: str, params: Tuple = None) -> List[Dict]:
        """
        执行查询SQL
        
        Args:
            sql: SQL语句
            params: 查询参数
            
        Returns:
            查询结果列表
        """
        conn = self._get_connection()
        try:
            with conn.cursor(pymysql.cursors.DictCursor) as cursor:
                cursor.execute(sql, params)
                return cursor.fetchall()
        finally:
            conn.close()
    
    def execute_update(self, sql: str, params: Tuple = None) -> int:
        """
        执行更新SQL (INSERT/UPDATE/DELETE)
        
        Args:
            sql: SQL语句
            params: 更新参数
            
        Returns:
            影响的行数
        """
        conn = self._get_connection()
        try:
            with conn.cursor() as cursor:
                result = cursor.execute(sql, params)
                conn.commit()
                return result
        except Exception as e:
            conn.rollback()
            raise e
        finally:
            conn.close()
    
    def batch_update(self, table: str, set_column: str, id_column: str, 
                     data: List[Tuple[Any, Any]], batch_size: int = 500) -> int:
        """
        批量更新数据
        
        Args:
            table: 表名
            set_column: 要更新的列名
            id_column: WHERE条件的列名
            data: 数据列表 [(value1, id1), (value2, id2), ...]
            batch_size: 每批更新的数量
            
        Returns:
            总更新行数
        """
        if not data:
            logger.warning("数据为空，跳过更新")
            return 0
            
        conn = self._get_connection()
        total_updated = 0
        
        try:
            with conn.cursor() as cursor:
                sql = f"UPDATE {table} SET {set_column} = %s WHERE {id_column} = %s"
                
                for i in range(0, len(data), batch_size):
                    batch = data[i:i + batch_size]
                    cursor.executemany(sql, batch)
                    conn.commit()
                    total_updated += cursor.rowcount
                    logger.info(f"已更新 {total_updated}/{len(data)} 条记录...")
                    
            logger.info(f"批量更新完成，共更新 {total_updated} 条记录")
            return total_updated
            
        except Exception as e:
            conn.rollback()
            logger.error(f"批量更新失败: {e}")
            raise
        finally:
            conn.close()
    
    def delete_by_date(self, table: str, date_column: str, date_value: str) -> int:
        """
        按日期删除数据
        
        Args:
            table: 表名
            date_column: 日期列名
            date_value: 日期值 (yyyy-MM-dd)
            
        Returns:
            删除的行数
        """
        sql = f"DELETE FROM {table} WHERE {date_column} = %s"
        return self.execute_update(sql, (date_value,))
    
    def verify_count(self, table: str, where_clause: str = None, params: Tuple = None) -> int:
        """
        验证数据条数
        
        Args:
            table: 表名
            where_clause: WHERE条件 (不含WHERE关键字)
            params: 条件参数
            
        Returns:
            数据条数
        """
        sql = f"SELECT COUNT(*) as cnt FROM {table}"
        if where_clause:
            sql += f" WHERE {where_clause}"
            
        result = self.execute_query(sql, params)
        return result[0]['cnt'] if result else 0


# ============================================================
# Spark专用工具函数
# ============================================================

def clean_mysql_data_by_date(jdbc_url: str, db_user: str, db_password: str,
                              table: str, date_column: str, date_value: str) -> int:
    """
    清理MySQL中指定日期的数据 (用于Spark任务前置清理)
    
    Args:
        jdbc_url: JDBC URL
        db_user: 用户名
        db_password: 密码
        table: 表名
        date_column: 日期列名
        date_value: 日期值 (yyyy-MM-dd)
        
    Returns:
        删除的行数
    """
    mysql = MySQLHelper.from_jdbc_url(jdbc_url, db_user, db_password)
    logger.info(f"清理 {table} 中 {date_column} = {date_value} 的数据")
    deleted = mysql.delete_by_date(table, date_column, date_value)
    logger.info(f"清理完成，删除 {deleted} 条记录")
    return deleted


def batch_update_from_spark_df(df, jdbc_url: str, db_user: str, db_password: str,
                                table: str, set_column: str, id_column: str = 'movie_id',
                                batch_size: int = 500):
    """
    从Spark DataFrame批量更新MySQL
    
    注意: 这个函数会在Driver端执行，如果数据量很大，建议先filter/sample
    
    Args:
        df: Spark DataFrame (必须包含 set_column 和 id_column)
        jdbc_url: JDBC URL
        db_user: 用户名
        db_password: 密码
        table: 目标表名
        set_column: 要更新的列
        id_column: ID列名
        batch_size: 批次大小
    """
    # 收集数据到Driver (注意: 大数据量会OOM)
    rows = df.select(id_column, set_column).collect()
    
    # 转换为列表
    data = [(row[set_column], row[id_column]) for row in rows]
    
    # 批量更新
    mysql = MySQLHelper.from_jdbc_url(jdbc_url, db_user, db_password)
    mysql.batch_update(table, set_column, id_column, data, batch_size)


# ============================================================
# 测试代码
# ============================================================

if __name__ == '__main__':
    # 测试JDBC URL解析
    jdbc = "jdbc:mysql://localhost:3306/movie_db?useSSL=false"
    helper = MySQLHelper.from_jdbc_url(jdbc, "root", "password")
    print(f"Host: {helper.host}, Port: {helper.port}, DB: {helper.database}")
    
    # 测试查询
    # result = helper.execute_query("SELECT COUNT(*) as cnt FROM movies")
    # print(result)
