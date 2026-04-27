# MovieCat

一个面向 Android TV + 手机双端的影视聚合播放器 MVP。

当前版本已经把你图片里的第一阶段需求做成了工程骨架，并额外加入了一个局域网管理端网页:

- App 内支持添加和切换源地址
- 兼容常见 TVBox 配置 `sites[]` 解析
- 兼容常见影视 JSON 片单字段，如 `vod_name`、`vod_pic`、`vod_play_url`
- 使用 ExoPlayer 直接播放 `mp4` / `m3u8`
- 适配手机触屏和 TV 遥控焦点操作
- 内置局域网网页管理后台，可在手机或电脑上维护源地址和运行参数
- 使用 Room 保存源、收藏和历史记录
- 后台已暴露 DNS、请求头、分类参数、首页推荐位、Spider Jar / JS / Ext 配置
- 新增 CatVod Jar Spider 运行入口，并给 QuickJS Spider 留出了接入位

## 现在能做什么

1. 启动 App 后，会默认预置你常用的两个接口:
   `http://www.饭太硬.com/tv`
   `https://肥猫.com/`
2. App 会自动启动一个局域网 HTTP 管理页，页面地址会显示在首页的“局域网管理端”卡片里。
3. 同一局域网里的手机或电脑访问这个地址后，可以:
   添加源
   删除源
   让电视端立即切换并加载某个源
   修改 DNS、请求头、分类参数、首页推荐位
   配置 Spider 的 Jar、类名、脚本地址和 Ext
4. 如果源是 TVBox 配置，会先解析出 `sites` 站点列表。
5. 如果源直接返回影视 JSON 片单，会直接展示影片卡片并进入播放页。

## 项目结构

- `app/src/main/java/com/moviecat/app/data/remote`
  网络请求、TVBox/JSON 解析器、局域网管理页 HTTP 服务
- `app/src/main/java/com/moviecat/app/data/local`
  Room 数据库、源/收藏/历史 DAO
- `app/src/main/java/com/moviecat/app/data/repository`
  数据整合与默认源初始化
- `app/src/main/java/com/moviecat/app/ui`
  首页、播放器弹层、卡片组件、主题
- `app/src/main/java/com/moviecat/app/viewmodel`
  UI 状态、源切换、播放、局域网服务控制

## 局域网管理端说明

这是一个内置在 App 进程里的轻量 HTTP 服务，默认端口是 `8090`。

适合当前需求:

- 电视上不用再手动输入长网址
- 手机和电脑可以直接在浏览器里维护源
- 不需要额外部署后台
- 源的网络层参数和 Spider 参数也能一起管理，不再散落在电视端页面里

当前限制:

- App 需要保持运行，网页管理端才可访问
- QuickJS 入口已经留好，但当前工程里还没有真正 bundled 的 QuickJS runtime AAR
- CatVod Jar Spider 走的是兼容层，能覆盖一部分常见 Spider，但还不能承诺和 TVBox 完全等价
- `playerContent`、搜索和更深的筛选链路还没全部打通

## 本地运行

需要你本机准备:

- Android Studio
- JDK 17
- Android SDK / Platform 35

然后直接用 Android Studio 打开这个目录即可同步工程。

## 下一步最值得继续补的内容

1. 把局域网网页端扩展成完整“管理后台”，支持:
   DNS
   UA
   请求头
   分类过滤
   首页推荐位
2. 接入 QuickJS，兼容 JS 规则源
3. 继续扩展 CatVod 兼容层，补 `playerContent`、搜索和代理视频
4. 把局域网服务提升为前台服务，避免切后台后被系统回收
5. 增加搜索、分类筛选、播放详情页和 WebDAV/云同步
