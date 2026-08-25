package top.miragedge.biometitle.util;

import org.bukkit.block.Biome;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 相似群系检查。
 * 当玩家进入一个 biome 时，如果上一个 biome 与当前 biome 属于同一"相似组"，
 * 则不触发显示。
 * 相似组定义在配置文件中，例如：各种海洋群系归为一组。
 */
public class SimilarCheck {

    private final Map<String, Set<String>> similarGroups = new ConcurrentHashMap<>();

    /**
     * 加载相似群系组。
     * 配置格式：
     *   similar-check:
     *     groups:
     *       ocean_group:
     *         - OCEAN
     *         - DEEP_OCEAN
     *         - WARM_OCEAN
     *         - ...
     *       forest_group:
     *         - FOREST
     *         - FLOWER_FOREST
     *         - BIRCH_FOREST
     *         - ...
     *
     * 每个组内的 biome 名称统一转小写，并同时注册全键（minecraft:xxx）与裸名（xxx）两种形式，
     * 与 BiomeMatcher 的三种配置写法兼容。
     */
    public void loadGroups(Map<String, List<String>> groups) {
        similarGroups.clear();
        if (groups == null) return;
        for (Map.Entry<String, List<String>> entry : groups.entrySet()) {
            Set<String> set = new HashSet<>();
            for (String name : entry.getValue()) {
                String lower = name.trim().toLowerCase(Locale.ROOT);
                if (lower.isEmpty()) continue;
                set.add(lower);
                if (lower.contains(":")) {
                    // 含命名空间：注册去掉 minecraft: 前缀的裸名
                    set.add(lower.substring(lower.indexOf(':') + 1));
                } else {
                    // 裸名：注册带 minecraft: 前缀的全键
                    set.add("minecraft:" + lower);
                }
            }
            similarGroups.put(entry.getKey().trim().toLowerCase(Locale.ROOT), set);
        }
    }

    /** 清除所有分组 */
    public void clear() {
        similarGroups.clear();
    }

    /**
     * 判断两个 biome 是否属于同一个相似组。
     * 如果任一 biome 为 null，返回 false。
     */
    public boolean isSimilar(Biome biomeA, Biome biomeB) {
        if (biomeA == null || biomeB == null) return false;
        String keyA = normalize(biomeA);
        String keyB = normalize(biomeB);
        if (keyA.equals(keyB)) return true; // 同一个群系

        for (Set<String> group : similarGroups.values()) {
            if (group.contains(keyA) && group.contains(keyB)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(Biome biome) {
        return biome.getKey().toString().toLowerCase(Locale.ROOT);
    }
}