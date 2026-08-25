package top.miragedge.biometitle.listener;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import top.miragedge.biometitle.area.AreaWand;

/**
 * 法杖选区监听器。
 * 手持法杖时：
 *   - 左键单击方块：设置第一点
 *   - 右键单击方块：设置第二点
 */
public class WandListener implements Listener {

    private final AreaWand areaWand;

    public WandListener(AreaWand areaWand) {
        this.areaWand = areaWand;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getItem() == null || !areaWand.isWand(event.getItem())) return;
        if (event.getClickedBlock() == null) return;

        Player player = event.getPlayer();
        if (!player.hasPermission("biomestitle.command.setarea")) {
            player.sendMessage(areaWand.getNoPermissionMessage());
            event.setCancelled(true);
            return;
        }

        Location loc = event.getClickedBlock().getLocation();
        Action action = event.getAction();
        if (action == Action.LEFT_CLICK_BLOCK) {
            areaWand.setPos1(player, loc);
            event.setCancelled(true);
        } else if (action == Action.RIGHT_CLICK_BLOCK) {
            areaWand.setPos2(player, loc);
            event.setCancelled(true);
        }
    }
}