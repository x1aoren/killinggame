package com.killinggame.mod.events;

import com.killinggame.mod.KillingGameMod;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * 杀戮游戏事件处理器
 */
public class KillingGameEvents {
    
    /**
     * 注册所有事件
     */
    public static void registerEvents() {
        // 注册实体死亡事件
        ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((world, entity, killedEntity) -> {
            // 检查击杀者是否是玩家
            if (entity instanceof ServerPlayerEntity player) {
                if (killedEntity instanceof LivingEntity livingEntity) {
                    // 触发击杀处理
                    KillingGameMod.GAME_MANAGER.onEntityKilled(player, livingEntity.getType());
                }
            }
        });
        
        // 注册tick事件（用于更新游戏状态）
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            KillingGameMod.GAME_MANAGER.tick();
        });
    }
} 