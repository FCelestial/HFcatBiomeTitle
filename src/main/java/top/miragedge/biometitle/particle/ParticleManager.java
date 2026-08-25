package top.miragedge.biometitle.particle;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import top.miragedge.biometitle.biome.BiomeEntry;

import java.util.Locale;

/**
 * 粒子效果管理。
 * 负责：
 *  1. 进入群系时的粒子爆发（spawnEnterParticles）
 *  2. 群系内行走时的拖尾粒子（startTrailTask / cancelTrailTask）
 *
 * 粒子类型使用 {@link Particle} 枚举（Paper 26.2 中为 Keyed 枚举类）。
 * 拖尾任务使用 EntityScheduler（Folia 兼容，跟随玩家实体）。
 */

public class ParticleManager {

    private final Plugin plugin;

    public ParticleManager(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 在玩家位置生成进入群系粒子。
     *
     * @param player 玩家
     * @param config 粒子配置（null 则跳过）
     */
    public void spawnEnterParticles(Player player, BiomeEntry.ParticleConfig config) {
        if (config == null) return;
        Particle particle = parseParticle(config.getType());
        if (particle == null) return;

        Location loc = player.getLocation();
        World world = player.getWorld();
        try {
            world.spawnParticle(particle, loc, config.getCount(),
                    config.getOffsetX(), config.getOffsetY(), config.getOffsetZ(),
                    config.getSpeed());
        } catch (Exception e) {
            plugin.getLogger().warning("生成粒子失败: " + config.getType() + " - " + e.getMessage());
        }
    }

    /**
     * 在玩家位置生成一次拖尾粒子。
     *
     * @param player 玩家
     * @param config 粒子配置（null 则跳过）
     */
    public void spawnTrailParticle(Player player, BiomeEntry.ParticleConfig config, Particle particle) {
        if (config == null || particle == null) return;

        Location loc = player.getLocation().clone();
        // 稍微抬高，避免粒子陷入地面
        loc.setY(loc.getY() + 0.5);
        try {
            player.getWorld().spawnParticle(particle, loc, config.getCount(),
                    config.getOffsetX(), config.getOffsetY(), config.getOffsetZ(),
                    config.getSpeed());
        } catch (Exception e) {
            plugin.getLogger().warning("生成拖尾粒子失败: " + config.getType() + " - " + e.getMessage());
        }
    }

    /**
     * 启动玩家的拖尾粒子任务（每 10 tick 一次），返回任务句柄。
     * 使用 EntityScheduler，Paper 与 Folia 均兼容，跟随玩家实体。
     * 粒子解析失败时返回 null（只在 parseParticle 中警告一次，避免日志风暴）。
     */
    public ScheduledTask startTrailTask(Player player, BiomeEntry.ParticleConfig config) {
        if (config == null) return null;
        Particle particle = parseParticle(config.getType());
        if (particle == null) return null; // 已在 parseParticle 中警告一次

        return player.getScheduler().runAtFixedRate(plugin, task -> {
            if (!player.isOnline() || !player.isValid()) {
                task.cancel();
                return;
            }
            spawnTrailParticle(player, config, particle);
        }, null, 1L, 10L);
    }

    /**
     * 解析粒子类型字符串。
     * 兼容写法：FLAME / flame / minecraft:flame / VILLAGER_HAPPY 等。
     * 优先尝试 Registry.PARTICLE_TYPE（支持数据包注册的自定义粒子），
     * 失败再回退 Particle 枚举名。
     *
     * @return 解析出的 Particle；无法解析时返回 null（并打印警告一次）
     */
    public Particle parseParticle(String type) {
        if (type == null || type.isEmpty()) return null;
        String normalized = type.trim();

        // 1. 注册表解析（支持 minecraft:flame 与数据包自定义粒子）
        try {
            NamespacedKey key = NamespacedKey.fromString(normalized.toLowerCase(Locale.ROOT));
            if (key != null) {
                Particle registered = Registry.PARTICLE_TYPE.get(key);
                if (registered != null) return registered;
            }
        } catch (Exception ignored) {
            // 非法键名，走枚举回退
        }

        // 2. 枚举回退（FLAME / VILLAGER_HAPPY / 去掉命名空间后的名字）
        String bare = normalized;
        if (bare.indexOf(':') >= 0) {
            bare = bare.substring(bare.indexOf(':') + 1);
        }
        try {
            return Particle.valueOf(bare.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            plugin.getLogger().warning("无法解析粒子类型: " + type);
            return null;
        }
    }
}