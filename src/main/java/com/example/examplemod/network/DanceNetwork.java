package com.example.examplemod.network;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.client.ClientDanceRenderer;
import com.example.examplemod.dance.DanceConfig;
import com.example.examplemod.dance.DanceSource;
import com.example.examplemod.music.DanceTrack;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/** 只允许服务端向客户端发送状态；客户端不能通过此通道伪造播放。 */
public final class DanceNetwork {
    private static final String VERSION = "1";
    private static final int MAX_SOURCES = 4096;
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(ExampleMod.MODID, "dance"), () -> VERSION, VERSION::equals, VERSION::equals);
    private DanceNetwork() { }

    public static void register() {
        CHANNEL.registerMessage(0, Snapshot.class, Snapshot::encode, Snapshot::decode, DanceNetwork::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void sendSnapshot(ServerPlayer player, List<DanceSource> sources) {
        double distance = (player.server.getPlayerList().getViewDistance() + 2) * 16.0 + DanceConfig.RADIUS.get();
        // 只发送玩家附近的数据，并限制最大包大小，避免大型服务器广播整个维度的唱片机。
        var nearby = sources.stream().filter(source -> {
            var pos = source.position();
            return Math.abs(pos.getX() + 0.5 - player.getX()) <= distance
                    && Math.abs(pos.getZ() + 0.5 - player.getZ()) <= distance;
        }).sorted(Comparator.comparingDouble(source -> source.position().distToCenterSqr(player.position())))
                .limit(MAX_SOURCES).toList();
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Snapshot(
                player.serverLevel().dimension().location(), player.serverLevel().getGameTime(),
                DanceConfig.RADIUS.get(), DanceTrack.CURRENT.bpm(), nearby));
    }

    private static void handle(Snapshot message, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientDanceRenderer.accept(message)));
        context.setPacketHandled(true);
    }

    public record Snapshot(ResourceLocation dimension, long serverTime, double radius, double bpm,
                           List<DanceSource> sources) {
        private void encode(FriendlyByteBuf buffer) {
            buffer.writeResourceLocation(dimension);
            buffer.writeLong(serverTime);
            buffer.writeDouble(radius);
            buffer.writeDouble(bpm);
            buffer.writeVarInt(sources.size());
            for (var source : sources) {
                buffer.writeBlockPos(source.position());
                buffer.writeLong(source.startedAt());
            }
        }

        private static Snapshot decode(FriendlyByteBuf buffer) {
            var dimension = buffer.readResourceLocation();
            long time = buffer.readLong();
            double radius = buffer.readDouble(), bpm = buffer.readDouble();
            int size = buffer.readVarInt();
            if (!Double.isFinite(radius) || radius < 1 || radius > 64
                    || !Double.isFinite(bpm) || bpm < 30 || bpm > 300 || size < 0 || size > MAX_SOURCES) {
                throw new IllegalArgumentException("无效的舞蹈同步数据");
            }
            var sources = new ArrayList<DanceSource>(size);
            for (int i = 0; i < size; i++) sources.add(new DanceSource(buffer.readBlockPos(), buffer.readLong()));
            return new Snapshot(dimension, time, radius, bpm, List.copyOf(sources));
        }
    }
}
