# HFcatBiomeTitle 设计方案

> 基于 NotSabrina/BiomesTitle 开源项目改造，修复数据包群系命名空间识别问题

---

## 一、项目概述

**插件名**：HFcatBiomeTitle
**版本**：1.0.0
**构建**：Paper 26.2 API + JDK 25 + Maven
**包名**：`top.miragedge.biometitle`
**GitHub**：待定（FCelestial 组织下）

### 改造动机

原 BiomesTitle 插件核心缺陷：

```java
// 原始代码 — 无法识别数据包群系
String newBiome = to.getBlock().getBiome().toString();
// 原版群系 → "PLAINS" ✓
// 数据包群系 → "CUSTOM" ✗ 配置白写了
```

`Block.getBiome()` 返回 `Biome` 接口实例。原版群系有对应枚举常量，`toString()` 返回名称。但数据包群系不在枚举中，`getBiome()` 返回 `Biome.CUSTOM`，丢失了实际命名空间信息。

### 解决方案

Paper 1.21.4+ 的 `Biome` 接口继承了 `Keyed`，有 `getKey()` 方法返回 `NamespacedKey`：

```java
// 修复后 — 正确获取全命名空间键
Biome biome = block.getBiome();
NamespacedKey key = biome.getKey();
String biomeKey = key.toString();
// 原版群系 → "minecraft:plains" ✓
// 数据包群系 → "incendium:ash_barrens" ✓
```

---

## 二、功能清单

### 核心功能（从原版继承 + 修复）

| 功能 | 说明 | 来源 |
|------|------|------|
| 群系标题 | 进入群系时显示 Title/Subtitle | 原版 + KcBiomeTitle |
| ActionBar 模式 | 在 ActionBar 上显示群系名 | KcBiomeTitle |
| 数据包群系支持 | 正确识别 `namespace:key` 格式 | **新增（核心修复）** |
| 冷却间隔 | 防止频繁弹出标题 | KcBiomeTitle |
| 领地忽略 | 在领地内不显示标题 | KcBiomeTitle |
| 禁用世界 | 指定世界不显示标题 | 原版 |
| 进入命令 | 进入群系时执行命令 | 原版 |
| 粒子效果 | 进入群系时生成粒子 | 原版 |
| 拖尾粒子 | 在群系内行走时持续粒子 | 原版 |
| PlaceholderAPI | 消息支持 PAPI 变量 | 原版 |
| 自定义区域 | 用法杖圈选区域显示标题 | 原版 |

### 新增/改进功能

| 功能 | 说明 |
|------|------|
| MiniMessage 支持 | 用 Adventure Component API 替代旧 ChatColor |
| Geyser 颜色降级 | 基岩版玩家自动降级为 § 颜色码 |
| 配置自动补全 | 新版本新增配置键自动合并进现有文件 |
| 相似群系检查 | 群系名称相似时不重复弹出（优化版） |
| 异步配置读取 | 大配置文件异步加载，不阻塞主线程 |
| Folia 兼容调度 | 支持 Folia 服务器（可选） |

---

## 三、配置设计

### config.yml 结构

