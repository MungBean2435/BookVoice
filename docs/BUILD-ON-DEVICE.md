# 在 arm64 Linux 上构建

Android SDK 自带的 `aapt2` / `zipalign` 是 **x86_64 ELF**，在 aarch64 机器上直接跑会报
`AAPT2 Daemon #0: Daemon startup failed`。

绕法：用 `qemu-user-static` 跑 x86_64 版 aapt2。只影响资源编译这一步，JDK / Kotlin 仍是原生 arm64。

## 一次性准备

```bash
apt-get install -y qemu-user-static binutils

# aapt2 只依赖 glibc / libgcc，解一份 x86_64 的 glibc 出来
mkdir -p /opt/x86root
dpkg-deb -x libc6_*_amd64.deb /opt/x86root
dpkg-deb -x libgcc-s1_*_amd64.deb /opt/x86root
ln -sfn usr/lib64 /opt/x86root/lib64
ln -sfn usr/lib   /opt/x86root/lib

# 版本必须与 AGP 匹配：AGP 8.7.3 对应 aapt2 12006047，
# 可在 ~/.gradle/caches/modules-2/files-2.1/com.android.tools.build/aapt2/ 下找到
cp ~/.gradle/caches/.../aapt2-8.7.3-12006047-linux/aapt2 /opt/aapt2-x86_64
printf '#!/bin/sh\nexec /usr/bin/qemu-x86_64-static -L /opt/x86root /opt/aapt2-x86_64 "$@"\n' > /opt/aapt2
chmod +x /opt/aapt2

# 用户级 gradle.properties，不进仓库
echo 'android.aapt2FromMavenOverride=/opt/aapt2' >> ~/.gradle/gradle.properties
```

## 构建

```bash
export JAVA_HOME=/path/to/jdk17
export ANDROID_HOME=/path/to/android-sdk
./gradlew assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`

## 已知限制

- 上面只验证了 `assembleDebug`；`assembleRelease` 还要跑 `zipalign`（同样是 x86_64 ELF），需要同样的 qemu 包装。
- aapt2 走模拟，资源编译比原生慢；本项目资源不多，实测整包约 30 秒。
- 如果 `services.gradle.org` 拉不动 gradle-wrapper 的发行包，可以先用镜像装一份本地 Gradle 8.9 直接 `gradle assembleDebug`。
