package top.miragedge.biometitle.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import top.miragedge.biometitle.biome.BiomeEntry;
import top.miragedge.biometitle.util.SimilarCheck;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Level;

/**
 * 配置管理器。
 * 负责：
 *  1. 默认配置保存
 *  2. 配置自动补全（新版本新增键自动合并）
 *  3. 热重载
 *  4. 提供各配置项的读取接口
 */
public class ConfigManager {

    private final Plugin plugin;
    private FileConfiguration config;

    public ConfigManager(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 初始化配置：保存默认配置并启用自动补全。
     *
     * 说明：DESIGN.md 中"异步配置读取"条目——本插件配置规模约 100 个群系条目，
     * YamlConfiguration 加载耗时远小于 1ms，异步化反而引入启动时序复杂度
     * （监听器/命令注册必须等待配置就绪），因此采用启动时同步一次性加载
     * （符合 DESIGN 八、性能优化"启动时一次性加载到内存，运行时不读文件"）。
     */
    public void init() {
        plugin.saveDefaultConfig();
        mergeDefaults();
        reload();
    }

    /**
     * 配置自动补全：
     * 将 jar 内的默认配置与磁盘配置合并，缺失的键自动补上。
     * 不会覆盖用户已有的配置。
     */
    private void mergeDefaults() {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) return;
        try {
            FileConfiguration disk = YamlConfiguration.loadConfiguration(configFile);
            InputStream in = plugin.getResource("config.yml");
            if (in == null) return;
            try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                FileConfiguration defaults = YamlConfiguration.loadConfiguration(reader);
                boolean changed = mergeSection(disk, defaults, "");
                if (changed) {
                    // 保存前备份原文件（YamlConfiguration.save 会丢弃注释，备份以防用户需要恢复）
                    File backup = new File(configFile.getParentFile(), "config.yml.bak");
                    try {
                        java.nio.file.Files.copy(configFile.toPath(), backup.toPath(),
                                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    } catch (java.io.IOException ex) {
                        plugin.getLogger().warning("备份 config.yml 失败（忽略）：" + ex.getMessage());
                    }
                    disk.save(configFile);
                    plugin.getLogger().info("配置自动补全完成：新增了缺失的配置项（原配置已备份为 config.yml.bak）");
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "配置自动补全失败：", e);
        }
    }

    /** 递归合并：将 defaults 中缺失的键补进 disk */
    private boolean mergeSection(FileConfiguration disk, ConfigurationSection defaults, String path) {
        boolean changed = false;
        for (String key : defaults.getKeys(false)) {
            String fullPath = path.isEmpty() ? key : path + "." + key;
            if (defaults.isConfigurationSection(key)) {
                ConfigurationSection defSection = defaults.getConfigurationSection(key);
                ConfigurationSection diskSection = disk.getConfigurationSection(fullPath);
                if (diskSection == null) {
                    // 整个小节缺失：复制默认值
                    disk.set(fullPath, defaults.get(fullPath));
                    changed = true;
                } else {
                    changed |= mergeSection(disk, defSection, fullPath);
                }
            } else {
                if (!disk.contains(fullPath)) {
                    disk.set(fullPath, defaults.get(fullPath));
                    changed = true;
                }
            }
        }
        return changed;
    }

    /** 重新加载配置（同时尝试合并新增的默认键） */
    public void reload() {
        mergeDefaults();
        plugin.reloadConfig();
        config = plugin.getConfig();
    }

    public FileConfiguration getConfig() {
        return config;
    }

    // ==================== 显示设置 ====================

    public String getDefaultDisplay() {
        return config.getString("display.default", "title");
    }

    public boolean isActionbarMode() {
        return config.getBoolean("display.actionbar", false);
    }

    public int getFadeIn() {
        return Math.max(0, config.getInt("display.fadeIn", 10));
    }

    public int getStay() {
        return Math.max(0, config.getInt("display.stay", 40));
    }

    public int getFadeOut() {
        return Math.max(0, config.getInt("display.fadeOut", 10));
    }

    // ==================== 冷却 ====================

    public int getMinInterval() {
        return Math.max(1, config.getInt("min-interval", 8));
    }

    // ==================== 领地 ====================

    public boolean isIgnoreRes() {
        return config.getBoolean("ignore-res", true);
    }

    // ==================== 禁用世界 ====================

    public Set<String> getDisabledWorlds() {
        Set<String> worlds = new HashSet<>();
        for (String w : config.getStringList("disable-worlds")) {
            worlds.add(w.trim().toLowerCase(Locale.ROOT));
        }
        return worlds;
    }

    // ==================== 相似检查 ====================

    public boolean isSimilarCheckEnabled() {
        // 兼容两种写法：similar-check: true / similar-check: { enabled: true, groups: {...} }
        if (config.isBoolean("similar-check")) {
            return config.getBoolean("similar-check", true);
        }
        return config.getBoolean("similar-check.enabled", true);
    }

    /** 加载相似群系分组到 SimilarCheck */
    public void loadSimilarGroups(SimilarCheck similarCheck) {
        Map<String, List<String>> groups = new LinkedHashMap<>();
        ConfigurationSection section = config.getConfigurationSection("similar-check.groups");
        if (section != null) {
            for (String groupName : section.getKeys(false)) {
                groups.put(groupName, section.getStringList(groupName));
            }
        }
        similarCheck.loadGroups(groups);
    }

    // ==================== 全局拖尾粒子 ====================

    public BiomeEntry.ParticleConfig getGlobalTrailParticles() {
        ConfigurationSection section = config.getConfigurationSection("trail-particles");
        return BiomeEntry.ParticleConfig.fromSection(section);
    }

    // ==================== 群系 ====================

    public ConfigurationSection getBiomesSection() {
        return config.getConfigurationSection("biomes");
    }
}