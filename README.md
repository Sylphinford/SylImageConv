# Syl ImageConv

一个极简的 Android 图片格式转换工具。支持 **JPG / PNG / WEBP 互转**，可批量、可选质量、WEBP 可选有损/无损。

- 包名：`io.github.sylphinford.imageconv`
- 最低支持：Android 8.0（API 26）
- 许可：[Apache License 2.0](LICENSE)

## 功能

- **格式互转**：JPG、PNG、WEBP 三种输出格式任选
- **批量转换**：一次添加多张，逐个转换并显示每张的状态
- **三种进图方式**
  1. 应用内「添加图片」按钮，走系统文件选择器（可从任意文件管理器里选）
  2. 在其他应用里「分享」图片到 Syl ImageConv（单张、多张都支持）
  3. 文件管理器里「用其他应用打开」→ Syl ImageConv
- **质量百分比**：1%–100% 可调（JPG / 有损 WEBP 生效）
- **WEBP 有损 / 无损**：可切换；Android 11 以下自动用 quality=100 表示无损
- **体积对比**：转换完成后，列表第二行会显示 `分辨率 · 源大小 → 转换后大小`
- **输出目录**：默认 `Pictures/SylImageConv`（转换完相册里直接可见），也可自选任意文件夹
- **自动处理**：EXIF 旋转校正、透明背景转 JPG 时铺白底、超大图自动降采样防 OOM、重名自动加 `(1)`
- **开源许可**：右上角菜单「开源许可」可查看第三方组件清单与协议全文

## 界面

单页结构，上半部分是待转换列表，下半部分是设置项：

```
┌──────────────────────────────┐
│ Syl ImageConv                │
├──────────────────────────────┤
│ [缩略图] 文件名      1920×1080 · 1.2 MB   ×  │
│ [缩略图] 文件名      800×600 · 240 KB      ×  │
│ ...                          │
├──────────────────────────────┤
│ 输出格式   [JPG][PNG][WEBP]   │
│ 编码方式   [有损][无损]        │  ← 仅 WEBP 时出现
│ 质量  ──────●──────  90%      │  ← PNG / 无损 WEBP 时隐藏
│ 保存到  Pictures/SylImageConv 更改│
│ 已选 3 张              清空   │
│ [ 添加图片 ]  [ 开始转换 ]     │
│ ▓▓▓▓▓▓░░░░  正在转换 2/3 …    │
└──────────────────────────────┘
```

## 安装

系统要求 Android 8.0 及以上。可按下文自行构建安装；已发布的安装包见仓库 Releases 页面。

## 构建

需要 JDK 17 与 Android SDK（platform 34 + build-tools 34.0.0）。

### Android Studio

1. `File > Open` 打开本目录
2. 等待 Gradle Sync 完成（会自动下载依赖）
3. `Run ▶` 装到设备上，或 `Build > Build Bundle(s)/APK(s) > Build APK(s)`

### 命令行

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`local.properties` 里的 `sdk.dir` 需指向本机 SDK 路径，该文件不入库。

release 构建开启 R8 代码压缩与资源收缩，产物约 1.7 MB：

```bash
./gradlew assembleRelease
# 产物：app/build/outputs/apk/release/app-release.apk
```

## 签名

仓库不包含任何签名密钥。release 构建从项目根目录的 `keystore.properties` 读取签名信息，
该文件已列入 `.gitignore`：

```properties
storeFile=/absolute/path/to/release.jks
storePassword=your_store_password
keyAlias=your_key_alias
keyPassword=your_key_password
```

生成自己的密钥库：

```bash
keytool -genkeypair -v -keystore release.jks -alias sylimageconv \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=Your Name, O=Your Org, C=CN"
```

未提供 `keystore.properties` 时 `assembleRelease` 依然可以构建，只是产物为未签名 APK。

覆盖安装要求签名一致：更换密钥后必须先卸载旧版本，否则会报
`INSTALL_FAILED_UPDATE_INCOMPATIBLE`。

## 技术要点

| 项 | 说明 |
|---|---|
| 语言 / UI | Kotlin + ViewBinding，Material 3，单 Activity |
| minSdk / targetSdk | 26 / 34 |
| 解码 | `BitmapFactory` + `inJustDecodeBounds` 预读尺寸，OOM 时按 1/2、1/4… 重试 |
| 方向 | `androidx.exifinterface` 读 `TAG_ORIENTATION`，用 `Matrix` 校正后再编码 |
| 编码 | `Bitmap.compress`；API 30+ 用 `WEBP_LOSSLESS` / `WEBP_LOSSY`，低版本用 `WEBP` + quality=100 |
| 落盘（默认） | API 29+ `MediaStore` + `RELATIVE_PATH=Pictures/SylImageConv` + `IS_PENDING` 原子提交；API 26–28 直接写公共 Pictures 目录并触发媒体扫描 |
| 落盘（自选） | SAF 树 URI + `DocumentFile`，权限持久化；写失败自动回退到默认目录 |
| 线程 | `lifecycleScope` + `Dispatchers.IO` 逐张串行转换，主线程只更新进度 |

## 已知限制

- PNG 是无损格式，质量百分比对它无效（界面会提示并隐藏滑块）
- 输出 WEBP 时单边不能超过 16383 像素（格式规格限制，宽高各用 14 bit 存储）。超出时会在列表里提示改用 JPG，不会自动缩小
- 输出不带原图 EXIF 元数据（只保留像素内容与方向校正结果）
- 通过「分享」进来的文件，读权限只在本次任务内有效；转换请一次性做完，不要中途杀进程
- 输入格式取决于系统解码器：JPG/PNG/WEBP/GIF/BMP/HEIC 一般都能读，但输出只支持 JPG/PNG/WEBP

## 项目结构

```
app/src/main/java/io/github/sylphinford/imageconv/   源码
app/src/main/res/                                    布局与资源
app/src/main/assets/licenses/                        协议全文（打包进 APK）
tools/gen_licenses.py                                第三方许可清单生成脚本
tools/deps.txt                                       依赖快照
```

## 许可

本项目以 [Apache License 2.0](LICENSE) 授权。

随 APK 分发的第三方运行时组件全部为 Apache License 2.0（AndroidX、Material Components、
Kotlin 标准库、kotlinx.coroutines 等），完整清单见
[THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md)，应用内「开源许可」页面同步展示该清单与协议全文。

清单由脚本读取各组件 POM 中的 `<licenses>` 元数据生成，依赖变更后重新运行：

```bash
python tools/gen_licenses.py --refresh
```

遇到缺少协议声明的组件时脚本会以非零码退出，提示在 `MANUAL` 表中补正。
