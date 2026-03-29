根据提供的代码，系统中定义了以下日志（事件）类型，用于记录用户行为并发送到Kafka消息队列：

1. **浏览历史** (`ViewHistoryEvent`)  
   - 记录用户观看电影的时间。
   - 属性：`userId`、`movieId`、`viewTime`

2. **评分** (`RatingEvent`)  
   - 记录用户对电影的评分及操作（CREATE、UPDATE、DELETE、CLEAR）。
   - 属性：`userId`、`movieId`、`rating`、`operation`、`ratingTime`

3. **评论** (`CommentEvent`)  
   - 记录用户发表的短评或长评，以及操作（CREATE、UPDATE、DELETE）。
   - 属性：`userId`、`movieId`、`commentId`、`type`（1短评/2长评）、`operation`、`contentLength`

4. **评论点赞** (`CommentLikeEvent`)  
   - 记录用户对评论的点赞或取消点赞（LIKE、UNLIKE）。
   - 属性：`userId`、`commentId`、`operation`

5. **收藏** (`FavoriteEvent`)  
   - 记录用户将电影添加到收藏夹或移除（ADD、REMOVE）。
   - 属性：`userId`、`movieId`、`folderId`、`operation`

6. **已观看** (`WatchedEvent`)  
   - 记录用户标记电影为已观看的时间。
   - 属性：`userId`、`movieId`、`watchedTime`

7. **收藏夹操作** (`FavoriteFolderActionEvent`)  
   - 记录用户对收藏夹的创建、更新、分享、删除等操作。
   - 属性：`userId`、`folderId`、`folderName`、`isPublic`、`operation`

8. **搜索** (`SearchEvent`)  
   - 记录用户的搜索关键词、过滤条件、结果数量及搜索耗时。
   - 属性：`userId`、`searchKeyword`、`filterConditions`、`resultCount`、`searchTime`

9. **用户注册** (`UserRegisterEvent`)  
   - 记录新用户注册事件。
   - 属性：`userId`

10. **用户登录** (`UserLoginEvent`)  
    - 记录用户登录事件。
    - 属性：`userId`

这些事件均实现了 `KeyedEvent` 接口，提供 `getUserId()` 和 `getKeyId()` 方法，用于Kafka消息分区键的生成。所有事件通过 `EventEnvelope` 封装，包含全局唯一ID、事件类型、发生时间戳和具体数据。系统使用基于**发件箱模式（Outbox Pattern）**的Kafka发布机制，确保事件在事务提交后可靠发送。

---

## ODS/DWD 字段对齐补充（ELT）

Kafka ODS 事件明细表已按契约抽取以下关键字段：

- 收藏夹动作：`folderName`、`isPublic`、`operation`
- 搜索行为：`searchKeyword`、`filterConditions`、`resultCount`、`searchTime`

DWD 宽表在此基础上增加统一行为位：`is_favorite_folder_action`，用于 DWS/ADS 统计口径。