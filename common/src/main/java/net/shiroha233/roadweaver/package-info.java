/**
 * RoadWeaver 服务端与客户端的共享实现。
 *
 * <p>依赖方向：api/command/client → generation/planning/structure/map →
 * terrain/pathfinding/persistence → core/config/util。反向依赖一律通过接口或事件中转，
 * 不允许下层 import 上层。</p>
 */
package net.shiroha233.roadweaver;
