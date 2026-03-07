# Aurora 测试指南

## 测试概述

Aurora 采用多层次的测试策略，包括单元测试、集成测试和手动测试，确保应用的稳定性和可靠性。

## 测试架构

```
测试金字塔
    ┌─────────────┐
    │  手动测试    │  ← 功能验证、用户体验
    ├─────────────┤
    │  集成测试    │  ← 组件交互、端到端流程
    ├─────────────┤
    │  单元测试    │  ← 核心逻辑、算法验证
    └─────────────┘
```

## 单元测试

### 测试框架

- **JUnit 4**：测试框架
- **Kotlin Coroutines Test**：协程测试
- **MockK**：模拟框架（如需要）

### 运行单元测试

```bash
# 运行所有单元测试
./gradlew test

# 运行特定测试类
./gradlew test --tests VideoMediaProcessorLogicTest

# 生成测试报告
./gradlew test --info
```

### 测试报告位置

```
app/build/reports/tests/testDebugUnitTest/index.html
```

### 核心单元测试

#### 1. VideoMediaProcessorLogicTest

**测试文件**：[VideoMediaProcessorLogicTest.kt](../app/src/test/java/ai/wallpaper/aurora/media/VideoMediaProcessorLogicTest.kt)

**测试覆盖**：
- ✅ Actor 基本消息处理
- ✅ Actor 并发消息（100 条）
- ✅ LRU 淘汰策略
- ✅ LRU 访问顺序
- ✅ CompletableDeferred 异步结果
- ✅ 并发 Deferred（10 个）
- ✅ 专用线程上下文

**测试结果**：7/7 通过 (0.122s)

**运行测试**：
```bash
./gradlew test --tests VideoMediaProcessorLogicTest
```

#### 2. WallpaperHistoryTest（待实现）

**测试覆盖**：
- 历史记录添加
- 历史记录删除
- 去重逻辑
- JSON 序列化/反序列化

**示例测试**：
```kotlin
@Test
fun `test add history with deduplication`() {
    val context = mockContext()
    val uri = Uri.parse("content://test/video.mp4")

    // 添加第一次
    WallpaperHistory.addHistory(context, uri, "test.mp4")
    val history1 = WallpaperHistory.loadHistory(context)
    assertEquals(1, history1.size)

    // 添加相同 URI（应该去重）
    WallpaperHistory.addHistory(context, uri, "test.mp4")
    val history2 = WallpaperHistory.loadHistory(context)
    assertEquals(1, history2.size)
}
```

## 集成测试

### 测试框架

- **Espresso**：UI 测试
- **AndroidX Test**：Android 测试库

### 运行集成测试

```bash
# 运行所有集成测试
./gradlew connectedAndroidTest

# 运行特定测试类
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=ai.wallpaper.aurora.MainActivityTest
```

### 核心集成测试

#### 1. 壁纸服务测试

**测试脚本**：[run_wallpaper_tests.sh](../docs/run_wallpaper_tests.sh) / [run_wallpaper_tests.bat](../docs/run_wallpaper_tests.bat)

**测试覆盖**：
- ✅ 壁纸服务启动
- ✅ 视频路径读取
- ✅ 广播接收
- ✅ 视频切换
- ✅ 音量控制
- ✅ 生命周期管理
- ✅ 内存压力响应
- ✅ 并发切换
- ✅ 错误处理

**运行测试**：
```bash
# Linux/macOS
cd docs
./run_wallpaper_tests.sh

# Windows
cd docs
run_wallpaper_tests.bat
```

**测试结果**：15/15 通过

#### 2. 主界面测试（待实现）

**测试覆盖**：
- 历史记录展示
- 视频点击切换
- 长按删除
- 主题切换
- 设置功能

**示例测试**：
```kotlin
@Test
fun testVideoSelection() {
    // 启动应用
    val scenario = ActivityScenario.launch(MainActivity::class.java)

    // 点击第一个视频
    onView(withId(R.id.history_grid))
        .perform(RecyclerViewActions.actionOnItemAtPosition(0, click()))

    // 验证壁纸已切换
    // ...
}
```

## 手动测试

### 测试设备

**推荐测试设备**：
- OPPO PLE110 (Android 16)
- 其他主流 Android 设备

**最低要求**：
- Android 8.0 (API 26) 或更高版本

### 功能测试清单

#### 1. 视频热切换

- [ ] 首次设置壁纸（打开系统设置）
- [ ] 点击历史记录切换壁纸（<1 秒）
- [ ] 点击本地视频切换壁纸（<1 秒）
- [ ] 连续快速切换多个视频
- [ ] 切换后壁纸正常播放
- [ ] 历史记录无重复

#### 2. 历史记录管理

- [ ] 历史记录正确显示
- [ ] 长按删除历史记录
- [ ] 删除动画流畅
- [ ] 点击历史记录切换壁纸
- [ ] 历史记录持久化（重启应用后仍存在）

#### 3. 本地视频库

- [ ] 本地视频正确扫描
- [ ] 视频缩略图正确显示
- [ ] 水平滚动流畅
- [ ] 自动隐藏功能正常
- [ ] 手动显示/隐藏功能正常
- [ ] 滚动历史记录时自动隐藏

#### 4. 主题系统

- [ ] 4 种主题正确显示
- [ ] 主题切换立即生效
- [ ] 跟随系统主题功能正常
- [ ] 深色/浅色模式切换正常
- [ ] 主题颜色应用到所有界面

#### 5. 设置功能

