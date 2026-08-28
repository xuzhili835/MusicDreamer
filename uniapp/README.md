# MusicDreamer uniapp(微信小程序端)

Vue3 + Vite 版 uni-app,与 backend/frontend 平级的第三个仓库。目标端:微信小程序(体验版演示),代码同构可编 H5。

## 运行

```bash
npm install
npm run dev:mp-weixin     # 开发:产物 dist/dev/mp-weixin,用微信开发者工具导入该目录
npm run build:mp-weixin   # 发布:产物 dist/build/mp-weixin
```

微信开发者工具 → 导入项目 → 目录选 `dist/dev/mp-weixin` → AppID 自动读 manifest → 预览二维码手机扫码(开发版)。

## 联调要点

- `src/config.js` 的 `BASE_URL` 填电脑**局域网 IP**(`ipconfig`),手机与电脑必须同一 WiFi/热点;电脑换网络要同步改。
- 真机首次运行:小程序右上角胶囊 → 开发调试 → 勾"不校验合法域名"(体验版演示同理)。
- 真机连不上时先查 Windows 防火墙是否放行 8080 入站。
- manifest.json 的 `mp-weixin.appid` 已填小程序 AppID;AppSecret 只在后端(Mod_login 的 gitignored `application-secret.yml`),前端永不持有。

## 登录

- 账号密码登录:复用 `/api/v1/user/login`。
- 微信一键登录:`wx.login` 取 code → `POST /api/v1/user/wx/login`(网关白名单)→ 后端 jscode2session 换 openid → 已绑定直接发 JWT,未绑定自动建号(占位邮箱 `wx_{openid}@wx.placeholder`)。
- 绑定/解绑:`POST/DELETE /api/v1/user/wx/bind`(登录态),入口待放到个人设置页。
