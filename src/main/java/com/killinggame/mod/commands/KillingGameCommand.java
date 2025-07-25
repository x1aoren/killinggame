package com.killinggame.mod.commands;

import com.killinggame.mod.KillingGameMod;
import com.killinggame.mod.utils.TextUtils;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

/**
 * 生物大逃杀命令处理类
 */
public class KillingGameCommand {

    /**
     * 注册命令
     */
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("killinggame")
                .requires(source -> source.hasPermissionLevel(2)) // 需要权限等级2（OP）
                .then(CommandManager.literal("start")
                        .executes(KillingGameCommand::startGame))
                .then(CommandManager.literal("stop")
                        .executes(KillingGameCommand::stopGame))
                .then(CommandManager.literal("status")
                        .executes(KillingGameCommand::getGameStatus))
                .then(CommandManager.literal("help")
                        .executes(KillingGameCommand::showHelp))
                .then(CommandManager.literal("set")
                        .then(CommandManager.literal("rounds")
                                .then(CommandManager.argument("数量", IntegerArgumentType.integer(1, 50))
                                        .executes(KillingGameCommand::setRounds))))
                .then(CommandManager.literal("set")
                        .then(CommandManager.literal("time")
                                .then(CommandManager.argument("分钟", IntegerArgumentType.integer(1, 60))
                                        .executes(KillingGameCommand::setTime)))));
    }

    /**
     * 开始游戏命令处理
     */
    private static int startGame(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        if (KillingGameMod.GAME_MANAGER.isGameActive()) {
            if (source.getPlayer() != null) {
                source.getPlayer().sendMessage(Text.literal(TextUtils.formatText("&c游戏已经在进行中！")), false);
            }
            return 0;
        }
        
        KillingGameMod.GAME_MANAGER.startGame(source.getServer());
        if (source.getPlayer() != null) {
            source.getPlayer().sendMessage(Text.literal(TextUtils.formatText("&a生物大逃杀已开始！")), false);
        }
        return 1;
    }

    /**
     * 停止游戏命令处理
     */
    private static int stopGame(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        if (!KillingGameMod.GAME_MANAGER.isGameActive()) {
            if (source.getPlayer() != null) {
                source.getPlayer().sendMessage(Text.literal(TextUtils.formatText("&c游戏尚未开始！")), false);
            }
            return 0;
        }
        
        KillingGameMod.GAME_MANAGER.stopGame(source.getServer());
        if (source.getPlayer() != null) {
            source.getPlayer().sendMessage(Text.literal(TextUtils.formatText("&c生物大逃杀已停止！")), false);
        }
        return 1;
    }

    /**
     * 获取游戏状态命令处理
     */
    private static int getGameStatus(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        boolean isActive = KillingGameMod.GAME_MANAGER.isGameActive();
        String status = isActive ? "&a正在进行" : "&c未开始";
        if (source.getPlayer() != null) {
            source.getPlayer().sendMessage(Text.literal(TextUtils.formatText("&6生物大逃杀状态：" + status)), false);
        }
        return 1;
    }

    /**
     * 显示帮助命令处理
     */
    private static int showHelp(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        if (source.getPlayer() != null) {
            source.getPlayer().sendMessage(Text.literal(TextUtils.formatText("&6&l===== &e生物大逃杀指令帮助 &6&l=====")), false);
            source.getPlayer().sendMessage(Text.literal(TextUtils.formatText("")), false);
            source.getPlayer().sendMessage(Text.literal(TextUtils.formatText("&a&l游戏控制指令：")), false);
            source.getPlayer().sendMessage(Text.literal(TextUtils.formatText("&e/killinggame start &7- &f开始生物大逃杀游戏")), false);
            source.getPlayer().sendMessage(Text.literal(TextUtils.formatText("&e/killinggame stop &7- &f停止当前游戏")), false);
            source.getPlayer().sendMessage(Text.literal(TextUtils.formatText("&e/killinggame status &7- &f查看当前游戏状态")), false);
            source.getPlayer().sendMessage(Text.literal(TextUtils.formatText("")), false);
            source.getPlayer().sendMessage(Text.literal(TextUtils.formatText("&b&l游戏配置指令：")), false);
            source.getPlayer().sendMessage(Text.literal(TextUtils.formatText("&e/killinggame set rounds <数量> &7- &f设置游戏总轮数 &8(&7范围: 1-50&8)")), false);
            source.getPlayer().sendMessage(Text.literal(TextUtils.formatText("&e/killinggame set time <分钟> &7- &f设置每轮时间间隔 &8(&7范围: 1-60分钟&8)")), false);
            source.getPlayer().sendMessage(Text.literal(TextUtils.formatText("")), false);
            source.getPlayer().sendMessage(Text.literal(TextUtils.formatText("&d&l其他指令：")), false);
            source.getPlayer().sendMessage(Text.literal(TextUtils.formatText("&e/killinggame help &7- &f显示此帮助菜单")), false);
            source.getPlayer().sendMessage(Text.literal(TextUtils.formatText("")), false);
            source.getPlayer().sendMessage(Text.literal(TextUtils.formatText("&6&l================================")), false);
        }
        return 1;
    }

    /**
     * 设置轮数命令处理
     */
    private static int setRounds(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        int rounds = IntegerArgumentType.getInteger(context, "数量");
        
        KillingGameMod.GAME_MANAGER.setMaxRounds(rounds);
        if (source.getPlayer() != null) {
            source.getPlayer().sendMessage(Text.literal(TextUtils.formatText("&a总轮数已设置为: &e" + rounds + " &a轮")), false);
        }
        return 1;
    }

    /**
     * 设置时间命令处理
     */
    private static int setTime(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        int minutes = IntegerArgumentType.getInteger(context, "分钟");
        
        KillingGameMod.GAME_MANAGER.setRoundTimeMinutes(minutes);
        if (source.getPlayer() != null) {
            source.getPlayer().sendMessage(Text.literal(TextUtils.formatText("&a每轮间隔时间已设置为: &e" + minutes + " &a分钟")), false);
        }
        return 1;
    }
} 