# 屏幕固定

适用于 Android 9 及更新版本的手机和平板。直接在设备上安装 `lan-web-kiosk-1.2.apk`，从桌面打开「屏幕固定」即可设置。覆盖升级保留已保存的网页地址，并清除旧的应用密码记录。

## 打开网页和退出

1. 让设备连接到网页服务器所在的局域网。首次打开时，输入网页的 IPv4 地址和端口，点击「打开网页」。例如输入 `192.168.1.10` 与 `8080`，打开 `http://192.168.1.10:8080/`。使用 HTTPS 时勾选「使用 HTTPS」，证书须能通过安卓系统校验。
2. 网页以全屏显示。点击链接和滚动照常使用，左右边缘返回手势和系统返回键会留在当前网页。网页加载失败时可以点击「重试」。
3. 用系统导航回到桌面即可离开应用。下次打开会载入保存的网页。若要更换 IP 或端口，在桌面长按「屏幕固定」图标，选择「修改地址」。

## 使用安卓屏幕固定

从屏幕边缘呼出系统导航后，可以进入最近任务界面并固定应用。请按平板的系统提示解除固定。如果设备已设置解除固定时验证，系统会要求使用设备锁屏凭据。旧版启用的应用专用设备锁定会在升级后解除。 [安卓屏幕固定说明](https://support.google.com/android/answer/9455138?hl=zh-Hans)

## 从源码构建

使用 JDK 17 或 21、Android SDK Platform 37.0 和 Build Tools 37.0.0。通过 `ANDROID_HOME` 或 Android Studio 生成的 `local.properties` 指定 SDK 目录。

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleRelease
```

使用 Android SDK 的 `apksigner` 为 release APK 签名。给已部署设备安装更新时，沿用该设备现有 APK 的签名证书，并提高新版本的版本号。
