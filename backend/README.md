# Music Dreamer 悦享音乐系统

在线音乐分享平台（课程设计项目）：听众收听、歌手创作、管理员运营；支持本地文件与外部链接（B 站 / YouTube）两种方式导入歌曲，统一审核制发布；媒体处理（下载/转码/响度均衡/AI 转写）任务化异步执行；基于物品协同过滤的个性化推荐。

> 环境准备:Nacos 2.2.3 + MySQL8(执行 init/init.sql)+ Redis;数据库密码等私密配置放各服务 src/main/resources/application-local.yml(gitignored,不提供真实值)。

## 工程结构

```
MusicDreamer/                 # 工作区（设计文档与项目管理文件放这里）
├── backend/                  # 后端仓库（可独立 git init）
│   ├── common/               # 公共库：统一响应 Mess / 错误码 / JWT / 用户上下文
│   ├── gateway/              # API 网关 :8080（路由 / 统一鉴权 / 限流 / 静态 /data 转发）
│   ├── Mod_login/            # 认证与用户 :8001
│   ├── Mod_music/            # 歌曲·审核·搜索·统计·协同过滤推荐 :8002
│   ├── Mod_upload/           # 文件上传校验 + /data 静态资源 :8007
│   ├── Mod_songList/         # 歌单 :8008
│   ├── Mod_like/             # 收藏 :8009
│   ├── Mod_media/            # 媒体处理（进程执行器/下载管线/响度/转写/字幕）:8010
│   ├── Mod_setting/          # 配置中心 + 公告 :8005
│   ├── init/init.sql         # 数据库初始化（19 张表 + 默认数据）
│   ├── monitor/              # Prometheus / 告警规则 / Alertmanager 配置
│   ├── scripts/fetch-tools.js# 媒体工具一键拉取
│   ├── tools.manifest.json   # 工具清单（版本锁定，进 Git）
│   ├── tools/                # 本地工具（Maven、m2 仓库、yt-dlp、ffmpeg、whisper、nacos，均不进 Git，由 fetch-tools.js 拉取或本机自备）
│   ├── Dockerfile.service / Dockerfile.media
│   └── docker-compose.yml    # 基础设施 + 监控 + 全量业务编排
└── frontend/                 # 前端仓库（Vue 3 + Vite + Element Plus + Pinia，可独立 git init）
```

## 快速开始（开发环境）

> **空机器自装清单**（仓库里只有源码/脚本/编排文件，以下需要自己装）：
> JDK 17~22（别用 25 EA，Lombok 编译会挂）· Node.js 18+（前端）· Docker Desktop（起 MySQL/Redis/Nacos；没有 Docker 就手动装 MySQL 8、Redis 6、Nacos **2.2.3**——客户端是 2.x gRPC，1.x 注册中心连不上）· Maven（或直接用 IDEA 内置 Maven 打开 backend/ 工程；tools/ 里的 Maven 只在开发机本地，不进 Git）。

### 1. 基础设施（Docker）

```bash
docker compose up -d mysql redis nacos
# MySQL 首次启动自动执行 init/init.sql（建库建表 + 默认账号）
```

> **密码对齐**：各服务默认连的是开发机本机库（yml 默认值，含密码）。**用 Docker 起的库，启动服务前先设 `MYSQL_PASSWORD=123456`**（Windows `set` / PowerShell `$env:`，或写进 IDEA 运行配置），否则本地服务连库会失败；本机自装 MySQL 且密码与默认值不同的同理。

### 2. 媒体工具（首次执行一次，在 **Git Bash** 里跑）

```bash
node scripts/fetch-tools.js              # 拉取 yt-dlp / ffmpeg / whisper-cli 到 tools/bin/
node scripts/fetch-tools.js --models     # 可选：拉取 whisper small 模型（约466MB）
node scripts/fetch-tools.js --nacos      # 可选：拉取 Nacos 2.2.3 到 tools/nacos（无 Docker 机器用，配 scripts/nacos-start.cmd 启动）
```

工具探测顺序：`tools_path` 配置 → 项目 `tools/` 目录 → 系统 PATH，装了全局的队友不拉也能跑。

### 3. 后端

```bash
# 需自备 JDK 与 Maven（或用 IDEA 打开 backend/ 工程用内置 Maven 构建）
cd backend
mvn -DskipTests package

# 本地起服务（任选；推荐先起核心链路）
java -jar gateway/target/gateway-1.0.0.jar
java -jar Mod_login/target/mod-login-1.0.0.jar
java -jar Mod_music/target/mod-music-1.0.0.jar
java -jar Mod_setting/target/mod-setting-1.0.0.jar
java -jar Mod_songList/target/mod-songlist-1.0.0.jar
java -jar Mod_like/target/mod-like-1.0.0.jar
java -jar Mod_upload/target/mod-upload-1.0.0.jar
java -jar Mod_media/target/mod-media-1.0.0.jar
```

默认账号：`admin / admin123`（管理员，唯一种子账号；其余账号注册创建，邮箱未配置时激活链接打印在 Mod_login 日志里）。

### 4. 前端

```bash
# frontend 为独立仓库，可直接 git init
cd ../frontend
npm install
npm run dev        # http://localhost:5173（代理 /api 与 /data 到网关 :8080）
```

### 5. 监控（可选）

```bash
docker compose up -d prometheus grafana alertmanager sentinel-dashboard
# Prometheus http://localhost:9090 · Grafana http://localhost:3001 (admin/admin)
# Sentinel 控制台 http://localhost:8858 (sentinel/sentinel)
```

## 业务口径（速记）

- **审核制**：歌手/管理员上传（本地文件或链接导入）→ 审核中（status=1）→ 管理员审核通过 → 全站公开（status=2）；驳回记录原因可重提；违规已发布歌曲可下架（0）/重新上架（2）。
- **角色**：普通用户（role=0）只听；歌手（1）与管理员（2）可上传与链接导入。
- **播放统计**：播完才计（audio onended 上报），明细 play_history + 热计数 play_count 同事务。
- **推荐**：Item-based 协同过滤，每日 03:00 离线重算歌曲相似度（Top-50 入 song_similarity），在线查表聚合，冷启动兜底热门榜。

## 设计文档

- 《MusicDreamer悦享音乐系统概要设计说明书.docx》（根目录）
- 《MusicDreamer悦享音乐系统详细设计说明书.docx》（根目录）
- 工作区根目录《MusicDreamer悦享音乐系统详细设计文档.md》（与 docx 同源，Mermaid 图可直接渲染）

## 常见问题

- **容器互连**：容器化部署时配置里的 `localhost` 一律改为服务名（mysql/redis/nacos）。
- **B 站下载 412**：保持 yt-dlp 最新 + 自动 buvid cookie（服务已内置），反复出现配置 `download_proxy`。
- **转写提示模型未下载**：`node scripts/fetch-tools.js --models` 或管理端-模型管理页下载。
- **Windows 编码**：JVM 统一 `-Dfile.encoding=UTF-8`（镜像与脚本已带）。
