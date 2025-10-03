# 结构搜寻条件和机制详解

## 📋 概述

RoadWeaver 使用 Minecraft 原版的结构定位系统来查找结构（如村庄），然后在它们之间生成道路。

---

## ⚙️ 配置参数

### 1. 结构搜寻配置

```java
// 位置: ModConfig.java

@Entry(category = "structures")
public static int maxLocatingCount = 100;  // 最大搜寻结构数量

@Entry(category = "structures")
public static String structureToLocate = "#minecraft:village";  // 要搜寻的结构

@Entry(category = "pre-generation")
public static int initialLocatingCount = 7;  // 世界加载时初始搜寻数量
```

### 参数说明

| 参数 | 默认值 | 范围 | 说明 |
|------|--------|------|------|
| **maxLocatingCount** | 100 | 1-∞ | 最多搜寻多少个结构 |
| **structureToLocate** | `#minecraft:village` | 标签/ID | 要搜寻的结构类型 |
| **initialLocatingCount** | 7 | 1-∞ | 世界加载时预搜寻的结构数量 |

---

## 🔍 结构搜寻机制

### 1. 搜寻触发时机

#### A. 世界加载时（初始搜寻）

```java
// 位置: ModEventHandler.java - ServerWorldEvents.LOAD

if (structureLocationData.structureLocations().size() < ModConfig.initialLocatingCount) {
    for (int i = 0; i < ModConfig.initialLocatingCount; i++) {
        StructureConnector.cacheNewConnection(serverWorld, false);
        tryGenerateNewRoads(serverWorld, true, 5000);
    }
}
```

**条件**:
- 世界首次加载
- 已搜寻的结构数量 < `initialLocatingCount`（默认 7）

**行为**:
- 从**世界出生点**开始搜寻
- 搜寻 `initialLocatingCount` 个结构
- 立即开始生成道路

#### B. 区块生成时（持续搜寻）

```java
// 位置: RoadFeature.java - tryFindNewStructureConnection()

if (villageLocations.size() < ModConfig.maxLocatingCount) {
    chunksForLocatingCounter++;
    if (chunksForLocatingCounter > 300) {
        StructureConnector.cacheNewConnection(serverWorld, true);
        chunksForLocatingCounter = 1;
    }
}
```

**条件**:
- 已搜寻的结构数量 < `maxLocatingCount`（默认 100）
- 每生成 **300 个区块**触发一次

**行为**:
- 从**玩家位置**开始搜寻
- 每次搜寻 1 个结构
- 自动创建连接并生成道路

---

### 2. 搜寻算法

#### 核心方法: `StructureLocator.locateConfiguredStructure()`

```java
public static void locateConfiguredStructure(
    ServerWorld serverWorld, 
    int locateCount,           // 搜寻数量
    boolean locateAtPlayer     // 是否从玩家位置搜寻
) {
    // 从指定位置开始搜寻
    BlockPos startPos = locateAtPlayer ? player.getBlockPos() : serverWorld.getSpawnPos();
    
    // 调用 Minecraft 原版结构定位 API
    Pair<BlockPos, RegistryEntry<Structure>> pair = serverWorld.getChunkManager()
        .getChunkGenerator()
        .locateStructure(
            serverWorld,           // 世界
            registryEntryList,     // 结构列表
            startPos,              // 起始位置
            100,                   // 搜寻半径（区块数）
            true                   // 跳过已知结构
        );
}
```

#### 搜寻参数

| 参数 | 值 | 说明 |
|------|-----|------|
| **起始位置** | 出生点/玩家位置 | 搜寻的中心点 |
| **搜寻半径** | 100 区块 | 约 1600 方块半径 |
| **跳过已知** | true | 不会重复搜寻同一个结构 |

---

### 3. 结构类型支持

#### 格式说明

配置项 `structureToLocate` 支持两种格式：

##### A. 标签格式（推荐）

```
#minecraft:village          // 所有村庄类型
#minecraft:mineshaft        // 所有废弃矿井
#minecraft:ocean_ruin       // 所有海底遗迹
```

**优点**:
- 包含该标签下的所有变种
- 例如 `#minecraft:village` 包含：
  - 平原村庄
  - 沙漠村庄
  - 热带草原村庄
  - 雪原村庄
  - 针叶林村庄

