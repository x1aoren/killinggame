package com.killinggame.mod;

import com.killinggame.mod.commands.KillingGameCommand;
import com.killinggame.mod.events.KillingGameEvents;
import com.killinggame.mod.game.GameManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 生物大逃杀主模组类
 */
public class KillingGameMod implements ModInitializer {
    public static final String MOD_ID = "killinggame";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    
    // 游戏管理器实例
    public static final GameManager GAME_MANAGER = new GameManager();
    
    // 服务器实例
    private static MinecraftServer server;
    
    @Override
    public void onInitialize() {
        LOGGER.info("生物大逃杀模组初始化");
        
        try {
            // 注册指令
            CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
                KillingGameCommand.register(dispatcher);
            });
            
            // 注册事件
            KillingGameEvents.registerEvents();
            
            LOGGER.info("生物大逃杀模组已加载完成");
        } catch (Exception e) {
            LOGGER.error("生物大逃杀模组初始化失败", e);
        }
    }
    
    /**
     * 获取游戏管理器实例
     */
    public static GameManager getGameManager() {
        return GAME_MANAGER;
    }
    
    /**
     * 设置服务器实例
     */
    public static void setServer(MinecraftServer minecraftServer) {
        server = minecraftServer;
    }
    
    /**
     * 获取服务器实例
     */
    public static MinecraftServer getServer() {
        return server;
    }
} 