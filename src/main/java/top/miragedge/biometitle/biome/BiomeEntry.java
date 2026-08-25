package top.miragedge.biometitle.biome;

import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 单个群系的配置数据模型。
 * 支持字段：title / subtitle / display / sound / commands / particles / trail-particles。
 */
public class BiomeEntry {

    /** 主标题（MiniMessage 格式） */
    private String title;
    /** 副标题（MiniMessage 格式） */
    private String subtitle;
    /** 显示方式：title / actionbar / subtitle / bossbar（为空则用全局默认） */
    private String displayType;
    /** 进入群系时播放的音效（minecraft:xxx 格式） */
    private List<String> sounds;
    /** 进入群系时执行的命令（console 执行，支持 %player%） */
    private List<String> commands;
    /** 进入群系时生成的粒子配置（可为 null） */
    private ParticleConfig particles;
    /** 群系内行走时的拖尾粒子配置（可为 null） */
    private ParticleConfig trailParticles;

    public static BiomeEntry fromConfig(ConfigurationSection section) {
        BiomeEntry entry = new BiomeEntry();
        entry.title = section.getString("title", "") == null ? "" : section.getString("title");
        entry.subtitle = section.getString("subtitle", "") == null ? "" : section.getString("subtitle");
        // 兼容 KcBiomeTitle 的 message 字段（作为标题）
        if (entry.title.isEmpty() && section.isString("message")) {
            entry.title = section.getString("message");
        }
        entry.displayType = section.getString("display", "");
        entry.sounds = new ArrayList<>(section.getStringList("sound"));
        entry.commands = new ArrayList<>(section.getStringList("commands"));
        entry.particles = ParticleConfig.fromSection(section.getConfigurationSection("particles"));
        entry.trailParticles = ParticleConfig.fromSection(section.getConfigurationSection("trail-particles"));
        return entry;
    }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getSubtitle() { return subtitle; }
    public void setSubtitle(String subtitle) { this.subtitle = subtitle; }

    public String getDisplayType() { return displayType; }
    public void setDisplayType(String displayType) { this.displayType = displayType; }

    public List<String> getSounds() { return Collections.unmodifiableList(sounds); }
    public void setSounds(List<String> sounds) { this.sounds = new ArrayList<>(sounds); }

    public List<String> getCommands() { return Collections.unmodifiableList(commands); }
    public void setCommands(List<String> commands) { this.commands = new ArrayList<>(commands); }

    @Nullable
    public ParticleConfig getParticles() { return particles; }
    public void setParticles(ParticleConfig particles) { this.particles = particles; }

    @Nullable
    public ParticleConfig getTrailParticles() { return trailParticles; }
    public void setTrailParticles(ParticleConfig trailParticles) { this.trailParticles = trailParticles; }

    /** 是否有任何显示内容（标题/副标题） */
    public boolean hasDisplay() {
        return (title != null && !title.isEmpty()) || (subtitle != null && !subtitle.isEmpty());
    }

    /** 是否配置了任何效果（显示/音效/命令/粒子/拖尾） */
    public boolean hasAnyEffect() {
        return hasDisplay()
                || !sounds.isEmpty()
                || !commands.isEmpty()
                || particles != null
                || trailParticles != null;
    }

    /** 粒子配置（type/count/offset/speed） */
    public static class ParticleConfig {
        private final String type;
        private final int count;
        private final double offsetX;
        private final double offsetY;
        private final double offsetZ;
        private final double speed;

        public ParticleConfig(String type, int count, double offsetX, double offsetY, double offsetZ, double speed) {
            this.type = type == null ? "" : type;
            this.count = count;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.offsetZ = offsetZ;
            this.speed = speed;
        }

        /** 从配置节读取；节不存在或 type 为空时返回 null */
        @Nullable
        public static ParticleConfig fromSection(@Nullable ConfigurationSection section) {
            if (section == null) return null;
            String type = section.getString("type", "");
            if (type == null || type.isEmpty()) return null;
            int count = section.getInt("count", 1);
            double ox = section.getDouble("offsetX", 0.1);
            double oy = section.getDouble("offsetY", 0.1);
            double oz = section.getDouble("offsetZ", 0.1);
            double speed = section.getDouble("speed", 0.1);
            return new ParticleConfig(type, count, ox, oy, oz, speed);
        }

        public String getType() { return type; }
        public int getCount() { return count; }
        public double getOffsetX() { return offsetX; }
        public double getOffsetY() { return offsetY; }
        public double getOffsetZ() { return offsetZ; }
        public double getSpeed() { return speed; }
    }
}
