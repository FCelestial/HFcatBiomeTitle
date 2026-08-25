package top.miragedge.biometitle.display;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import top.miragedge.biometitle.biome.BiomeEntry;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 显示分发管理器。
 * 负责将群系配置中的标题/副标题分发到对应显示方式：
 *   - title：Title 显示（淡入/停留/淡出）
 *   - actionbar：ActionBar 显示
 *   - subtitle：仅 Subtitle（空主标题 + 副标题）
 *   - bossbar：BossBar 显示
 *
 * 显示方式优先级：条目 display > display.actionbar > display.default > title
 * 对 Geyser 基岩版玩家自动降级为 § 颜色码。
 */
public class DisplayManager {

    private final Plugin plugin;
    private final MessageUtil messageUtil;

    // 可刷新字段（reload 时更新，volatile 保证 Folia 跨线程可见性）
    private volatile boolean actionbarMode;
    private volatile String defaultDisplay;
    private volatile int fadeIn;
    private volatile int stay;
    private volatile int fadeOut;

    /** 活跃的 BossBar 映射（玩家 UUID → BossBar 实例） */
    private final Map<UUID, BossBar> activeBossBars = new ConcurrentHashMap<>();

    /** 玩家 UUID → BossBar 自动移除任务（用于取消旧任务，防止误删新 BossBar） */
    private final Map<UUID, ScheduledTask> bossBarRemovalTasks = new ConcurrentHashMap<>();

    /** 玩家 UUID → ActionBar 重复发送句柄（实现可控停留时长） */
    private final Map<UUID, ActionBarHandle> actionBarHandles = new ConcurrentHashMap<>();

    /** ActionBar 重复发送任务句柄：repeat 负责每 40 tick 重发，clear 负责到时清除 */
    private static class ActionBarHandle {
        private ScheduledTask repeat;
        private ScheduledTask clear;
        private void cancel() {
            if (repeat != null) repeat.cancel();
            if (clear != null) clear.cancel();
        }
    }

    public DisplayManager(Plugin plugin, MessageUtil messageUtil, boolean actionbarMode,
                          String defaultDisplay, int fadeIn, int stay, int fadeOut) {
        this.plugin = plugin;
        this.messageUtil = messageUtil;
        refresh(actionbarMode, defaultDisplay, fadeIn, stay, fadeOut);
    }

    /** 刷新显示参数（供重载时调用） */
    public void refresh(boolean actionbarMode, String defaultDisplay, int fadeIn, int stay, int fadeOut) {
        this.actionbarMode = actionbarMode;
        this.defaultDisplay = defaultDisplay == null || defaultDisplay.isEmpty() ? "title" : defaultDisplay;
        this.fadeIn = fadeIn;
        this.stay = stay;
        this.fadeOut = fadeOut;
    }

    /**
     * 向玩家显示群系配置。
     *
     * @param player 目标玩家
     * @param entry  群系配置条目
     * @param isGeyserPlayer 是否为基岩版玩家
     */
    public void display(Player player, BiomeEntry entry, boolean isGeyserPlayer) {
        // 无任何文本时跳过显示（可能只有音效/粒子/命令的配置）
        String titleRaw = entry.getTitle();
        String subRaw = entry.getSubtitle();
        if ((titleRaw == null || titleRaw.isEmpty()) && (subRaw == null || subRaw.isEmpty())) return;

        // 确定显示方式：条目自定义 > 全局 ActionBar 模式 > display.default > title
        String displayType = entry.getDisplayType();
        if (displayType == null || displayType.isEmpty()) {
            if (actionbarMode) {
                displayType = "actionbar";
            } else {
                displayType = defaultDisplay;
            }
        }

        switch (displayType.toLowerCase()) {
            case "actionbar" -> showActionBar(player, entry, isGeyserPlayer);
            case "subtitle" -> showSubtitle(player, entry, isGeyserPlayer);
            case "bossbar" -> showBossBar(player, entry, isGeyserPlayer);
            default -> showTitle(player, entry, isGeyserPlayer);
        }
    }

    /** 显示 Title（含 Subtitle） */
    private void showTitle(Player player, BiomeEntry entry, boolean isGeyserPlayer) {
        Component title = buildComponent(entry.getTitle(), player, isGeyserPlayer);
        Component subtitle = buildComponent(entry.getSubtitle(), player, isGeyserPlayer);
        player.showTitle(Title.title(title, subtitle, times()));
    }

    /** 显示 Subtitle（空主标题 + 副标题） */
    private void showSubtitle(Player player, BiomeEntry entry, boolean isGeyserPlayer) {
        Component subtitle = buildComponent(entry.getSubtitle(), player, isGeyserPlayer);
        player.showTitle(Title.title(Component.empty(), subtitle, times()));
    }

