# COS 与百炼接入

将 `.env.example` 复制为 `.env`，只在本地编辑。不要把密钥写入源码、截图、日志或 GitHub。

## COS

1. 创建腾讯云 COS **私有读写**桶，记录带 APPID 后缀的桶名及 region。
2. 使用 CAM 最小权限子账号，授予目标桶 `PutObject`、`GetObject`、`DeleteObject`；不需要公共读或浏览器直接持有密钥。
3. 设置 `STORAGE_MODE=cos`、`COS_REGION`、`COS_BUCKET`、`COS_SECRET_ID`、`COS_SECRET_KEY`，重启两个应用。
4. 上传、下载、删除测试图片；私有图片不应从匿名 API 或裸桶 URL 读取。对象删除默认延迟 30 分钟，失败后台重试。

本项目接入官方 Java SDK，以 HTTPS 写入私有桶；访问通过后端权限校验与代理读取。因此不需要给桶开放前端 CORS。当前未实现 CDN/预签名大文件直传，多用户大规模图片读取时后端代理带宽会成为瓶颈。

官方资料：[COS Java 快速入门](https://cloud.tencent.com/document/product/436/10199)、[私有访问签名说明](https://cloud.tencent.com/document/product/436/35217)。

## 阿里云百炼

使用官方通义万相异步文生图协议，支持 `wanx2.1-t2i-turbo` / `wanx2.1-t2i-plus` / `wan2.2-t2i-flash` / `wan2.2-t2i-plus`。当前适配器不混用 wan2.6 的新协议。

```dotenv
AI_MODE=dashscope
DASHSCOPE_API_KEY=your-local-key
DASHSCOPE_BASE_URL=https://YOUR_WORKSPACE.cn-beijing.maas.aliyuncs.com/api/v1
DASHSCOPE_MODEL=wanx2.1-t2i-turbo
DEMO_SEED=false
```

工作空间、密钥和 endpoint 必须位于相同地域，按个人控制台当前官方说明填写；不要把 `YOUR_WORKSPACE` 原样使用。中国北京示例仅为域名格式，不保证你的账号可直接使用。每个请求显式 `n=1`、`size=1024*1024`、`watermark=true`，由后台 POST 提交并 GET `/tasks/{task_id}` 轮询。真实调用会按你的账号计费。

生成图片 URL 默认允许北京/上海/新加坡的 DashScope 结果域名；实际使用其他官方结果域名时，将 `AI_RESULT_HOSTS` 配成可信域名白名单，并在 compose 环境中透传该变量。不要添加本机、私网、任意域名或 URL 路径。下载禁止重定向，不带百炼 Authorization 给结果图片地址。

成功结果经过同样图片检查和图库配额检查，再归档到配置的对象存储。公开 AI 图片也要管理员审核。提交 UNKNOWN 需到云控制台核查；本项目不提供自动重复提交按钮。若可确定 provider task ID，可在备份数据库后按 RUNBOOK 的状态恢复步骤操作。

官方资料：[万相文生图异步 API](https://help.aliyun.com/en/model-studio/text-to-image-v2-api-reference)。截至项目实现日期，文档同时提供不同代际协议；本适配器采用其中“wan2.5 and earlier models”的 text2image 请求与 results 数组响应。

## 开发工具

MyBatis X 是 IDE 辅助工具，可选安装用于跳转 Mapper/生成实体，不属于运行时依赖，也不是项目核心业务能力。可以用 Flyway 表结构手动维护实体，不需要该插件才能运行。

接口文档使用适配 Spring Boot 3 的 Knife4j Jakarta starter / springdoc 与 `@Operation`。[官方兼容说明](https://doc.xiaominfo.com/docs/quick-start/start-knife4j-version)。分页使用 MP 的独立 JSQLParser 插件依赖，[官方安装说明](https://baomidou.com/en/getting-started/install/)。
