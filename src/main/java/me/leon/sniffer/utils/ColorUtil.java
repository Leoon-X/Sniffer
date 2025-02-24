package me.leon.sniffer.utils;

import org.bukkit.ChatColor;

import java.util.ArrayList;
import java.util.List;

public class ColorUtil {

    public static String translate(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    public static List<String> translate(List<String> lines) {
        List<String> translated = new ArrayList<>();
        for (String line : lines) {
            translated.add(translate(line));
        }
        return translated;
    }

    public static String strip(String text) {
        return ChatColor.stripColor(text);
    }

    public static String getProgressBar(double current, double max, int totalBars, char symbol, ChatColor completedColor, ChatColor notCompletedColor) {
        double percent = (current / max);
        int progressBars = (int) (totalBars * percent);

        StringBuilder builder = new StringBuilder();
        builder.append(completedColor);

        for (int i = 0; i < totalBars; i++) {
            if (i == progressBars) {
                builder.append(notCompletedColor);
            }
            builder.append(symbol);
        }

        return builder.toString();
    }

    public static String colorValue(double value, double threshold) {
        if (value > threshold) {
            return ChatColor.RED + String.valueOf(value);
        } else {
            return ChatColor.GREEN + String.valueOf(value);
        }
    }

    public static String formatHeader(String text) {
        return ChatColor.GRAY + "» " + ChatColor.YELLOW + text + ChatColor.GRAY + " «";
    }

    public static String formatInfo(String label, Object value) {
        return ChatColor.GRAY + label + ": " + ChatColor.WHITE + value;
    }

    public static String formatWarning(String text) {
        return ChatColor.RED + "⚠ " + ChatColor.YELLOW + text;
    }

    public static String formatSuccess(String text) {
        return ChatColor.GREEN + "✔ " + ChatColor.WHITE + text;
    }
}