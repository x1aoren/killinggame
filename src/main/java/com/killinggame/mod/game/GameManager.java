package com.killinggame.mod.game;

import com.killinggame.mod.KillingGameMod;
import com.killinggame.mod.utils.TextUtils;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreHolder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.text.Style;
import net.minecraft.util.Formatting;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.sound.SoundEvents;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 游戏管理器，负责管理游戏状态和进程
 */
public class GameManager {
    // 游戏配置 - 改为实例变量以便动态修改
    private int maxRounds = 8;
    private int roundTimeMinutes = 3;
    private int roundTimeTicks; // 自动计算
    
    // 游戏状态
    private boolean gameActive = false;
    private int currentTick = 0;
    private Map<UUID, Object> targetMap = new HashMap<>(); // 存储目标（EntityType或玩家UUID）
    private Map<UUID, Boolean> playerCompletedRound = new HashMap<>();
    private Map<UUID, Boolean> isPlayerTarget = new HashMap<>(); // 标记目标是否为玩家
    private Set<EntityType<?>> usedEntityTargets = new HashSet<>(); // 跟踪已使用的实体目标
    private Set<UUID> usedPlayerTargets = new HashSet<>(); // 跟踪已使用的玩家目标
    
    // 可能的目标实体列表
    private final List<EntityType<?>> possibleTargets = Arrays.asList(
            // 敌对生物
            EntityType.ZOMBIE, EntityType.SKELETON, EntityType.CREEPER, 
            EntityType.SPIDER, EntityType.ENDERMAN, EntityType.WITCH,
            EntityType.BLAZE, EntityType.SLIME, EntityType.MAGMA_CUBE,
            EntityType.PHANTOM, EntityType.DROWNED, EntityType.HUSK,
            EntityType.STRAY, EntityType.PILLAGER, EntityType.VINDICATOR,
            EntityType.PIGLIN, EntityType.PIGLIN_BRUTE, EntityType.HOGLIN,
            EntityType.ZOGLIN, EntityType.GHAST, EntityType.GUARDIAN,
            EntityType.ELDER_GUARDIAN, EntityType.SHULKER, EntityType.VEX,
            EntityType.EVOKER, EntityType.RAVAGER, EntityType.SILVERFISH,
            
            // 中立生物
            EntityType.WOLF, EntityType.DOLPHIN, EntityType.GOAT,
            EntityType.PANDA, EntityType.POLAR_BEAR, EntityType.BEE,
            
            // 被动生物
            EntityType.SHEEP, EntityType.PIG, EntityType.COW, EntityType.CHICKEN,
            EntityType.HORSE, EntityType.DONKEY, EntityType.MULE, EntityType.LLAMA,
            EntityType.RABBIT, EntityType.FOX, EntityType.SQUID, EntityType.BAT,
            EntityType.OCELOT, EntityType.CAT, EntityType.MOOSHROOM, EntityType.PARROT,
            EntityType.VILLAGER, EntityType.WANDERING_TRADER, EntityType.STRIDER,
            EntityType.AXOLOTL, EntityType.TURTLE, EntityType.FROG
    );
    
    // 击杀玩家的概率 (30%)
    private static final double PLAYER_TARGET_CHANCE = 0.3;
    
    /**
     * 构造函数
     */
    public GameManager() {
        // 初始化游戏刻数
        this.roundTimeTicks = this.roundTimeMinutes * 60 * 20;
    }
    
