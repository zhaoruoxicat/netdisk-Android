# NetDisk for Android · 自建网盘安卓客户端

**本项目是 [zhaoruoxicat/netdisk](https://github.com/zhaoruoxicat/netdisk) 的配套 Android 客户端。**

[netdisk](https://github.com/zhaoruoxicat/netdisk) 是一个基于 PHP 8.4 + MySQL 的极简网盘程序。
本项目不重新实现任何服务端逻辑，只做一件事：**把那个网盘装进手机**，用原生 Android 界面完成
平时需要开浏览器才能做的操作，并提供浏览器做不到的两件事——**后台续传上传**和**调用系统下载器下载**。

> 服务端是必须的。先用上面的仓库把网盘跑起来，再用这个 App 连上去。

---

## 目录

- [为什么需要一个 App](#为什么需要一个-app)
- [功能](#功能)
- [界面](#界面)
- [环境要求](#环境要求)
- [下载与安装](#下载与安装)
- [从源码构建](#从源码构建)
- [首次使用](#首次使用)
- [使用说明](#使用说明)
- [常见问题](#常见问题)
- [兼容性](#兼容性)
- [技术实现](#技术实现)
- [项目结构](#项目结构)
- [参与贡献](#参与贡献)
- [免责声明](#免责声明)

---

## 为什么需要一个 App

网盘的网页版在手机上能用，但有三件事很难受：

| 网页版的痛点                | 这个 App 的做法                           |
| --------------------- | ------------------------------------ |
| 手机浏览器上传大文件时切到后台，上传就断了 | 上传跑在**前台服务**里，锁屏、切 App 都不中断，进度常驻通知栏  |
| 传一半断了要从头再来            | 复刻了网页版的**分片 + 断点续传**协议，重选同一个文件会接着传   |
| 下载要靠浏览器接管，文件落在哪不好找    | 交给**系统下载器**，自动进「下载」目录，完成有系统通知，支持断点续传 |

除此之外，登录、浏览、新建文件夹、重命名、移动、删除、分享、分享管理都在，操作路径比网页版短。

## 功能

### 已完成

| 模块        | 说明                                                                          |
| --------- | --------------------------------------------------------------------------- |
| **登录**    | 填写服务器地址 + 账号密码；地址可只填 `192.168.1.10:8088`，自动补 `http://`，也支持装在子目录（`/netdisk`） |
| **文件浏览**  | 目录逐层进入、下拉刷新、空目录提示、文件夹与文件分别显示大小 / 时间                                         |
| **上传**    | 多选文件、后台队列逐个上传、通知栏实时百分比、断点续传、自动重试                                            |
| **下载**    | 系统 `DownloadManager` 下载，支持断点续传与完成通知，落盘到系统「下载」目录                             |
| **新建文件夹** | 当前目录下创建                                                                     |
| **重命名**   | 文件与文件夹均可                                                                    |
| **移动**    | 填写目标目录，留空表示根目录                                                              |
| **删除**    | 二次确认，删除文件夹时会在提示里说明「内部内容一并删除」                                                |
| **分享**    | 生成分享直链，弹窗内一键复制                                                              |
| **分享管理**  | 查看全部分享（含下载次数 / 创建时间 / 源文件是否还存在）、复制链接、单个取消、一键清空                              |
| **会话保持**  | 登录态持久化，再次打开直接进文件列表；会话失效自动跳回登录页                                              |
| **退出登录**  | 清除本地会话与 Cookie                                                              |

### 暂未实现

以下服务端能力目前只有网页版有，App 端还没做（欢迎 PR）：

- 文件多选与批量操作（App 端目前一次操作一个条目）
- 上传大文件时的**暂停 / 取消**（只能等它传完，或杀掉 App）
- 分享落地页的预览、分享有效期设置
- WebDAV（这是服务端能力，与本 App 无关）
- 文件搜索、排序切换、缩略图预览

## 界面

```
┌─────────────┐   ┌─────────────┐   ┌─────────────┐
│  登录页      │   │  文件列表    │   │  分享管理    │
│             │   │             │   │             │
│   [ 图标 ]   │   │ 自建网盘  ↑↓ │   │ 分享管理    │
│   自建网盘   │   │ ─────────── │   │ ─────────── │
│ ┌─────────┐ │   │ 📁 项目资料  │   │ 合同.pdf    │
│ │服务器地址│ │   │ 📁 照片      │   │ http://...  │
│ └─────────┘ │   │ 📄 报表.xlsx │   │ 下载 3 次   │
│ ┌─────────┐ │   │ 📄 合同.pdf  │   │ [复制][取消]│
│ │ 用户名   │ │   │             │   │             │
│ └─────────┘ │   │             │   │             │
│ ┌─────────┐ │   │             │   │             │
│ │ 密码     │ │   │             │   │             │
│ └─────────┘ │   │             │   │             │
│ [  登 录  ] │   │             │   │             │
└─────────────┘   └─────────────┘   └─────────────┘
```

**文件列表页的交互：**

- 点击文件夹 → 进入；点击文件 → 弹出操作菜单
- 长按任意条目 → 弹出同一个操作菜单
- 文件菜单：下载 / 分享 / 重命名 / 移动 / 删除
- 文件夹菜单：分享 / 重命名 / 移动 / 删除
- 返回键 → 返回上一层目录（已在根目录则退出 App）
- 右上角菜单：上传、新建文件夹、分享管理、退出登录

## 环境要求

| 项目         | 要求                                                                                   |
| ---------- | ------------------------------------------------------------------------------------ |
| Android 版本 | **Android 10（API 29）及以上**                                                            |
| 服务器        | 已部署 [zhaoruoxicat/netdisk](https://github.com/zhaoruoxicat/netdisk)，**服务端版本 ≥ v2.1** |
| 网络         | 能访问到网盘地址即可。局域网、内网穿透、公网域名都行，HTTP / HTTPS 均可                                           |

> **注意服务端版本**：分享管理用到的 `shares` / `unshare` 接口是 **v2.1** 才加进 `api.php` 的。
> 服务端太旧时，App 会直接提示「服务端版本过旧，不支持该功能」，而不是丢一句看不懂的报错。
> 页面其它功能不受影响。升级方式：用新版程序包的 `api.php` 覆盖服务器上的同名文件，表结构不会变。

## 下载与安装

### 方式一：直接下载 APK（推荐给普通用户）

1. 到本仓库的 [Releases](../../releases) 下载最新版 `NetDisk-Android-vX.Y.apk`；
2. 在手机上点击安装；
3. 首次安装需要允许「安装未知来源应用」——这是 Android 对**所有**非应用商店渠道 APK 的要求。

### 方式二：自己构建

见下一节。

> 本 App **没有**上传到任何应用商店，也**不会**自动更新。想换新版本就重新下载安装一次覆盖即可，登录状态会保留。

## 从源码构建

### 需要准备

- **Android Studio**（Ladybug 或更新版本） / 或命令行 + **JDK 17**
- **Android SDK**，装上 **API 34** 平台与 Build-Tools
- 能连通 Google 的 Maven 仓库（国内网络可能需要配置镜像）

### 用 Android Studio

1. `File → Open`，选中本仓库根目录；
2. 等 Gradle Sync 走完；
3. 连接手机（打开 USB 调试）或启动模拟器；
4. 点运行按钮。

### 用命令行

```bash
# 调试包：app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleDebug

# 正式包（未签名）：app/build/outputs/apk/release/app-release-unsigned.apk
./gradlew assembleRelease
```

想发布自己的版本，需要在 `app/build.gradle.kts` 里补上签名配置，或用 Android Studio 的
`Build → Generate Signed App Bundle / APK` 走一遍向导。

### 版本信息

| 项                      | 值               |
| ---------------------- | --------------- |
| applicationId          | `com.zrx.cloud` |
| versionName            | `1.2`           |
| versionCode            | `3`             |
| minSdk                 | 29              |
| targetSdk / compileSdk | 34              |
| JDK                    | 17              |
| Gradle                 | 8.7             |
| AGP / Kotlin           | 8.5.2 / 2.0.20  |

## 首次使用

```
1. 确认服务端能打开
   浏览器访问 http://你的地址/index.php，能看到登录框、底部有「NetDisk v2.2」字样。

2. 在 App 里填服务器地址
   只填 IP 和端口也行：  192.168.1.10:8088
   装在子目录要带上：    http://192.168.1.10:8088/netdisk
   HTTPS 站点：          https://pan.example.com
   结尾的斜杠可写可不写。

3. 填网页版账号密码
   就是登录 index.php 用的那套。注意：WebDAV 账号是独立的一套，在这里用不了。

4. 登录
```

### 地址填写示例

| 你的情况       | 这样填                            |
| ---------- | ------------------------------ |
| 局域网、默认端口   | `192.168.1.10`                 |
| 局域网、自定义端口  | `192.168.1.10:8088`            |
| 装在子目录      | `192.168.1.10:8088/netdisk`    |
| 有域名和 HTTPS | `https://pan.example.com`      |
| 内网穿透       | `https://xxxx.frp.example.com` |

## 使用说明

### 上传

1. 进入目标文件夹；
2. 点右上角 **上传** 图标；
3. 系统文件选择器里**可多选**；
4. 确认后提示「已加入上传队列（N 个），进度见通知栏」；
5. 下拉通知栏看进度，单个文件传完会再发一条通知。

上传特点：

- **后台不中断**。上传跑在前台服务里，锁屏、切到别的 App、清后台都不会断。
- **断点续传**。传一半失败或被中断，重新选同一个文件、传到同一个目录，会自动跳过已传完的分片接着传。
  （判定依据是「文件名 + 大小 + 目录」，所以**重命名过的文件会被当成新文件重传**。）
- **失败重试**。单个分片失败会指数退避重试 4 次（1s → 2s → 4s → 8s）。
- **排队执行**。多选时按顺序一个一个传，不会互相抢带宽。
- 上传完成后文件列表会自动刷新。

> Android 13 及以上需要授予「通知」权限才能看到进度条，App 会在第一次上传前询问。

### 下载

点击文件 → **下载**：

- 交给系统下载器，文件落在系统「下载」目录（`/sdcard/Download/`）；
- 支持断点续传，网络中断后系统会自动继续；
- 完成后有系统通知，点通知可直接打开文件；
- 下载任务在系统下载管理器里可见（部分机型在「文件管理」里能找到）。

### 分享与分享管理

- 在文件菜单里点 **分享** → 弹出分享直链 → 点「复制」进剪贴板；
  注意服务端是**无访问码**的直链，直接把链接发出去任何人都能下载，谨慎分享。
- 右上角菜单 → **分享管理**：
  - 每条显示文件名、完整链接、下载次数、创建时间；源文件已被删除的会标注「源文件已删除」；
  - **复制** 单条链接
  - **取消** 单条分享（链接立即失效，有二次确认）
  - 右上角 **清空全部**（所有分享链接立即失效，有二次确认）

### 重命名 / 移动 / 删除

- **重命名**：输入新名字，确认即可。
- **移动**：输入目标目录路径（如 `备份/2026`），**留空表示移动到根目录**。
- **删除**：有二次确认。删除文件夹时提示里会注明内部内容一并删除，且删除会同时清理该文件相关的分享记录。

### 退出登录

右上角菜单 → **退出登录**。会清除本地保存的 Cookie 与 CSRF 令牌并回到登录页。
服务端会话过期的处理方式相同——任何请求收到「登录已失效」，App 会自动登出并跳回登录页。

## 常见问题

### 登录相关

<details>
<summary><b>提示「用户名或密码不正确」</b></summary>

先确认这个账号密码能在浏览器里登录 `index.php`。网页版能登、App 登不上，通常是这两件事：

- **服务器地址填错了**，比如漏了子目录 `/netdisk`，或者端口写错。App 会明确提示
  「服务器返回的不是网盘首页」，遇到这个提示就回头检查地址。
- **服务器只接受 HTTPS**，App 会提示「服务器把登录请求跳转到了 xxx，请把服务器地址改成这个再登录」，
  按提示把地址换成 `https://` 开头的那串即可。

</details>

<details>
<summary><b>提示「已连上服务器，但返回的不是网盘首页」</b></summary>

地址指向了别的站点，或者没带子目录。确认浏览器打开这个地址时看到的是网盘登录页。

</details>

<details>
<summary><b>提示「服务器返回 HTTP 404 / 500」</b></summary>

404 一般是地址不对（端口、子目录）；500 是服务端 PHP 报错，需要去看服务器的错误日志。

</details>

<details>
<summary><b>提示「连不上服务器」</b></summary>

- 手机和服务端不在同一个网络（比如手机用 4G、服务器在局域网）；
- 服务端防火墙没放行端口；
- 内网穿透的地址/端口变了。

用手机浏览器打开那个地址验证一下，能打开就说明是 App 的地址格式问题。

</details>

### 上传相关

<details>
<summary><b>提示「服务端版本过旧，不支持该功能」</b></summary>

只有**分享管理**会出现这个，因为 `shares` / `unshare` 是服务端 v2.1 才加的。
升级方法：用新版程序包的 `api.php` 覆盖服务器上的同名文件。

还是报错的话，多半是 PHP 的 **OPcache** 没刷新——`opcache.validate_timestamps=0` 时
必须重启 php-fpm / Apache 才能生效。

</details>

<details>
<summary><b>提示「服务器上传上限过小，连最小分片都收不下」</b></summary>

服务端的 `post_max_size` 太小。分片上传的最小分片是 **256 KB**，
所以 `post_max_size` 至少要能装下 256 KB 加一点表单字段。

建议配置（服务端正式部署时一起改掉）：

```ini
upload_max_filesize = 64M
post_max_size       = 68M
upload_tmp_dir      = /一个剩余空间够大的目录
max_execution_time  = 0
```

Nginx 还要加 `client_max_body_size 64m;`，否则请求在 PHP 之前就被拒了。
服务端仓库自带 `.user.ini` / `.htaccess` 已经包含前几项，Nginx 用户需要自己补。

</details>

<details>
<summary><b>提示「超过单文件上限 X GB」</b></summary>

超出了服务端配置的单文件上限，这是服务端行为，App 只是如实转达。

</details>

<details>
<summary><b>上传卡在某百分比不动了</b></summary>

- 网络抖动：单个分片会自动重试 4 次（最长退避到 8 秒），等一下通常能自己恢复；
- 一直不动就下拉通知栏确认进度更新是否已停止，然后重选同一个文件再传一次——
  已传完的分片会被跳过，不会白传。

</details>

<details>
<summary><b>上传完了但列表里看不到</b></summary>

服务端在**合并分片**（大文件可能要几十秒）。App 会持续轮询等待合并完成，
超时后会提示「服务器合并超时，请稍后在文件列表查看」——稍等片刻下拉刷新即可。

</details>

<details>
<summary><b>关掉 App 上传会断吗</b></summary>

不会。上传是前台服务，从最近任务里划掉 App 也不影响。
想彻底停止上传，需要到系统「应用信息」里强制停止，或等它传完。

</details>

### 下载相关

<details>
<summary><b>下载失败 / 卡在等待中</b></summary>

系统下载器下载时会带上登录 Cookie。如果会话在这期间过期了，下载会失败——
重新登录后再下载即可。另外检查系统下载管理器里是否有多条同名任务堆积。

</details>

<details>
<summary><b>文件名重复会被覆盖吗</b></summary>

同名文件下载到「下载」目录时，系统下载器会自动改名（如 `合同 (1).pdf`），不会覆盖已有文件。

</details>

## 兼容性

- 已在 **Android 10 ~ Android 15** 上使用（`minSdk 29`）。
- 服务器地址支持 **HTTP 与 HTTPS**。为保证能连局域网里的原始网盘地址，App 声明了
  `usesCleartextTraffic="true"`，即允许明文 HTTP。**公网部署请务必上 HTTPS。**
- 本 App 不走任何中转服务器，所有流量**直连你自己填的网盘地址**，不采集、不上报任何数据。

## 技术实现

App 端与原版网页版共用一套协议，因此任何一端的行为都能作为另一端的参照。

### 认证

服务端是传统的 **PHP Session（Cookie）+ CSRF 令牌**：

1. `POST index.php`，表单 `do=login&username=&password=`；
2. 登录成功后首页 HTML 里带 `var CSRF = "..."`，App 用正则取出并保存；
3. 之后所有接口请求 `api.php`，同步带上会话 Cookie 与 `X-CSRF-Token`（以及表单里的 `csrf`）。

两个易踩的坑，代码里都专门处理了：

- **Cookie 必须交给 `CookieJar` 统一管**。早期实现从 `Set-Cookie` 里硬找 `PHPSESSID=`，
  一旦服务端改过 `session.name`、或一次下发多个 Cookie，就一个都取不到，
  而症状会伪装成「用户名或密码不正确」。现在由 OkHttp 的 `CookieJar` 收发，
  自定义实现的 [`CookieStore`](app/src/main/java/com/zrx/cloud/data/CookieStore.kt) 只负责往
  `SharedPreferences` 里做 `name=value` 的持久化。
- **必须跟随重定向**。不少服务器会把 HTTP 301 跳到 HTTPS，或给目录补斜杠。
  不跟随的话登录请求根本没落到服务端，却同样表现成「密码错误」。
  跟随之后，跳转会把 POST 变成 GET（密码丢失），所以代码会用重定向后的地址**自动重试一次**；
  若仍失败，就直接把正确地址告诉用户，而不是含糊地说一句密码不对。

### 分片上传

与网页版 `assets/upload.js` **完全相同的四步协议**：

```
App (ChunkedUploader)                       服务端 (api.php)
  │
  ├─ action=limits ──────────────────────────► 问服务端「单文件上限 / 分片大小区间」
  │                                             （post_max_size 是 PHP_INI_PERDIR，运行时改不了，
  │                                               所以只能问，再据此定分片大小）
  │
  ├─ action=init ────────────────────────────► 建任务，或返回已落盘的分片（断点续传）
  │   upload_id / path / file_name /            state.json 存在暂存目录
  │   file_size / chunk_size / last_modified
  │
  ├─ action=chunk × N（4 个并发 worker）─────► 逐片落盘：先写临时名再原子 rename
  │   upload_id / chunk_index / chunk           并发时不至于把半个分片当成已完成
  │   （单片失败 → 指数退避重试 4 次）
  │
  └─ action=merge ───────────────────────────► flock 独占锁拼接 → 校验总大小 → 落盘
      upload_id                                合并被占用时返回 merging，App 每 1.5s 轮询，
                                               最多等 60 秒；合并幂等，重复调用不会产生副本
```

`upload_id` 取 `sha256("文件名|大小|0|目录").take(32)`。

之所以固定第 3 位为 `0`：Android 通过 SAF（Storage Access Framework）拿到的 `Uri`
**没有可靠的 `lastModified`**，与其写个不稳定的值让续传时好时坏，不如固定为 0——
同一文件传到同一目录时 id 恒定，断点续传稳定有效。代价是**重命名后会被当作新文件**。

### 上传服务

[`UploadService`](app/src/main/java/com/zrx/cloud/upload/UploadService.kt) 是前台服务：

- `LinkedBlockingQueue` 排队，逐个上传，**串行**不抢带宽；
- 进度通过 `NotificationManagerCompat.notify` 更新同一条常驻通知；
- 全部完成后发一条 `ACTION_REFRESH` 广播，`MainActivity` 收到后自动刷新列表；
- `START_NOT_STICKY` —— 被系统杀掉不自动重启，避免半截任务让人困惑。

### 下载

用系统 `DownloadManager`，而不是自己写下载器，换来三个免费能力：
断点续传、系统通知、在系统下载管理器里可见。由于 `DownloadManager` 不走 OkHttp，
需要手动从 [`Session.cookieHeader`](app/src/main/java/com/zrx/cloud/data/Session.kt)
取 Cookie 加进请求头。

### 会话持久化

[`Session`](app/src/main/java/com/zrx/cloud/data/Session.kt)（`SharedPreferences`）保存：

| 键          | 内容                         |
| ---------- | -------------------------- |
| `base_url` | 服务器地址（自动补 `http://`、去尾部斜杠） |
| `username` | 用户名（下次登录自动填上）              |
| `csrf`     | CSRF 令牌                    |
| `cookies`  | 会话 Cookie，逐行 `name=value`  |

**密码不会保存在本地。**

### 数据库与存储（服务端侧）

App 端不落任何业务数据，所有状态都在服务端：

| 表              | 用途                                                                 |
| -------------- | ------------------------------------------------------------------ |
| `users`        | 网页版登录账号（`password_hash`）                                           |
| `shares`       | 分享记录（`token` / `path` / `name` / `is_dir` / `hits` / `created_at`） |
| `webdav_users` | WebDAV 独立账号，与本 App 无关                                              |

上传任务状态不落库，以 `state.json` 存放在服务端暂存目录的 `task_<upload_id>/` 下。

### 用到的接口

| action    | 参数                                                                           | 用途                           |
| --------- | ---------------------------------------------------------------------------- | ---------------------------- |
| `list`    | `path`                                                                       | 列目录                          |
| `mkdir`   | `path`, `name`                                                               | 新建文件夹                        |
| `rename`  | `path`, `name`                                                               | 重命名                          |
| `move`    | `paths[]`, `dest`                                                            | 移动                           |
| `delete`  | `paths[]`                                                                    | 删除                           |
| `share`   | `path`                                                                       | 创建分享，返回 `url`                |
| `shares`  | —                                                                            | 分享列表（**需服务端 ≥ v2.1**）        |
| `unshare` | `id`，或 `target=all`                                                          | 取消单个 / 清空全部（**需服务端 ≥ v2.1**） |
| `limits`  | —                                                                            | 上传能力探测                       |
| `init`    | `upload_id`, `path`, `file_name`, `file_size`, `chunk_size`, `last_modified` | 建 / 恢复上传任务                   |
| `chunk`   | `upload_id`, `chunk_index`, `chunk`                                          | 上传单个分片                       |
| `merge`   | `upload_id`                                                                  | 合并分片                         |
| `abort`   | `upload_id`                                                                  | 放弃上传任务                       |

下载走 `download.php?p=<URL 编码的路径>`，不在 `api.php` 里。

## 项目结构

```
app/src/main/java/com/zrx/cloud/
├── App.kt                      Application，初始化 Session
├── data/
│   ├── NetDiskApi.kt           全部服务端调用（登录 / 文件 / 分享 / 分片上传）
│   ├── Session.kt              服务器地址、用户名、CSRF、Cookie 的持久化
│   ├── CookieStore.kt          OkHttp CookieJar 实现，Cookie 落 SharedPreferences
│   └── Models.kt               FileItem / ShareItem / Limits / InitResult / ApiException
├── upload/
│   ├── UploadService.kt        上传前台服务：队列、进度通知、完成广播
│   └── ChunkedUploader.kt      分片上传：探测 → init → 4 并发分片 → merge 轮询
├── ui/
│   ├── LoginActivity.kt        登录
│   ├── MainActivity.kt         文件列表与全部文件操作
│   ├── SharesActivity.kt       分享管理
│   ├── FileAdapter.kt          文件列表适配器
│   └── ShareAdapter.kt         分享列表适配器
└── util/
    ├── Format.kt               文件大小格式化
    └── Sha256.kt               计算 upload_id

app/src/main/res/
├── layout/                     布局（activity_login / activity_main / activity_shares / item_* / dialog_input）
├── menu/                       main / shares
├── drawable/                   ic_file / ic_folder / ic_upload / ic_download / ic_more / ic_back
└── values/                     strings / colors / themes
```

服务端仓库见 👉 [zhaoruoxicat/netdisk](https://github.com/zhaoruoxicat/netdisk)。

# 
