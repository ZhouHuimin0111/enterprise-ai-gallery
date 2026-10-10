# 验证记录

验证日期：2026-10-10。业务与测试代码提交：`fc91694d64c27e1536006abf6a9f64e540bfbc3b`。

可核验来源：[GitHub Actions 完整运行](https://github.com/ZhouHuimin0111/enterprise-ai-gallery/actions/runs/38039738237)。环境为 Ubuntu、Java 21、真实 MySQL 8.4、Redis 7.4、Nginx、两个 Spring Boot 实例。对象存储使用共享本地卷，AI 使用明确标记的 demo provider。

## 结果

| 检查 | 结果 | 证据 |
| --- | --- | --- |
| 后端单元测试 | 12 项通过 | [JUnit 摘要](verification/backend-tests.json) |
| 真实 MySQL/Redis 集成测试 | 18 项通过 | [JUnit 摘要](verification/backend-tests.json) |
| 双实例 HTTP / WebSocket 业务链路 | 全部通过 | [E2E 原始报告](verification/e2e.json) |
| 独立 SQL 一致性核验 | 三项异常计数均为 0 | [SQL 输出](verification/database-check.txt) |
| 桌面 / 手机 Chromium 操作 | 8 项通过 | [运行摘要](verification/summary.json)、CI 日志 |
| 实际页面截图 | 16 张 | [截图目录](screenshots/) |

截图是测试运行中的真实页面，素材来自项目自带的程序化图形，不代表 AI 模型效果。CI 原始 XML、Playwright HTML 报告及容器日志在该次运行的 `verification-reports` artifact 中保留 14 天；上述摘要、E2E/SQL 输出和截图永久随仓库保存。

## 与 Java 后端面试相关的验证

- 私有图片的元数据、检索和二进制内容均拒绝其他用户；全局管理员也不能绕过私人空间权限。
- 空间容量设为两张图片，提交 20 个上传请求，恰好接受 2 个；16 个请求使用同一旧版本编辑，恰好 1 个成功，其余冲突，不覆盖已提交内容。这是并发正确性测试，不是 QPS 压测。
- 两人同时接受同一邀请只有 1 人成功；团队 VIEWER 无法上传或编辑，团队 ADMIN 无法提升或移除 OWNER。
- 同用户 5 个并发 AI 创建请求最多接受 3 个进行中任务；幂等请求只创建一个任务及最终图片；不确定提交不自动再次调用模型。
- 同一 Redis Token 能访问两个实例；HTTP 修改明确发生在 api-1，真实 WebSocket 连接在 api-2，能收到对应版本；重复使用握手票据返回 401。
- 移除团队成员后读取被拒绝，已建立连接在后续快照关闭；旧版本保存返回 409。浏览器测试还验证两名成员的页面实时同步。
- 在 AI 任务处于 SUBMITTED 时停止两个应用，再启动，原任务 ID 继续轮询并完成；验证使用可跨进程恢复的 demo provider。
- 批量权限失败整批回滚；实际裁剪旋转后尺寸变化，空间字节数与图片之和一致；独立 SQL 检查配额偏差、重复成员、成功任务缺图均为 0。
- 桌面和手机分别验证接口文档、注册登录/私有上传/编辑/分析/任务、团队邀请与协作、管理员审核与停用账号。

## 可复现与边界

按 README 启动后运行 `./mvnw verify`、`node scripts/e2e.mjs`、`bash scripts/assert-database.sh` 和 `npm run test:ui`。测试会创建账号和资料并重启应用，应使用独立演示环境。

开发机 Docker Linux 引擎当前不可用，完整容器运行由 GitHub Actions 执行；以上结论来自实际 CI 测试，而非本机容器验证。COS SDK 和百炼 HTTP 适配器通过契约测试，但没有真实云凭据，尚未做真实云端上传、计费调用及生成效果验证。切换云环境需要遵循 [CLOUD_SETUP](CLOUD_SETUP.md) 并自行联调。当前结果不代表生产规模、跨机房高可用或模型质量评估。
