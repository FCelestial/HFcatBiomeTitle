package top.miragedge.biometitle.biome;

import org.bukkit.NamespacedKey;
import org.bukkit.block.Biome;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 群系匹配引擎（核心修复点）。
 *
 * 原版 BiomesTitle 使用 {@code to.getBlock().getBiome().toString()}，
 * 数据包群系会返回 "CUSTOM"，丢失命名空间信息。
 * 本类改用 {@link Biome#getKey()} 获取完整 NamespacedKey：
 *   - 原版群系   -> "minecraft:plains"
 *   - 数据包群系 -> "incendium:ash_barrens"
 *
 * 配置键统一转为小写存储，支持三种写法（不区分大小写）：
 *   PLAINS / minecraft:plains / plains
 */
public class BiomeMatcher {

    /** 配置键（小写） -> 群系条目 */
    private final Map<String, BiomeEntry> entries = new ConcurrentHashMap<>();
    /** 原始配置键数量（不含自动注册的 minecraft: 前缀别名） */
    private int originalKeyCount = 0;

    /**
     * 匹配指定群系对应的配置条目。
     * 匹配顺序：全键 -> 枚举名 -> 键的 value 部分。
     *
     * @param biome 群系（可能为数据包群系，此时 getKey() 返回完整命名空间键）
     * @return 匹配到的条目，未匹配时返回 null
     */
    @SuppressWarnings({"deprecation", "removal"}) // biome.name()（OldEnum）仅为兼容回退，主路径使用 getKey()
    @Nullable
    public BiomeEntry match(@Nullable Biome biome) {
        if (biome == null) return null;
        NamespacedKey key = biome.getKey();
        if (key == null) return null;

        // 1. 全键匹配："minecraft:plains" / "incendium:ash_barrens"
        String fullKey = key.toString().toLowerCase(Locale.ROOT);
        BiomeEntry entry = entries.get(fullKey);
        if (entry != null) return entry;

        // 2. 枚举名匹配（原版 "PLAINS" -> "plains"）；数据包群系 name() 同样返回键的 value
        try {
            String enumName = biome.name().toLowerCase(Locale.ROOT);
            entry = entries.get(enumName);
            if (entry != null) return entry;
        } catch (UnsupportedOperationException ignored) {
            // 某些非标准 Biome 实现可能不支持 name()
        }

        // 3. 键的 value 部分匹配（"minecraft:plains" -> "plains"）
        String value = key.getKey().toLowerCase(Locale.ROOT);
        if (!value.equals(fullKey)) {
            entry = entries.get(value);
            if (entry != null) return entry;
        }
        return null;
    }

    /** 从配置节加载全部群系条目 */
    public void loadFromConfig(ConfigurationSection biomes) {
        entries.clear();
        originalKeyCount = 0;
        if (biomes == null) return;
        for (String key : biomes.getKeys(false)) {
            ConfigurationSection section = biomes.getConfigurationSection(key);
            if (section == null) continue;
            BiomeEntry entry = BiomeEntry.fromConfig(section);
            String normalized = normalizeKey(key);
            BiomeEntry existing = entries.put(normalized, entry);
            if (existing != null) {
                // 同名键覆盖（后面覆盖前面）
                continue;
            }
            originalKeyCount++;
            // 同时注册去掉 minecraft: 前缀的别名，保证 "minecraft:plains" 与 "plains" 均可用
            if (normalized.startsWith("minecraft:")) {
                entries.putIfAbsent(normalized.substring("minecraft:".length()), entry);
            }
        }
    }

    /** 归一化配置键：转小写、去空格 */
    public static String normalizeKey(String key) {
        return key.trim().toLowerCase(Locale.ROOT);
    }

    /** 原始配置条目数量（不含自动注册的别名，用于日志） */
    public int size() {
        return originalKeyCount;
    }
}