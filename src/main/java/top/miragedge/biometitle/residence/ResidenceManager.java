package top.miragedge.biometitle.residence;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import java.lang.reflect.Method;
import java.util.logging.Level;

/**
 * 领地检测管理器。
 * 当玩家位于领地内时不显示群系标题（由配置 ignore-res 控制）。
 *
 * 支持的领地插件（通过反射检测，避免硬依赖）：
 *  - Dominion（GriefPrevention 重构版）：API 或命令检测
 *  - GriefPrevention：DataStore.findClaim
 *  - Residence：getResidenceManager().getByLoc
 *  - Lands：LandsAPI.isClaimed
 *
 * 未安装任何领地插件时，isInResidence 恒返回 false。
 */
public class ResidenceManager {

    private final Plugin plugin;
    private boolean enabled;

    // 反射缓存
    private Method gpFindClaim = null;
    private Method residenceGetByLoc = null;
    private Method landsIsClaimed = null;
    private Method dominionGetClaim = null;

    public ResidenceManager(Plugin plugin) {
        this.plugin = plugin;
    }

    /** 初始化：检测已安装的领地插件（只对已安装但反射失败的打 WARNING，未安装的跳过） */
    public void init() {
        PluginManager pm = plugin.getServer().getPluginManager();

        // Dominion（尝试多种可能的 API 类名，兼容不同版本）
        if (pm.getPlugin("Dominion") != null) {
            String[] dominionCandidates = {
                "com.dominion.api.DominionAPI",
                "com.dominion.api.Dominion",
                "com.dominion.Dominion",
                "com.dominion.DominionPlugin",
                "com.griefdefender.api.GriefDefender"  // Dominion 基于 GriefDefender，可能沿用
            };
            for (String className : dominionCandidates) {
                try {
                    Class<?> clazz = Class.forName(className);
                    // 查找 getInstance 或 getApi 方法
                    Method instanceMethod = null;
                    try {
                        instanceMethod = clazz.getMethod("getInstance");
                    } catch (NoSuchMethodException e1) {
                        try {
                            instanceMethod = clazz.getMethod("getApi");
                        } catch (NoSuchMethodException e2) {
                            continue;
                        }
                    }
                    Object api = instanceMethod.invoke(null);
                    // 查找 getClaim 或 getClaimAt 方法
                    Method getClaim = null;
                    try {
                        getClaim = api.getClass().getMethod("getClaim", Location.class);
                    } catch (NoSuchMethodException e1) {
                        try {
                            getClaim = api.getClass().getMethod("getClaimAt", Location.class);
                        } catch (NoSuchMethodException e2) {
                            continue;
                        }
                    }
                    dominionGetClaim = getClaim;
                    enabled = true;
                    plugin.getLogger().info("领地检测：已启用 Dominion（API: " + className + "）");
                    return;
                } catch (Exception ignored) {
                    // 继续尝试下一个
                }
            }
            plugin.getLogger().warning("Dominion 已安装但无法识别 API 类，领地检测对该插件无效。请检查 Dominion 版本或联系作者。");
        }

        // GriefPrevention
        if (!enabled && pm.getPlugin("GriefPrevention") != null) {
            try {
                Class<?> gpClass = Class.forName("me.ryanhamshire.GriefPrevention.GriefPrevention");
                Class<?> dataStoreClass = Class.forName("me.ryanhamshire.GriefPrevention.DataStore");
                Method instanceMethod = gpClass.getMethod("getInstance");
                Object gp = instanceMethod.invoke(null);
                Method getDataStore = gpClass.getMethod("getDataStore");
                Object dataStore = getDataStore.invoke(gp);
                gpFindClaim = dataStoreClass.getMethod("findClaim", Location.class, boolean.class, boolean.class);
                enabled = true;
                plugin.getLogger().info("领地检测：已启用 GriefPrevention");
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "GriefPrevention 检测失败（反射签名不匹配？）", e);
            }
        }

        // Residence
        if (!enabled && pm.getPlugin("Residence") != null) {
            try {
                Class<?> residenceClass = Class.forName("com.bekvon.bukkit.residence.Residence");
                Method instanceMethod = residenceClass.getMethod("getInstance");
                Object residence = instanceMethod.invoke(null);
                Method getResidenceManager = residenceClass.getMethod("getResidenceManager");
                Object manager = getResidenceManager.invoke(residence);
                residenceGetByLoc = manager.getClass().getMethod("getByLoc", Location.class);
                enabled = true;
                plugin.getLogger().info("领地检测：已启用 Residence");
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Residence 检测失败（反射签名不匹配？）", e);
            }
        }

        // Lands
        if (!enabled && pm.getPlugin("Lands") != null) {
            try {
                Class<?> landsApiClass = Class.forName("me.angeschossen.lands.api.LandsAPI");
                landsIsClaimed = landsApiClass.getMethod("isClaimed", Location.class);
                enabled = true;
                plugin.getLogger().info("领地检测：已启用 Lands");
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Lands 检测失败（反射签名不匹配？）", e);
            }
        }

        if (!enabled) {
            plugin.getLogger().fine("未安装支持的领地插件，领地检测已禁用");
        }
    }

    /** 是否启用了领地检测 */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 检测玩家是否位于领地内。
     *
     * @param player 玩家
     * @param loc    位置（通常为玩家当前位置）
     * @return 在领地内返回 true
     */
    public boolean isInResidence(Player player, Location loc) {
        if (!enabled) return false;
        try {
            if (dominionGetClaim != null) {
                Object claim = dominionGetClaim.invoke(getDominionApi(), loc);
                return claim != null;
            }
            if (gpFindClaim != null) {
                // findClaim(location, ignoreHeight, excludeSubdivision)
                Object claim = gpFindClaim.invoke(getGriefPreventionDataStore(), loc, false, true);
                return claim != null;
            }
            if (residenceGetByLoc != null) {
                Object res = residenceGetByLoc.invoke(getResidenceManagerInstance(), loc);
                return res != null;
            }
            if (landsIsClaimed != null) {
                Object result = landsIsClaimed.invoke(null, loc);
                return Boolean.TRUE.equals(result);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.FINE, "领地检测异常（忽略）：", e);
        }
        return false;
    }

    // ---- 反射辅助：缓存 API 实例 ----

    private Object dominionApiInstance;
    private Object getDominionApi() throws Exception {
        if (dominionApiInstance == null) {
            Class<?> clazz = Class.forName("com.dominion.api.DominionAPI");
            dominionApiInstance = clazz.getMethod("getInstance").invoke(null);
        }
        return dominionApiInstance;
    }

    private Object gpDataStore;
    private Object getGriefPreventionDataStore() throws Exception {
        if (gpDataStore == null) {
            Class<?> clazz = Class.forName("me.ryanhamshire.GriefPrevention.GriefPrevention");
            Object gp = clazz.getMethod("getInstance").invoke(null);
            gpDataStore = clazz.getMethod("getDataStore").invoke(gp);
        }
        return gpDataStore;
    }

    private Object residenceManagerInstance;
    private Object getResidenceManagerInstance() throws Exception {
        if (residenceManagerInstance == null) {
            Class<?> clazz = Class.forName("com.bekvon.bukkit.residence.Residence");
            Object residence = clazz.getMethod("getInstance").invoke(null);
            residenceManagerInstance = clazz.getMethod("getResidenceManager").invoke(residence);
        }
        return residenceManagerInstance;
    }
}