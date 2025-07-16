package com.killinggame.mod;

import com.killinggame.mod.commands.KillingGameCommand;
import com.killinggame.mod.events.KillingGameEvents;
import com.killinggame.mod.game.GameManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 杀戮游戏主模组类
 */
public class KillingGameMod implements ModInitializer {
    public static final String MOD_ID = "killinggame";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    
    // 游戏管理器实例
    public static final GameManager GAME_MANAGER = new GameManager();
    
    @Override
    public void onInitialize() {
        LOGGER.info("杀戮游戏模组初始化");
        
        // 注册指令
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            KillingGameCommand.register(dispatcher);
        });
        
        // 注册事件
        KillingGameEvents.registerEvents();
        
        LOGGER.info("杀戮游戏模组已加载完成");
    }
    
    /**
     * 获取游戏管理器实例
     */
    public static GameManager getGameManager() {
        return GAME_MANAGER;
    }
} 