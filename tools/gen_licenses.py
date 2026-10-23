#!/usr/bin/env python3
"""生成第三方许可清单。

用法：
    python tools/gen_licenses.py            # 用 tools/deps.txt 里已提交的依赖快照
    python tools/gen_licenses.py --refresh  # 先调 Gradle 重新解析依赖，再生成

输出：
    THIRD_PARTY_LICENSES.md                        仓库内的人读版
    app/src/main/assets/licenses/third_party_licenses.txt   应用内展示版

协议信息来自 Gradle 缓存中每个模块 POM 的 <licenses> 声明，不做猜测。
"""
import os
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CACHE = Path(os.environ["USERPROFILE"]) / ".gradle/caches/modules-2/files-2.1"
DEPS_FILE = ROOT / "tools/deps.txt"
NS = "{http://maven.apache.org/POM/4.0.0}"
APACHE_URL = "https://www.apache.org/licenses/LICENSE-2.0"

# POM 里没有 <licenses> 的模块，按源码实际协议人工补正
MANUAL = {
    "com.google.guava:listenablefuture:1.0": (
        "Apache License, Version 2.0",
        APACHE_URL,
        "POM 缺失 licenses 声明，源码取自 Guava",
    ),
}

# 构建期工具，不随 APK 分发
BUILD_TOOLS = [
    ("Android Gradle Plugin", "8.5.2", "Apache License 2.0", "https://www.apache.org/licenses/LICENSE-2.0"),
    ("Kotlin / kotlin-gradle-plugin", "1.9.24", "Apache License 2.0", "https://www.apache.org/licenses/LICENSE-2.0"),
    ("Gradle", "8.7", "Apache License 2.0", "https://www.apache.org/licenses/LICENSE-2.0"),
    ("Gradle Wrapper (gradle-wrapper.jar)", "8.7", "Apache License 2.0", "https://www.apache.org/licenses/LICENSE-2.0"),
    ("Android SDK (platform 34 / build-tools 34.0.0)", "34",
     "Android SDK Terms of Service（非开源协议，仅约束开发环境）", "https://developer.android.com/studio/terms"),
]

GRADLE_INIT = """
allprojects {
    tasks.register("printDeps") {
        doLast {
            def cfg = configurations.findByName("releaseRuntimeClasspath")
            if (cfg != null) {
                cfg.resolvedConfiguration.lenientConfiguration.getArtifacts().each {
                    println it.moduleVersion.id.toString()
                }
            }
        }
    }
}
"""


def refresh_deps():
    init = ROOT / "tools/.printdeps.gradle"
    init.write_text(GRADLE_INIT, encoding="utf-8")
    env = dict(os.environ)
    env.setdefault("JAVA_HOME", os.environ.get("JAVA_HOME", ""))
    out = subprocess.run(
        ["gradlew.bat", "-q", "-I", str(init), ":app:printDeps", "--console=plain"],
        cwd=ROOT, capture_output=True, text=True, shell=True, env=env,
    )
    init.unlink(missing_ok=True)
    if out.returncode != 0:
        sys.exit(f"Gradle 解析依赖失败：\n{out.stdout}\n{out.stderr}")
    coords = sorted({
        line.strip() for line in out.stdout.splitlines()
        if line.count(":") == 2 and " " not in line.strip()
    })
    DEPS_FILE.write_text("\n".join(coords) + "\n", encoding="utf-8")
    print(f"已刷新 {DEPS_FILE.name}：{len(coords)} 个模块")


def find_pom(group, artifact, version):
    base = CACHE / group / artifact / version
    if not base.is_dir():
        return None
    poms = sorted(base.glob("*/*.pom"))
    return poms[0] if poms else None


def parse_licenses(pom):
    try:
        root = ET.parse(pom).getroot()
    except ET.ParseError:
        return []
    out = []
    for lic in root.findall(f"{NS}licenses/{NS}license"):
        name = (lic.findtext(f"{NS}name") or "").strip()
        url = (lic.findtext(f"{NS}url") or "").strip()
        if name or url:
            out.append((name or "(未命名)", url))
    return out


def normalize(name, url):
    blob = f"{name} {url}".lower()
    if "apache" in blob:
        return "Apache License, Version 2.0", APACHE_URL
    if "mit" in blob:
        return "MIT License", url
    if "bsd" in blob:
        return "BSD License", url
    return name, url


def collect():
    coords = [l.strip() for l in DEPS_FILE.read_text(encoding="utf-8").splitlines() if l.strip()]
    rows, missing = [], []
    for coord in coords:
        g, a, v = coord.split(":")
        if coord in MANUAL:
            name, url, note = MANUAL[coord]
            rows.append((g, a, v, name, url, note))
            continue
        pom = find_pom(g, a, v)
        lics = parse_licenses(pom) if pom else []
        if not lics:
            missing.append(coord)
            rows.append((g, a, v, "未声明", "", "需人工核查"))
            continue
        name, url = normalize(*lics[0])
        rows.append((g, a, v, name, url, ""))
    rows.sort(key=lambda r: (r[0], r[1]))
    return rows, missing


