# Aurora 文档索引

欢迎查阅 Aurora 项目文档。本目录包含项目的完整技术文档。

## 📚 核心文档

### [ARCHITECTURE.md](ARCHITECTURE.md)
**系统架构和设计模式**

详细介绍 Aurora 的整体架构、核心组件、设计模式和技术栈。包括：
- 系统架构图
- 核心组件说明
- 设计模式（进程隔离、观察者模式、Actor 模式、LRU 缓存）
- 数据流和性能优化
- 安全性和兼容性

### [HOT_SWITCH.md](HOT_SWITCH.md)
**视频热切换技术**

Aurora 的核心创新功能，实现视频壁纸的即时切换。包括：
- 遥控器-电视通信模型
- 双通道通信机制（文件 + 广播）
- 核心实现代码
- 性能对比（3-5 倍提升）
- 技术创新点和安全性

### [RESOURCE_MANAGEMENT.md](RESOURCE_MANAGEMENT.md)
**内存和资源优化**

详细说明资源管理策略和优化技术。包括：
- VideoMediaProcessor（播放器池管理）
- MemoryPressureMonitor（内存压力监控）
- LRU 缓存策略
- Actor 模式并发控制
- 性能指标（80-85% 内存优化）

## 🛠️ 开发指南

### [DEVELOPMENT.md](DEVELOPMENT.md)
**开发环境和构建流程**

完整的开发指南，帮助开发者快速上手。包括：
- 环境要求和项目设置
- 项目结构说明
- 开发工作流和代码规范
- 调试技巧和常见问题
- 构建和发布流程

### [TESTING.md](TESTING.md)
**测试策略和执行指南**

全面的测试文档，确保代码质量。包括：
- 单元测试（7/7 通过）
- 集成测试（15/15 通过）
- 手动测试清单
- 压力测试和性能基准
- 测试报告和持续集成

### [API_REFERENCE.md](API_REFERENCE.md)
**核心 API 和组件参考**

详细的 API 文档，方便开发者查阅。包括：
- 核心服务（VideoLiveWallpaperService）
- 数据管理（WallpaperHistory）
- 媒体处理（VideoMediaProcessor, MemoryPressureMonitor）
- 工具类（LocalVideoScanner, LRUPlayerPool）
- 主题系统和常量定义

## 📖 快速导航

### 新手入门
1. 阅读 [README.md](../README.md) 了解项目概况
2. 参考 [DEVELOPMENT.md](DEVELOPMENT.md) 设置开发环境
3. 查看 [ARCHITECTURE.md](ARCHITECTURE.md) 理解系统架构

### 核心功能
- **视频热切换**：[HOT_SWITCH.md](HOT_SWITCH.md)
- **资源管理**：[RESOURCE_MANAGEMENT.md](RESOURCE_MANAGEMENT.md)
- **API 参考**：[API_REFERENCE.md](API_REFERENCE.md)

### 开发和测试
- **开发指南**：[DEVELOPMENT.md](DEVELOPMENT.md)
- **测试指南**：[TESTING.md](TESTING.md)

## 📊 文档统计

| 文档 | 大小 | 主题 |
|------|------|------|
| ARCHITECTURE.md | 12K | 系统架构 |
| HOT_SWITCH.md | 8.5K | 热切换技术 |
| RESOURCE_MANAGEMENT.md | 8.9K | 资源管理 |
| DEVELOPMENT.md | 9.3K | 开发指南 |
| TESTING.md | 9.7K | 测试指南 |
| API_REFERENCE.md | 15K | API 参考 |

**总计**：6 个核心文档，约 63K 内容

## 🔄 文档更新

所有文档均已更新至 v1.0 版本（2026-03-08），与项目最新进度保持同步。

### 文档维护原则
- **一个主题一个文档**：每个文档专注于一个主题
- **结构清晰**：使用标题、列表、代码块等组织内容
- **示例丰富**：提供实际代码示例和使用场景
- **持续更新**：随项目发展及时更新文档

## 📝 贡献文档

如果您发现文档有误或需要改进，欢迎：
1. 在 GitHub 上提交 Issue
2. 提交 Pull Request 修改文档
3. 联系维护团队

## 📧 联系方式

- **GitHub**：https://github.com/QuickerStudio/Aurora
- **Issues**：https://github.com/QuickerStudio/Aurora/issues

---

**文档版本**：v1.0
**更新日期**：2026-03-08
**维护者**：QuickerStudio 开发团队
