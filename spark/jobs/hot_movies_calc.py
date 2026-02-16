#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
电影热度计算任务 - PySpark版本
原Java逻辑: SparkHotMoviesJob
计算公式: HotScore = (ViewCount * 0.1) + (CommentCount * 0.5) + (RatingCount * 0.3) + (AvgRating * 1.0)
"""

import sys
import argparse
from datetime import datetime, timedelta
from pyspark.sql import SparkSession
from pyspark.sql.functions import (
    col, count, avg, coalesce, lit, current_date,
    year, month, weekofyear, to_date, expr, when
)


def parse_args():
    """解析命令行参数"""
    parser = argparse.ArgumentParser(description='电影热度计算任务')
    parser.add_argument('--jdbc-url', default='jdbc:mysql://localhost:3306/movie_db?useSSL=false',
                        help='MySQL JDBC连接URL')
    parser.add_argument('--db-user', default='root', help='数据库用户名')
    parser.add_argument('--db-password', default='Hadoop@123', help='数据库密码')
    parser.add_argument('--hive-db', default='ods_movie_db', help='Hive数据库名')
    parser.add_argument('--w-view', type=float, default=0.1, help='浏览量权重')
    parser.add_argument('--w-comment', type=float, default=0.5, help='评论权重')
    parser.add_argument('--w-rating', type=float, default=0.3, help='评分数权重')
    parser.add_argument('--w-score', type=float, default=1.0, help='平均分权重')
    parser.add_argument('--calc-date', help='计算日期(yyyy-MM-dd)，默认今天')
    return parser.parse_args()


def clean_today_data(jdbc_url, db_user, db_password, calc_date):
    """清理当日旧数据"""
    import sys
    import os
    # 添加utils目录到路径
    sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..'))
    from utils.mysql_utils import clean_mysql_data_by_date
    
    deleted = clean_mysql_data_by_date(
        jdbc_url, db_user, db_password, 
        'stats_hot_movies', 'calc_date', calc_date
    )
    return deleted


def calc_hot_score(spark, hive_db, period, calc_date, w_v, w_c, w_r, w_s):
    """
    计算热度分数
    :param period: DAILY/WEEKLY/MONTHLY
    :param calc_date: 计算日期字符串 yyyy-MM-dd
    """
    from pyspark.sql.functions import datediff

    calc_dt = datetime.strptime(calc_date, '%Y-%m-%d')

    # 根据周期设置过滤条件
    if period == 'DAILY':
        # 当日
        date_filter = f"to_date(action_time) = '{calc_date}'"
    elif period == 'WEEKLY':
        # 本周 (周一到周日)
        week_start = calc_dt - timedelta(days=calc_dt.weekday())
        week_end = week_start + timedelta(days=6)
        date_filter = (
            f"to_date(action_time) >= '{week_start.strftime('%Y-%m-%d')}' "
            f"and to_date(action_time) <= '{week_end.strftime('%Y-%m-%d')}'"
        )
    elif period == 'MONTHLY':
        # 当月
        date_filter = f"year(action_time) = {calc_dt.year} and month(action_time) = {calc_dt.month}"
    else:
        raise ValueError(f"Unknown period: {period}")

    print(f"[{period}] 使用过滤条件: {date_filter}")

    # 为避免懒加载下临时视图被后续覆盖，按周期/日期区分视图名
    view_suffix = f"{period.lower()}_{calc_date.replace('-', '')}"
    v_view = f"v_stats_{view_suffix}"
    c_view = f"c_stats_{view_suffix}"
    r_view = f"r_stats_{view_suffix}"

    # 1. 统计浏览量
    view_sql = f"""
        SELECT movie_id, COUNT(*) as view_cnt
        FROM {hive_db}.ods_view_history
        WHERE {date_filter}
        GROUP BY movie_id
    """
    v_count = spark.sql(view_sql)
    v_count.createOrReplaceTempView(v_view)

    # 2. 统计评论量
    comment_sql = f"""
        SELECT movie_id, COUNT(*) as comment_cnt
        FROM {hive_db}.ods_comments
        WHERE {date_filter}
        GROUP BY movie_id
    """
    c_count = spark.sql(comment_sql)
    c_count.createOrReplaceTempView(c_view)

    # 3. 统计评分数和平均分
    rating_sql = f"""
        SELECT 
            movie_id, 
            COUNT(*) as rating_cnt,
            AVG(rating) as avg_score
        FROM {hive_db}.ods_ratings
        WHERE {date_filter}
        GROUP BY movie_id
    """
    r_stats = spark.sql(rating_sql)
    r_stats.createOrReplaceTempView(r_view)

    # 4. 全连接计算热度分数
    result_sql = f"""
        SELECT 
            COALESCE(v.movie_id, c.movie_id, r.movie_id) as movie_id,
            (
                COALESCE(v.view_cnt, 0) * {w_v} + 
                COALESCE(c.comment_cnt, 0) * {w_c} + 
                COALESCE(r.rating_cnt, 0) * {w_r} + 
                COALESCE(r.avg_score, 0) * {w_s}
            ) as hot_score,
            '{period}' as period_type,
            '{calc_date}' as calc_date
        FROM {v_view} v
        FULL OUTER JOIN {c_view} c ON v.movie_id = c.movie_id
        FULL OUTER JOIN {r_view} r ON COALESCE(v.movie_id, c.movie_id) = r.movie_id
    """

    return spark.sql(result_sql)


def write_to_mysql(df, jdbc_url, db_user, db_password):
    """写入MySQL"""
    print(f"开始写入MySQL: {jdbc_url}")
    
    df.write \
        .format("jdbc") \
        .option("url", jdbc_url) \
        .option("dbtable", "stats_hot_movies") \
        .option("user", db_user) \
        .option("password", db_password) \
        .option("driver", "com.mysql.cj.jdbc.Driver") \
        .mode("append") \
        .save()
    
    print("写入完成")


def main():
    args = parse_args()
    
    # 计算日期
    calc_date = args.calc_date or datetime.now().strftime('%Y-%m-%d')
    
    print(f"""
========================================
启动电影热度计算任务
========================================
计算日期: {calc_date}
权重配置: View={args.w_view}, Comment={args.w_comment}, Rating={args.w_rating}, Score={args.w_score}
Hive库: {args.hive_db}
    """)
    
    # 清理旧数据
    clean_today_data(args.jdbc_url, args.db_user, args.db_password, calc_date)
    
    # 初始化SparkSession
    spark = SparkSession.builder \
        .appName(f"MovieHotScoreCalc_{calc_date}") \
        .enableHiveSupport() \
        .getOrCreate()
    
    try:
        # 计算三种周期的热度
        result_daily = calc_hot_score(
            spark, args.hive_db, 'DAILY', calc_date,
            args.w_view, args.w_comment, args.w_rating, args.w_score
        )
        result_weekly = calc_hot_score(
            spark, args.hive_db, 'WEEKLY', calc_date,
            args.w_view, args.w_comment, args.w_rating, args.w_score
        )
        result_monthly = calc_hot_score(
            spark, args.hive_db, 'MONTHLY', calc_date,
            args.w_view, args.w_comment, args.w_rating, args.w_score
        )
        
        # 合并结果
        final_result = result_daily.union(result_weekly).union(result_monthly)
        
        # 显示部分结果
        print("计算结果预览:")
        final_result.show(10, truncate=False)
        
        # 统计数量
        count = final_result.count()
        print(f"共计算 {count} 条热度记录")
        
        # 写入MySQL
        write_to_mysql(final_result, args.jdbc_url, args.db_user, args.db_password)
        
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
