# Aurora 真机压力测试指南

## 测试准备

### 1. 安装 APK
```bash
# APK 位置
app/build/outputs/apk/debug/app-debug.apk

# 安装命令
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 2. 启动 Logcat 监控
```bash
# 清空旧日志
adb logcat -c

# 开始监控（过滤关键标签）
adb logcat -v time \
  VideoMediaProcessor:D \
  MemoryPressureMonitor:D \
  LRUPlayerPool:D \
  MainActivity:D \
  System.err:W \
  AndroidRuntime:E \
  *:S | tee AndroidLog/realdevice_test_$(date +%Y%m%d_%H%M%S).log
```

## 测试场景

### 场景 1: 快速滚动压力测试
**目标**: 验证 LRU 策略和播放器池上限

**操作步骤**:
1. 打开应用
2. 在历史记录列表中快速上下滚动 30 秒
3. 观察 Logcat 输出

**预期结果**:
- ✅ 播放器数量不超过 3 个
- ✅ 看到 "LRU evicting player" 日志
- ✅ 没有 OutOfMemoryError
- ✅ UI 流畅，无明显卡顿

**关键日志**:
```
VideoMediaProcessor: Creating player: content://... (pool size: 1)
VideoMediaProcessor: Creating player: content://... (pool size: 2)
VideoMediaProcessor: Creating player: content://... (pool size: 3)
VideoMediaProcessor: LRU evicting player: content://...
```

---

### 场景 2: 本地视频库滚动测试
**目标**: 验证视频裁剪和缓冲控制

**操作步骤**:
1. 展开本地视频库
2. 快速左右滑动 20 次
3. 观察内存使用

**预期结果**:
- ✅ 每个视频只加载前 10 秒
- ✅ 缓冲控制在 2-5 秒
- ✅ 内存增长平稳

**关键日志**:
```
VideoMediaProcessor: Creating player with clipping: 0-10000ms
DefaultLoadControl: Buffer: min=2000ms, max=5000ms
```

---

### 场景 3: 内存压力响应测试
**目标**: 验证系统内存紧张时的自动降级

**操作步骤**:
1. 打开多个其他应用（消耗内存）
2. 返回 Aurora 应用
3. 快速滚动

**预期结果**:
- ✅ 看到 "Memory pressure: RUNNING_LOW" 日志
- ✅ 播放器数量自动减少到 2 个
- ✅ 应用不崩溃

**关键日志**:
```
MemoryPressureMonitor: Memory pressure: RUNNING_LOW, trimming player pool
VideoMediaProcessor: Trimming pool from 3 to 2
VideoMediaProcessor: Trimmed 1 players, new size: 2
```

---

### 场景 4: 长时间运行稳定性测试
**目标**: 验证无内存泄漏

**操作步骤**:
1. 连续使用应用 10 分钟
2. 在历史记录和本地库之间切换
3. 使用 Android Profiler 监控内存

**预期结果**:
- ✅ 内存曲线平稳，无持续增长
- ✅ GC 频率正常
- ✅ 无内存泄漏警告

---

### 场景 5: 极限并发测试
**目标**: 验证 Actor 模式的并发安全

**操作步骤**:
1. 快速在历史记录中点击多个视频
2. 同时滚动列表
3. 快速切换到本地库

**预期结果**:
- ✅ 无 ConcurrentModificationException
- ✅ 无死锁
- ✅ 所有操作响应正常

---

### 场景 6: 后台返回测试
**目标**: 验证 UI_HIDDEN 内存释放

**操作步骤**:
1. 打开应用并滚动
2. 按 Home 键进入后台
3. 等待 30 秒
4. 返回应用

**预期结果**:
- ✅ 后台时看到 "UI hidden, clearing players" 日志
- ✅ 返回前台时播放器重新创建
- ✅ 无崩溃

**关键日志**:
```
MemoryPressureMonitor: UI hidden, clearing players
VideoMediaProcessor: Releasing all players (count: 3)
```

---

## 性能指标收集

### 使用 Android Profiler

1. **内存监控**:
   ```
   Android Studio → View → Tool Windows → Profiler
   选择设备和进程 → Memory
   ```

   **关键指标**:
   - Java Heap: 应该 < 100MB
   - Native Heap: 应该 < 50MB
   - Graphics: 应该 < 30MB

2. **CPU 监控**:
   ```
   Profiler → CPU
   ```

   **关键指标**:
   - 主线程 CPU 使用率 < 50%
   - 无长时间阻塞（> 16ms）

### 使用 adb 命令

```bash
# 实时内存监控
watch -n 1 'adb shell dumpsys meminfo ai.wallpaper.aurora | grep -A 10 "App Summary"'

