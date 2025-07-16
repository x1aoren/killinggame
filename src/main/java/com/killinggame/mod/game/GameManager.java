package com.killinggame.mod.game;

import com.killinggame.mod.KillingGameMod;
import com.killinggame.mod.utils.TextUtils;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 游戏管理器，负责管理游戏状态和进程
 */
public class GameManager {
    // 游戏配置
    private static final int MAX_ROUNDS = 8;
    private static final int ROUND_TIME_MINUTES = 5;
    private static final int ROUND_TIME_TICKS = ROUND_TIME_MINUTES * 60 * 20; // 5分钟（以游戏刻计）
    
    // 游戏状态
    private boolean gameActive = false;
    private int currentTick = 0;
    private Map<UUID, Object> targetMap = new HashMap<>(); // 存储目标（EntityType或玩家UUID）
    private Map<UUID, Boolean> playerCompletedRound = new HashMap<>();
    private Map<UUID, Boolean> isPlayerTarget = new HashMap<>(); // 标记目标是否为玩家
    
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
        
        // 创建计分板
        Scoreboard scoreboard = server.getScoreboard();
        ScoreboardObjective objective = scoreboard.getObjective("轮数");
        if (objective != null) {
            scoreboard.removeObjective(objective);
        }
        objective = scoreboard.addObjective("轮数", ScoreboardCriterion.DUMMY, Text.literal("§6§l杀戮游戏 §f- §e轮数"), ScoreboardCriterion.RenderType.INTEGER);
        scoreboard.setObjectiveSlot(1, objective); // 显示在记分板侧边
        
