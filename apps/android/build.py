#!/usr/bin/env python3
"""用官方 SDK/NDK 构建可安装测试 APK；不依赖额外的 Gradle 服务。"""
import os
from pathlib import Path
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[2]
APP = ROOT / "apps/android"
OUT = ROOT / "target/android"
SDK = Path(os.environ["ANDROID_SDK_ROOT"])
NDK = Path(os.environ["ANDROID_NDK_HOME"])
JAVA = Path(os.environ["JAVA_HOME"]) / "bin"
TOOLS = SDK / "build-tools/35.0.0"
ANDROID_JAR = SDK / "platforms/android-34/android.jar"
DATA = Path(os.environ["CHUHAI_DATA_DIR"])


def run(*args, env=None):
    subprocess.run([str(arg) for arg in args], cwd=ROOT, env=env, check=True)


OUT.mkdir(parents=True, exist_ok=True)
assets = OUT / "assets"
assets.mkdir(exist_ok=True)
for source, name in [(DATA / "dict.qj", "dict.qj"), (DATA / "wubi/wubi86.tsv", "wubi86.tsv"), (ROOT / "LICENSE", "LICENSE")]:
    shutil.copy2(source, assets / name)
(assets / "NOTICE.txt").write_text("出海舰基于 Qingjian 0.1.4 修改，https://github.com/qingjian-team/qingjian\n源码按 GPL-3.0-or-later 开放。保留原作者及数据来源声明。\n" + (ROOT / "assets/wubi/README.md").read_text() + "\n" + (ROOT / "assets/sample/README.md").read_text())
native = OUT / "lib"
toolchain = NDK / "toolchains/llvm/prebuilt/darwin-x86_64/bin"
for target, abi, compiler in [("aarch64-linux-android", "arm64-v8a", "aarch64-linux-android26-clang"), ("x86_64-linux-android", "x86_64", "x86_64-linux-android26-clang")]:
    environment = os.environ.copy()
    environment[f"CARGO_TARGET_{target.upper().replace('-', '_')}_LINKER"] = str(toolchain / compiler)
    environment[f"CC_{target.replace('-', '_')}"] = str(toolchain / compiler)
    run("cargo", "build", "-p", "chuhai-android-native", "--release", "--locked", "--target", target, env=environment)
    directory = native / abi
    directory.mkdir(parents=True, exist_ok=True)
    run(toolchain / compiler, "-shared", "-fPIC", "-O2", "-Wl,-z,max-page-size=16384", APP / "native/bridge.c", ROOT / f"target/{target}/release/libchuhai_android_native.a", "-ldl", "-llog", "-lm", "-o", directory / "libchuhai.so")

resources = OUT / "resources.zip"
run(TOOLS / "aapt2", "compile", "--dir", APP / "res", "-o", resources)
generated = OUT / "generated"
generated.mkdir(exist_ok=True)
unsigned = OUT / "unsigned.apk"
run(TOOLS / "aapt2", "link", "-o", unsigned, "--manifest", APP / "AndroidManifest.xml", "-I", ANDROID_JAR, "--min-sdk-version", "26", "--target-sdk-version", "35", "--version-code", "1", "--version-name", "0.1.0-test", "--java", generated, "-A", assets, resources)
classes = OUT / "classes"
classes.mkdir(exist_ok=True)
sources = list((APP / "src").rglob("*.java")) + list(generated.rglob("*.java"))
run(JAVA / "javac", "-encoding", "UTF-8", "-source", "8", "-target", "8", "-classpath", ANDROID_JAR, "-d", classes, *sources)
dex = OUT / "dex"
dex.mkdir(exist_ok=True)
run(TOOLS / "d8", "--min-api", "26", "--lib", ANDROID_JAR, "--output", dex, *classes.rglob("*.class"))
with zipfile.ZipFile(unsigned, "a", zipfile.ZIP_DEFLATED) as archive:
    archive.write(dex / "classes.dex", "classes.dex")
    for library in native.rglob("*.so"):
        archive.write(library, str(library.relative_to(OUT)))
aligned = OUT / "aligned.apk"
run(TOOLS / "zipalign", "-f", "-P", "16", "4", unsigned, aligned)
key = OUT / "test-signing.jks"
if not key.exists():
    run(JAVA / "keytool", "-genkeypair", "-keystore", key, "-storepass", "android", "-keypass", "android", "-alias", "androiddebugkey", "-dname", "CN=Chuhai Local Test", "-keyalg", "RSA", "-validity", "3650")
apk = OUT / "出海舰-0.1.0-test.apk"
run(TOOLS / "apksigner", "sign", "--ks", key, "--ks-pass", "pass:android", "--key-pass", "pass:android", "--out", apk, aligned)
run(TOOLS / "apksigner", "verify", "--verbose", apk)
print(apk)
