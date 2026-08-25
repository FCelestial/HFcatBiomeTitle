package top.miragedge.biometitle.command;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import top.miragedge.biometitle.area.AreaManager;
import top.miragedge.biometitle.area.AreaWand;
import top.miragedge.biometitle.display.MessageUtil;

/**
 * 设置区域命令。
 * 用法：/bt setarea <名称> <标题> [副标题]
 * 玩家必须先用法杖选择两个点（左键/右键）。
 */
public class SetAreaCommand {

    private final AreaManager areaManager;
    private final AreaWand areaWand;
    private final MessageUtil messageUtil;

    public SetAreaCommand(AreaManager areaManager, AreaWand areaWand, MessageUtil messageUtil) {
        this.areaManager = areaManager;
        this.areaWand = areaWand;
        this.messageUtil = messageUtil;
    }

    /**
     * 执行 setarea 子命令。
     *
     * @param sender  命令发送者
     * @param args    参数（args[0]="setarea"，args[1]=名称，args[2]=标题，args[3..]=副标题）
     * @return true 表示命令已处理
     */
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageUtil.quickParse("<red>该命令只能由玩家执行</red>"));
            return true;
        }

        if (!player.hasPermission("biomestitle.command.setarea")) {
            player.sendMessage(messageUtil.quickParse("<red>你没有权限执行该命令（biomestitle.command.setarea）</red>"));
            return true;
        }

        if (args.length < 3) {
            player.sendMessage(messageUtil.quickParse(
                    "<gold>用法：</gold><aqua>/bt setarea <名称> <标题> [副标题]</aqua>"));
            return true;
        }

        String name = args[1];
        String title = args[2];
        String subtitle = args.length >= 4 ? String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length)) : "";

        Location pos1 = areaWand.getPos1(player);
        Location pos2 = areaWand.getPos2(player);
        if (pos1 == null || pos2 == null) {
            player.sendMessage(messageUtil.quickParse(
                    "<red>请先使用法杖选择两个点：</red><yellow>左键选第一点，右键选第二点</yellow>"));
            return true;
        }

        if (!pos1.getWorld().getName().equalsIgnoreCase(pos2.getWorld().getName())) {
            player.sendMessage(messageUtil.quickParse("<red>两个点必须在同一个世界！</red>"));
            return true;
        }

        boolean created = areaManager.createArea(name, pos1, pos2, title, subtitle);
        if (created) {
            player.sendMessage(messageUtil.quickParse(
                    "<green>区域创建成功：</green><yellow>" + name + "</yellow>"));
            player.sendMessage(messageUtil.quickParse("<gray>标题：</gray><white>" + title + "</white>"));
            if (!subtitle.isEmpty()) {
                player.sendMessage(messageUtil.quickParse("<gray>副标题：</gray><white>" + subtitle + "</white>"));
            }
            areaWand.clearSelection(player);
        } else {
            player.sendMessage(messageUtil.quickParse(
                    "<red>创建失败：区域名称已存在或坐标无效（区域名不区分大小写）</red>"));
        }
        return true;
    }
}