        // 设置所有玩家的初始轮数为1
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            scoreboard.getPlayerScore(player.getName().getString(), objective).setScore(1);
            assignNewTarget(player);
            playerCompletedRound.put(player.getUuid(), false);
        }
        
        broadcastMessage("§6§l杀戮游戏 §a已开始！每位玩家需要击杀特定目标来完成轮数。");
        broadcastMessage("§e总共有 §c" + MAX_ROUNDS + " §e轮，每轮间隔 §c" + ROUND_TIME_MINUTES + " §e分钟。");
        broadcastMessage("§b祝你好运！");
        broadcastMessage("§d提示：目标可能是生物，也可能是其他玩家！");
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
        ScoreboardObjective objective = scoreboard.getObjective("轮数");
        if (objective != null) {
            scoreboard.removeObjective(objective);
        }
        
        broadcastMessage("§6§l杀戮游戏 §c已停止！");
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
        if (currentTick % ROUND_TIME_TICKS == 0) {
            updateRounds(server);
        }
    }
    
    /**
     * 更新游戏轮次
     */
    private void updateRounds(MinecraftServer server) {
        Scoreboard scoreboard = server.getScoreboard();
        ScoreboardObjective objective = scoreboard.getObjective("轮数");
        if (objective == null) {
            return;
        }
        
        // 重置所有玩家的本轮完成状态
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (!playerCompletedRound.getOrDefault(player.getUuid(), false)) {
                broadcastMessage("§e玩家 §f" + player.getName().getString() + " §c未能在规定时间内完成目标！");
            } else {
                // 已完成目标的玩家增加轮数
                String playerName = player.getName().getString();
                int currentRound = scoreboard.getPlayerScore(playerName, objective).getScore();
                if (currentRound < MAX_ROUNDS) {
                    scoreboard.getPlayerScore(playerName, objective).setScore(currentRound + 1);
                    broadcastMessage("§e玩家 §f" + playerName + " §a进入第 §6" + (currentRound + 1) + " §a轮！");
                }
            }
            
            // 为每位玩家分配新的目标
            assignNewTarget(player);
            playerCompletedRound.put(player.getUuid(), false);
        }
        
        // 检查是否有玩家完成全部8轮
        checkForWinner(server);
    }
    
    /**
     * 检查获胜者
     */
    private void checkForWinner(MinecraftServer server) {
        Scoreboard scoreboard = server.getScoreboard();
        ScoreboardObjective objective = scoreboard.getObjective("轮数");
        if (objective == null) {
            return;
        }
        
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            String playerName = player.getName().getString();
            int currentRound = scoreboard.getPlayerScore(playerName, objective).getScore();
            
            if (currentRound > MAX_ROUNDS) {
                // 宣布获胜者
                String winnerMessage = "§6§l恭喜玩家 §e" + playerName + " §6§l完成全部 " + MAX_ROUNDS + " 轮挑战，获得胜利！";
                
                // 在聊天栏广播
                broadcastMessage(winnerMessage);
                
                // 使用title展示
                Text title = Text.literal(TextUtils.formatText("&6&l游戏结束"));
                Text subtitle = Text.literal(TextUtils.formatText("&e" + playerName + " &a获得胜利！"));
                
                for (ServerPlayerEntity serverPlayer : server.getPlayerManager().getPlayerList()) {
                    serverPlayer.sendMessage(Text.literal(winnerMessage));
                    serverPlayer.sendTitle(title, subtitle, 10, 70, 20);
                }
                
                // 停止游戏
                stopGame(server);
                break;
            }
        }
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
     * 完成一轮
     */
    private void completeRound(ServerPlayerEntity player, String targetName) {
        MinecraftServer server = getServer();
        if (server == null) return;
        
        Scoreboard scoreboard = server.getScoreboard();
        ScoreboardObjective objective = scoreboard.getObjective("轮数");
        if (objective == null) return;
        
        UUID playerUUID = player.getUuid();
        String playerName = player.getName().getString();
        
        // 标记玩家已完成本轮
        playerCompletedRound.put(playerUUID, true);
        
        // 获取当前轮数
        int currentRound = scoreboard.getPlayerScore(playerName, objective).getScore();
        
        // 提示玩家已完成目标
        Text message = Text.literal(TextUtils.formatText("&a恭喜！你成功击杀了目标: &e" + targetName));
        player.sendMessage(message, false);
        
        // 如果是最后一轮，直接增加分数并检查获胜
        if (currentRound >= MAX_ROUNDS) {
            scoreboard.getPlayerScore(playerName, objective).setScore(currentRound + 1);
            checkForWinner(server);
            return;
        }
        
        // 分配新的目标
        assignNewTarget(player);
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
        
        // 获取可能的玩家目标列表（排除自己）
        List<ServerPlayerEntity> possiblePlayerTargets = server.getPlayerManager().getPlayerList().stream()
                .filter(p -> !p.getUuid().equals(playerUUID))
                .collect(Collectors.toList());
        
        // 如果没有其他玩家或随机决定目标是实体，则分配实体目标
        if (possiblePlayerTargets.isEmpty() || !isPlayerTargetType) {
            EntityType<?> targetEntity = getRandomTarget();
            targetMap.put(playerUUID, targetEntity);
            isPlayerTarget.put(playerUUID, false);
            
            String entityName = getEntityName(targetEntity);
            Text message = Text.literal(TextUtils.formatText("&e你的新目标是: &c" + entityName));
            player.sendMessage(message, false);
        } else {
            // 分配玩家目标
            ServerPlayerEntity targetPlayer = possiblePlayerTargets.get(ThreadLocalRandom.current().nextInt(possiblePlayerTargets.size()));
            targetMap.put(playerUUID, targetPlayer.getUuid());
            isPlayerTarget.put(playerUUID, true);
            
            Text message = Text.literal(TextUtils.formatText("&e你的新目标是玩家: &c" + targetPlayer.getName().getString()));
            player.sendMessage(message, false);
        }
    }
    
    /**
     * 获取随机目标实体类型
     */
    private EntityType<?> getRandomTarget() {
        return possibleTargets.get(ThreadLocalRandom.current().nextInt(possibleTargets.size()));
    }
    
    /**
     * 获取实体类型的显示名称
     */
    private String getEntityName(EntityType<?> entityType) {
        return Registries.ENTITY_TYPE.getId(entityType).getPath();
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
            return KillingGameMod.SERVER;
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
} 