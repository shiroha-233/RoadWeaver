# NeoForge 构建修复说明

## 问题描述
NeoForge 模块编译失败，找不到 `net.neoforged.*` 和 Minecraft 类。

## 根本原因
1. **映射配置错误**：各子项目单独配置 mappings 导致不一致
2. **缺少 NeoForge 映射补丁**：Yarn 映射需要补丁才能与 NeoForge 兼容

## 解决方案

### 1. 添加 NeoForge 映射补丁版本
**文件**: `gradle.properties`
```properties
yarn_mappings_patch_neoforge_version=1.21+build.4
```

### 2. 统一映射配置到根 build.gradle
**文件**: `build.gradle` (subprojects 块)
```gradle
dependencies {
    minecraft "com.mojang:minecraft:${rootProject.minecraft_version}"
    
    // 统一使用 Yarn + NeoForge 补丁映射（所有平台）
    mappings loom.layered {
        it.mappings("net.fabricmc:yarn:${rootProject.yarn_mappings}:v2")
        it.mappings("dev.architectury:yarn-mappings-patch-neoforge:${rootProject.yarn_mappings_patch_neoforge_version}")
    }
}
```

### 3. 移除子项目中的重复 mappings 配置
- ✅ `common/build.gradle` - 移除 `mappings` 行
- ✅ `fabric/build.gradle` - 移除 `mappings` 行  
- ✅ `neoforge/build.gradle` - 移除 `mappings` 块

### 4. NeoForge 依赖配置
**文件**: `neoforge/build.gradle`
```gradle
dependencies {
    // NeoForge 依赖
    modImplementation "net.neoforged:neoforge:${rootProject.neoforge_version}"
    
    // Architectury API
    modImplementation "dev.architectury:architectury-neoforge:${rootProject.architectury_version}"
    
    // MidnightLib for NeoForge
    modImplementation include("maven.modrinth:midnightlib:${rootProject.midnightlib_neoforge_version}")
    
    common(project(path: ":common", configuration: "namedElements")) { transitive = false }
    shadowBundle project(path: ":common", configuration: "transformProductionNeoForge")
}
```

## 关键要点

### ✅ 正确做法
1. **统一映射配置**：在根 build.gradle 的 subprojects 块中配置
2. **使用 layered mappings**：Yarn + NeoForge 补丁
3. **所有平台使用相同映射**：确保跨平台兼容性

### ❌ 错误做法
1. ~~在各子项目中单独配置 mappings~~
2. ~~NeoForge 使用 officialMojangMappings()~~
3. ~~使用 `neoForge` 依赖方法（Loom 1.11 不支持）~~

## 测试命令

```powershell
# 清理构建
.\gradlew.bat clean

# 构建 NeoForge 模块
.\gradlew.bat :neoforge:build

# 构建所有模块
.\gradlew.bat build
```

## 参考项目
本修复方案参考了 RoadArchitect 项目的配置方式。

## 修改文件清单
- ✅ `gradle.properties` - 添加 yarn_mappings_patch_neoforge_version
- ✅ `build.gradle` - 统一 mappings 配置
- ✅ `common/build.gradle` - 移除 mappings
- ✅ `fabric/build.gradle` - 移除 mappings
- ✅ `neoforge/build.gradle` - 简化依赖配置

---
**修复日期**: 2025-10-04
**Minecraft 版本**: 1.21.1
**NeoForge 版本**: 21.1.82
**Architectury Loom**: 1.11.441