##### B. 直接 ID 格式

```
minecraft:village_plains    // 仅平原村庄
minecraft:desert_pyramid    // 沙漠神殿
minecraft:jungle_temple     // 丛林神庙
```

**优点**:
- 精确控制结构类型
- 可以只连接特定类型的结构

#### 常用结构标签

| 标签 | 包含的结构 |
|------|-----------|
| `#minecraft:village` | 所有村庄（5种生物群系） |
| `#minecraft:mineshaft` | 废弃矿井（普通、恶地） |
| `#minecraft:ocean_ruin` | 海底遗迹（冷水、温水） |
| `#minecraft:shipwreck` | 沉船（所有类型） |
| `#minecraft:ruined_portal` | 废弃传送门（所有类型） |

#### 常用结构 ID

| ID | 结构名称 |
|----|---------|
| `minecraft:village_plains` | 平原村庄 |
| `minecraft:village_desert` | 沙漠村庄 |
| `minecraft:village_savanna` | 热带草原村庄 |
| `minecraft:village_snowy` | 雪原村庄 |
| `minecraft:village_taiga` | 针叶林村庄 |
| `minecraft:pillager_outpost` | 掠夺者前哨站 |
| `minecraft:desert_pyramid` | 沙漠神殿 |
| `minecraft:jungle_temple` | 丛林神庙 |
| `minecraft:swamp_hut` | 沼泽小屋 |
| `minecraft:igloo` | 雪屋 |

---

## 🔗 连接创建机制

### 1. 连接条件

```java
// 位置: StructureConnector.createNewStructureConnection()

if (villagePosList.size() < 2) {
    return;  // 至少需要 2 个结构才能创建连接
}
```

**最小要求**: 至少找到 **2 个结构**

### 2. 连接算法

#### 最近邻算法

```java
private static BlockPos findClosestStructure(BlockPos currentVillage, List<BlockPos> allVillages) {
    BlockPos closestVillage = null;
    double minDistance = Double.MAX_VALUE;
    
    for (BlockPos village : allVillages) {
        if (!village.equals(currentVillage)) {
            double distance = currentVillage.getSquaredDistance(village);
            if (distance < minDistance) {
                minDistance = distance;
                closestVillage = village;
            }
        }
    }
    return closestVillage;
}
```

**逻辑**:
1. 每次找到新结构时
2. 计算它与所有已知结构的距离
3. 选择**最近的结构**创建连接
4. 避免重复连接

#### 重复检查

```java
private static boolean connectionExists(List<Records.StructureConnection> existingConnections, 
                                       BlockPos a, BlockPos b) {
    for (Records.StructureConnection connection : existingConnections) {
        if ((connection.from().equals(a) && connection.to().equals(b)) ||
            (connection.to().equals(b) && connection.from().equals(a))) {
            return true;  // 连接已存在（双向检查）
        }
    }
    return false;
}
```

**特点**:
- 双向检查（A→B 和 B→A 视为同一连接）
- 避免重复生成道路

---

## 📊 搜寻流程图

```
世界加载
    ↓
检查已搜寻数量 < initialLocatingCount?
    ↓ 是
从出生点搜寻 7 个结构
    ↓
创建连接（最近邻）
    ↓
开始生成道路
    ↓
玩家探索世界
    ↓
每 300 区块触发一次
    ↓
检查已搜寻数量 < maxLocatingCount?
    ↓ 是
从玩家位置搜寻 1 个结构
    ↓
创建连接（最近邻）
    ↓
继续生成道路
    ↓
重复直到达到 maxLocatingCount
```

---

## 🎯 搜寻策略

### 1. 初始阶段（世界加载）

**目标**: 快速建立基础道路网络

- **位置**: 世界出生点附近
- **数量**: 7 个结构（可配置）
- **半径**: 1600 方块
- **特点**: 集中在出生点周围，便于玩家快速找到村庄

### 2. 扩展阶段（玩家探索）

**目标**: 随着玩家探索扩展道路网络

- **位置**: 玩家当前位置
- **频率**: 每 300 区块
- **数量**: 每次 1 个
- **特点**: 跟随玩家探索方向，动态扩展