```yaml
# HFcatBiomeTitle 配置
# 群系键格式：原版用 Biome 枚举名（如 PLAINS），数据包用 namespace:path（如 incendium:ash_barrens）
# 不区分大小写匹配

# 显示设置
display:
  # 默认显示方式：title / actionbar / subtitle / bossbar
  default: "title"
  # ActionBar 模式（覆盖 default）
  actionbar: false
  # Title 淡入/停留/淡出（tick）
  fadeIn: 10
  stay: 40
  fadeOut: 10

# 冷却间隔（秒），同一玩家在此时间内不重复触发
min-interval: 8

# 在领地内不显示标题（需要 Dominion/GriefPrevention 等领地插件）
ignore-res: true

# 不显示标题的世界
disable-worlds:
  - "zc"
  - "pvp"

# 相似检查：如果新群系名称与上一个相似就不提示
similar-check: true

# 拖尾粒子（全局）
trail-particles:
  # type: "VILLAGER_HAPPY"
  # count: 1
  # offsetX: 0.1
  # offsetY: 0.1
  # offsetZ: 0.1

# 群系配置
biomes:
  # ===== 原版群系 =====
  # 支持两种写法：
  #   PLAINS:          （枚举名，不区分大小写）
  #   minecraft:plains: （全命名空间键）
  PLAINS:
    title: "<green>平原</green>"
    subtitle: "<gray>一望无际的草原</gray>"
    # display: "title"  # 可选，覆盖默认
    # sound:
    #   - "minecraft:entity.player.levelup"
    # commands:
    #   - "say %player% 进入了平原"
    # particles:
    #   type: "FLAME"
    #   count: 10
    #   offsetX: 0.5
    #   offsetY: 0.5
    #   offsetZ: 0.5
    # trail-particles:
    #   type: "VILLAGER_HAPPY"
    #   count: 5
    #   offsetX: 0.3
    #   offsetY: 0.1
    #   offsetZ: 0.3

  # ===== 数据包群系 =====
  # 直接用 namespace:path 格式，与服务器注册表一致
  "incendium:ash_barrens":
    title: "<dark_gray>灰烬荒原</dark_gray>"
    subtitle: "<gray>炽热灰烬覆盖着无尽的下界荒地</gray>"

  "terralith:siberian_taiga":
    title: "<dark_green>西伯利亚针叶林</dark_green>"
    subtitle: "<gray>无边针叶林通向严寒的北方</gray>"
```

### 配置兼容性

- **原版群系**：接受 `PLAINS`、`plains`、`minecraft:plains` 三种写法
- **数据包群系**：接受 `incendium:ash_barrens` 格式（与 `Biome.getKey().toString()` 一致）
- **匹配逻辑**：大小写不敏感，先匹配全键，再匹配枚举名
- **从 KcBiomeTitle 迁移**：配置格式几乎兼容，只需把 `title`/`subtitle` 的 `&` 颜色码改为 MiniMessage `<color>` 标签（也可保留 `&` 格式，内部做转换）

### 消息格式

- **优先 MiniMessage**：`<green>平原</green>`、`<gradient:#ff0000:#00ff00>标题</gradient>`
- **兼容旧格式**：自动转换 `&a`、`§a` 为 MiniMessage 等价
- **Geyser 降级**：基岩版玩家自动降级为 § 颜色码（16色），跳过渐变
- **MC 消息禁 emoji**：所有文本不含 emoji

---

## 四、架构设计

### 包结构

```
top.miragedge.biometitle/
├── HFcatBiomeTitle.java              # 主类（JavaPlugin）
├── config/
│   └── ConfigManager.java            # 配置管理（加载/热重载/自动补全）
├── biome/
│   ├── BiomeMatcher.java             # 群系匹配引擎（核心）
│   └── BiomeEntry.java               # 单个群系配置数据模型
├── listener/
│   ├── PlayerMoveListener.java       # 玩家移动 → 群系变化检测
│   └── WandListener.java             # 法杖选区监听
├── display/
│   ├── DisplayManager.java           # 显示分发（title/actionbar/subtitle/bossbar）
│   └── MessageUtil.java              # MiniMessage + Geyser 降级 + PAPI
├── area/
│   ├── AreaManager.java             # 自定义区域管理
│   └── AreaWand.java                # 法杖选区工具
├── command/
│   ├── BiomeTitleCommand.java        # 主命令 + TabCompleter
│   └── SetAreaCommand.java          # 设置区域命令
├── particle/
│   └── ParticleManager.java         # 粒子效果管理
├── residence/
│   └── ResidenceManager.java        # 领地检测（Dominion/GP/无）
└── util/
    ├── CooldownManager.java         # 冷却管理
    └── SimilarCheck.java            # 相似群系检查
```