    /**
     * 开始游戏
     */
    public void startGame(MinecraftServer server) {
        if (gameActive) {
            return;
        }
        
        gameActive = true;
        currentTick = 0;
        targetMap.clear();
        playerCompletedRound.clear();
        isPlayerTarget.clear();
        usedEntityTargets.clear();
        usedPlayerTargets.clear();
        
        // 创建计分板
        Scoreboard scoreboard = server.getScoreboard();
        ScoreboardObjective objective = scoreboard.getNullableObjective("轮数");
        if (objective != null) {
            scoreboard.removeObjective(objective);
        }
        objective = scoreboard.addObjective("轮数", ScoreboardCriterion.DUMMY, 
            Text.literal("§6§l生物大逃杀 §9- §e轮数"), ScoreboardCriterion.RenderType.INTEGER, 
            true, null);
        scoreboard.setObjectiveSlot(ScoreboardDisplaySlot.SIDEBAR, objective); // 显示在记分板侧边
        
        // 创建绿色队伍
        String teamName = "green";
        // 使用原版指令创建队伍
        server.getCommandManager().executeWithPrefix(
            server.getCommandSource(),
            "team add " + teamName
        );
        server.getCommandManager().executeWithPrefix(
            server.getCommandSource(),
            "team modify " + teamName + " color green"
        );
        
        // 加入队伍前关闭反馈
        server.getCommandManager().executeWithPrefix(
            server.getCommandSource(),
            "gamerule sendCommandFeedback false"
        );
        // 设置所有玩家的初始轮数为1，并加入绿色队伍
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            ScoreHolder scoreHolder = ScoreHolder.fromName(player.getName().getString());
            scoreboard.getOrCreateScore(scoreHolder, objective).setScore(1);
            assignNewTarget(player);
            playerCompletedRound.put(player.getUuid(), false);
            // 使用原版指令加入绿色队伍
            server.getCommandManager().executeWithPrefix(
                server.getCommandSource(),
                "team join " + teamName + " " + player.getName().getString()
            );
        }
        // 加入队伍后恢复反馈
        
