package rearth.transparentae2.network;

import java.util.List;
import java.util.function.Consumer;

import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import appeng.api.networking.IGridNode;
import rearth.transparentae2.Config;
import rearth.transparentae2.NetworkRenderService;

public final class TransferPathNetworking {
    private static final double VIEW_DISTANCE_SQUARED = 128.0 * 128.0;
    private static Consumer<TransferPathPayload> clientHandler = payload -> {
    };

    private TransferPathNetworking() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(
                TransferPathPayload.TYPE,
                TransferPathPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> clientHandler.accept(payload)));
    }

    public static void setClientHandler(Consumer<TransferPathPayload> handler) {
        clientHandler = handler;
    }

    public static void send(TransferPathPayload payload, IGridNode anchor) {
        if (!Config.ENABLE_TRANSFER_PATHS.getAsBoolean() || !NetworkRenderService.isEnabled(anchor)) {
            return;
        }

        MinecraftServer server = anchor.getLevel().getServer();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (isNearPath(player, payload.legs())) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    private static boolean isNearPath(ServerPlayer player, List<List<GlobalPos>> legs) {
        for (var leg : legs) {
            for (var point : leg) {
                if (point.dimension().equals(player.level().dimension())
                        && point.pos().distSqr(player.blockPosition()) <= VIEW_DISTANCE_SQUARED) {
                    return true;
                }
            }
        }
        return false;
    }
}
