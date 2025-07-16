package com.killinggame.mod.utils;

import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * 文本格式化工具类
 */
public class TextUtils {
    /**
     * 格式化文本，支持&符号颜色代码
     * @param text 包含&颜色代码的文本
     * @return 格式化后的文本
     */
    public static String formatText(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        
        return text.replace("&0", "§0")
                .replace("&1", "§1")
                .replace("&2", "§2")
                .replace("&3", "§3")
                .replace("&4", "§4")
                .replace("&5", "§5")
                .replace("&6", "§6")
                .replace("&7", "§7")
                .replace("&8", "§8")
                .replace("&9", "§9")
                .replace("&a", "§a")
                .replace("&b", "§b")
                .replace("&c", "§c")
                .replace("&d", "§d")
                .replace("&e", "§e")
                .replace("&f", "§f")
                .replace("&k", "§k")
                .replace("&l", "§l")
                .replace("&m", "§m")
                .replace("&n", "§n")
                .replace("&o", "§o")
                .replace("&r", "§r");
    }
    
    /**
     * 创建带有格式化的Text对象
     * @param content 内容
     * @return Text对象
     */
    public static Text colored(String content) {
        return Text.literal(formatText(content));
    }
    
    /**
     * 创建带有格式化和样式的Text对象
     * @param content 内容
     * @param style 样式
     * @return Text对象
     */
    public static Text styled(String content, Style style) {
        return Text.literal(formatText(content)).setStyle(style);
    }
    
    /**
     * 创建带有格式化颜色的Text对象
     * @param content 内容
     * @param formatting 颜色
     * @return Text对象
     */
    public static Text colored(String content, Formatting formatting) {
        return Text.literal(formatText(content)).styled(style -> style.withColor(formatting));
    }
} 