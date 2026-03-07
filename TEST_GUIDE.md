# 壁纸服务集成测试指南

## 测试文件
- `WallpaperServiceIntegrationTest.kt` - 壁纸服务后端逻辑测试（9个测试用例）
- `StressTest.kt` - 播放器池压力测试（6个测试用例）

## 运行方式

### 方式1: Android Studio 图形界面（推荐）

1. **连接真实设备**
   - 通过 USB 连接手机
   - 确保开启 USB 调试
   - 在 Android Studio 中确认设备已连接

2. **运行所有测试**
   - 在项目视图中找到 `app/src/androidTest/java/ai/wallpaper/aurora/WallpaperServiceIntegrationTest.kt`
   - 右键点击文件名
   - 选择 `Run 'WallpaperServiceIntegrationTest'`

3. **运行单个测试**
   - 打开测试文件
   - 点击测试方法左侧的绿色运行按钮
   - 例如：点击 `test1_fileBasedCommunication()` 左侧的 ▶️

### 方式2: 命令行

```bash
# 运行所有 instrumented tests
./gradlew connectedAndroidTest

# 只运行壁纸服务测试
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=ai.wallpaper.aurora.WallpaperServiceIntegrationTest

# 只运行压力测试
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=ai.wallpaper.aurora.StressTest
```

## 测试用例说明

### WallpaperServiceIntegrationTest（壁纸服务测试）

| 测试编号 | 测试名称 | 测试目标 | 预期结果 |
|---------|---------|---------|---------|
| test1 | 文件通信机制 | 验证 video_live_wallpaper_file_path 读写 | 路径保存和读取一致 |
| test2 | 历史记录验证机制 | 验证只有壁纸服务播放后才添加历史 | 点击不添加，播放后添加 |
| test3 | 音量控制功能 | 验证 unmute 文件和音量广播 | 文件创建/删除正常 |
| test4 | 壁纸服务状态检测 | 检测当前壁纸是否为 Aurora | 显示当前壁纸信息 |
| test5 | 跨进程文件访问 | 验证主进程和壁纸进程文件访问 | 文件读写正常 |
| test6 | 历史记录去重 | 验证相同视频不重复添加 | 添加3次只保留1条 |
| test7 | 历史记录限制 | 验证最多保存50条 | 添加60条只保留50条 |
| test8 | 广播安全性 | 验证 RECEIVER_NOT_EXPORTED | Android 13+ 使用安全广播 |
| test9 | 完整流程模拟 | 模拟从选择到播放的完整流程 | 所有步骤正常执行 |

### StressTest（压力测试）

| 测试编号 | 测试名称 | 测试目标 | 预期结果 |
|---------|---------|---------|---------|
| test1 | 历史卡片快速滚动 | 模拟疯狂滑动100个视频 | 池大小≤3，内存增长<50MB |
| test2 | 本地库增量加载 | 模拟5次增量加载（共100个） | 池大小≤3，内存增长<50MB |
| test3 | 双池并发压力 | 同时操作历史和本地库 | 总播放器≤6，内存增长<100MB |
| test4 | 极限场景 | 连续200次快速操作 | 内存波动<50MB，无泄漏 |
| test5 | 频繁删除操作 | 20轮创建-删除循环 | 内存增长<30MB |
| test6 | 增量加载边界 | 从10条增长到100条 | 池大小≤3，内存增长<50MB |

## 查看测试结果

### Android Studio 中查看

1. 测试运行后，底部会显示 `Run` 窗口
2. 绿色 ✓ 表示通过，红色 ✗ 表示失败
3. 点击测试名称可以查看详细日志

### Logcat 中查看

1. 打开 Android Studio 的 Logcat 窗口
2. 过滤标签：
   - `WallpaperServiceTest` - 壁纸服务测试日志
   - `StressTest` - 压力测试日志
3. 所有测试都会输出详细的步骤和结果

### 命令行查看

测试报告位置：
```
app/build/reports/androidTests/connected/index.html
```

在浏览器中打开此文件可以查看完整的测试报告。

## 测试前准备

### 必需条件
- ✅ 真实 Android 设备（API 21+）
- ✅ USB 调试已开启
- ✅ 设备已连接到电脑

### 可选条件（用于 test4）
- 如果想测试壁纸服务状态检测，需要先手动设置 Aurora 为壁纸
- 步骤：打开 Aurora → 选择视频 → 点击"设为壁纸"

## 常见问题

### Q1: 测试失败 "No connected devices"
**解决方案**：
1. 检查 USB 连接
2. 确认设备已开启 USB 调试
3. 运行 `adb devices` 确认设备已连接

### Q2: 测试超时
**解决方案**：
1. 确保设备性能良好
2. 关闭其他占用资源的应用
3. 增加测试超时时间（在测试方法上添加 `@Test(timeout = 10000)`）

### Q3: 广播接收失败
**解决方案**：
1. 检查 Android 版本（Android 13+ 需要 RECEIVER_NOT_EXPORTED）
2. 确认包名正确
3. 查看 Logcat 是否有权限错误

### Q4: 历史记录测试失败
**解决方案**：
1. 确保测试前清理了环境（`@Before` 会自动清理）
2. 检查文件权限
3. 查看 `wallpaper_history.json` 文件是否正常

## 测试覆盖率

当前测试覆盖的功能模块：
- ✅ 文件通信层（video_live_wallpaper_file_path）
- ✅ 历史记录管理（WallpaperHistoryManager）
- ✅ 跨进程广播通信（ACTION_VIDEO_PLAYBACK_STARTED）
- ✅ 音量控制（unmute 文件 + 广播）
- ✅ 播放器池管理（LRUPlayerPool）
- ✅ 内存管理和泄漏检测

未覆盖的功能（需要真实壁纸服务运行）：
- ⚠️ MediaPlayer 实际播放
- ⚠️ Surface 渲染
- ⚠️ 壁纸服务生命周期

## 下一步

1. **运行测试**：先运行 `WallpaperServiceIntegrationTest` 验证基础功能
2. **查看日志**：在 Logcat 中查看详细的测试步骤
3. **修复问题**：如果有测试失败，根据日志定位问题
4. **压力测试**：基础功能正常后，运行 `StressTest` 验证性能
5. **真实测试**：手动设置壁纸，观察实际运行效果

## 联系方式

如果测试遇到问题，请提供：
1. 测试失败的截图
2. Logcat 完整日志
3. 设备型号和 Android 版本