        broadcastMessage("§6§l生物大逃杀 §a已开始！每位玩家需要击杀特定目标来完成轮数。");
        broadcastMessage("§e总共有 §c" + maxRounds + " §e轮，每轮间隔 §c" + roundTimeMinutes + " §e分钟。");
        broadcastMessage("§b祝你好运！");
        broadcastMessage("§d提示：目标可能是生物，也可能是其他玩家！");
        // 开启游戏时播放经验球音效
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            player.getWorld().playSound(
                null,
                player.getBlockPos(),
                SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP,
                net.minecraft.sound.SoundCategory.PLAYERS,
                1.0F,
                1.0F
            );
        }
    }
    
    /**
     * 停止游戏
     */
    public void stopGame(MinecraftServer server) {
        if (!gameActive) {
            return;
        }
        
        gameActive = false;
        
        // 移除计分板
        Scoreboard scoreboard = server.getScoreboard();
        ScoreboardObjective objective = scoreboard.getNullableObjective("轮数");
        if (objective != null) {
            scoreboard.removeObjective(objective);
        }
        
        // 退出队伍前关闭反馈
        server.getCommandManager().executeWithPrefix(
            server.getCommandSource(),
            "gamerule sendCommandFeedback false"
        );
        // 所有玩家退出队伍分组
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            server.getCommandManager().executeWithPrefix(
                server.getCommandSource(),
                "team leave " + player.getName().getString()
            );
        }
        // 退出队伍后恢复反馈
        
        broadcastMessage("§6§l生物大逃杀 §c已停止！");
    }
    
    /**
     * 游戏tick更新
     */
    public void tick() {
        if (!gameActive) {
            return;
        }
        
        MinecraftServer server = getServer();
        if (server == null) {
            return;
        }
        
        currentTick++;
        
        // 每5分钟检查一次轮次更新
        if (currentTick % roundTimeTicks == 0) {
            updateRounds(server);
        }
        
        // 每秒刷新一次动作栏目标提示
        if (currentTick % 20 == 0) {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                Object target = targetMap.get(player.getUuid());
                boolean isPlayer = isPlayerTarget.getOrDefault(player.getUuid(), false);
                if (target != null) {
                    if (isPlayer) {
                        String name = server.getPlayerManager().getPlayer((UUID)target) != null ?
                            server.getPlayerManager().getPlayer((UUID)target).getName().getString() : "目标玩家";
                        player.sendMessage(
                            Text.literal("你的目标: ")
                                .setStyle(Style.EMPTY.withColor(0x00BFFF).withBold(true))
                                .append(Text.literal(name).setStyle(Style.EMPTY.withColor(0xAA00FF).withBold(true))),
                            true
                        );
                    } else {
                        String entityName = getEntityName((EntityType<?>)target);
                        player.sendMessage(
                            Text.literal("你的目标: ")
                                .setStyle(Style.EMPTY.withColor(0x00BFFF).withBold(true))
                                .append(Text.literal(entityName).setStyle(Style.EMPTY.withColor(0xFF5555).withBold(true))),
                            true
                        );
                    }
                }
            }
        }
    }
    
    /**
     * 更新游戏轮次
     */
    private void updateRounds(MinecraftServer server) {
        Scoreboard scoreboard = server.getScoreboard();
        ScoreboardObjective objective = scoreboard.getNullableObjective("轮数");
        if (objective == null) {
            return;
        }
        
        // 处理未完成目标的玩家
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (!playerCompletedRound.getOrDefault(player.getUuid(), false)) {
                broadcastMessage("§e玩家 §f" + player.getName().getString() + " §c未能在规定时间内完成目标！");
                
                // 为每位未完成目标的玩家分配新的目标
                assignNewTarget(player);
                playerCompletedRound.put(player.getUuid(), false);
            }
        }
        
        // 重置已使用目标列表，允许下一轮重新使用所有目标
        usedEntityTargets.clear();
        usedPlayerTargets.clear();
        
        // 检查是否有玩家完成全部8轮
        checkForWinner(server);
    }
    
    /**
     * 完成一轮
     */
    private void completeRound(ServerPlayerEntity player, String targetName) {
        MinecraftServer server = getServer();
        if (server == null) return;
        
        Scoreboard scoreboard = server.getScoreboard();
        ScoreboardObjective objective = scoreboard.getNullableObjective("轮数");
        if (objective == null) return;
        
        UUID playerUUID = player.getUuid();
        String playerName = player.getName().getString();
        
        // 标记玩家已完成本轮
        playerCompletedRound.put(playerUUID, true);
        
        // 获取当前轮数
        ScoreHolder scoreHolder = ScoreHolder.fromName(playerName);
        int currentRound = scoreboard.getOrCreateScore(scoreHolder, objective).getScore();
        
        // 提示玩家已完成目标
        Text message = Text.literal(TextUtils.formatText("&a恭喜！你成功击杀了目标: &e" + targetName));
        player.sendMessage(message, false);
        
        // 增加轮数并检查是否达到最大轮数
        int newRound = currentRound + 1;
        scoreboard.getOrCreateScore(scoreHolder, objective).setScore(newRound);
        
        // 检查是否完成了所有轮数
        if (newRound > maxRounds) {
            // 已完成所有轮数，宣布胜利
            announceWinner(server, playerName);
            return;
        } else if (newRound == maxRounds) {
            // 进入最后一轮
            Text roundMessage = Text.literal(TextUtils.formatText("&a你已进入最后一轮！"));
            player.sendMessage(roundMessage, false);
        } else {
            // 普通进入下一轮
            Text roundMessage = Text.literal(TextUtils.formatText("&a你已进入第 &6" + newRound + " &a轮！"));
            player.sendMessage(roundMessage, false);
        }
        
        // 分配新的目标
        assignNewTarget(player);
    }
    
    /**
     * 检查获胜者
     */
    private void checkForWinner(MinecraftServer server) {
        Scoreboard scoreboard = server.getScoreboard();
        ScoreboardObjective objective = scoreboard.getNullableObjective("轮数");
        if (objective == null) {
            return;
        }
        
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            String playerName = player.getName().getString();
            ScoreHolder scoreHolder = ScoreHolder.fromName(playerName);
            int currentRound = scoreboard.getOrCreateScore(scoreHolder, objective).getScore();
            
            if (currentRound > maxRounds) {
                // 宣布获胜者
                announceWinner(server, playerName);
                break;
            }
        }
    }
    
    /**
     * 宣布获胜者
     */
    private void announceWinner(MinecraftServer server, String playerName) {
        // 宣布获胜者
        String winnerMessage = "§6§l恭喜玩家 §e" + playerName + " §6§l完成全部 " + maxRounds + " 轮挑战，获得胜利！";
        
        // 在聊天栏广播
        broadcastMessage(winnerMessage);
        
        // 使用title展示
        String titleText = TextUtils.formatText("&6&l游戏结束");
        String subtitleText = TextUtils.formatText("&e" + playerName + " &a获得胜利！");
        
        for (ServerPlayerEntity serverPlayer : server.getPlayerManager().getPlayerList()) {
            serverPlayer.sendMessage(Text.literal(winnerMessage));
            // 使用自定义 sendTitle 方法发送标题和副标题
            sendTitle(serverPlayer, titleText, subtitleText, 10, 70, 20);
        }
        // 胜利时播放成就完成音效
        for (ServerPlayerEntity serverPlayer : server.getPlayerManager().getPlayerList()) {
            serverPlayer.getWorld().playSound(
                null,
                serverPlayer.getBlockPos(),
                SoundEvents.ENTITY_PLAYER_LEVELUP,
                net.minecraft.sound.SoundCategory.PLAYERS,
                1.0F,
                1.0F
            );
        }
        
        // 停止游戏
        stopGame(server);
    }
    
    /**
     * 处理实体被击杀事件
     */
    public void onEntityKilled(ServerPlayerEntity player, EntityType<?> entityType) {
        if (!gameActive) return;
        
        UUID playerUUID = player.getUuid();
        Object target = targetMap.get(playerUUID);
        
        // 检查目标是否为实体类型，并且与击杀的实体类型匹配
        if (target instanceof EntityType && target.equals(entityType) && !isPlayerTarget.getOrDefault(playerUUID, false)) {
            String entityName = getEntityName(entityType);
            completeRound(player, entityName);
        }
    }
    
    /**
     * 处理玩家被击杀事件
     */
    public void onPlayerKilled(ServerPlayerEntity killer, ServerPlayerEntity victim) {
        if (!gameActive) return;
        
        UUID killerUUID = killer.getUuid();
        Object target = targetMap.get(killerUUID);
        
        // 检查目标是否为玩家UUID，并且与被击杀的玩家UUID匹配
        if (target instanceof UUID && target.equals(victim.getUuid()) && isPlayerTarget.getOrDefault(killerUUID, true)) {
            String victimName = victim.getName().getString();
            completeRound(killer, victimName);
        }
    }
    
    /**
     * 为玩家分配新的目标
     */
    public void assignNewTarget(ServerPlayerEntity player) {
        UUID playerUUID = player.getUuid();
        MinecraftServer server = getServer();
        
        if (server == null) return;
        
        // 决定目标是实体还是玩家 (30%几率是玩家)
        boolean isPlayerTargetType = ThreadLocalRandom.current().nextDouble() < PLAYER_TARGET_CHANCE;
        
        // 获取可能的玩家目标列表（排除自己和已使用的玩家目标）
        List<ServerPlayerEntity> possiblePlayerTargets = server.getPlayerManager().getPlayerList().stream()
                .filter(p -> !p.getUuid().equals(playerUUID) && !usedPlayerTargets.contains(p.getUuid()))
                .collect(Collectors.toList());
        
        // 如果没有其他玩家或随机决定目标是实体，则分配实体目标
        if (possiblePlayerTargets.isEmpty() || !isPlayerTargetType) {
            EntityType<?> targetEntity = getRandomTarget();
            targetMap.put(playerUUID, targetEntity);
            isPlayerTarget.put(playerUUID, false);
            
            String entityName = getEntityName(targetEntity);
            Text message = Text.literal(TextUtils.formatText("&e你的新目标是: &c" + entityName));
            player.sendMessage(message, false);
            // 动作栏显示
            player.sendMessage(
                Text.literal("你的目标: ")
                    .setStyle(Style.EMPTY.withColor(0x00BFFF).withBold(true))
                    .append(Text.literal(entityName).setStyle(Style.EMPTY.withColor(0xFF5555).withBold(true))),
                true
            );
        } else {
            // 分配玩家目标
            ServerPlayerEntity targetPlayer = possiblePlayerTargets.get(ThreadLocalRandom.current().nextInt(possiblePlayerTargets.size()));
            targetMap.put(playerUUID, targetPlayer.getUuid());
            isPlayerTarget.put(playerUUID, true);
            usedPlayerTargets.add(targetPlayer.getUuid()); // 标记为已使用
            
            Text message = Text.literal(TextUtils.formatText("&e你的新目标是玩家: &d" + targetPlayer.getName().getString()));
            player.sendMessage(message, false);
            // 动作栏显示
            player.sendMessage(
                Text.literal("你的目标: ")
                    .setStyle(Style.EMPTY.withColor(0x00BFFF).withBold(true))
                    .append(Text.literal(targetPlayer.getName().getString()).setStyle(Style.EMPTY.withColor(0xAA00FF).withBold(true))),
                true
            );
        }
    }
    
    /**
     * 获取随机目标实体类型
     */
    private EntityType<?> getRandomTarget() {
        EntityType<?> target;
        do {
            target = possibleTargets.get(ThreadLocalRandom.current().nextInt(possibleTargets.size()));
        } while (usedEntityTargets.contains(target));
        usedEntityTargets.add(target);
        return target;
    }
    
    /**
     * 获取实体类型的显示名称
     */
    private String getEntityName(EntityType<?> entityType) {
        // 获取实体的翻译名称而不是路径名，这样可以显示中文
        return entityType.getName().getString();
    }
    
    /**
     * 广播消息给所有玩家
     */
    private void broadcastMessage(String message) {
        MinecraftServer server = getServer();
        if (server != null) {
            Text text = Text.literal(message);
            server.getPlayerManager().broadcast(text, false);
        }
    }
    
    /**
     * 获取服务器实例
     */
    private MinecraftServer getServer() {
        try {
            return KillingGameMod.getServer();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * 获取游戏活跃状态
     */
    public boolean isGameActive() {
        return gameActive;
    }
    
    /**
     * 获取最大轮数
     */
    public int getMaxRounds() {
        return maxRounds;
    }

    /**
     * 设置最大轮数
     */
    public void setMaxRounds(int maxRounds) {
        this.maxRounds = maxRounds;
        this.roundTimeTicks = this.roundTimeMinutes * 60 * 20; // 重新计算游戏刻
        MinecraftServer server = getServer();
        if (server != null) {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                player.getWorld().playSound(
                    null,
                    player.getBlockPos(),
                    SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(),
                    net.minecraft.sound.SoundCategory.PLAYERS,
                    1.0F,
                    1.0F
                );
            }
            // 同步设置到所有客户端
            com.killinggame.mod.game.GameSettingsSyncHelper.syncGameSettingsToAll(server, this.maxRounds, this.roundTimeMinutes);
        }
    }

    /**
     * 获取每轮时间（分钟）
     */
    public int getRoundTimeMinutes() {
        return roundTimeMinutes;
    }

    /**
     * 设置每轮时间（分钟）
     */
    public void setRoundTimeMinutes(int roundTimeMinutes) {
        this.roundTimeMinutes = roundTimeMinutes;
        this.roundTimeTicks = this.roundTimeMinutes * 60 * 20; // 重新计算游戏刻
        MinecraftServer server = getServer();
        if (server != null) {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                player.getWorld().playSound(
                    null,
                    player.getBlockPos(),
                    SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(),
                    net.minecraft.sound.SoundCategory.PLAYERS,
                    1.0F,
                    1.0F
                );
            }
            // 同步设置到所有客户端
            com.killinggame.mod.game.GameSettingsSyncHelper.syncGameSettingsToAll(server, this.maxRounds, this.roundTimeMinutes);
        }
    }

    /**
     * 获取每轮时间（游戏刻）
     */
    public int getRoundTimeTicks() {
        return roundTimeTicks;
    }

    /**
     * 检查玩家的目标是否为其他玩家
     */
    public boolean isTargetPlayer(UUID playerUUID) {
        return isPlayerTarget.getOrDefault(playerUUID, false);
    }
    
    /**
     * 获取玩家的目标
     */
    public Object getPlayerTarget(UUID playerUUID) {
        return targetMap.get(playerUUID);
    }

    // 发送标题和副标题（适配 1.21+）
    public static void sendTitle(ServerPlayerEntity player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        // 先设置显示时间
        player.networkHandler.sendPacket(new TitleFadeS2CPacket(fadeIn, stay, fadeOut));
        // 主标题
        player.networkHandler.sendPacket(new TitleS2CPacket(Text.literal(title)));
        // 副标题
        player.networkHandler.sendPacket(new TitleS2CPacket(Text.literal(subtitle)));
    }
} 