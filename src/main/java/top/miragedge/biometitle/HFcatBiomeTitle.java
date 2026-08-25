package top.miragedge.biometitle;

import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import top.miragedge.biometitle.area.AreaManager;
import top.miragedge.biometitle.area.AreaWand;
import top.miragedge.biometitle.biome.BiomeMatcher;
import top.miragedge.biometitle.command.BiomeTitleCommand;
import top.miragedge.biometitle.command.SetAreaCommand;
import top.miragedge.biometitle.config.ConfigManager;
import top.miragedge.biometitle.display.DisplayManager;
import top.miragedge.biometitle.display.MessageUtil;
import top.miragedge.biometitle.listener.PlayerMoveListener;
import top.miragedge.biometitle.listener.WandListener;
import top.miragedge.biometitle.particle.ParticleManager;
import top.miragedge.biometitle.residence.ResidenceManager;
import top.miragedge.biometitle.util.CooldownManager;
import top.miragedge.biometitle.util.SimilarCheck;

import java.util.Objects;

/**
 * HFcatBiomeTitle — 群系标题显示插件
 * 支持数据包群系命名空间识别（incendium:ash_barrens 等）
 *
 * 基于 NotSabrina/BiomesTitle 改造，修复核心缺陷：
 * 原版用 Block.getBiome().toString() 无法获取数据包群系的命名空间键，
 * 改用 Biome.getKey() 获取完整 NamespacedKey。
 */
public class HFcatBiomeTitle extends JavaPlugin {

    private static HFcatBiomeTitle instance;

    private ConfigManager configManager;
    private BiomeMatcher biomeMatcher;
    private CooldownManager cooldownManager;
    private SimilarCheck similarCheck;
    private MessageUtil messageUtil;
    private ParticleManager particleManager;
    private DisplayManager displayManager;
    private ResidenceManager residenceManager;
    private AreaManager areaManager;
    private AreaWand areaWand;
    private PlayerMoveListener moveListener;

    @Override
    public void onEnable() {
        instance = this;

        // 配置管理（含自动补全）
        configManager = new ConfigManager(this);
        configManager.init();

        // 群系匹配引擎
        biomeMatcher = new BiomeMatcher();
        biomeMatcher.loadFromConfig(configManager.getBiomesSection());

        // 冷却
        cooldownManager = new CooldownManager(configManager.getMinInterval());

        // 相似检查
        similarCheck = new SimilarCheck();
        configManager.loadSimilarGroups(similarCheck);

        // 消息工具
        messageUtil = new MessageUtil(this);
        messageUtil.setPapiEnabled(getServer().getPluginManager().getPlugin("PlaceholderAPI") != null);

        // 粒子
        particleManager = new ParticleManager(this);

        // 显示
        displayManager = new DisplayManager(this, messageUtil,
                configManager.isActionbarMode(),
                configManager.getDefaultDisplay(),
                configManager.getFadeIn(), configManager.getStay(), configManager.getFadeOut());

        // 领地
        residenceManager = new ResidenceManager(this);
        residenceManager.init();

        // 自定义区域
        areaManager = new AreaManager(this);
        areaManager.init();

        // 法杖
        areaWand = new AreaWand(this, messageUtil);

        // 监听器
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(cooldownManager, this);
        moveListener = new PlayerMoveListener(this, configManager, biomeMatcher, cooldownManager,
                similarCheck, residenceManager, displayManager, particleManager, areaManager);
        pm.registerEvents(moveListener, this);
        pm.registerEvents(new WandListener(areaWand), this);

        // 命令
        SetAreaCommand setAreaCommand = new SetAreaCommand(areaManager, areaWand, messageUtil);
        BiomeTitleCommand cmd = new BiomeTitleCommand(this, biomeMatcher, areaManager,
                areaWand, setAreaCommand, messageUtil);
        Objects.requireNonNull(getCommand("bt")).setExecutor(cmd);
        Objects.requireNonNull(getCommand("bt")).setTabCompleter(cmd);

        getLogger().info("HFcatBiomeTitle 已启用！已加载 "
                + biomeMatcher.size() + " 个群系配置，"
                + areaManager.size() + " 个自定义区域。");
    }

    @Override
    public void onDisable() {
        // 清理所有玩家的拖尾粒子任务
        if (moveListener != null) {
            getServer().getOnlinePlayers().forEach(moveListener::stopTrailTask);
        }
        // 清理所有活跃 BossBar 与 ActionBar 任务
        if (displayManager != null) {
            displayManager.hideAllBossBars();
            displayManager.removeAllActionBars();
        }
        // 保存区域
        if (areaManager != null) {
            areaManager.save();
        }
        instance = null;
        getLogger().info("HFcatBiomeTitle 已禁用！");
    }

    /** 重载配置（供 /bt reload 调用） */
    public void reloadPlugin() {
        configManager.reload();
        biomeMatcher.loadFromConfig(configManager.getBiomesSection());
        configManager.loadSimilarGroups(similarCheck);
        messageUtil.setPapiEnabled(getServer().getPluginManager().getPlugin("PlaceholderAPI") != null);
        displayManager.refresh(configManager.isActionbarMode(),
                configManager.getDefaultDisplay(),
                configManager.getFadeIn(), configManager.getStay(), configManager.getFadeOut());
        areaManager.load();
        if (moveListener != null) {
            moveListener.refreshConfig();
        }
        getLogger().info("HFcatBiomeTitle 配置已重载");
    }

    public static HFcatBiomeTitle getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() { return configManager; }
    public BiomeMatcher getBiomeMatcher() { return biomeMatcher; }
    public CooldownManager getCooldownManager() { return cooldownManager; }
    public SimilarCheck getSimilarCheck() { return similarCheck; }
    public MessageUtil getMessageUtil() { return messageUtil; }
    public ParticleManager getParticleManager() { return particleManager; }
    public DisplayManager getDisplayManager() { return displayManager; }
    public ResidenceManager getResidenceManager() { return residenceManager; }
    public AreaManager getAreaManager() { return areaManager; }
    public AreaWand getAreaWand() { return areaWand; }
}