def write_markdown(rows):
    groups = {}
    for r in rows:
        groups.setdefault(r[3], []).append(r)

    md = ["# 第三方许可声明 (Third-Party Licenses)\n",
          "Syl ImageConv 的发布产物中包含下列第三方开源组件。本文件由 `tools/gen_licenses.py`",
          "读取各组件 POM 中的 `<licenses>` 元数据生成，请勿手工编辑。\n",
          f"- 运行时组件（随 APK 分发）：**{len(rows)} 个**",
          "- 涉及协议：" + "、".join(f"**{k}**（{len(v)} 个）" for k, v in sorted(groups.items(), key=lambda x: -len(x[1]))),
          "- 协议全文：见仓库根目录 [`LICENSE`](LICENSE)\n"]

    for name, items in sorted(groups.items(), key=lambda x: (-len(x[1]), x[0])):
        md.append(f"## {name}\n")
        urls = sorted({r[4] for r in items if r[4]})
        if urls:
            md.append(f"协议地址：<{urls[0]}>\n")
        md.append("| 组件 | 版本 | 备注 |")
        md.append("|---|---|---|")
        for g, a, v, _, _, note in items:
            md.append(f"| `{g}:{a}` | {v} | {note} |")
        md.append("")

    md.append("## 构建期工具（不随 APK 分发）\n")
    md.append("| 工具 | 版本 | 协议 |")
    md.append("|---|---|---|")
    for name, ver, lic, url in BUILD_TOOLS:
        md.append(f"| {name} | {ver} | [{lic}]({url}) |")
    md.append("")
    md.append("## 素材\n")
    md.append("界面图标与启动图标为手写 vector path，形状参考 Material Design Icons")
    md.append("（Apache License 2.0，<https://github.com/google/material-design-icons>）。")
    md.append("未使用任何第三方图片、字体或音频资源。\n")
    md.append("## Apache License 2.0 合规说明\n")
    md.append("上述 Apache 2.0 组件均以未修改的二进制形式（AAR/JAR）链接进 APK。")
    md.append("根据协议第 4 条，分发时需随附协议副本并保留版权与归属声明，本项目通过以下方式履行：\n")
    md.append("1. 仓库根目录提供 `LICENSE` 全文，`NOTICE` 列出主要归属方；")
    md.append("2. 应用内「开源许可」页面展示本清单与协议全文（打包在 `assets/licenses/`）；")
    md.append("3. 未修改任何第三方组件的版权声明，未使用其商标。\n")

    (ROOT / "THIRD_PARTY_LICENSES.md").write_text("\n".join(md), encoding="utf-8")


def write_asset(rows):
    groups = {}
    for r in rows:
        groups.setdefault(r[3], []).append(r)

    txt = ["开源许可 / Open Source Licenses", "",
           "Syl ImageConv 使用了以下开源组件，在此致谢。",
           f"随 APK 分发的运行时组件共 {len(rows)} 个，协议全文见文末。", ""]
    for name, items in sorted(groups.items(), key=lambda x: (-len(x[1]), x[0])):
        txt += ["=" * 60, name, "=" * 60]
        for g, a, v, _, _, note in items:
            line = f"{g}:{a}:{v}"
            if note:
                line += f"  ({note})"
            txt.append(line)
        txt.append("")

    txt += ["=" * 60, "构建期工具（不随 APK 分发）", "=" * 60]
    txt += [f"{n} {ver} — {lic}" for n, ver, lic, _ in BUILD_TOOLS]
    txt += ["", "=" * 60, "图标素材", "=" * 60,
            "界面图标为手写 vector path，形状参考 Material Design Icons",
            "(Apache License 2.0, https://github.com/google/material-design-icons)。", ""]

    out = ROOT / "app/src/main/assets/licenses/third_party_licenses.txt"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text("\n".join(txt), encoding="utf-8")


def sync_legal_files():
    """把仓库根的 LICENSE / NOTICE 同步进 assets，保证 APK 内自带协议副本与归属声明。"""
    assets = ROOT / "app/src/main/assets/licenses"
    assets.mkdir(parents=True, exist_ok=True)
    for src_name, dst_name in (("LICENSE", "apache-2.0.txt"), ("NOTICE", "NOTICE.txt")):
        src = ROOT / src_name
        if src.is_file():
            (assets / dst_name).write_bytes(src.read_bytes())
        else:
            print(f"警告：缺少 {src_name}，跳过同步")


def main():
    if "--refresh" in sys.argv:
        refresh_deps()
    rows, missing = collect()
    write_markdown(rows)
    write_asset(rows)
    sync_legal_files()

    groups = {}
    for r in rows:
        groups.setdefault(r[3], []).append(r)
    print(f"运行时组件 {len(rows)} 个")
    for k, v in sorted(groups.items(), key=lambda x: -len(x[1])):
        print(f"  {k}: {len(v)}")
    if missing:
        print(f"警告：{len(missing)} 个模块缺少 licenses 声明，需在 MANUAL 中补正：")
        for m in missing:
            print(f"  {m}")
        sys.exit(1)


if __name__ == "__main__":
    main()