### 核心类设计

#### BiomeMatcher（群系匹配引擎）

```java
public class BiomeMatcher {
    // 配置键 → BiomeEntry 映射
    // 键统一存储为小写
    private final Map<String, BiomeEntry> entries = new ConcurrentHashMap<>();

    // 匹配方法：给定 Biome，返回配置条目
    public BiomeEntry match(Biome biome) {
        // 1. 获取 NamespacedKey
        NamespacedKey key = biome.getKey();
        String fullKey = key.toString();  // "minecraft:plains" or "incendium:ash_barrens"

        // 2. 先按全键匹配（小写）
        BiomeEntry entry = entries.get(fullKey.toLowerCase());
        if (entry != null) return entry;

        // 3. 再按枚举名匹配（如 "PLAINS" → "plains"）
        String enumName = biome.name().toLowerCase();
        entry = entries.get(enumName);
        if (entry != null) return entry;

        // 4. 再按 key 的 value 部分匹配（如 "minecraft:plains" → "plains"）
        entry = entries.get(key.getKey());
        if (entry != null) return entry;

        // 5. 没匹配到
        return null;
    }

    // 从配置加载
    public void loadFromConfig(FileConfiguration config) {
        ConfigurationSection biomes = config.getConfigurationSection("biomes");
        if (biomes == null) return;
        for (String key : biomes.getKeys(false)) {
            // 统一小写存储
            String normalized = key.toLowerCase();
            BiomeEntry entry = BiomeEntry.fromConfig(biomes.getConfigurationSection(key));
            entries.put(normalized, entry);
        }
    }
}
```

#### BiomeEntry（数据模型）

```java
public class BiomeEntry {
    private String title;           // 主标题（MiniMessage）
    private String subtitle;        // 副标题
    private String displayType;     // title/actionbar/subtitle/bossbar
    private List<String> sounds;
    private List<String> commands;
    private ParticleConfig particles;
    private ParticleConfig trailParticles;

    // 从配置节加载
    public static BiomeEntry fromConfig(ConfigurationSection section) {
        // 兼容两种字段名：
        //   title/subtitle（KcBiomeTitle 格式）
        //   message/subtitle（原版格式）
        // 兼容两种颜色码：
        //   &a/§a（旧格式，自动转 MiniMessage）
        //   <green>（MiniMessage 原生）
    }
}
```

#### PlayerMoveListener（移动监听）

```java
@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
public void onPlayerMove(PlayerMoveEvent event) {
    // 只在跨方块时检测（优化性能）
    Location from = event.getFrom();
    Location to = event.getTo();
    if (from.getBlockX() == to.getBlockX()
        && from.getBlockY() == to.getBlockY()
        && from.getBlockZ() == to.getBlockZ()) return;

    Player player = event.getPlayer();

    // 禁用世界检查
    if (disabledWorlds.contains(player.getWorld().getName())) return;

    // 领地检查
    if (ignoreRes && residenceManager.isInResidence(player, to)) return;

    // 冷却检查
    if (cooldownManager.isOnCooldown(player)) return;

    // 获取群系
    Biome fromBiome = from.getBlock().getBiome();
    Biome toBiome = to.getBlock().getBiome();
    if (fromBiome.equals(toBiome)) return;

    // 匹配配置
    BiomeEntry entry = biomeMatcher.match(toBiome);
    if (entry == null) return;

    // 相似检查
    if (similarCheck && similarCheck.isSimilar(player, toBiome)) return;

    // 显示标题
    displayManager.display(player, entry);

    // 播放音效
    playSounds(player, entry);

    // 执行命令
    executeCommands(player, entry);

    // 生成粒子
    spawnParticles(player, entry);

    // 设置冷却
    cooldownManager.setCooldown(player, minInterval);
}
```

#### ResidenceManager（领地检测）

