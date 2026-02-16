# 高级搜索功能说明

## 功能概述

高级搜索支持根据**关键词、导演、演员、类型、地区、评分、年份**等多维度进行组合搜索。

## 接口信息

- **接口**: `POST /movie/search`
- **Content-Type**: `application/json`
- **返回**: 分页电影列表

## 请求参数 (MovieSearchDTO)

| 字段 | 类型 | 说明 | 示例 |
|------|------|------|------|
| `keyword` | String | 搜索关键词（匹配电影名、别名、导演、演员、编剧、简介） | `"盗梦空间"` |
| `genres` | List<String> | 电影类型筛选（支持多选） | `["科幻", "动作"]` |
| `regions` | List<String> | 地区筛选（支持多选） | `["美国", "中国大陆"]` |
| `directors` | List<String> | **导演筛选（支持多选，新增）** | `["克里斯托弗·诺兰"]` |
| `actors` | List<String> | **演员筛选（支持多选，新增）** | `["莱昂纳多·迪卡普里奥"]` |
| `minScore` | Double | 最低评分 (0.0-10.0) | `8.0` |
| `maxScore` | Double | 最高评分 (0.0-10.0) | `10.0` |
| `year` | String | 特定年份精确筛选 | `"2010"` |
| `startYear` | Integer | 起始年份范围筛选 | `2000` |
| `endYear` | Integer | 结束年份范围筛选 | `2020` |
| `sortBy` | String | 排序字段: score/year/votes | `"score"` |
| `sortOrder` | String | 排序方向: desc/asc | `"desc"` |
| `page` | Integer | 页码，从1开始 | `1` |
| `size` | Integer | 每页数量 | `20` |

## 使用示例

### 1. 基础关键词搜索
```json
{
  "keyword": "盗梦空间",
  "page": 1,
  "size": 10
}
```

### 2. 导演筛选 - 只看诺兰的电影
```json
{
  "directors": ["诺兰"],
  "sortBy": "year",
  "sortOrder": "desc",
  "page": 1,
  "size": 20
}
```
> 💡 **提示**: 支持模糊匹配，输入 "诺兰" 即可匹配 "克里斯托弗·诺兰"

### 3. 演员筛选 - 查找莱昂纳多主演的电影
```json
{
  "actors": ["莱昂纳多"],
  "minScore": 8.0,
  "page": 1,
  "size": 20
}
```
> 💡 **提示**: 输入 "莱昂纳多" 或 "迪卡普里奥" 或 "Leonardo" 或 "DiCaprio" 都能匹配

### 4. 组合搜索 - 诺兰导演的科幻电影
```json
{
  "directors": ["克里斯托弗·诺兰"],
  "genres": ["科幻"],
  "minScore": 8.0,
  "sortBy": "score",
  "sortOrder": "desc",
  "page": 1,
  "size": 10
}
```

### 5. 多导演/多演员筛选
```json
{
  "directors": ["克里斯托弗·诺兰", "史蒂文·斯皮尔伯格"],
  "actors": ["汤姆·汉克斯", "莱昂纳多·迪卡普里奥"],
  "genres": ["剧情"],
  "regions": ["美国"],
  "minScore": 8.0,
  "startYear": 2000,
  "endYear": 2023,
  "page": 1,
  "size": 20
}
```

### 6. 复杂组合搜索
```json
{
  "keyword": "梦境",
  "directors": ["克里斯托弗·诺兰"],
  "genres": ["科幻", "悬疑"],
  "regions": ["美国", "英国"],
  "minScore": 8.5,
  "startYear": 2010,
  "endYear": 2020,
  "sortBy": "votes",
  "sortOrder": "desc",
  "page": 1,
  "size": 10
}
```

## 技术实现

### 导演/演员筛选实现原理

由于导演和演员存储在 MySQL 的 JSON 字段中，使用 `JSON_SEARCH` + `LIKE` 进行模糊匹配筛选：

```sql
-- 导演筛选（模糊匹配）
JSON_SEARCH(directors, 'one', CONCAT('%', #{director}, '%'), NULL, '$[*].name') IS NOT NULL
OR directors LIKE CONCAT('%', #{director}, '%')

-- 演员筛选（模糊匹配）
JSON_SEARCH(actors, 'one', CONCAT('%', #{actor}, '%'), NULL, '$[*].name') IS NOT NULL
OR actors LIKE CONCAT('%', #{actor}, '%')
```

### 模糊匹配特性

- 支持部分匹配：输入 "诺兰" 可以匹配 "克里斯托弗·诺兰"
- 支持中英文：输入 "Nolan" 或 "诺兰" 都能匹配
- 自动处理 JSON 和普通文本格式

### 多选逻辑

- **导演多选**: 多个导演之间是 **OR** 关系（匹配任一导演）
- **演员多选**: 多个演员之间是 **OR** 关系（匹配任一演员）
- **不同类型之间**: 是 **AND** 关系（同时满足所有条件）

例如：`directors: ["诺兰", "斯皮尔"]` 会查找导演是诺兰**或**斯皮尔伯格的电影。

## 注意事项

1. **精确匹配**: 导演和演员名称需要精确匹配（区分大小写，但不区分全半角）
2. **性能考虑**: JSON 搜索在大数据量时可能较慢，建议配合其他条件（如年份、评分）一起使用
3. **通配符支持**: 如需模糊匹配，可以修改 SQL 使用 `%` 通配符

## 与关键词搜索的区别

| 方式 | 匹配范围 | 适用场景 |
|------|----------|----------|
| `keyword` | 电影名、别名、导演、演员、编剧、简介 | 模糊搜索，不知道具体导演/演员名 |
| `directors` | 仅匹配导演名 | 精确筛选特定导演的电影 |
| `actors` | 仅匹配演员名 | 精确筛选特定演员的电影 |

建议：如果要找特定导演的电影，使用 `directors` 字段比 `keyword` 更精确。
