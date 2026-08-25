package top.miragedge.biometitle.area;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import top.miragedge.biometitle.display.MessageUtil;

/**
 * 法杖选区工具。
 * 用法：
 *   /bt wand        获取法杖
 *   左键选择第一点，右键选择第二点
 *   /bt setarea <名称> <标题> [副标题]   保存选区为自定义区域
 *
 * 选区状态存储在玩家物品的 PDC 中（持久化，不会因重载丢失）。
 */
public class AreaWand {

    private final Plugin plugin;
    private final MessageUtil messageUtil;

    private static final String POS1_KEY = "bt_pos1";
    private static final String POS2_KEY = "bt_pos2";

    public AreaWand(Plugin plugin, MessageUtil messageUtil) {
        this.plugin = plugin;
        this.messageUtil = messageUtil;
    }

    /** 创建法杖物品 */
    public ItemStack createWand() {
        ItemStack wand = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = wand.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("§6§l群系区域法杖"));
            meta.lore(java.util.List.of(
                    Component.text("§7左键：选择第一点"),
                    Component.text("§7右键：选择第二点"),
                    Component.text("§7使用 /bt setarea <名称> <标题> [副标题] 保存")
            ));
            meta.getPersistentDataContainer().set(
                    new NamespacedKey(plugin, "bt_wand"),
                    PersistentDataType.BYTE, (byte) 1
            );
            wand.setItemMeta(meta);
        }
        return wand;
    }

    /** 判断物品是否为法杖 */
    public boolean isWand(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(
                new NamespacedKey(plugin, "bt_wand"), PersistentDataType.BYTE);
    }

    /** 设置第一点（作用于玩家手持的法杖：主手优先，副手次之） */
    public void setPos1(Player player, Location loc) {
        ItemStack item = findWandItem(player);
        if (item == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, POS1_KEY), PersistentDataType.STRING, serialize(loc));
        item.setItemMeta(meta);
        player.sendMessage(messageUtil.quickParse("<green>已设置第一点：</green><yellow>" + format(loc) + "</yellow>"));
    }

    /** 设置第二点 */
    public void setPos2(Player player, Location loc) {
        ItemStack item = findWandItem(player);
        if (item == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, POS2_KEY), PersistentDataType.STRING, serialize(loc));
        item.setItemMeta(meta);
        player.sendMessage(messageUtil.quickParse("<green>已设置第二点：</green><yellow>" + format(loc) + "</yellow>"));
        player.sendMessage(messageUtil.quickParse("<gold>可用 </gold><aqua>/bt setarea <名称> <标题> [副标题]</aqua><gold> 保存选区</gold>"));
    }

    /** 查找玩家手持的法杖（主手优先，副手次之） */
    public ItemStack findWandItem(Player player) {
        ItemStack main = player.getInventory().getItemInMainHand();
        if (isWand(main)) return main;
        ItemStack off = player.getInventory().getItemInOffHand();
        if (isWand(off)) return off;
        return null;
    }

    /** 获取玩家的第一点（无则 null） */
    public Location getPos1(Player player) {
        return getPos(player, POS1_KEY);
    }

    /** 获取玩家的第二点 */
    public Location getPos2(Player player) {
        return getPos(player, POS2_KEY);
    }

    /** 清除玩家选区 */
    public void clearSelection(Player player) {
        ItemStack item = findWandItem(player);
        if (item == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        NamespacedKey k1 = new NamespacedKey(plugin, POS1_KEY);
        NamespacedKey k2 = new NamespacedKey(plugin, POS2_KEY);
        meta.getPersistentDataContainer().remove(k1);
        meta.getPersistentDataContainer().remove(k2);
        item.setItemMeta(meta);
    }

    /** 无权限提示 */
    public Component getNoPermissionMessage() {
        return messageUtil.quickParse("<red>你没有权限使用群系区域法杖（biomestitle.command.setarea）</red>");
    }

    private Location getPos(Player player, String key) {
        ItemStack item = findWandItem(player);
        if (item == null) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String raw = pdc.get(new NamespacedKey(plugin, key), PersistentDataType.STRING);
        return deserialize(raw);
    }

    private String serialize(Location loc) {
        return loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }

    private Location deserialize(String raw) {
        if (raw == null) return null;
        String[] parts = raw.split(":");
        if (parts.length != 4) return null;
        try {
            org.bukkit.World world = plugin.getServer().getWorld(parts[0]);
            if (world == null) return null;
            return new Location(world, Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String format(Location loc) {
        return loc.getWorld().getName() + " " + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }
}