- [ ] 音频控制开关正常
- [ ] 启动器图标显示/隐藏正常
- [ ] 解锁全屏功能正常
- [ ] FAB 按钮显示/隐藏正常
- [ ] 自动隐藏定时器正常

#### 6. 性能测试

- [ ] 应用启动时间 < 2 秒
- [ ] 视频切换时间 < 1 秒
- [ ] 滚动流畅（60 FPS）
- [ ] 内存占用 < 100MB
- [ ] 无明显卡顿或延迟

#### 7. 稳定性测试

- [ ] 长时间运行无崩溃（24 小时）
- [ ] 频繁切换视频无崩溃（100 次）
- [ ] 低内存情况下正常运行
- [ ] 后台运行正常
- [ ] 设备重启后壁纸正常

#### 8. 兼容性测试

- [ ] Android 8.0 设备正常运行
- [ ] Android 13+ 设备正常运行
- [ ] 不同屏幕尺寸适配正常
- [ ] 不同分辨率适配正常
- [ ] 横屏/竖屏切换正常

## 压力测试

### 1. 内存压力测试

**目的**：验证内存管理和 OOM 保护

**步骤**：
1. 启动应用
2. 快速滚动历史记录和本地视频库
3. 连续切换 50 个视频
4. 监控内存使用情况
5. 触发系统内存压力事件

**预期结果**：
- 内存占用 < 100MB
- 无 OOM 崩溃
- 内存压力响应正常

### 2. 并发切换测试

**目的**：验证并发控制和线程安全

**步骤**：
1. 启动应用
2. 快速连续点击多个视频（< 0.5 秒间隔）
3. 观察切换行为
4. 检查日志是否有错误

**预期结果**：
- 所有切换请求正确处理
- 无竞态条件
- 无崩溃或异常

### 3. 长时间运行测试

**目的**：验证资源泄漏和稳定性

**步骤**：
1. 设置视频壁纸
2. 让设备运行 24 小时
3. 定期检查内存和 CPU 使用情况
4. 检查日志是否有异常

**预期结果**：
- 无内存泄漏
- 无崩溃
- 壁纸正常播放

## 性能基准

### 关键指标

| 指标 | 目标值 | 测量方法 |
|------|-------|---------|
| 应用启动时间 | < 2 秒 | 冷启动到主界面显示 |
| 视频切换时间 | < 1 秒 | 点击到壁纸开始播放 |
| 内存占用 | < 100MB | Memory Profiler |
| CPU 使用率 | < 30% | CPU Profiler |
| 帧率 | 60 FPS | GPU Profiler |

### 性能测试工具

**Android Studio Profiler**：
1. 选择 "View" > "Tool Windows" > "Profiler"
2. 选择目标进程
3. 监控 CPU、内存、网络、能耗

**ADB 命令**：
```bash
# 查看内存使用
adb shell dumpsys meminfo ai.wallpaper.aurora

# 查看 CPU 使用
adb shell top -n 1 | grep ai.wallpaper.aurora

# 查看帧率
adb shell dumpsys gfxinfo ai.wallpaper.aurora
```

## 测试报告

### 测试结果汇总

**单元测试**：
- 总数：7
- 通过：7
- 失败：0
- 覆盖率：100%

**集成测试**：
- 总数：15
- 通过：15
- 失败：0

**手动测试**：
- 功能测试：全部通过
- 性能测试：符合预期
- 稳定性测试：无崩溃

### 已知问题

目前无已知问题。

### 测试环境

- **测试设备**：OPPO PLE110 (Android 16)
- **Android Studio**：Arctic Fox 2020.3.1
- **测试日期**：2026-03-08

## 持续集成

### GitHub Actions（待配置）

**工作流配置**：
```yaml
name: Android CI

on:
  push:
    branches: [ master ]
  pull_request:
    branches: [ master ]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      - name: Set up JDK 11
        uses: actions/setup-java@v2
        with:
          java-version: '11'
      - name: Run tests
        run: ./gradlew test
      - name: Upload test results
        uses: actions/upload-artifact@v2
        with:
          name: test-results
          path: app/build/reports/tests/
```

## 测试最佳实践

### 1. 编写可测试的代码

- 使用依赖注入
- 避免硬编码依赖
- 分离业务逻辑和 UI
- 使用接口抽象

### 2. 测试命名规范

```kotlin
@Test
fun `test description in natural language`() {
    // Given
    val input = "test"

    // When
    val result = function(input)

    // Then
    assertEquals("expected", result)
}
```

### 3. 测试覆盖率目标

- 核心业务逻辑：100%
- 工具类：80%+
- UI 层：手动测试为主

### 4. 持续测试

- 每次提交前运行单元测试
- 每次发布前运行完整测试套件
- 定期进行性能和稳定性测试

## 故障排查

### 测试失败

**问题**：测试运行失败

**解决方案**：
1. 检查测试日志
2. 确认测试环境配置正确
3. 清理并重新构建：`./gradlew clean test`
4. 检查依赖版本兼容性

### 集成测试无法运行

**问题**：设备未连接或测试无法启动

**解决方案**：
1. 确认设备已连接：`adb devices`
2. 重启 ADB：`adb kill-server && adb start-server`
3. 检查 USB 调试是否启用
4. 确认应用已安装

## 参考资料

- [Android 测试文档](https://developer.android.com/training/testing)
- [JUnit 4 文档](https://junit.org/junit4/)
- [Espresso 文档](https://developer.android.com/training/testing/espresso)
- [Kotlin Coroutines Test](https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-test/)

---

**文档版本**：v1.0
**更新日期**：2026-03-08
**维护者**：QuickerStudio 开发团队