    /**
     * 显示 ActionBar（标题 | 副标题；标题为空时只显示副标题）。
     *
     * 注意：客户端的 ActionBar 约 2-3 秒会自动消失，服务端无法直接指定停留时长。
     * 因此这里通过【定时重复发送】实现可控停留：每 40 tick（2 秒）重发一次，
     * 直到 fadeIn + stay + fadeOut 总时长结束，最后发送空格消息清除。
     */
    private void showActionBar(Player player, BiomeEntry entry, boolean isGeyserPlayer) {
        String titleRaw = entry.getTitle();
        String subRaw = entry.getSubtitle();
        if ((titleRaw == null || titleRaw.isEmpty()) && (subRaw == null || subRaw.isEmpty())) return;

        Component msg;
        if (titleRaw == null || titleRaw.isEmpty()) {
            msg = buildComponent(subRaw, player, isGeyserPlayer);
        } else {
            msg = buildComponent(titleRaw, player, isGeyserPlayer);
            if (subRaw != null && !subRaw.isEmpty()) {
                msg = msg.append(Component.text(" | ")).append(buildComponent(subRaw, player, isGeyserPlayer));
            }
        }
        showActionBarTimed(player, msg);
    }

    /** 带时长的 ActionBar：立即显示 + 每 40 tick 重发 + 到时清除 */
    private void showActionBarTimed(Player player, Component msg) {
        removeActionBar(player); // 先取消旧任务，防止任务堆积

        // 总显示时长 = fadeIn + stay + fadeOut（tick），至少 1 tick
        long totalTicks = Math.max(1L, (long) fadeIn + stay + fadeOut);

        player.sendActionBar(msg); // 立即显示

        ActionBarHandle handle = new ActionBarHandle();
        actionBarHandles.put(player.getUniqueId(), handle);

        // 每 40 tick（2 秒）重发一次，防止客户端自动消失（客户端约 3 秒清除）
        handle.repeat = player.getScheduler().runAtFixedRate(plugin, task -> {
            if (!player.isOnline() || !player.isValid()) {
                task.cancel();
                return;
            }
            player.sendActionBar(msg);
        }, null, 40L, 40L);

        // 总时长结束后：取消重发任务并清除 ActionBar
        handle.clear = player.getScheduler().runDelayed(plugin, task -> {
            ActionBarHandle h = actionBarHandles.remove(player.getUniqueId());
            if (h != null) h.cancel();
            if (player.isOnline()) {
                player.sendActionBar(Component.text(" "));
            }
        }, null, totalTicks);
    }

    /** 移除并取消玩家的 ActionBar 任务 */
    public void removeActionBar(Player player) {
        ActionBarHandle handle = actionBarHandles.remove(player.getUniqueId());
        if (handle != null) {
            handle.cancel();
        }
    }

    /** 清理所有玩家的 ActionBar（onDisable 调用） */
    public void removeAllActionBars() {
        for (UUID uid : actionBarHandles.keySet()) {
            ActionBarHandle handle = actionBarHandles.remove(uid);
            if (handle != null) handle.cancel();
        }
        actionBarHandles.clear();
    }

    /** 显示 BossBar（5 秒后自动移除；再次触发会先移除旧的） */
    private void showBossBar(Player player, BiomeEntry entry, boolean isGeyserPlayer) {
        removeBossBar(player);

        Component name = buildComponent(entry.getTitle(), player, isGeyserPlayer);
        if (Component.empty().equals(name)) {
            name = buildComponent(entry.getSubtitle(), player, isGeyserPlayer);
        }
        BossBar bar = BossBar.bossBar(name, 1.0f, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS);
        player.showBossBar(bar);
        activeBossBars.put(player.getUniqueId(), bar);

        // 自动移除：5 秒后清除（EntityScheduler 保证 Folia 上实体线程执行）
        ScheduledTask removal = player.getScheduler().runDelayed(plugin, task -> {
            bossBarRemovalTasks.remove(player.getUniqueId());
            removeBossBar(player);
        }, null, 100L);
        bossBarRemovalTasks.put(player.getUniqueId(), removal);
    }

    /** 移除玩家的 BossBar（只移除当前活跃的那个） */
    public void removeBossBar(Player player) {
        BossBar old = activeBossBars.remove(player.getUniqueId());
        if (old != null) {
            try {
                player.hideBossBar(old);
            } catch (Exception ignored) {}
        }
        // 取消挂起的自动移除任务，避免误删后续新 BossBar
        ScheduledTask removal = bossBarRemovalTasks.remove(player.getUniqueId());
        if (removal != null) {
            removal.cancel();
        }
    }

    /** 清理所有玩家的 BossBar（onDisable 调用） */
    public void hideAllBossBars() {
        for (UUID uid : activeBossBars.keySet()) {
            Player player = plugin.getServer().getPlayer(uid);
            if (player != null) {
                removeBossBar(player);
            }
        }
        activeBossBars.clear();
        bossBarRemovalTasks.clear();
    }

    /** 玩家退出时清理 */
    public void onPlayerQuit(Player player) {
        removeBossBar(player);
        removeActionBar(player);
    }

    private Title.Times times() {
        return Title.Times.times(
                Duration.ofMillis(fadeIn * 50L),
                Duration.ofMillis(stay * 50L),
                Duration.ofMillis(fadeOut * 50L)
        );
    }

    /** 构建 Component，自动处理 Geyser 降级 */
    private Component buildComponent(String raw, Player player, boolean isGeyserPlayer) {
        if (raw == null || raw.isEmpty()) return Component.empty();
        if (isGeyserPlayer) {
            // 基岩版玩家：降级为 § 格式后解析为 Component
            String downgraded = messageUtil.downgradeForGeyser(raw, player);
            return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                    .legacySection().deserialize(downgraded);
        }
        return messageUtil.parse(raw, player);
    }
}