```java
public class ResidenceManager {
    private final boolean enabled;

    // 检测玩家是否在领地内
    public boolean isInResidence(Player player, Location loc) {
        if (!enabled) return false;
        // 方案1：Dominion API
        // 方案2：GriefPrevention API
        // 方案3：通用方案 — 检查常见领地插件的 API
        // 方案4：不集成具体插件，用配置的坐标区域
    }
}
```

领地检测策略：
- **softdepend Dominion**：如果服务器安装了 Dominion，用其 API 检查
- **无 Dominion 时**：退化为不检查（`ignore-res` 配置项被忽略）
- **不使用反射**：编译时依赖 Dominion API（如果有的话），或者直接用命令检测

---

## 五、技术选型

| 项目 | 选择 | 理由 |
|------|------|------|
| 构建工具 | Maven | 原版用 Maven，保持一致 |
| Paper API | 26.2 | 匹配服务器 Leaf 26.2 |
| JDK | 25 | Paper 26.2 要求 class version 69 |
| shade | maven-dependency-plugin unpack | shade 3.x 不兼容 JDK 25（ASM 不支持 class version 69） |
| 消息 | Adventure Component (MiniMessage) | Paper 原生，支持渐变 |
| 颜色兼容 | 自动转换 `&` → MiniMessage | 兼容旧配置 |
| 配置 | YamlConfiguration | Bukkit 标准 |
| bStats | 可选 | 原版有，可保留或移除 |

### Maven 依赖

```xml
<dependencies>
    <!-- Paper API 26.2 -->
    <dependency>
        <groupId>io.papermc.paper</groupId>
        <artifactId>paper-api</artifactId>
        <version>26.2.build.111-SNAPSHOT</version>
        <scope>provided</scope>
    </dependency>
    <!-- PlaceholderAPI（可选） -->
    <dependency>
        <groupId>me.clip</groupId>
        <artifactId>placeholderapi</artifactId>
        <version>2.11.6</version>
        <scope>provided</scope>
    </dependency>
    <!-- bStats（可选） -->
    <dependency>
        <groupId>org.bstats</groupId>
        <artifactId>bstats-bukkit</artifactId>
        <version>3.0.2</version>
        <scope>compile</scope>
    </dependency>
</dependencies>
```

### plugin.yml

```yaml
name: HFcatBiomeTitle
version: 1.0.0
main: top.miragedge.biometitle.HFcatBiomeTitle
api-version: '1.26'
description: 群系标题显示插件，支持数据包群系命名空间
author: FCelestial
softdepend:
  - PlaceholderAPI
  - Dominion
commands:
  bt:
    description: HFcatBiomeTitle 主命令
    usage: /bt <reload|get|wand|setarea>
    permission: biomestitle.admin
permissions:
  biomestitle.admin:
    description: 管理权限
    default: op
  biomestitle.command.wand:
    description: 获取法杖
    default: op
  biomestitle.command.setarea:
    description: 设置区域
    default: op
```

---

## 六、核心匹配逻辑（关键设计）

### Biome.getKey() 的行为

| 群系类型 | `getBiome()` 返回 | `getKey()` 返回 | `toString()` 返回 |
|---------|-------------------|-----------------|-------------------|
| 原版平原 | `Biome.PLAINS` | `minecraft:plains` | `minecraft:plains` |
| 原版森林 | `Biome.FOREST` | `minecraft:forest` | `minecraft:forest` |
| Incendium | 注册表对象 | `incendium:ash_barrens` | `incendium:ash_barrens` |
| Terralith | 注册表对象 | `terralith:siberian_taiga` | `terralith:siberian_taiga` |

### 匹配流程

```
Block.getBiome() → Biome 对象
       ↓
biome.getKey() → NamespacedKey（如 "incendium:ash_barrens"）
       ↓
key.toString().toLowerCase() → "incendium:ash_barrens"
       ↓
entries.get("incendium:ash_barrens") → BiomeEntry ✓
```

