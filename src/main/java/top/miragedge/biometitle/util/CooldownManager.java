package top.miragedge.biometitle.util;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 冷却管理器。
 * 防止同一玩家在短时间内频繁触发群系标题。
 * 线程安全（ConcurrentHashMap），PlayerQuitEvent 自动清理。
 */
public class CooldownManager implements Listener {

    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private volatile long intervalMillis; // 冷却间隔（毫秒），volatile 保证重载可见性

    public CooldownManager(int intervalSeconds) {
        this.intervalMillis = intervalSeconds * 1000L;
    }

    /** 更新冷却间隔（用于重载后更新） */
    public void setInterval(int intervalSeconds) {
        this.intervalMillis = intervalSeconds * 1000L;
    }

    /** 检查玩家是否在冷却中 */
    public boolean isOnCooldown(Player player) {
        Long last = cooldowns.get(player.getUniqueId());
        if (last == null) return false;
        return (System.currentTimeMillis() - last) < intervalMillis;
    }

    /** 设置玩家冷却 */
    public void setCooldown(Player player) {
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis());
    }

    /** 清除指定玩家的冷却 */
    public void removeCooldown(Player player) {
        cooldowns.remove(player.getUniqueId());
    }

    /** 玩家退出时清理 */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        cooldowns.remove(event.getPlayer().getUniqueId());
    }
}