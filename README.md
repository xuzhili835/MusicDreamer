# Music Dreamer · 悦享音乐系统

课设全栈项目:Spring Cloud 微服务后端 + Vue3 网页端 + uni-app 微信小程序端,含原创音乐上传、审核制发布、协同过滤推荐、听歌识曲、歌词自动获取(LRCLIB/字幕/Whisper)、B 站链接导入等完整业务闭环。

## 仓库结构

| 目录 | 说明 | 技术栈 |
| --- | --- | --- |
| `backend/` | 微服务后端(9 模块) | Spring Boot 2.7 · Spring Cloud · Nacos · Gateway · MyBatis-Plus · MySQL · Redis · Flyway |
| `frontend/` | 桌面网页端 | Vue 3 · Vite · Pinia · Element Plus(Sakura Echo 视觉风格) |
| `uniapp/` | 微信小程序端 | uni-app(Vue 3 · Vite),微信一键登录 |
| `docs/` | 设计文档与测试报告 | — |

后端模块:`gateway`(:8080 统一入口/JWT 鉴权/吊销地板)、`Mod_login`、`Mod_music`、`Mod_upload`、`Mod_songList`、`Mod_like`、`Mod_media`(媒体工具链:yt-dlp/ffmpeg/whisper)、`Mod_setting`、`common`(JWT/错误码/全局异常)。

## 快速开始

```bash
# 后端(需 MySQL8 + Redis + Nacos,本地私密配置见各服务 application-local.yml 模式)
cd backend && tools/mvn.sh -DskipTests package

# 网页端
cd frontend && npm install && npm run dev   # http://localhost:5173

# 小程序端(产物导入微信开发者工具)
cd uniapp && npm install && npm run dev:mp-weixin   # dist/dev/mp-weixin
```

默认管理员:`admin / admin123`(数据库 init 脚本内置)。

## 说明

- 课程管理表格(项目日程计划、问题清单 xlsx)含成员个人信息,不入公开仓库。
- 数据库密码、微信 AppSecret 等本地私密配置走 gitignored 的 `application-local.yml` / `application-secret.yml` / 环境变量,不进版本库。
- 详细设计见 `backend/MusicDreamer悦享音乐系统详细设计文档.md` 与 `docs/`。
