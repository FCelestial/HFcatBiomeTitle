package top.miragedge.biometitle.area;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * 自定义区域管理器。
 * 负责自定义区域的创建、删除、查询与持久化（areas.yml）。
 */
public class AreaManager {

    private final Plugin plugin;
    private final Map<String, Area> areas = new ConcurrentHashMap<>();
    private File areasFile;

    /** 自定义区域数据模型 */
    public static class Area {
        private final String name;
        private final String world;
        private final int minX, minY, minZ, maxX, maxY, maxZ;
        private String title;
        private String subtitle;

        public Area(String name, String world, int minX, int minY, int minZ,
                    int maxX, int maxY, int maxZ, String title, String subtitle) {
            this.name = name;
            this.world = world;
            this.minX = Math.min(minX, maxX);
            this.minY = Math.min(minY, maxY);
            this.minZ = Math.min(minZ, maxZ);
            this.maxX = Math.max(minX, maxX);
            this.maxY = Math.max(minY, maxY);
            this.maxZ = Math.max(minZ, maxZ);
            this.title = title;
            this.subtitle = subtitle;
        }

        public String getName() { return name; }
        public String getWorld() { return world; }
        public String getTitle() { return title; }
        public String getSubtitle() { return subtitle; }

        /** 判断位置是否在区域内 */
        public boolean contains(Location loc) {
            if (loc.getWorld() == null) return false;
            if (!loc.getWorld().getName().equalsIgnoreCase(world)) return false;
            int x = loc.getBlockX(), y = loc.getBlockY(), z = loc.getBlockZ();
            return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
        }

        @Override
        public String toString() {
            return "Area{name='" + name + "', world='" + world + "', title='" + title + "'}";
        }
    }

    public AreaManager(Plugin plugin) {
        this.plugin = plugin;
    }

    /** 初始化：加载 areas.yml */
    public void init() {
        areasFile = new File(plugin.getDataFolder(), "areas.yml");
        load();
    }

    /** 加载所有区域（若文件损坏则保留内存数据并备份原文件） */
    public void load() {
        if (!areasFile.exists()) {
            areas.clear();
            return;
        }
        // 读取原始内容，判断文件是否为空/损坏
        String raw = "";
        try {
            raw = new String(java.nio.file.Files.readAllBytes(areasFile.toPath()),
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            plugin.getLogger().log(Level.WARNING, "读取 areas.yml 失败（保留当前数据）", e);
            return;
        }
        FileConfiguration conf = YamlConfiguration.loadConfiguration(areasFile);
        ConfigurationSection section = conf.getConfigurationSection("areas");
        if (section == null || section.getKeys(false).isEmpty()) {
            if (!raw.trim().isEmpty()) {
                // 文件有内容但解析不出区域 -> 疑似损坏，备份后保留内存数据
                try {
                    java.nio.file.Files.copy(areasFile.toPath(),
                            new File(areasFile.getParentFile(), "areas.yml.corrupt-" + System.currentTimeMillis()).toPath());
                    plugin.getLogger().warning("areas.yml 无法解析，已备份为 areas.yml.corrupt-*，保留当前内存数据");
                } catch (java.io.IOException e) {
                    plugin.getLogger().log(Level.WARNING, "备份 areas.yml 失败", e);
                }
                return;
            }
            // 文件为空（正常首次创建），清空
            areas.clear();
            return;
        }
        Map<String, Area> parsed = new ConcurrentHashMap<>();
        for (String name : section.getKeys(false)) {
            ConfigurationSection a = section.getConfigurationSection(name);
            if (a == null) continue;
            String world = a.getString("world", "");
            int[] pos1 = parsePos(a.getString("pos1", ""));
            int[] pos2 = parsePos(a.getString("pos2", ""));
            if (world.isEmpty() || pos1 == null || pos2 == null) continue;
            parsed.put(name, new Area(name, world,
                    pos1[0], pos1[1], pos1[2],
                    pos2[0], pos2[1], pos2[2],
                    a.getString("title", ""), a.getString("subtitle", "")));
        }
        areas.clear();
        areas.putAll(parsed);
        plugin.getLogger().info("已加载 " + areas.size() + " 个自定义区域");
    }

    /** 保存所有区域到 areas.yml */
    public void save() {
        if (areasFile == null) return;
        FileConfiguration conf = new YamlConfiguration();
        ConfigurationSection section = conf.createSection("areas");
        for (Area area : areas.values()) {
            ConfigurationSection a = section.createSection(area.name);
            a.set("world", area.world);
            a.set("pos1", area.minX + "," + area.minY + "," + area.minZ);
            a.set("pos2", area.maxX + "," + area.maxY + "," + area.maxZ);
            a.set("title", area.title);
            a.set("subtitle", area.subtitle);
        }
        try {
            if (!areasFile.getParentFile().exists()) areasFile.getParentFile().mkdirs();
            // 原子写入：先写临时文件再替换，防止宕机产生半截文件
            File tmp = new File(areasFile.getParentFile(), "areas.yml.tmp");
            conf.save(tmp);
            java.nio.file.Files.move(tmp.toPath(), areasFile.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "保存 areas.yml 失败", e);
        }
    }

    /** 创建区域（若同名已存在则返回 false） */
    public boolean createArea(String name, Location loc1, Location loc2, String title, String subtitle) {
        if (loc1.getWorld() == null || loc2.getWorld() == null) return false;
        if (!loc1.getWorld().getName().equalsIgnoreCase(loc2.getWorld().getName())) return false;
        if (!name.matches("[a-zA-Z0-9_-]+")) return false; // 区域名仅允许字母数字下划线连字符
        String key = name.toLowerCase(Locale.ROOT);
        if (areas.containsKey(key)) return false;
        Area area = new Area(key, loc1.getWorld().getName(),
                loc1.getBlockX(), loc1.getBlockY(), loc1.getBlockZ(),
                loc2.getBlockX(), loc2.getBlockY(), loc2.getBlockZ(),
                title, subtitle);
        areas.put(key, area);
        save();
        return true;
    }

    /** 删除区域 */
    public boolean removeArea(String name) {
        boolean removed = areas.remove(name.toLowerCase(Locale.ROOT)) != null;
        if (removed) save();
        return removed;
    }

    /** 获取玩家所在区域（进入检测），无则 null；重叠时按名称排序取第一个（结果确定） */
    public Area getAreaAt(Location loc) {
        Area best = null;
        for (Area area : areas.values()) {
            if (area.contains(loc)) {
                if (best == null || area.getName().compareTo(best.getName()) < 0) {
                    best = area;
                }
            }
        }
        return best;
    }

    /** 获取区域 */
    public Area getArea(String name) {
        return areas.get(name.toLowerCase(Locale.ROOT));
    }

    /** 列出所有区域名 */
    public List<String> getAreaNames() {
        List<String> names = new ArrayList<>(areas.keySet());
        Collections.sort(names);
        return names;
    }

    public int size() {
        return areas.size();
    }

    private int[] parsePos(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        String[] parts = raw.split(",");
        if (parts.length != 3) return null;
        try {
            return new int[]{Integer.parseInt(parts[0].trim()),
                    Integer.parseInt(parts[1].trim()),
                    Integer.parseInt(parts[2].trim())};
        } catch (NumberFormatException e) {
            return null;
        }
    }
}