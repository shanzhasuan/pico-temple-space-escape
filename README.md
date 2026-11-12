# 神庙之空间逃亡

基于公开 PICO Spatial SDK6.1.9 的第一人称3D神庙跑酷游戏，当前版本0.6.0，Android包名`com.shanzhasuan.templesprint`。

四个区域包含丛林遗迹、峡谷栈桥、机关回廊和圣坛逃脱。玩家在三条路线之间切换，收集金币，跳过倒木、火焰和断路，滑铲穿过低门，并在路口按箭头转向。游戏包含逐步加速、守卫追击、坐姿校准及默认开启的舒适模式。

## 操作

- 自动向前奔跑，摇杆左右换道或确认路口转向。
- 扳机、A/X或摇杆向上跳跃；握键或摇杆向下滑铲。
- B/Y暂停，选关后点击“开始游戏”或“开始本关”。

## 本地构建

需要JDK17–21、Android SDK35，使用仓库内Gradle8.13 Wrapper。外部依赖来自Google、Maven Central、Gradle Plugin Portal及公开Volcengine Maven仓库；构建不需要Node、PICO Spatial Editor或模型生成服务。

```sh
./gradlew assembleDebug testDebugUnitTest lintDebug
./gradlew assembleRelease
```

调试APK输出于`app/build/outputs/apk/debug/`。本地Release默认生成未签名APK，独立安装前请使用自己的签名配置。Android SDK位置可由Android Studio或本地`local.properties`配置，该文件不提交。

## 资源与发布

应用运行所需模型、纹理和音频均在`app/src/main/assets/`，无需重新生成。模型由PICO AI Generation Service生成，并在本地进行坐标及游戏尺寸适配；动态火焰的低面数几何和运动在应用内实现。录音音效的CC0来源及处理说明见`app/src/main/assets/audio/SOURCES.txt`。

`vibelab.yaml`包含公开仓库及版本发布配置。本地上传素材根目录`assets/`和发布缓存`.vibelab/`不提交到Git；应用资源目录`app/src/main/assets/`正常提交。
