# Aurora

Android 动态壁纸应用

## Gradle 镜像配置

为了加快构建速度，项目已配置国内镜像源（按速度排序）：

1. **腾讯云** - 平均延迟 4ms
   - `https://mirrors.cloud.tencent.com/nexus/repository/maven-public/`

2. **阿里云** - 平均延迟 14ms
   - `https://maven.aliyun.com/repository/public/`
   - `https://maven.aliyun.com/repository/google/`

3. **华为云** - 平均延迟 27ms
   - `https://mirrors.huaweicloud.com/repository/maven/`

镜像配置位于 [settings.gradle.kts](settings.gradle.kts)，已同时配置插件仓库和依赖仓库。
