# 电影接口测试文档

## 测试文件

- **MovieControllerIntegrationTest.java** - 完整的集成测试，覆盖所有改进功能

## 测试覆盖范围

### 1. 获取电影详情接口 (`GET /movie/detail/{id}`)
- ✅ 正常获取电影详情
- ✅ 电影不存在（返回404）
- ✅ 电影ID为0（参数验证失败）
- ✅ 电影ID为负数（参数验证失败）

### 2. 高级搜索接口 (`POST /movie/search`)
- ✅ 正常搜索返回分页数据
- ✅ 关键词超过100字符（参数验证失败）
- ✅ 评分超出0-10范围（参数验证失败）
- ✅ 年份格式错误（参数验证失败）
- ✅ 页码为0（参数验证失败）
- ✅ 每页数量超过100（参数验证失败）

### 3. 获取热门电影接口 (`GET /movie/hot`)
- ✅ 正常获取热门电影列表
- ✅ 使用默认limit参数
- ✅ limit为0（参数验证失败）
- ✅ limit超过100（参数验证失败）

### 4. 按类型筛选接口 (`GET /movie/genre/{genre}`)
- ✅ 正常按类型筛选
- ✅ 页码为0（参数验证失败）

### 5. 按年份筛选接口 (`GET /movie/year/{year}`)
- ✅ 正常按年份筛选
- ✅ 年份早于1900（参数验证失败）

### 6. 获取最新电影接口 (`GET /movie/latest`)
- ✅ 正常获取最新电影
- ✅ 使用默认分页参数

## 运行测试

### 方式一：运行所有测试
```bash
mvn test
```

### 方式二：只运行电影接口测试
```bash
mvn test -Dtest=MovieControllerIntegrationTest
```

### 方式三：运行特定测试类
```bash
# 运行获取详情的所有测试
mvn test -Dtest=MovieControllerIntegrationTest$GetDetailTests

# 运行搜索接口的所有测试
mvn test -Dtest=MovieControllerIntegrationTest$SearchTests
```

### 方式四：在 IDE 中运行
1. 打开 `MovieControllerIntegrationTest.java`
2. 右键点击类名或方法名
3. 选择 "Run" 或 "Debug"

## 测试结果说明

### 成功的测试输出示例
```
MockHttpServletRequest:
      HTTP Method = GET
      Request URI = /movie/detail/1
       Parameters = {}
          Headers = []
             Body = null

MockHttpServletResponse:
           Status = 200
    Error message = null
          Headers = [Content-Type:"application/json"]
     Content type = application/json
             Body = {"code":200,"message":"Success","data":{"id":1,"name":"盗梦空间","score":9.3}}
```

### 参数验证失败的输出示例
```
MockHttpServletResponse:
           Status = 200
    Error message = null
          Headers = [Content-Type:"application/json"]
     Content type = application/json
             Body = {"code":400,"message":"电影ID必须大于0","data":null}
```

## 测试特性

### 1. 使用 @Nested 组织测试
每个接口的测试用例都放在一个嵌套类中，结构清晰，易于维护。

### 2. 使用 @DisplayName 描述测试
每个测试都有清晰的中文描述，方便理解测试意图。

### 3. MockMvc 完整测试链路
所有测试都通过 MockMvc 模拟 HTTP 请求，真实测试 Controller 层的行为。

### 4. 全面的参数验证测试
覆盖了所有参数验证注解（@Min、@Max、@Size、@Pattern等）的测试场景。

## 注意事项

1. **Mock 数据**：所有测试使用 `@MockBean` 模拟 Service 层，不依赖真实数据库
2. **快速运行**：因为没有真实数据库交互，测试运行速度很快
3. **独立性**：每个测试都是独立的，互不影响
4. **可重复性**：每次运行结果一致

## 扩展建议

如果需要测试真实数据库交互，可以：
1. 使用 `@DataJpaTest` 或 `@SpringBootTest` 搭配测试数据库
2. 使用 H2 内存数据库进行集成测试
3. 使用 Testcontainers 搭建真实 MySQL 环境测试
