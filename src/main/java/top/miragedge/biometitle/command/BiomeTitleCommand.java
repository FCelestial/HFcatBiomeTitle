package top.miragedge.biometitle.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import top.miragedge.biometitle.HFcatBiomeTitle;
import top.miragedge.biometitle.area.AreaManager;
import top.miragedge.biometitle.area.AreaWand;
import top.miragedge.biometitle.biome.BiomeEntry;
import top.miragedge.biometitle.biome.BiomeMatcher;
import top.miragedge.biometitle.display.MessageUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 主命令处理器。
 * 用法：
 *   /bt help             显示帮助
 *   /bt reload           重载配置
 *   /bt get              查看当前群系信息
 *   /bt wand             获取法杖
 *   /bt setarea ...      设置自定义区域
 *   /bt listarea         列出自定义区域
 *   /bt removearea <名称> 删除自定义区域
 */
public class BiomeTitleCommand implements CommandExecutor, TabCompleter {

    private final HFcatBiomeTitle plugin;
    private final BiomeMatcher biomeMatcher;
    private final AreaManager areaManager;
    private final AreaWand areaWand;
    private final SetAreaCommand setAreaCommand;
    private final MessageUtil messageUtil;

    public BiomeTitleCommand(HFcatBiomeTitle plugin, BiomeMatcher biomeMatcher,
                             AreaManager areaManager, AreaWand areaWand,
                             SetAreaCommand setAreaCommand, MessageUtil messageUtil) {
        this.plugin = plugin;
        this.biomeMatcher = biomeMatcher;
        this.areaManager = areaManager;
        this.areaWand = areaWand;
        this.setAreaCommand = setAreaCommand;
        this.messageUtil = messageUtil;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                if (!sender.hasPermission("biomestitle.admin")) {
                    sender.sendMessage(messageUtil.quickParse("<red>你没有权限执行该命令</red>"));
                    return true;
                }
                plugin.reloadPlugin();
                sender.sendMessage(messageUtil.quickParse("<green>配置已重载！</green>"));
            }
            case "get" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(messageUtil.quickParse("<red>该命令只能由玩家执行</red>"));
                    return true;
                }
                handleGet(player);
            }
            case "wand" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(messageUtil.quickParse("<red>该命令只能由玩家执行</red>"));
                    return true;
                }
                if (!player.hasPermission("biomestitle.command.wand")) {
                    player.sendMessage(messageUtil.quickParse("<red>你没有权限获取法杖</red>"));
                    return true;
                }
                player.getInventory().addItem(areaWand.createWand());
                player.sendMessage(messageUtil.quickParse(
                        "<green>已给予群系区域法杖！</green><gray>左键选第一点，右键选第二点，然后使用 /bt setarea 保存</gray>"));
            }
            case "setarea" -> setAreaCommand.execute(sender, args);
            case "listarea" -> {
                if (!sender.hasPermission("biomestitle.admin")) {
                    sender.sendMessage(messageUtil.quickParse("<red>你没有权限执行该命令</red>"));
                    return true;
                }
                List<String> names = areaManager.getAreaNames();
                if (names.isEmpty()) {
                    sender.sendMessage(messageUtil.quickParse("<gray>暂无自定义区域</gray>"));
                } else {
                    sender.sendMessage(messageUtil.quickParse("<gold>自定义区域列表：</gold>"));
                    for (String name : names) {
                        AreaManager.Area area = areaManager.getArea(name);
                        sender.sendMessage(messageUtil.quickParse(
                                "<yellow>- " + name + "</yellow> <gray>(" + area.getWorld() + ")</gray>"));
                    }
                }
            }
            case "removearea" -> {
                if (!sender.hasPermission("biomestitle.admin")) {
                    sender.sendMessage(messageUtil.quickParse("<red>你没有权限执行该命令</red>"));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(messageUtil.quickParse(
                            "<gold>用法：</gold><aqua>/bt removearea <名称></aqua>"));
                    return true;
                }
                if (areaManager.removeArea(args[1])) {
                    sender.sendMessage(messageUtil.quickParse("<green>已删除区域：</green><yellow>" + args[1] + "</yellow>"));
                } else {
                    sender.sendMessage(messageUtil.quickParse("<red>未找到区域：</red><yellow>" + args[1] + "</yellow>"));
                }
            }
            default -> sendHelp(sender);
        }
        return true;
    }

    /** /bt get：查看当前群系信息 */
    private void handleGet(Player player) {
        var biome = player.getLocation().getBlock().getBiome();
        if (biome == null || biome.getKey() == null) {
            player.sendMessage(messageUtil.quickParse("<red>无法获取当前群系</red>"));
            return;
        }
        String key = biome.getKey().toString();
        BiomeEntry entry = biomeMatcher.match(biome);
        player.sendMessage(messageUtil.quickParse(
                "<gold>当前群系：</gold><aqua>" + key + "</aqua>"));
        if (entry != null) {
            String title = entry.getTitle();
            player.sendMessage(messageUtil.quickParse(
                    "<gray>已配置标题：</gray><white>" + (title == null || title.isEmpty() ? "（无）" : title) + "</white>"));
        } else {
            player.sendMessage(messageUtil.quickParse("<gray>该群系未配置</gray>"));
        }
    }

    /** 帮助信息 */
    private void sendHelp(CommandSender sender) {
        sender.sendMessage(messageUtil.quickParse("<gold>===== HFcatBiomeTitle 帮助 =====</gold>"));
        sender.sendMessage(messageUtil.quickParse("<aqua>/bt help</aqua> <gray>- 显示帮助</gray>"));
        sender.sendMessage(messageUtil.quickParse("<aqua>/bt reload</aqua> <gray>- 重载配置（需要 biomestitle.admin）</gray>"));
        sender.sendMessage(messageUtil.quickParse("<aqua>/bt get</aqua> <gray>- 查看当前群系</gray>"));
        sender.sendMessage(messageUtil.quickParse("<aqua>/bt wand</aqua> <gray>- 获取区域法杖（需要 biomestitle.command.wand）</gray>"));
        sender.sendMessage(messageUtil.quickParse("<aqua>/bt setarea &lt;名称&gt; &lt;标题&gt; [副标题]</aqua> <gray>- 保存选区为自定义区域</gray>"));
        sender.sendMessage(messageUtil.quickParse("<aqua>/bt listarea</aqua> <gray>- 列出自定义区域</gray>"));
        sender.sendMessage(messageUtil.quickParse("<aqua>/bt removearea &lt;名称&gt;</aqua> <gray>- 删除自定义区域（需要 biomestitle.admin）</gray>"));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            for (String sub : List.of("help", "reload", "get", "wand", "setarea", "listarea", "removearea")) {
                if (sub.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    completions.add(sub);
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("removearea")
                && sender.hasPermission("biomestitle.admin")) {
            for (String name : areaManager.getAreaNames()) {
                if (name.startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    completions.add(name);
                }
            }
        }
        return completions;
    }
}