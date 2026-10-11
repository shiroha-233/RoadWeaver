# 缓存与道路生命周期约定

## 数据所有权

| 数据 | 权威来源 | 内存作用域 | 释放 / 失效入口 |
| --- | --- | --- | --- |
| 连接与规划瓦片 | `StructureFileStorage` | 世界路径 | 服务端关停 flush；`ConnectionListUpdater` 串行化状态修改 |
| 道路聚合与区块倒排 | `RoadChunkStore` | 世界路径 | `RoadShardStorage` 写入后使受影响计划失效；关停 flush / close |
| 区块世界生成计划 | `RoadWorldgenPlanCache` | `ServerLevel` 实例 | 道路增删改使受影响区块及邻居失效；维度卸载清理 |
| 粗采样地形瓦片 | `CoarseTerrainTileCache` | `ServerLevel` 实例 + 瓦片键 | 有界内存 LRU；维度卸载 / 服务端重启清理；过期加载不得回填 |
| 待生成路径 | `PlannedPathCache` | `ServerLevel` 实例 + 文件 | 道路落盘后 discard；维度卸载清理；指纹变动不复用 |

## 状态与并发约束

1. 连接由 `PLANNED`（或重启恢复的 `GENERATING`）领取为 `GENERATING`；只有仍处于 `GENERATING` 的连接才能由工作任务完成为 `COMPLETED` / `FAILED`，运行中检测到取消或提交失败时回到 `PLANNED`。已删除或终态连接不得被迟到任务恢复。
2. 普通生成和闲时生成共用 `ConnectionListUpdater.transition` 原子比较并修改。闲时任务提交后，即使玩家离开或关闭闲时生成，也必须保留 in-flight 所有权直到回调结束，避免普通线程重复生成。
3. 规划任务在服务端主线程完成连接合并后，才能将规划瓦片标记为已规划；异常、中断或 epoch 变化须释放标记以允许重试。
4. 道路写入先持久化、再发布内存快照，索引提交与快照写入在同一锁内串行化。临时存储/编译故障产生的空世界生成计划不能进入缓存。
5. 服务端关停先失效任务 epoch、停工作池，再取消生成任务和 flush 缓存；维度卸载清理持有 `ServerLevel` 引用的缓存。新增缓存应明确容量、世界键、失效触发点和失败重试策略。

## 后续需要解决的问题

- **持久化崩溃窗口**：`RoadChunkStore` 每秒批量写 `index.json`。进程突然终止时，尚未提交的新增/删除不会从孤立 NBT 自动恢复；正常关服会 flush。若要求每条道路返回时即持久可靠，需要日志或可恢复的事务提交，而不是仅扫描区块分片。
- **执行中的旧任务**：`ThreadPoolManager.shutdownNow()` 不等待线程退出；`Road.generateRoad` 写盘前也未验证 epoch。下一步应为规划、生成、写盘建立协作式取消边界，并在关闭存储前等待已有写任务结束。
- **运行期线程池重建**：任务在队列中被丢弃时不一定运行 `finally`；普通/闲时生成的 in-flight 计数和连接状态需要与任务队列的取消结果联动，避免 `GENERATING` 长期悬挂。
- **规划区域集合的内存预算**：`RoadPlanningService.TERRAIN_REPAIR_TILES` 与 `ROAD_BACKFILL_PLAN_TILES` 记录访问过的瓦片直到关服，长时间探索可能持续增长；应与持久化规划瓦片的裁剪策略统一。
- **测试基线**：当前 `PathPostProcessorBoundsTest.staircaseSearchNodesProduceWidelySpacedCurveControls` 独立运行仍失败（路径控制点简化不足）；修复时应结合道路形状与水体边界回归测试，避免仅调阈值掩盖几何问题。
