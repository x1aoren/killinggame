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
            source.sendFeedback(() -> Text.literal(TextUtils.formatText("&c游戏已经在进行中！")), false);
            return 0;
        }
        
        KillingGameMod.GAME_MANAGER.startGame(source.getServer());
        source.sendFeedback(() -> Text.literal(TextUtils.formatText("&a生物大逃杀已开始！")), true);
        return 1;
    }

    /**
     * 停止游戏命令处理
     */
    private static int stopGame(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        if (!KillingGameMod.GAME_MANAGER.isGameActive()) {
            source.sendFeedback(() -> Text.literal(TextUtils.formatText("&c游戏尚未开始！")), false);
            return 0;
        }
        
        KillingGameMod.GAME_MANAGER.stopGame(source.getServer());
        source.sendFeedback(() -> Text.literal(TextUtils.formatText("&c生物大逃杀已停止！")), true);
        return 1;
    }

    /**
     * 获取游戏状态命令处理
     */
    private static int getGameStatus(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        boolean isActive = KillingGameMod.GAME_MANAGER.isGameActive();
        String status = isActive ? "&a正在进行" : "&c未开始";
        
        source.sendFeedback(() -> Text.literal(TextUtils.formatText("&6生物大逃杀状态：" + status)), false);
        return 1;
    }

    /**
     * 显示帮助命令处理
     */
    private static int showHelp(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        source.sendFeedback(() -> Text.literal(TextUtils.formatText("&6===== &e生物大逃杀指令帮助 &6=====")), false);
        source.sendFeedback(() -> Text.literal(TextUtils.formatText("&e/killinggame start &7- &f开始游戏")), false);
        source.sendFeedback(() -> Text.literal(TextUtils.formatText("&e/killinggame stop &7- &f停止游戏")), false);
        source.sendFeedback(() -> Text.literal(TextUtils.formatText("&e/killinggame status &7- &f查看游戏状态")), false);
        source.sendFeedback(() -> Text.literal(TextUtils.formatText("&e/killinggame set rounds <数量> &7- &f设置总轮数(1-50)")), false);
        source.sendFeedback(() -> Text.literal(TextUtils.formatText("&e/killinggame set time <分钟> &7- &f设置每轮时间(1-60分钟)")), false);
        source.sendFeedback(() -> Text.literal(TextUtils.formatText("&e/killinggame help &7- &f显示此帮助")), false);
        source.sendFeedback(() -> Text.literal(TextUtils.formatText("&6========================")), false);
        
        return 1;
    }

    /**
     * 设置轮数命令处理
     */
    private static int setRounds(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        int rounds = IntegerArgumentType.getInteger(context, "数量");
        
        KillingGameMod.GAME_MANAGER.setMaxRounds(rounds);
        source.sendFeedback(() -> Text.literal(TextUtils.formatText("&a总轮数已设置为: &e" + rounds + " &a轮")), true);
        return 1;
    }

    /**
     * 设置时间命令处理
     */
    private static int setTime(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        int minutes = IntegerArgumentType.getInteger(context, "分钟");
        
        KillingGameMod.GAME_MANAGER.setRoundTimeMinutes(minutes);
        source.sendFeedback(() -> Text.literal(TextUtils.formatText("&a每轮间隔时间已设置为: &e" + minutes + " &a分钟")), true);
        return 1;
    }
} 