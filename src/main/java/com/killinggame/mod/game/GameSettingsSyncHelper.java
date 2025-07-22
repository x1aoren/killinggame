package com.killinggame.mod.game;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.client.MinecraftClient;

public class GameSettingsSyncHelper {
    public static final Identifier SYNC_SETTINGS_PACKET_ID = new Identifier("killinggame", "sync_settings");
    // 客户端本地显示用变量
    public static int clientMaxRounds = 8;
    public static int clientRoundTimeMinutes = 3;

    // 服务端：同步设置到所有玩家
    public static void syncGameSettingsToAll(MinecraftServer server, int maxRounds, int roundTimeMinutes) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            syncGameSettingsToPlayer(player, maxRounds, roundTimeMinutes);
        }
    }

    // 服务端：同步设置到单个玩家
    public static void syncGameSettingsToPlayer(ServerPlayerEntity player, int maxRounds, int roundTimeMinutes) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(maxRounds);
        buf.writeInt(roundTimeMinutes);
        ServerPlayNetworking.send(player, SYNC_SETTINGS_PACKET_ID, buf);
    }

    // 客户端：注册接收处理
    public static void registerClientReceiver() {
        ClientPlayNetworking.registerGlobalReceiver(SYNC_SETTINGS_PACKET_ID, (client, handler, buf, responseSender) -> {
            int maxRounds = buf.readInt();
            int roundTimeMinutes = buf.readInt();
            client.execute(() -> {
                clientMaxRounds = maxRounds;
                clientRoundTimeMinutes = roundTimeMinutes;
                // 可在此处触发界面刷新等操作
            });
        });
    }
} 