### 兼容性匹配（三种写法都支持）

```
配置键 "PLAINS" → 存储为 "plains"
配置键 "minecraft:plains" → 存储为 "minecraft:plains"
配置键 "minecraft:PLAINS" → 存储为 "minecraft:plains"

匹配时依次尝试：
  1. fullKey.toLowerCase()     → "minecraft:plains"     ✓
  2. biome.name().toLowerCase() → "plains"              ✓
  3. key.getKey()              → "plains"               ✓
```

---

## 七、从 KcBiomeTitle 迁移

### 配置迁移

KcBiomeTitle 的配置可以直接复用，只需少量调整：

1. **颜色码转换**（可选）：`&a` → `<green>`，但插件内部会自动转换，不改也能用
2. **顶层结构调整**：
   - `actionBar: true` → `display.actionbar: true`
   - `settings.fadeIn` → `display.fadeIn`
   - `disable-worlds` → `disable-worlds`（不变）
   - `min-interval` → `min-interval`（不变）
   - `ignore-res` → `ignore-res`（不变）
   - `similar-check` → `similar-check`（不变）
3. **群系段**：完全兼容，无需修改

### 预设配置

插件将内置一份完整配置，包含：
- 全部原版群系（MC 1.21.4+ / 26.2）
- Incendium 下界群系（8 个）
- Nullscape 末地群系（3 个）
- Stellarity 群系（从服务器配置复制）
- Terralith 群系（从服务器配置复制）
- 26.2 新增群系（硫黄洞穴等）

---

## 八、性能优化

| 优化点 | 方案 |
|--------|------|
| PlayerMoveEvent 高频 | 只在跨方块边界时检测（比较 blockX/Y/Z） |
| Biome 查询 | `Block.getBiome()` 是区块内查询，O(1) |
| 配置查找 | ConcurrentHashMap，O(1) |
| 冷却 | ConcurrentHashMap<UUID, Long>，PlayerQuitEvent 清理 |
| 粒子 | 只在进入群系时生成一次，不在每次 move 生成 |
| 拖尾粒子 | 定时任务（每 N tick）而非每次 move |
| 配置加载 | 启动时一次性加载到内存，运行时不读文件 |

---

## 九、开发计划

### Phase 1：核心功能（MVP）
1. 项目骨架（pom.xml, plugin.yml, 主类）
2. BiomeMatcher（群系匹配引擎）
3. PlayerMoveListener（移动监听 + 群系检测）
4. DisplayManager（标题/ActionBar 显示）
5. ConfigManager（配置加载）
6. 内置配置文件（原版 + 数据包群系）

### Phase 2：增强功能
7. 冷却管理
8. 相似检查
9. 领地检测
10. 粒子效果
11. 命令执行
12. PlaceholderAPI 集成

### Phase 3：区域系统
13. 法杖工具
14. 区域选区
15. 区域配置（Area.yml）
16. 区域标题显示

### Phase 4：打磨
17. 配置自动补全
18. Geyser 颜色降级
19. Folia 兼容（可选）
20. 从 KcBiomeTitle 迁移指南
21. 多轮审查 + 构建

---

## 十、注意事项

1. **不重启服务器**：部署后告知用户"需要重启才生效"
2. **MC 消息禁 emoji**：所有文本不含 emoji
3. **中文全汉化**：配置注释、日志输出、消息文本用中文
4. **配置兼容**：从 KcBiomeTitle 迁移时不需要删旧配置
5. **shade 不兼容 JDK 25**：用 maven-dependency-plugin unpack 替代（如需打包依赖）
6. **Paper API 版本**：需确认 26.2 的准确 Maven 版本号
7. **数据包群系的 Biome.CUSTOM 情况**：如果 `getBiome()` 确实返回 `Biome.CUSTOM`，需要用 `RegistryAccess` 查找实际键

---

*设计者：狐魇星玖 (FCelestial)*
*日期：2026-08-25*