# 导出内存快照
adb shell am dumpheap ai.wallpaper.aurora /data/local/tmp/heap.hprof
adb pull /data/local/tmp/heap.hprof AndroidLog/

# 查看线程信息
adb shell ps -T -p $(adb shell pidof ai.wallpaper.aurora)
```

---

## 日志分析要点

### 正常日志模式
```
✅ VideoMediaProcessor: Creating player: ... (pool size: 1)
✅ VideoMediaProcessor: Creating player: ... (pool size: 2)
✅ VideoMediaProcessor: Creating player: ... (pool size: 3)
✅ VideoMediaProcessor: LRU evicting player: ...
✅ VideoMediaProcessor: Trimmed 1 players, new size: 2
```

### 异常日志模式
```
❌ OutOfMemoryError: Failed to allocate ...
❌ ConcurrentModificationException
❌ IllegalStateException: Player is not prepared
❌ DEAD_OBJECT: Transaction failed
❌ Process ... has died: prcp TOP
```

---

## 对比测试

### 优化前 vs 优化后

| 指标 | 优化前 | 优化后 | 测试方法 |
|-----|-------|-------|---------|
| 播放器数量 | 无限制 | ≤ 3 | Logcat 计数 |
| 内存峰值 | 500MB+ | < 100MB | Profiler |
| 滚动卡顿 | 频繁 | 无 | 肉眼观察 |
| 崩溃率 | 高 | 低 | 重复测试 |

---

## 测试报告模板

```markdown
# Aurora 真机测试报告

**测试时间**: 2026-03-07
**设备型号**: [填写]
**Android 版本**: [填写]
**内存大小**: [填写]

## 场景 1: 快速滚动
- [ ] 通过 / [ ] 失败
- 播放器峰值数量: ___
- 内存峰值: ___ MB
- 异常日志: [粘贴]

## 场景 2: 本地视频库
- [ ] 通过 / [ ] 失败
- 视频加载时长: ___ 秒
- 内存增长: ___ MB
- 异常日志: [粘贴]

## 场景 3: 内存压力
- [ ] 通过 / [ ] 失败
- 是否触发降级: [ ] 是 / [ ] 否
- 降级后播放器数: ___
- 异常日志: [粘贴]

## 场景 4: 长时间运行
- [ ] 通过 / [ ] 失败
- 运行时长: ___ 分钟
- 内存泄漏: [ ] 是 / [ ] 否
- 异常日志: [粘贴]

## 场景 5: 极限并发
- [ ] 通过 / [ ] 失败
- 并发异常: [ ] 是 / [ ] 否
- 异常日志: [粘贴]

## 场景 6: 后台返回
- [ ] 通过 / [ ] 失败
- 资源释放: [ ] 是 / [ ] 否
- 异常日志: [粘贴]

## 总体评价
- 稳定性: [ ] 优秀 / [ ] 良好 / [ ] 一般 / [ ] 差
- 性能: [ ] 优秀 / [ ] 良好 / [ ] 一般 / [ ] 差
- 建议: [填写]
```

---

## 下一步

1. **执行测试**: 按照上述场景逐一测试
2. **收集日志**: 保存完整的 Logcat 输出到 `AndroidLog/`
3. **分析结果**: 对比预期结果和实际结果
4. **优化迭代**: 根据测试结果调整算法参数

---

## 快速命令参考

```bash
# 安装 APK
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 启动应用
adb shell am start -n ai.wallpaper.aurora/.MainActivity

# 清空日志
adb logcat -c

# 监控日志
adb logcat -v time VideoMediaProcessor:D MemoryPressureMonitor:D *:S

# 查看内存
adb shell dumpsys meminfo ai.wallpaper.aurora

# 强制 GC
adb shell am force-stop ai.wallpaper.aurora

# 模拟内存压力
adb shell am send-trim-memory ai.wallpaper.aurora RUNNING_LOW
```
