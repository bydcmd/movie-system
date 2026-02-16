# 管理员模块整改

生成时间：2026-02-15 14:46:53
文件名：2026-02-15_144653_admin-module-hardening.md

## 任务背景
用户要求审查管理员模块并允许直接修改问题点。确认的业务决策：
- “删除用户”改为注销（逻辑删除，status=2）。
- 电影/影人 ID 不强制由前端传入，保留缺省自动生成策略。

## 方案详情

### 现状分析
- JWT 拦截器仅基于 Token 设置权限，未校验用户状态/密码版本，存在已禁用/注销/改密用户继续访问风险。
- 管理端删除/更新操作未校验影响行数，可能“假成功”。
- 活跃用户统计包含禁用用户（status=1）。
- 删除电影未清理 allYears 元数据缓存，年份筛选可能残留。
- 管理端分页参数未做最小/最大值校验。

### 技术方案
- 在 JwtInterceptor 中：校验用户存在、状态为正常、passwordVersion 与 Token 一致，并使用数据库角色设置权限。
- deleteUser 改为逻辑注销：更新 status=2、updateTime、passwordVersion++（必要时同步昵称）。
- 管理端增删改校验影响行数，不存在则抛出明确异常。
- 删除电影时补充清理 movieMetadata:allYears 缓存。
- 控制器层加入 @Validated 与分页/ID 参数约束。
- 修正活跃用户统计 SQL 仅统计正常用户。

### 影响范围
- src/main/java/com/movie/backend/config/JwtInterceptor.java
- src/main/java/com/movie/backend/controller/admin/AdminController.java
- src/main/java/com/movie/backend/service/impl/AdminServiceImpl.java
- src/main/resources/mapper/UserMapper.xml
- src/test/java/com/movie/backend/controller/admin/AdminControllerTest.java

## 原子步骤清单

### 步骤 1：安全校验增强
- 操作对象：JwtInterceptor
- 具体动作：校验用户状态与 passwordVersion，使用数据库角色设置权限
- 预期结果：已禁用/注销/改密用户的旧 Token 失效
- 关键里程碑：是

### 步骤 2：管理端参数校验
- 操作对象：AdminController
- 具体动作：添加 @Validated 与 @Min/@Max/@NotBlank 约束
- 预期结果：非法参数直接 400
- 关键里程碑：否

### 步骤 3：删除/更新结果校验与注销逻辑
- 操作对象：AdminServiceImpl
- 具体动作：注销用户（status=2 + passwordVersion++），并校验增删改影响行数
- 预期结果：不存在记录不再“假成功”
- 关键里程碑：是

### 步骤 4：统计与缓存一致性修复
- 操作对象：UserMapper.xml、AdminServiceImpl
- 具体动作：修正活跃用户统计；删除电影补充清理 allYears 缓存
- 预期结果：统计准确、缓存一致
- 关键里程碑：否

### 步骤 5：测试适配
- 操作对象：AdminControllerTest
- 具体动作：Mock UserMapper，保证管理员 Token 校验通过
- 预期结果：测试稳定且覆盖关键路径
- 关键里程碑：否

## 预期结果
- 管理端权限校验更严格，已注销/禁用/改密用户无法继续访问。
- 删除/更新接口返回结果真实可靠。
- 统计与缓存一致性提升。
- 管理端分页参数更安全。
