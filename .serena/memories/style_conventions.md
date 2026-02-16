# 代码风格与约定

## 通用
- 文件编码：UTF-8（无 BOM）

## Spark 作业（Python）
- 函数命名：snake_case（如 `parse_args`、`calc_hot_score`）
- 文档字符串：使用三引号，内容为中文（如“解析命令行参数”）
- 业务 SQL：使用多行字符串（f"""...""")
- 日志：以 `print` 为主（从作业文件可见）

说明：风格来自 `spark/jobs/hot_movies_calc.py` 的符号与注释语言。
