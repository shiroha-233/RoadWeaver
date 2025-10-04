# 地图调试功能修复说明

## 修复日期
2025-10-05

## 问题描述

### 问题1：计划道路显示为直线
- **现象**：地图上的计划道路（PLANNED状态）显示为从起点到终点的直线，而不是连接实际结构位置
- **原因**：原代码只使用了`StructureConnection`的`from`和`to`坐标绘制直线，没有利用实际的道路路径数据

### 问题2：地图缩小后精度太低
- **现象**：缩小地图后，道路显示精度急剧下降，看不清路径细节
- **原因**：LOD（细节层次）系统的阈值设置过于激进，导致缩小时快速降低渲染精度

### 问题3：文件过大难以维护
- **现象**：`RoadDebugScreen.java` 文件超过800行，包含多种功能混杂在一起
- **原因**：所有渲染逻辑、UI绘制、事件处理都在一个文件中

## 修复方案

### 1. 连接线渲染逻辑优化

#### 新增虚线绘制功能
在 `RenderUtils.java` 中新增 `drawDashedLine()` 方法，用于绘制虚线连接：

```java
public static void drawDashedLine(GuiGraphics ctx, int x0, int y0, int x1, int y1, int color)
```

#### 智能连接匹配
在 `MapRenderer.java` 的 `drawConnections()` 方法中：
- 优先尝试匹配对应的 `RoadData`，使用实际道路路径
- 如果找不到道路数据，回退到直线连接
- 使用虚线区分计划中的道路和已完成的道路

**关键代码逻辑**：
```java
// 尝试找到对应的道路数据
Records.RoadData matchingRoad = findMatchingRoad(connection, roads);

if (matchingRoad != null && matchingRoad.roadSegmentList() != null) {
    // 使用道路数据绘制实际路径（虚线）
    drawDashedLine(...);
} else {
    // 回退到直线连接（虚线）
    drawDashedLine(...);
}
```

### 2. LOD系统优化

#### 调整道路渲染阈值
优化前的阈值（过于激进）：
- 300块/格：1/64精度
- 500块/格：1/128精度
- 1000块/格：1/256精度

优化后的阈值（更平滑）：
- 150块/格：1/32精度（新增）
- 300块/格：1/64精度
- 600块/格：1/128精度
- 1200块/格：1/256精度
- 2500块/格：1/512精度

#### 新增LOD级别
在 `MapRenderer.RoadLODLevel` 枚举中新增 `THIRTY_SECOND` 级别，提供更细腻的过渡。

### 3. 模块化重构

#### 文件拆分结构
将原来的 `RoadDebugScreen.java` (807行) 拆分为5个文件：

1. **RoadDebugScreen.java** (270行)
   - 主屏幕类
   - 事件处理（鼠标、滚轮）
   - 坐标转换
   - 布局计算

2. **MapRenderer.java** (330行)
   - 地图元素渲染
   - LOD系统管理
   - 道路路径绘制
   - 连接线绘制
   - 结构节点绘制
   - 玩家标记绘制

3. **GridRenderer.java** (70行)
   - 背景网格绘制
   - 网格间距计算
   - 坐标标签显示

4. **UIRenderer.java** (130行)
   - 标题面板
   - 统计面板
   - 图例面板
   - 工具提示

5. **RenderUtils.java** (130行)
   - 基础绘制工具
   - 线条绘制（实线/虚线）
   - 圆形绘制
   - 面板绘制

#### 优势
- **可维护性**：每个文件职责单一，易于理解和修改
- **可测试性**：模块化后便于单元测试
- **可扩展性**：新增功能只需修改对应模块
- **代码复用**：工具方法可在其他地方复用

## 技术细节

### 虚线绘制算法
使用Bresenham直线算法的改进版本，支持虚线模式：
- 虚线长度：5像素
- 间隔长度：3像素
- 自动适应任意角度

### 道路匹配算法
```java
private Records.RoadData findMatchingRoad(StructureConnection connection, List<RoadData> roads) {
    // 检查道路的起点和终点是否匹配连接
    // 允许100格内的误差范围
    // 支持双向匹配（A->B 或 B->A）
}
```

### LOD计算公式
```java
double blocksPerPixel = 1.0 / (baseScale * zoom);
double blocksPerGrid = blocksPerPixel * TARGET_GRID_PX;
```

## 使用说明

### 地图功能
1. **平移**：按住鼠标左键拖动
2. **缩放**：鼠标滚轮
3. **传送**：点击结构节点
4. **查看信息**：鼠标悬停在结构上

### 视觉说明
- **绿色圆点**：结构位置
- **黄色虚线**：计划中的道路
- **橙色虚线**：生成中的道路
- **红色虚线**：生成失败的道路
- **蓝色实线**：已完成的道路路径
- **红色圆点**：玩家位置

## 测试建议

### 功能测试
1. 打开调试地图（默认按键：R）
2. 检查计划道路是否正确连接结构
3. 缩小地图，观察道路精度变化
4. 测试平移、缩放、点击传送功能

### 性能测试
1. 在有大量道路的世界中打开地图
2. 观察帧率是否稳定
3. 快速缩放测试响应速度

## 已知限制

1. **道路匹配精度**：使用100格误差范围，极端情况可能匹配错误
2. **虚线渲染**：在极高缩放级别下可能出现视觉瑕疵
3. **内存占用**：大量道路数据会增加内存使用

## 后续优化建议

1. **道路数据关联**：在 `StructureConnection` 中直接存储对应的 `RoadData` 引用
2. **渲染缓存**：缓存屏幕坐标，减少重复计算
3. **异步渲染**：将复杂渲染移到后台线程
4. **自适应LOD**：根据帧率动态调整LOD级别

## 文件清单

### 新增文件
- `forge/src/main/java/net/countered/settlementroads/client/gui/MapRenderer.java`
- `forge/src/main/java/net/countered/settlementroads/client/gui/GridRenderer.java`
- `forge/src/main/java/net/countered/settlementroads/client/gui/UIRenderer.java`
- `forge/src/main/java/net/countered/settlementroads/client/gui/RenderUtils.java`

### 修改文件
- `forge/src/main/java/net/countered/settlementroads/client/gui/RoadDebugScreen.java` (完全重写)

### 代码统计
- 总行数：~930行（原807行）
- 新增功能代码：~200行
- 重构代码：~730行
- 文件数量：1 → 5

## 编译测试

```bash
./gradlew :forge:compileJava
```

✅ 编译成功，无错误无警告

## 版本信息
- Minecraft: 1.20.1
- Forge: 47.3.0
- Java: 21
