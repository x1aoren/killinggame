package com.killinggame.mod.game;

import com.killinggame.mod.KillingGameMod;
import com.killinggame.mod.utils.TextUtils;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ScoreboardPlayerScore;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

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
    private Map<UUID, EntityType<?>> targetEntityMap = new HashMap<>();
    private Map<UUID, Boolean> playerCompletedRound = new HashMap<>();
    
    // 可能的目标实体列表
    private final List<EntityType<?>> possibleTargets = Arrays.asList(
            EntityType.ZOMBIE, EntityType.SKELETON, EntityType.CREEPER, 
            EntityType.SPIDER, EntityType.ENDERMAN, EntityType.WITCH,
            EntityType.BLAZE, EntityType.SLIME, EntityType.SHEEP,
            EntityType.PIG, EntityType.COW, EntityType.CHICKEN
    );
    
    /**
     * 开始游戏
     */
    public void startGame(MinecraftServer server) {
        if (gameActive) {
            return;
        }
        
        gameActive = true;
        currentTick = 0;
        targetEntityMap.clear();
        playerCompletedRound.clear();
        
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
        
        broadcastMessage("§6§l杀戮游戏 §a已开始！每位玩家需要击杀特定目标生物来完成轮数。");
        broadcastMessage("§e总共有 §c" + MAX_ROUNDS + " §e轮，每轮间隔 §c" + ROUND_TIME_MINUTES + " §e分钟。");
        broadcastMessage("§b祝你好运！");
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
                ScoreboardPlayerScore playerScore = scoreboard.getPlayerScore(player.getName().getString(), objective);
                int currentRound = playerScore.getScore();
                if (currentRound < MAX_ROUNDS) {
                    playerScore.setScore(currentRound + 1);
                    broadcastMessage("§e玩家 §f" + player.getName().getString() + " §a进入第 §6" + (currentRound + 1) + " §a轮！");
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
            ScoreboardPlayerScore playerScore = scoreboard.getPlayerScore(player.getName().getString(), objective);
            int currentRound = playerScore.getScore();
            
            if (currentRound > MAX_ROUNDS) {
                // 宣布获胜者
                String winnerMessage = "§6§l恭喜玩家 §e" + player.getName().getString() + " §6§l完成全部 " + MAX_ROUNDS + " 轮挑战，获得胜利！";
                
                // 在聊天栏广播
                broadcastMessage(winnerMessage);
                
                // 使用title展示
                Text title = Text.literal(TextUtils.formatText("&6&l游戏结束"));
                Text subtitle = Text.literal(TextUtils.formatText("&e" + player.getName().getString() + " &a获得胜利！"));
                
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
        if (!gameActive) {
            return;
        }
        
        UUID playerUUID = player.getUuid();
        EntityType<?> targetEntity = targetEntityMap.get(playerUUID);
        
        if (targetEntity == entityType) {
            MinecraftServer server = player.getServer();
            if (server == null) {
                return;
            }
            
            Scoreboard scoreboard = server.getScoreboard();
            ScoreboardObjective objective = scoreboard.getObjective("轮数");
            if (objective == null) {
                return;
            }
            
            ScoreboardPlayerScore playerScore = scoreboard.getPlayerScore(player.getName().getString(), objective);
            int currentRound = playerScore.getScore();
            
            // 标记玩家已完成本轮目标
            playerCompletedRound.put(playerUUID, true);
            
            // 发送完成信息
            player.sendMessage(Text.literal(TextUtils.formatText("&a你成功击杀了目标 &e" + getEntityName(targetEntity) + "&a！")));
            
            // 如果是最后一轮，立即检查获胜
            if (currentRound >= MAX_ROUNDS) {
                playerScore.setScore(MAX_ROUNDS + 1);
                checkForWinner(server);
            }
        }
    }
    
    /**
     * 为玩家分配新的目标
     */
    public void assignNewTarget(ServerPlayerEntity player) {
        if (!gameActive) {
            return;
        }
        
        EntityType<?> randomTarget = getRandomTarget();
        targetEntityMap.put(player.getUuid(), randomTarget);
        
        String targetName = getEntityName(randomTarget);
        player.sendMessage(Text.literal(TextUtils.formatText("&6本轮击杀目标: &e" + targetName)), true);
    }
    
    /**
     * 随机获取一个目标实体
     */
    private EntityType<?> getRandomTarget() {
        return possibleTargets.get(ThreadLocalRandom.current().nextInt(possibleTargets.size()));
    }
    
    /**
     * 获取实体的显示名称
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
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                player.sendMessage(Text.literal(message));
            }
        }
        KillingGameMod.LOGGER.info(message);
    }
    
    /**
     * 获取当前服务器实例
     */
    private MinecraftServer getServer() {
        try {
            return net.minecraft.server.MinecraftServer.getServer();
        } catch (Exception e) {
            KillingGameMod.LOGGER.error("无法获取服务器实例", e);
            return null;
        }
    }
    
    /**
     * 游戏是否活跃
     */
    public boolean isGameActive() {
        return gameActive;
    }
    
    /**
     * 获取玩家的目标实体
     */
    public EntityType<?> getPlayerTarget(UUID playerUUID) {
        return targetEntityMap.get(playerUUID);
    }
} 