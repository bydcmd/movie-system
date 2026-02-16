#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
评分融合同步任务 - PySpark版本
原Java逻辑: SparkRatingSyncJob
算法: 贝叶斯加权平均，融合豆瓣评分和本站评分（本站评分 1-5 星会换算为 1-10）
公式: (豆瓣分 * K + 本站平均分 * 本站人数) / (K + 本站人数)
"""

import sys
import argparse
from pyspark.sql import SparkSession
from pyspark.sql.functions import (
    col, avg, count, when, coalesce, lit, round as spark_round
)


def parse_args():
    """解析命令行参数"""
    parser = argparse.ArgumentParser(description='评分融合同步任务')
    parser.add_argument('--jdbc-url', default='jdbc:mysql://localhost:3306/movie_db?useSSL=false&allowPublicKeyRetrieval=true',
                        help='MySQL JDBC连接URL')
    parser.add_argument('--db-user', default='root', help='数据库用户名')
    parser.add_argument('--db-password', default='root', help='数据库密码')
    parser.add_argument('--hive-db', default='ods_movie_db', help='Hive数据库名')
    parser.add_argument('--min-votes', type=int, default=10,
                        help='起算阈值:本站至少多少人打分才使用融合算法')
    parser.add_argument('--smooth-k', type=int, default=50,
                        help='平滑常数K:相当于增加K个"隐形豆瓣用户"来稳定分数')
    return parser.parse_args()


def calc_weighted_score(spark, hive_db, min_votes, smooth_k):
    """
    计算融合评分
    逻辑:
    1. 本站没人评 -> 用豆瓣分
    2. 本站人数 < min_votes -> 强制用豆瓣分(防止偏差)
    3. 否则 -> 使用贝叶斯公式融合
    """
    print(f"计算参数: 起算人数={min_votes}, 平滑常数K={smooth_k}")
    
    # 1. 读取本站评分数据 (从Hive)
    inner_stats_sql = f"""
        SELECT 
            movie_id, 
            AVG(rating) as inner_avg_raw, 
            COUNT(*) as inner_count
        FROM {hive_db}.ods_ratings
        GROUP BY movie_id
    """
    inner_stats = spark.sql(inner_stats_sql)
    inner_stats.createOrReplaceTempView("inner_stats")
    
    # 2. 读取豆瓣数据 (从MySQL)
    movie_info = spark.read \
        .format("jdbc") \
        .option("url", args.jdbc_url) \
        .option("dbtable", "movies") \
        .option("user", args.db_user) \
        .option("password", args.db_password) \
        .option("driver", "com.mysql.cj.jdbc.Driver") \
        .load() \
        .select("movie_id", "douban_score")
    
    movie_info.createOrReplaceTempView("movie_info")
    
    # 3. 关联数据并计算新分数
    result_sql = f"""
        SELECT 
            m.movie_id,
            m.douban_score,
            CASE 
                WHEN i.inner_avg_raw IS NULL THEN NULL
                WHEN i.inner_avg_raw > 5 THEN i.inner_avg_raw / 5
                ELSE i.inner_avg_raw * 2
            END as inner_avg,
            i.inner_count,
            CASE 
                WHEN i.inner_avg_raw IS NULL THEN m.douban_score
                WHEN i.inner_count < {min_votes} THEN m.douban_score
                ELSE (
                    m.douban_score * {smooth_k} + 
                    (CASE 
                        WHEN i.inner_avg_raw > 5 THEN i.inner_avg_raw / 5
                        ELSE i.inner_avg_raw * 2
                     END) * i.inner_count
                ) / ({smooth_k} + i.inner_count)
            END as new_score
        FROM movie_info m
        LEFT JOIN inner_stats i ON m.movie_id = i.movie_id
    """
    
    return spark.sql(result_sql)


def update_mysql_batch(df, jdbc_url, db_user, db_password):
    """
    批量更新MySQL
    使用 utils.mysql_utils 工具类
    """
    import sys
    import os
    # 添加utils目录到路径
    sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..'))
    from utils.mysql_utils import MySQLHelper
    
    print("开始批量更新MySQL...")
    
    # 收集数据到Driver (注意: 如果数据量很大，需要考虑分批处理)
    rows = df.collect()
    
    # 准备数据 [(score, movie_id), ...]
    data = []
    for row in rows:
        movie_id = row['movie_id']
        raw_score = row['new_score']
        final_score = round(raw_score, 1) if raw_score is not None else 0.0
        data.append((final_score, movie_id))
    
    # 使用工具类批量更新
    mysql = MySQLHelper.from_jdbc_url(jdbc_url, db_user, db_password)
    updated_count = mysql.batch_update(
        table='movies',
        set_column='score',
        id_column='movie_id',
        data=data,
        batch_size=500
    )
    
    print(f"总共更新 {updated_count} 条电影评分")


def main():
    global args
    args = parse_args()
    
    print(f"""
========================================
启动评分融合同步任务
========================================
MySQL: {args.jdbc_url}
起算人数: {args.min_votes}
平滑常数K: {args.smooth_k}
    """)
    
    # 初始化SparkSession
    spark = SparkSession.builder \
        .appName("MovieRatingSync") \
        .enableHiveSupport() \
        .getOrCreate()
    
    try:
        # 计算融合评分
        result_df = calc_weighted_score(
            spark, args.hive_db, args.min_votes, args.smooth_k
        )
        
        # 显示部分结果
        print("计算结果预览 (前10条):")
        result_df.show(10, truncate=False)
        
        # 统计信息
        total_count = result_df.count()
        using_douban = result_df.filter(
            (col("inner_avg").isNull()) | (col("inner_count") < args.min_votes)
        ).count()
        using_mixed = total_count - using_douban
        
        print(f"""
评分统计:
- 总电影数: {total_count}
- 使用豆瓣分: {using_douban} ({using_douban/total_count*100:.1f}%)
- 使用融合分: {using_mixed} ({using_mixed/total_count*100:.1f}%)
        """)
        
        # 更新到MySQL
        update_mysql_batch(result_df, args.jdbc_url, args.db_user, args.db_password)
        
        print("""
========================================
任务执行成功!
========================================
        """)
        
    except Exception as e:
        print(f"任务执行失败: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)
    finally:
        spark.stop()


if __name__ == '__main__':
    main()
