# 旋转控制

为指定应用固定屏幕旋转角度的 LSPosed 模块。

在 LSPosed 管理器中把作用域设为目标应用后，模块会在这些应用的进程里替换 Activity 的屏幕方向。

支持的角度：跟随系统、0°（竖屏）、90°（横屏）、180°（反向竖屏）、270°（反向横屏）。

## 要求

- Android 8.1 及以上（minSdk 27）
- LSPosed，或其他兼容 Xposed API 82 的框架
- root —— 配置需要写入系统设置，见下文

## 构建

```
./gradlew assembleDebug
```

在 `local.properties` 中指定 Android SDK 路径，或用 Android Studio 直接打开本工程。

## 使用

1. 安装并打开应用，按提示授予 root 权限
2. 打开 LSPosed 管理器，启用「旋转控制」
3. 作用域中勾选需要控制的第三方应用。不必勾选系统框架
4. 回到应用，点击目标应用选择角度
5. 修改后重启目标应用生效

## 实现

### Hook

在作用域应用的进程里 Hook `android.app.Activity` 的三个位置：

| 位置 | 作用 |
| --- | --- |
| `setRequestedOrientation(int)` | 把应用设置的方向替换为目标角度 |
| `onCreate(Bundle)` | 写入 `ActivityInfo.screenOrientation` 并应用方向 |
| `onResume()` | 兜底 |

第一处用于避免 Activity 反复重建。若只在方向变化之后纠正，应用在 `onCreate` 中把方向改回原值会再次触发方向变化，形成重建循环；直接替换参数可以让方向一次收敛。

### 配置存储

配置保存在 `Settings.System`：

- `app_rotation_enabled` —— 全局开关
- `app_rotation_<包名>` —— 角度

被 Hook 的进程以目标应用自身的身份运行，读不到模块应用的数据目录，因此需要一个跨进程可读的位置。`Settings.System` 的读取不需要任何权限，也不受包可见性限制。

写入需要 root：系统只允许白名单内的设置项被普通应用写入，因此模块通过 root 执行 `settings` 命令完成写入。

## 已知限制

- 在最小宽度不小于 600dp 的设备（平板、折叠屏展开态）上，targetSdk 31 及以上的应用会被系统忽略方向请求。代码尝试改写 `ActivityInfo.screenOrientation` 绕过，未在大屏设备上验证。
- 自行处理方向的少数应用（如部分使用 SurfaceView 的游戏和视频播放器）可能覆盖本设置。
- 应用列表只列出带启动器图标的第三方应用。
