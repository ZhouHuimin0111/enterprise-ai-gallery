# API 与权限

所有 JSON 响应：`{"code":"OK","message":"成功","data":...}`。失败使用真实 HTTP 400/401/403/404/409/429/503 等状态及枚举 code。二进制图片内容端点返回 PNG；失败仍返回标准 JSON。

登录后 `Authorization: Bearer <token>`，Token 存 Redis 30 分钟滑动过期；WebSocket 使用另行签发的短期票据。ID、空间和角色由服务端校验，上传 body 不接受任意 uploaderId / reviewStatus。

| 路径 | 作用 |
| --- | --- |
| POST `/api/auth/register` / `/login` / `/logout` | 注册、登录、注销 |
| GET `/api/auth/me` | 当前用户 |
| PUT `/api/users/me` / POST `/api/users/me/password` | 修改名称 / 核对旧密码改密，使旧会话失效 |
| GET `/api/admin/users` / PUT或DELETE `/api/admin/users/{id}` | 管理员分页查询 / 启停或逻辑删除普通账号 |
| GET `/api/spaces/public` / `/api/spaces` | 公共空间与模式 / 我的空间 |
| POST `/api/spaces` | `{name,type:PRIVATE或TEAM}` |
| GET / PUT `/api/spaces/{id}` | 空间详情 / 所有者重命名 |
| DELETE `/api/spaces/{id}` | 所有者删除没有图片或 AI 历史的空空间 |
| GET `/api/spaces/{id}/members` | 团队成员 |
| POST `/api/spaces/{id}/invites` | `{role:ADMIN/EDITOR/VIEWER}`，返回一次性 token |
| POST `/api/spaces/accept-invite` | `{token}` 接受邀请 |
| PUT / DELETE `/api/spaces/{id}/members/{userId}` | 改角色 / 移除 |
| GET `/api/spaces/{id}/analytics` / `/audit` | 分析 / 团队管理审计 |
| GET `/api/pictures` | spaceId、q、category、tag、minWidth/minHeight、sort、page、size、mine |
| POST `/api/pictures` | multipart file、spaceId、title、category、tags(JSON数组) |
| GET `/api/pictures/{id}` / `/content` | 元数据 / 鉴权图片二进制 |
| PUT `/api/pictures/{id}` | `{title,category,tags,version}` |
| POST `/api/pictures/{id}/transform` | `{version,x,y,width,height,rotation}`，旋转 0/90/180/270 |
| DELETE `/api/pictures/{id}?version=...` | 版本条件删除 |
| POST `/api/pictures/batch` | `{ids,versions:{图片ID:版本},remove,category,tags}` |
| GET `/api/pictures/review-queue` | 管理员公共待审队列 |
| POST `/api/pictures/{id}/review` | `{version,status:APPROVED或REJECTED,reason}` |
| POST `/api/ai/jobs` | `{spaceId,prompt,title}`，必填 `Idempotency-Key` 8–64 位字母数字下划线/连字符 |
| GET `/api/ai/jobs` / `/{id}` | 只读取自己的任务 |
| POST `/api/collaboration/ticket` | `{pictureId}`，仅团队图片 |
| WS `/ws/collaborate?ticket=...` | 需要可信 Origin；票据只消费一次，消息为快照/变更 |

## 权限矩阵

| 操作 | PUBLIC | PRIVATE | TEAM |
| --- | --- | --- | --- |
| 浏览已审核图片 | 所有人 | 仅本人 | 所有成员 |
| 浏览未审核图片 | 上传者 / 全局管理员 | 不适用 | 不适用 |
| 上传 | 登录用户 | 本人 | OWNER / ADMIN / EDITOR |
| 编辑/删除 | 上传者 / 全局管理员 | 本人 | OWNER / ADMIN / EDITOR |
| 公开审核 | 全局管理员 | 不允许 | 不允许 |
| 空间分析 | 全局管理员 | 本人 | 所有成员 |
| 邀请 EDITOR / VIEWER | 不适用 | 不适用 | OWNER / ADMIN |
| 邀请/调整 ADMIN | 不适用 | 不适用 | OWNER |
| 修改/移除 OWNER | 不适用 | 不适用 | 不允许 |
| 操作审计 | 不提供独立查询 | 不提供独立查询 | OWNER / ADMIN |

全局管理员不能自动跨越私有/团队空间。团队编辑者可编辑任意团队图片，不仅自己的图片；这是团队共享设计。邀请代码为 bearer 凭证，知晓代码者可接受，未绑定邮箱；需要更严格企业邀请时可扩展到绑定用户/企业域和邮件确认。
