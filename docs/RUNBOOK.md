# 运行与排障

## 本地 Java 开发

Java 21、Maven 3.9、MySQL 8.4、Redis 7.4。创建 `framehub` 数据库并填写 `DB_URL`、`DB_USER`、`DB_PASSWORD`、`REDIS_HOST`。Flyway 自动迁移。启动 `./mvnw spring-boot:run`；默认后端 8081，前端通过 Compose Nginx 8080 访问。

Windows PowerShell 使用 `./mvnw.cmd`。Maven wrapper 自行下载 Maven，需要网络。IDE 推荐安装 Lombok 支持，数据库 UTC。

## Compose

```bash
docker compose ps
docker compose logs --tail=100 api-1 api-2
docker compose logs --tail=100 mysql redis web
docker compose stop
docker compose start
```

`docker compose down` 默认保留 named volumes。不要对需要保留数据的环境使用 `down -v`。应用使用 uid 10001 非 root，启动时单独初始化共享对象卷目录所有权。

健康探针 Compose 检查 TCP 监听，应用 `/actuator/health` 提供 MySQL/Redis 依赖健康。内部 8081、3306、6379 不暴露给宿主；Nginx 隐藏 actuator。正式部署应由编排器使用完整 Actuator readiness，而不只检查端口。当前 nginx Docker DNS 每 5 秒刷新，以兼容重建容器后的 IP 变化。

## 配置与首次管理员

只在首次创建 admin 时使用 BOOTSTRAP_ADMIN_PASSWORD。修改环境变量不能给已存在的管理员改密码。登录后账号设置可核对旧密码改密；不提供忘记密码重置。忘记密码需经可信数据库维护流程生成新 BCrypt hash 并递增 auth_version，不要明文入库。

正式部署：替换所有演示数据库/Redis/管理员密码、DEMO_SEED=false、配置 COS 和百炼、关闭 ENABLE_API_DOCS 并设置 HIDE_API_DOCS=true、限制 ALLOWED_ORIGINS、使用 HTTPS 反代。修改 nginx CSP 的 connect-src 以匹配实际域名的 wss。开发 CORS 不使用 `*` 或 cookie 跨域凭证。

## 故障处理

| 情况 | 处理 |
| --- | --- |
| Docker engine pipe 不存在 | 开启 Docker Desktop 的 Linux 容器引擎；确认 `docker info` 有 Server，之后再启动项目 |
| 公开图库为空 | DEMO_SEED 只在第一次创建公共空间时生效；可手动上传并审核，不删除数据库来强行重新播种 |
| COS 503 | 核对私有桶、region、CAM 最小权限、服务端网络；不要把 secret 或 SDK完整请求写日志 |
| 409 版本冲突 | 获取最新图片，保留/合并用户草稿，携带最新 version 再保存 |
| 409 QUOTA_EXCEEDED | 删除旧素材释放配额；查看 DB 图片字节总和与 used_bytes，不手工减计数绕过限制 |
| WebSocket 无变更 | 确认可信 Origin、一次性票据、Nginx Upgrade、Redis Pub/Sub；15 秒快照会补偿漏消息 |
| AI UNKNOWN | 提交结果不确定，不自动再 POST。核对云控制台 task；确认 task ID 后才做管理员恢复 |
| AI FAILED | 查看稳定 errorCode、云账户模型权限/地域/额度、结果域名和空间容量；不在日志公开提示词或密钥 |
| storage_gc 堆积 | 检查 ObjectStore 删除权限/网络。对象引用不为零时不会删除；清理失败 attempts 增加 |

AI UNKNOWN 人工恢复：先备份数据库；核对云任务属于该用户请求、provider 未改变、空间权限仍有效、任务未过期，再把已确认 task ID 写入 provider_task_id、status 改为 SUBMITTED、error_code=NULL、updated_at=UTC_TIMESTAMP()。禁止修改成 QUEUED 后直接重投未知结果。本项目没有云凭据，未执行过真实云恢复。

## 备份与限制

MySQL 与本地 objects 卷需同时备份，COS 开启版本/生命周期和备份策略；Redis AOF everysec 可能丢最近一秒会话/票据/缓存，重登录可恢复，但不应把它描述成零丢失。图库业务真相在 MySQL 与持久对象存储。

单 MySQL/Redis 不构成生产高可用。当前没有企业计费、SSO、恶意图片机器审核、跨云容灾和像素级 CRDT 协作。公开内容采用人工审核；文件格式验证不等同内容安全审核。
