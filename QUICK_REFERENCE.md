# RoadWeaver 快速参考

## 🎯 核心机制速查

### 结构搜寻

| 触发时机 | 起始位置 | 频率 | 数量 |
|---------|---------|------|------|
| **世界加载** | 出生点 | 一次 | 7个（可配置） |
| **区块生成** | 玩家位置 | 每300区块 | 1个 |

**搜寻参数**:
- 半径: 100区块（1600方块）
- 跳过已知结构: 是
- 最大数量: 100个（可配置）

### 连接算法

**最近邻算法**: 每个新结构连接到最近的已知结构

```
新结构 → 计算距离 → 选择最近 → 创建连接 → 生成道路
```

### 道路生成状态

| 状态 | 颜色 | 说明 |
|------|------|------|
| PLANNED | 🟡 黄色 | 计划生成，等待队列 |
| GENERATING | 🟠 橙色 | 正在生成中 |
| COMPLETED | 🟢 绿色 | 已完成（不显示连接线） |
| FAILED | 🔴 红色 | 生成失败 |

---

## ⚙️ 配置参数

### 结构配置

```java
maxLocatingCount = 100              // 最大搜寻结构数量
structureToLocate = "#minecraft:village"  // 要搜寻的结构
initialLocatingCount = 7            // 世界加载时初始搜寻数量
```

### 道路配置

```java
averagingRadius = 1                 // 道路平滑半径
allowArtificial = true              // 允许人工道路（石质）
allowNatural = true                 // 允许自然道路（泥土）
placeWaypoints = false              // 放置路标
maxHeightDifference = 5             // 最大高度差（3-10）
maxTerrainStability = 4             // 最大地形稳定性（2-10）
```

### 性能配置

```java
maxConcurrentRoadGeneration = 3    // 最大并发道路生成数（1-10）
```

---

## 🎮 操作指南

### 调试界面（按 H 键）

**鼠标操作**:
- 左键拖拽: 平移地图
- 滚轮: 缩放地图
- 点击节点: 传送到该位置
- 悬停节点: 显示坐标信息

**界面元素**:
- 左上角: 图例面板
- 右上角: 统计面板
- 右下角: 比例尺
- 中央: 道路网络地图

---

## 🔍 常用结构

### 标签格式（推荐）

```
#minecraft:village          // 所有村庄
#minecraft:mineshaft        // 废弃矿井
#minecraft:ocean_ruin       // 海底遗迹
#minecraft:shipwreck        // 沉船
#minecraft:ruined_portal    // 废弃传送门
```

### 直接ID格式

```
minecraft:village_plains    // 平原村庄
minecraft:village_desert    // 沙漠村庄
minecraft:pillager_outpost  // 掠夺者前哨站
minecraft:desert_pyramid    // 沙漠神殿
minecraft:jungle_temple     // 丛林神庙
```

---

## 🐛 故障排除

### 道路不生成

1. 检查配置: `allowArtificial` 或 `allowNatural` 至少一个为 true
2. 查看日志: 搜索 "RoadWeaver" 关键字
3. 打开调试界面: 按 H 键查看连接状态

### 找不到结构

1. 验证结构ID: 使用 `/locate structure` 命令
2. 增加搜寻数量: 提高 `maxLocatingCount`
3. 检查维度: 某些结构只在特定维度生成

### 性能问题

1. 降低并发数: 减小 `maxConcurrentRoadGeneration`
2. 减少搜寻数量: 降低 `maxLocatingCount`
3. 查看日志: 检查是否有错误

---

## 📊 性能指标

| 操作 | 耗时 | 影响 |
|------|------|------|
| 结构搜寻 | 10-50ms | 低 |
| 道路生成 | 1-5秒 | 中（异步） |
| 调试界面 | <1ms | 极低 |

---

## 🔗 相关文档

- `STRUCTURE_LOCATING_MECHANISM.md` - 结构搜寻详细机制
- `ROAD_PERSISTENCE_FIX.md` - 道路持久化修复说明
- `TEST_GUIDE.md` - 完整测试指南
- `UI_REDESIGN_SUMMARY.md` - UI设计文档

---

**版本**: v2.0.2  
**更新**: 2025-10-03
