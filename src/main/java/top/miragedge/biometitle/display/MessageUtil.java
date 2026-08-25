package top.miragedge.biometitle.display;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 消息工具类。
 * 处理：
 *  1. MiniMessage 解析（优先，含渐变/彩虹支持）
 *  2. 旧格式 & 颜色码自动转换为 MiniMessage（兼容 KcBiomeTitle 配置）
 *  3. PlaceholderAPI 变量替换（如果启用）
 *  4. Geyser 基岩版玩家颜色降级（§ 颜色码，16 色；渐变/彩虹降级为单一颜色）
 */
public class MessageUtil {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final Pattern LEGACY_COLOR_PATTERN = Pattern.compile("(?i)[&§]([0-9a-fk-or])");
    private static final Pattern GRADIENT_PATTERN = Pattern.compile("<gradient:[^>]+>.*?</gradient>");
    private static final Pattern RAINBOW_PATTERN = Pattern.compile("<rainbow[^>]*>.*?</rainbow>");

    private final Plugin plugin;
    private volatile boolean papiEnabled = false; // volatile：reload 线程写入、区域线程读取
    private java.lang.reflect.Method papiMethod; // 缓存 PAPI 反射方法

    public MessageUtil(Plugin plugin) {
        this.plugin = plugin;
    }

    /** 设置 PlaceholderAPI 是否可用 */
    public void setPapiEnabled(boolean enabled) {
        this.papiEnabled = enabled;
    }

    /**
     * 将配置中的消息字符串解析为 Adventure Component。
     * 自动处理颜色格式转换。
     *
     * @param raw 原始字符串（可能含 MiniMessage 或 & 颜色码）
     * @param player 玩家（用于 PAPI 替换，可为 null）
     */
    public Component parse(String raw, Player player) {
        if (raw == null || raw.isEmpty()) return Component.empty();
        String text = raw;

        // 1. 替换 PAPI 变量（必须在颜色转换之前，确保 & 颜色码被保留）
        if (papiEnabled && player != null) {
            text = applyPlaceholderAPI(player, text);
        }

        // 2. 统一先把旧格式颜色码（& 码 / &x 十六进制 / &#RRGGBB）转换为 MiniMessage 标签，
        //    这样 MiniMessage 与旧格式混用也能正确处理（如 <green>&l平原</green>）
        text = convertLegacyToMiniMessage(text);

        // 3. 解析为 Component（失败时降级纯文本）
        return safeDeserialize(text);
    }

    /**
     * 将消息降级为 § 颜色码格式，用于 Geyser 基岩版玩家。
     * 渐变/彩虹标签降级为单一颜色（渐变取起始色，彩虹取白色），
     * 保证基岩版玩家仍能看到颜色而不是纯文本。
     *
     * @param raw 原始字符串
     * @param player 玩家
     * @return 降级后的纯文本字符串（含 § 颜色码）
     */
    public String downgradeForGeyser(String raw, Player player) {
        if (raw == null || raw.isEmpty()) return "";
        String text = raw;

        // 1. PAPI 替换
        if (papiEnabled && player != null) {
            text = applyPlaceholderAPI(player, text);
        }

        // 2. 先统一旧格式颜色码（&x / &#hex / &a），确保 MiniMessage 与旧格式混用也能正确降级
        text = convertLegacyToMiniMessage(text);

        // 3. 如果是 MiniMessage 格式，先转成 Component 再序列化为 § 格式
        if (containsMiniMessageTags(text)) {
            // Geyser 不支持渐变/彩虹：降级为单一颜色
            text = GRADIENT_PATTERN.matcher(text).replaceAll(m -> downgradeGradient(m.group(), false));
            text = RAINBOW_PATTERN.matcher(text).replaceAll(m -> downgradeGradient(m.group(), true));
            // 用 MiniMessage 解析后转 § 格式
            try {
                Component component = MINI_MESSAGE.deserialize(text);
                return LegacyComponentSerializer.legacySection().serialize(component);
            } catch (Exception ignored) {
                // 降级失败，剥离残留标签后返回纯文本
                text = text.replaceAll("<[^>]*>", "");
            }
        }

        // 4. 已是 &/§ 格式，直接转换 & 为 §（Geyser 支持 § 格式）
        return text.replace('&', '§');
    }

