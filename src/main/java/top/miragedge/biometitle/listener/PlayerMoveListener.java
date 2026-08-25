package top.miragedge.biometitle.listener;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.SoundCategory;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import top.miragedge.biometitle.area.AreaManager;
import top.miragedge.biometitle.biome.BiomeEntry;
import top.miragedge.biometitle.biome.BiomeMatcher;
import top.miragedge.biometitle.config.ConfigManager;
import top.miragedge.biometitle.display.DisplayManager;
import top.miragedge.biometitle.particle.ParticleManager;
import top.miragedge.biometitle.residence.ResidenceManager;
import top.miragedge.biometitle.util.CooldownManager;
import top.miragedge.biometitle.util.SimilarCheck;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * 玩家移动监听器。
 * 在玩家跨方块移动时检测群系变化，触发标题/音效/粒子/命令等。
 */
public class PlayerMoveListener implements Listener {

    private final Plugin plugin;
    private final ConfigManager configManager;
    private final BiomeMatcher biomeMatcher;
    private final CooldownManager cooldownManager;
    private final SimilarCheck similarCheck;
    private final ResidenceManager residenceManager;
    private final DisplayManager displayManager;
    private final ParticleManager particleManager;
    private final AreaManager areaManager;

    // 可刷新字段（reload 时更新，volatile 保证 Folia 跨线程可见性）
    private volatile Set<String> disabledWorlds;
    private volatile boolean ignoreRes;
    private volatile boolean similarCheckEnabled;
    private volatile BiomeEntry.ParticleConfig globalTrailParticles;

    /** 玩家上次所在的群系（用于相似检查） */
    private final Map<UUID, Biome> lastBiomes = new ConcurrentHashMap<>();

    /** 玩家上次所在的自定义区域（用于防止重复触发） */
    private final Map<UUID, String> lastAreas = new ConcurrentHashMap<>();

    /** 玩家当前的拖尾粒子任务 */
    private final Map<UUID, ScheduledTask> trailTasks = new ConcurrentHashMap<>();

    /** Geyser API 是否可用（启动时检测一次） */
    private boolean geyserAvailable = false;
    private java.lang.reflect.Method geyserApiMethod;
    private java.lang.reflect.Method geyserIsBedrockMethod;

    public PlayerMoveListener(Plugin plugin, ConfigManager configManager, BiomeMatcher biomeMatcher,
                              CooldownManager cooldownManager, SimilarCheck similarCheck,
                              ResidenceManager residenceManager, DisplayManager displayManager,
                              ParticleManager particleManager, AreaManager areaManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.biomeMatcher = biomeMatcher;
        this.cooldownManager = cooldownManager;
        this.similarCheck = similarCheck;
        this.residenceManager = residenceManager;
        this.displayManager = displayManager;
        this.particleManager = particleManager;
        this.areaManager = areaManager;
        refreshConfig();
        try {
            Class.forName("org.geysermc.geyser.api.GeyserApi");
            geyserAvailable = true;
        } catch (ClassNotFoundException e) {
            geyserAvailable = false;
        }
    }

