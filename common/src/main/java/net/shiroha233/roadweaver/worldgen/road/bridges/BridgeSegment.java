package net.shiroha233.roadweaver.worldgen.road.bridges;

import net.minecraft.world.phys.Vec3;
import net.shiroha233.roadweaver.core.model.RoadSegmentPlacement;
import net.shiroha233.roadweaver.util.Line;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 桥梁段数据结构
 */
public class BridgeSegment {
    private final Map<Set<Integer>, Line> bridgeLines = new HashMap<>();

    public BridgeSegment(boolean[] isBridge, List<RoadSegmentPlacement> segments) {
        List<List<Vec3>> list1 = new ArrayList<>();
        List<Set<Integer>> list2 = new ArrayList<>();
        list1.add(new ArrayList<>());
        list2.add(new HashSet<>());
        
        for (int i = 0; i < segments.size(); i++) {
            if (isBridge[i]) {
                list1.get(list1.size() - 1).add(Vec3.atCenterOf(segments.get(i).middlePos()));
                list2.get(list1.size() - 1).add(i);
            } else {
                if (!list1.get(list1.size() - 1).isEmpty()) {
                    list1.add(new ArrayList<>());
                    list2.add(new HashSet<>());
                }
            }
        }
        
        if (list1.get(list1.size() - 1).isEmpty()) {
            list1.remove(list1.size() - 1);
            list2.remove(list2.size() - 1);
        }

        for (int i = 0; i < list1.size(); i++) {
            List<Vec3> seg = list1.get(i);
            var line = new Line(seg.get(0), seg.get(seg.size() - 1));
            bridgeLines.put(list2.get(i), line);
        }
    }

    /**
     * 通过索引获取桥梁曲线
     * 
     * @param index 路段索引
     * @return 对应的桥梁曲线，如果不存在则返回 null
     */
    public Line getLine(int index) {
        for (Map.Entry<Set<Integer>, Line> entry : bridgeLines.entrySet()) {
            if (entry.getKey().contains(index)) {
                return entry.getValue();
            }
        }
        return null;
    }
}
