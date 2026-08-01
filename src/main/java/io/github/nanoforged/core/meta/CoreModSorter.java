package io.github.nanoforged.core.meta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * CoreMod 依赖图排序（Kahn 拓扑排序）。
 *
 * <p>规则：depends 指向的 coremod 先加载；同一可加载层内 priority 升序，
 * priority 相同按 id 字典序，保证同输入必然同输出（确定性）。
 *
 * @throws CoreModMetaException 重复 id、依赖缺失或依赖环
 */
public final class CoreModSorter {

    private CoreModSorter() {}

    public static List<CoreModMeta> sort(List<CoreModMeta> mods) {
        Map<String, CoreModMeta> byId = new HashMap<>();
        for (CoreModMeta meta : mods) {
            CoreModMeta previous = byId.putIfAbsent(meta.id(), meta);
            if (previous != null) {
                throw new CoreModMetaException("重复的 coremod id '" + meta.id() + "': "
                        + previous.source() + " 与 " + meta.source());
            }
        }

        // 依赖缺失检查（在拓扑前做，错误信息更直接）
        for (CoreModMeta meta : mods) {
            for (String dep : meta.depends()) {
                if (!byId.containsKey(dep)) {
                    throw new CoreModMetaException("coremod '" + meta.id() + "' (" + meta.source()
                            + ") 依赖缺失: '" + dep + "' 未被发现");
                }
            }
        }

        // Kahn：indegree = 未满足的 depends 数；dependents 为反向邻接
        Map<String, Integer> indegree = new HashMap<>();
        Map<String, List<String>> dependents = new HashMap<>();
        for (CoreModMeta meta : mods) {
            indegree.put(meta.id(), meta.depends().size());
            for (String dep : meta.depends()) {
                dependents.computeIfAbsent(dep, k -> new ArrayList<>()).add(meta.id());
            }
        }

        PriorityQueue<CoreModMeta> ready = new PriorityQueue<>(
                Comparator.comparingInt(CoreModMeta::priority).thenComparing(CoreModMeta::id));
        for (CoreModMeta meta : mods) {
            if (meta.depends().isEmpty()) {
                ready.add(meta);
            }
        }

        List<CoreModMeta> sorted = new ArrayList<>(mods.size());
        while (!ready.isEmpty()) {
            CoreModMeta meta = ready.poll();
            sorted.add(meta);
            for (String dependentId : dependents.getOrDefault(meta.id(), List.of())) {
                int remaining = indegree.merge(dependentId, -1, Integer::sum);
                if (remaining == 0) {
                    ready.add(byId.get(dependentId));
                }
            }
        }

        if (sorted.size() != mods.size()) {
            throw new CoreModMetaException("coremod 依赖存在环: " + describeCycle(byId, indegree));
        }
        return sorted;
    }

    /** 从未消尽的节点中回溯出一条环路径用于诊断 */
    private static String describeCycle(Map<String, CoreModMeta> byId, Map<String, Integer> indegree) {
        String start = null;
        for (Map.Entry<String, Integer> entry : indegree.entrySet()) {
            if (entry.getValue() > 0) {
                start = entry.getKey();
                break;
            }
        }
        Set<String> path = new LinkedHashSet<>();
        String current = start;
        while (current != null && path.add(current)) {
            String next = null;
            for (String dep : byId.get(current).depends()) {
                if (indegree.getOrDefault(dep, 0) > 0) {
                    next = dep;
                    break;
                }
            }
            current = next;
        }
        return String.join(" -> ", path);
    }
}