### 3. 限制机制

**防止过度搜寻**:
- 最大数量限制（默认 100）
- 搜寻半径限制（100 区块）
- 跳过已知结构

---

## 🛠️ 配置建议

### 场景 1: 密集道路网络

```json
{
  "maxLocatingCount": 200,
  "initialLocatingCount": 15,
  "structureToLocate": "#minecraft:village"
}
```

**效果**: 更多村庄，更密集的道路

### 场景 2: 稀疏道路网络

```json
{
  "maxLocatingCount": 50,
  "initialLocatingCount": 3,
  "structureToLocate": "#minecraft:village"
}
```

**效果**: 较少村庄，主干道路为主

### 场景 3: 特定结构类型

```json
{
  "maxLocatingCount": 100,
  "initialLocatingCount": 7,
  "structureToLocate": "minecraft:pillager_outpost"
}
```

**效果**: 只连接掠夺者前哨站

### 场景 4: 多种结构混合

**注意**: 当前版本不支持多种结构类型，只能选择一种标签或 ID

**解决方案**: 使用自定义数据包创建包含多种结构的标签

---

## 🔧 技术细节

### 1. 数据持久化

```java
// 结构位置保存
public static final AttachmentType<Records.StructureLocationData> STRUCTURE_LOCATIONS = 
    AttachmentRegistry.createPersistent(
        Identifier.of(SettlementRoads.MOD_ID, "village_locations"),
        Records.StructureLocationData.CODEC
    );
```

**特点**:
- 使用 Fabric 附件系统
- 自动保存到世界数据
- 重新加载世界后保留

### 2. 搜寻性能

**优化措施**:
- 使用原版 API（高效）
- 跳过已知结构（避免重复）
- 限制搜寻半径（100 区块）
- 异步执行（不阻塞主线程）

**性能影响**:
- 每次搜寻: ~10-50ms
- 频率: 每 300 区块一次
- 总体影响: 可忽略

### 3. 错误处理

```java
try {
    // 搜寻结构
} catch (CommandSyntaxException e) {
    LOGGER.warn("Failed to locate structure: " + ModConfig.structureToLocate);
}
```

**异常情况**:
- 结构 ID/标签无效
- 结构在该维度不存在
- 搜寻半径内无结构

**处理方式**:
- 记录警告日志
- 跳过本次搜寻
- 不影响其他功能

---

## 📝 调试信息

### 日志输出

```
[DEBUG] Locating 1 #minecraft:village
[DEBUG] Structure found at BlockPos{x=1234, y=64, z=5678}
```

### 调试界面（按 H 键）

- 🟢 绿色圆点: 已找到的结构
- 🟡 黄色连接线: 计划生成的道路
- 🟠 橙色连接线: 正在生成的道路

---

## ❓ 常见问题

### Q1: 为什么找不到结构？

**可能原因**:
1. 结构 ID/标签错误
2. 搜寻半径内无该结构
3. 已达到 `maxLocatingCount` 限制
4. 该维度不生成该结构

**解决方案**:
- 检查配置文件中的 `structureToLocate`
- 增加 `maxLocatingCount`
- 使用 `/locate structure` 命令验证

### Q2: 为什么只连接部分村庄？

**原因**: 使用最近邻算法，每个新村庄只连接最近的一个

**解决方案**: 这是设计行为，避免过多交叉连接

### Q3: 如何连接更远的结构？

**方法**:
1. 增加 `maxLocatingCount`
2. 探索更远的区域（触发玩家位置搜寻）
3. 手动使用调试界面传送到远处

### Q4: 可以连接不同类型的结构吗？

**当前版本**: 不支持，只能选择一种结构类型

**未来计划**: 可能支持多种结构类型混合

---

## 🎓 高级用法

### 自定义结构标签

创建数据包 `data/your_namespace/tags/worldgen/structure/custom_structures.json`:

```json
{
  "values": [
    "minecraft:village_plains",
    "minecraft:village_desert",
    "minecraft:pillager_outpost"
  ]
}
```

然后在配置中使用:
```
structureToLocate = "#your_namespace:custom_structures"
```

---

**文档版本**: v1.0  
**最后更新**: 2025-10-03  
**适用版本**: RoadWeaver 2.0.2+
