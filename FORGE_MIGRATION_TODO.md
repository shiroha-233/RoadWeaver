# Forge 移植待办事项

## ✅ 已完成
1. 创建 forge/build.gradle 配置文件
2. 更新 gradle.properties 添加 Forge 版本
3. 更新 settings.gradle 包含 fabric 和 forge 模块
4. 复制源代码到 forge 文件夹
5. 创建 mods.toml 配置文件
6. 更新主类 SettlementRoads.java 使用 Forge API

## 🔄 需要转换的核心文件

### 1. WorldDataAttachment.java
- **Fabric API**: `AttachmentRegistry.createPersistent()`
- **Forge 替代**: 使用 `SavedData` 系统或 `Capability` 系统
- **文件**: `forge/src/main/java/net/countered/settlementroads/persistence/attachments/WorldDataAttachment.java`

### 2. SettlementRoadsClient.java
- **Fabric API**: 
  - `ClientModInitializer`
  - `KeyBindingHelper.registerKeyBinding()`
  - `ClientTickEvents.END_CLIENT_TICK.register()`
- **Forge 替代**:
  - 使用 `@Mod.EventBusSubscriber(modid = MOD_ID, value = Dist.CLIENT)`
  - `RegisterKeyMappingsEvent`
  - `ClientTickEvent`
- **文件**: `forge/src/main/java/net/countered/settlementroads/client/SettlementRoadsClient.java`

### 3. ModEventHandler.java
- **Fabric API**: `ServerLifecycleEvents`
- **Forge 替代**: `ServerStartingEvent`, `ServerStoppingEvent`
- **文件**: `forge/src/main/java/net/countered/settlementroads/events/ModEventHandler.java`

### 4. RoadFeatureRegistry.java
- **Fabric API**: `Registry.register()`
- **Forge 替代**: `RegisterEvent` 或 `DeferredRegister`
- **文件**: `forge/src/main/java/net/countered/settlementroads/features/config/RoadFeatureRegistry.java`

### 5. SettlementRoadsDataGenerator.java
- **Fabric API**: `DataGeneratorEntrypoint`
- **Forge 替代**: `GatherDataEvent`
- **文件**: `forge/src/main/java/net/countered/settlementroads/SettlementRoadsDataGenerator.java`

## 📝 其他需要检查的文件

### Mixins
- 检查 `roadweaver.mixins.json` 中的 mixin 是否与 Forge 兼容
- 可能需要调整 mixin 配置

### 资源文件
- ✅ 已删除 fabric.mod.json
- ✅ 已创建 META-INF/mods.toml
- 语言文件 (lang/*.json) - 无需修改
- 结构文件 (structures/*.nbt) - 无需修改

## 🎯 关键差异

### Fabric vs Forge API 对照表

| 功能 | Fabric API | Forge API |
|------|-----------|-----------|
| 模组初始化 | `ModInitializer` | `@Mod` + 构造函数 |
| 客户端初始化 | `ClientModInitializer` | `@Mod.EventBusSubscriber(Dist.CLIENT)` |
| 数据附件 | `AttachmentType` | `SavedData` / `Capability` |
| 按键绑定 | `KeyBindingHelper` | `RegisterKeyMappingsEvent` |
| 事件注册 | `*.register()` | `@SubscribeEvent` |
| 注册表 | `Registry.register()` | `DeferredRegister` / `RegisterEvent` |

## 🚀 下一步行动

1. 转换 WorldDataAttachment 为 Forge SavedData 系统
2. 转换客户端初始化和按键绑定
3. 转换事件处理系统
4. 转换特性注册系统
5. 测试编译
6. 清理根目录旧 src 文件夹

## ⚠️ 注意事项

- Forge 1.20.1 使用 Java 17，不是 Java 21
- MidnightLib 需要确认 Forge 版本是否可用
- 某些 Fabric API 在 Forge 中可能没有直接等价物，需要自己实现
