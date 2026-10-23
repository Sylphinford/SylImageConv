# 第三方许可声明 (Third-Party Licenses)

Syl ImageConv 的发布产物中包含下列第三方开源组件。本文件由 `tools/gen_licenses.py`
读取各组件 POM 中的 `<licenses>` 元数据生成，请勿手工编辑。

- 运行时组件（随 APK 分发）：**63 个**
- 涉及协议：**Apache License, Version 2.0**（63 个）
- 协议全文：见仓库根目录 [`LICENSE`](LICENSE)

## Apache License, Version 2.0

协议地址：<https://www.apache.org/licenses/LICENSE-2.0>

| 组件 | 版本 | 备注 |
|---|---|---|
| `androidx.activity:activity` | 1.9.0 |  |
| `androidx.activity:activity-ktx` | 1.9.0 |  |
| `androidx.annotation:annotation-experimental` | 1.4.0 |  |
| `androidx.annotation:annotation-jvm` | 1.8.0 |  |
| `androidx.appcompat:appcompat` | 1.7.0 |  |
| `androidx.appcompat:appcompat-resources` | 1.7.0 |  |
| `androidx.arch.core:core-common` | 2.2.0 |  |
| `androidx.arch.core:core-runtime` | 2.2.0 |  |
| `androidx.cardview:cardview` | 1.0.0 |  |
| `androidx.collection:collection` | 1.1.0 |  |
| `androidx.concurrent:concurrent-futures` | 1.1.0 |  |
| `androidx.constraintlayout:constraintlayout` | 2.1.4 |  |
| `androidx.constraintlayout:constraintlayout-core` | 1.0.4 |  |
| `androidx.coordinatorlayout:coordinatorlayout` | 1.1.0 |  |
| `androidx.core:core` | 1.13.1 |  |
| `androidx.core:core-ktx` | 1.13.1 |  |
| `androidx.cursoradapter:cursoradapter` | 1.0.0 |  |
| `androidx.customview:customview` | 1.1.0 |  |
| `androidx.customview:customview-poolingcontainer` | 1.0.0 |  |
| `androidx.databinding:viewbinding` | 8.5.2 |  |
| `androidx.documentfile:documentfile` | 1.0.1 |  |
| `androidx.drawerlayout:drawerlayout` | 1.1.1 |  |
| `androidx.dynamicanimation:dynamicanimation` | 1.0.0 |  |
| `androidx.emoji2:emoji2` | 1.3.0 |  |
| `androidx.emoji2:emoji2-views-helper` | 1.3.0 |  |
| `androidx.exifinterface:exifinterface` | 1.3.7 |  |
| `androidx.fragment:fragment` | 1.5.4 |  |
| `androidx.interpolator:interpolator` | 1.0.0 |  |
| `androidx.legacy:legacy-support-core-utils` | 1.0.0 |  |
| `androidx.lifecycle:lifecycle-common-jvm` | 2.8.3 |  |
| `androidx.lifecycle:lifecycle-livedata` | 2.8.3 |  |
| `androidx.lifecycle:lifecycle-livedata-core` | 2.8.3 |  |
| `androidx.lifecycle:lifecycle-livedata-core-ktx` | 2.8.3 |  |
| `androidx.lifecycle:lifecycle-process` | 2.8.3 |  |
| `androidx.lifecycle:lifecycle-runtime-android` | 2.8.3 |  |
| `androidx.lifecycle:lifecycle-runtime-ktx-android` | 2.8.3 |  |
| `androidx.lifecycle:lifecycle-viewmodel` | 2.8.3 |  |
| `androidx.lifecycle:lifecycle-viewmodel-android` | 2.8.3 |  |
| `androidx.lifecycle:lifecycle-viewmodel-ktx` | 2.8.3 |  |
| `androidx.lifecycle:lifecycle-viewmodel-savedstate` | 2.8.3 |  |
| `androidx.loader:loader` | 1.0.0 |  |
| `androidx.localbroadcastmanager:localbroadcastmanager` | 1.0.0 |  |
| `androidx.print:print` | 1.0.0 |  |
| `androidx.profileinstaller:profileinstaller` | 1.3.1 |  |
| `androidx.recyclerview:recyclerview` | 1.3.2 |  |
| `androidx.resourceinspection:resourceinspection-annotation` | 1.0.1 |  |
| `androidx.savedstate:savedstate` | 1.2.1 |  |
| `androidx.savedstate:savedstate-ktx` | 1.2.1 |  |
| `androidx.startup:startup-runtime` | 1.1.1 |  |
| `androidx.tracing:tracing` | 1.0.0 |  |
| `androidx.transition:transition` | 1.5.0 |  |
| `androidx.vectordrawable:vectordrawable` | 1.1.0 |  |
| `androidx.vectordrawable:vectordrawable-animated` | 1.1.0 |  |
| `androidx.versionedparcelable:versionedparcelable` | 1.1.1 |  |
| `androidx.viewpager:viewpager` | 1.0.0 |  |
| `androidx.viewpager2:viewpager2` | 1.1.0-beta02 |  |
| `com.google.android.material:material` | 1.12.0 |  |
| `com.google.errorprone:error_prone_annotations` | 2.15.0 |  |
| `com.google.guava:listenablefuture` | 1.0 | POM 缺失 licenses 声明，源码取自 Guava |
| `org.jetbrains:annotations` | 23.0.0 |  |
| `org.jetbrains.kotlin:kotlin-stdlib` | 1.9.24 |  |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.8.1 |  |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm` | 1.8.1 |  |

## 构建期工具（不随 APK 分发）

| 工具 | 版本 | 协议 |
|---|---|---|
| Android Gradle Plugin | 8.5.2 | [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| Kotlin / kotlin-gradle-plugin | 1.9.24 | [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| Gradle | 8.7 | [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| Gradle Wrapper (gradle-wrapper.jar) | 8.7 | [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| Android SDK (platform 34 / build-tools 34.0.0) | 34 | [Android SDK Terms of Service（非开源协议，仅约束开发环境）](https://developer.android.com/studio/terms) |

## 素材

界面图标与启动图标为手写 vector path，形状参考 Material Design Icons
（Apache License 2.0，<https://github.com/google/material-design-icons>）。
未使用任何第三方图片、字体或音频资源。

## Apache License 2.0 合规说明

上述 Apache 2.0 组件均以未修改的二进制形式（AAR/JAR）链接进 APK。
根据协议第 4 条，分发时需随附协议副本并保留版权与归属声明，本项目通过以下方式履行：

1. 仓库根目录提供 `LICENSE` 全文，`NOTICE` 列出主要归属方；
2. 应用内「开源许可」页面展示本清单与协议全文（打包在 `assets/licenses/`）；
3. 未修改任何第三方组件的版权声明，未使用其商标。