    /**
     * 将渐变/彩虹标签降级为单色 MiniMessage 标签。
     * 渐变取起始色；彩虹用白色。
     */
    private static String downgradeGradient(String whole, boolean rainbow) {
        int start = whole.indexOf('>') + 1;
        int end = whole.lastIndexOf('<');
        String inner = (start > 0 && end > start) ? whole.substring(start, end) : "";
        if (rainbow) {
            return "<color:#ffffff>" + inner + "</color>";
        }
        // 提取渐变参数中的第一个 #RRGGBB
        String header = whole.substring(0, whole.indexOf('>') + 1);
        Matcher cm = Pattern.compile("#[0-9a-fA-F]{6}").matcher(header);
        if (cm.find()) {
            return "<color:" + cm.group() + ">" + inner + "</color>";
        }
        return inner;
    }

    /**
     * 判断字符串是否包含 MiniMessage 标签。
     * 检测形如 &lt;tag...&gt; 的 MiniMessage 标签（纯文本中的 "&lt;" 不误判）。
     */
    private boolean containsMiniMessageTags(String text) {
        return text.matches("(?s).*<[a-zA-Z][^>]*>.*");
    }

    /**
     * 将旧格式 & 颜色码转换为 MiniMessage 标签。
     * 支持：
     *   - 单字符码：&a -&gt; &lt;green&gt;，&c -&gt; &lt;red&gt;
     *   - 十六进制 &x&R&R&G&G&B&B（六连） -&gt; &lt;color:#RRGGBB&gt;
     *   - 简写 &amp;#RRGGBB -&gt; &lt;color:#RRGGBB&gt;
     */
    public static String convertLegacyToMiniMessage(String text) {
        if (text == null || text.isEmpty()) return text;

        // 1. &x&R&R&G&G&B&B 六连十六进制
        String hex6 = text.replaceAll("(?i)(?:&|§)x(?:&|§)([0-9a-f])(?:&|§)([0-9a-f])(?:&|§)([0-9a-f])(?:&|§)([0-9a-f])(?:&|§)([0-9a-f])(?:&|§)([0-9a-f])",
                "<color:#$1$2$3$4$5$6>");
        // 2. &#RRGGBB 简写
        String hexShort = hex6.replaceAll("(?i)(?:&|§)#([0-9a-f]{6})", "<color:#$1>");
        // 3. 单字符颜色/格式码
        StringBuffer sb = new StringBuffer();
        Matcher matcher = LEGACY_COLOR_PATTERN.matcher(hexShort);
        while (matcher.find()) {
            String code = matcher.group(1).toLowerCase(Locale.ROOT);
            String replacement = legacyToMiniMessageTag(code);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /** 单个 & 颜色码 → MiniMessage 标签映射 */
    private static String legacyToMiniMessageTag(String code) {
        return switch (code) {
            case "0" -> "<black>";
            case "1" -> "<dark_blue>";
            case "2" -> "<dark_green>";
            case "3" -> "<dark_aqua>";
            case "4" -> "<dark_red>";
            case "5" -> "<dark_purple>";
            case "6" -> "<gold>";
            case "7" -> "<gray>";
            case "8" -> "<dark_gray>";
            case "9" -> "<blue>";
            case "a" -> "<green>";
            case "b" -> "<aqua>";
            case "c" -> "<red>";
            case "d" -> "<light_purple>";
            case "e" -> "<yellow>";
            case "f" -> "<white>";
            case "k" -> "<obfuscated>";
            case "l" -> "<bold>";
            case "m" -> "<strikethrough>";
            case "n" -> "<underlined>";
            case "o" -> "<italic>";
            case "r" -> "<reset>";
            default -> "";
        };
    }

    /**
     * 替换 PlaceholderAPI 变量。
     * 仅在 PAPI 可用时调用。反射方法缓存，避免每次调用都查类。
     */
    private String applyPlaceholderAPI(@NotNull Player player, @NotNull String text) {
        try {
            if (papiMethod == null) {
                Class<?> papiClass = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
                papiMethod = papiClass.getMethod("setPlaceholders", Player.class, String.class);
            }
            Object result = papiMethod.invoke(null, player, text);
            if (result instanceof String) {
                return (String) result;
            }
        } catch (Exception ignored) {
            // PAPI 不可用，跳过
        }
        return text;
    }

    /** 安全解析：MiniMessage 解析失败时降级为纯文本，避免事件链崩溃 */
    private static Component safeDeserialize(String text) {
        try {
            return MINI_MESSAGE.deserialize(text);
        } catch (Exception e) {
            return Component.text(text);
        }
    }

    /** 快速解析纯文本（无玩家上下文） */
    public static Component quickParse(String text) {
        if (text == null || text.isEmpty()) return Component.empty();
        if (text.contains("<")) {
            return safeDeserialize(text);
        }
        // 转换旧格式
        String converted = convertLegacyToMiniMessage(text);
        return safeDeserialize(converted);
    }
}