    /** 刷新配置缓存（供重载时调用） */
    public void refreshConfig() {
        this.disabledWorlds = configManager.getDisabledWorlds();
        this.ignoreRes = configManager.isIgnoreRes();
        this.similarCheckEnabled = configManager.isSimilarCheckEnabled();
        this.globalTrailParticles = configManager.getGlobalTrailParticles();
        this.cooldownManager.setInterval(configManager.getMinInterval());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        try {
            handleMove(event);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "BiomeTitle 移动事件处理异常", e);
        }
    }

    private void handleMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        // 只在跨方块时检测（性能优化）
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();
        UUID uid = player.getUniqueId();

        // 禁用世界检查
        if (disabledWorlds.contains(player.getWorld().getName().toLowerCase())) {
            stopTrailTask(player);
            return;
        }

        // 领地检查（进入领地：停止拖尾 + 不显示）
        if (ignoreRes && residenceManager.isEnabled() && residenceManager.isInResidence(player, to)) {
            stopTrailTask(player);
            return;
        }

        // 自定义区域优先检测（注意：区域检测在冷却之前，进入区域提示不受群系冷却影响；
        // 冷却仍会限制同一区域重复触发频率）
        AreaManager.Area area = areaManager.getAreaAt(to);
        if (area != null) {
            String lastArea = lastAreas.get(uid);
            if (!area.getName().equals(lastArea)) {
                lastAreas.put(uid, area.getName());
                handleAreaEntry(player, area);
            }
            return;
        } else {
            // 离开区域：清除记录（群系检测继续）
            lastAreas.remove(uid);
        }

        // 冷却检查（群系提示）
        if (cooldownManager.isOnCooldown(player)) return;

        // 获取当前群系（用 Block.getBiome() 取 3D 群系，洞穴群系才有效）
        Biome toBiome = getBiome(to);
        if (toBiome == null || toBiome.getKey() == null) return;

        // 获取上次群系
        Biome fromBiome = lastBiomes.get(uid);

        // 群系相同则跳过（key 为 null 视为不同）
        if (fromBiome != null && fromBiome.getKey() != null
                && fromBiome.getKey().equals(toBiome.getKey())) return;

        // 相似检查（同一相似组的群系不重复提示）
        if (similarCheckEnabled && fromBiome != null && similarCheck.isSimilar(fromBiome, toBiome)) {
            lastBiomes.put(uid, toBiome);
            return;
        }

        // 匹配配置
        BiomeEntry entry = biomeMatcher.match(toBiome);
        if (entry == null || !entry.hasAnyEffect()) {
            stopTrailTask(player);
            lastBiomes.remove(uid);
            return;
        }

        // 记录上次群系
        lastBiomes.put(uid, toBiome);

        // Geyser 基岩版玩家颜色降级
        boolean isGeyser = geyserAvailable && isGeyserPlayer(player);

        // 显示标题
        displayManager.display(player, entry, isGeyser);

        // 播放音效
        playSounds(player, entry);

        // 执行命令
        executeCommands(player, entry);

        // 生成进入粒子
        particleManager.spawnEnterParticles(player, entry.getParticles());

        // 管理拖尾粒子
        handleTrailParticles(player, entry);

        // 设置冷却
        cooldownManager.setCooldown(player);
    }

    /** 处理进入自定义区域 */
    private void handleAreaEntry(Player player, AreaManager.Area area) {
        stopTrailTask(player);
        boolean isGeyser = geyserAvailable && isGeyserPlayer(player);
        BiomeEntry fakeEntry = new BiomeEntry();
        fakeEntry.setTitle(area.getTitle());
        fakeEntry.setSubtitle(area.getSubtitle());
        fakeEntry.setDisplayType("title");
        displayManager.display(player, fakeEntry, isGeyser);
        cooldownManager.setCooldown(player);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uid = player.getUniqueId();
        lastBiomes.remove(uid);
        lastAreas.remove(uid);
        stopTrailTask(player);
        displayManager.onPlayerQuit(player);
    }

    /** 获取位置的群系：用 Block.getBiome() 保留 Y 维度（洞穴群系可正常识别） */
    private Biome getBiome(Location loc) {
        return loc.getBlock().getBiome();
    }

    /** 播放音效 */
    private void playSounds(Player player, BiomeEntry entry) {
        for (String soundName : entry.getSounds()) {
            if (soundName == null || soundName.isEmpty()) continue;
            try {
                var sound = Registry.SOUND_EVENT.get(NamespacedKey.fromString(soundName.toLowerCase(java.util.Locale.ROOT)));
                if (sound != null) {
                    player.playSound(player.getLocation(), sound, SoundCategory.AMBIENT, 1.0f, 1.0f);
                } else {
                    // 备用：字符串形式播放
                    player.playSound(player.getLocation(), soundName, SoundCategory.AMBIENT, 1.0f, 1.0f);
                }
            } catch (Exception ignored) {
                // 忽略无效音效
            }
        }
    }

    /** 执行命令（配置由管理员写入，视为可信；%player% 替换玩家名） */
    private void executeCommands(Player player, BiomeEntry entry) {
        for (String cmd : entry.getCommands()) {
            if (cmd == null || cmd.isEmpty()) continue;
            String processed = cmd.replace("%player%", player.getName());
            // 命令分发属于全局调度器工作：Folia 上从区域线程直接调用会破坏目标插件的主线程假设，
            // 通过 GlobalRegionScheduler 调度（Paper 非 Folia 时等价主线程）。
            plugin.getServer().getGlobalRegionScheduler().run(plugin, task -> {
                try {
                    player.getServer().dispatchCommand(player.getServer().getConsoleSender(), processed);
                } catch (Exception ignored) {
                    // 忽略失败命令
                }
            });
        }
    }

    /** 管理拖尾粒子 */
    private void handleTrailParticles(Player player, BiomeEntry entry) {
        // 先停止旧任务
        stopTrailTask(player);

        // 配置优先级：biome 独有 > 全局
        BiomeEntry.ParticleConfig trailConfig = entry.getTrailParticles();
        if (trailConfig == null) {
            trailConfig = globalTrailParticles;
        }
        if (trailConfig != null) {
            ScheduledTask task = particleManager.startTrailTask(player, trailConfig);
            if (task != null) {
                trailTasks.put(player.getUniqueId(), task);
            }
        }
    }

    /** 停止并清理玩家的拖尾粒子任务 */
    public void stopTrailTask(Player player) {
        ScheduledTask old = trailTasks.remove(player.getUniqueId());
        if (old != null) {
            old.cancel();
        }
    }

    /** Geyser 基岩版玩家检测（反射方法缓存，仅 Geyser 安装时调用） */
    private boolean isGeyserPlayer(Player player) {
        try {
            if (geyserApiMethod == null || geyserIsBedrockMethod == null) {
                Class<?> geyserApiClass = Class.forName("org.geysermc.geyser.api.GeyserApi");
                geyserApiMethod = geyserApiClass.getMethod("api");
                Object api = geyserApiMethod.invoke(null);
                geyserIsBedrockMethod = api.getClass().getMethod("isBedrockPlayer", java.util.UUID.class);
            }
            Object api = geyserApiMethod.invoke(null);
            Object result = geyserIsBedrockMethod.invoke(api, player.getUniqueId());
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            return false;
        }
    }
}