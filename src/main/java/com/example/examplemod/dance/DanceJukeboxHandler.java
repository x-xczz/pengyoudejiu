package com.example.examplemod.dance;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.music.ModMusic;
import com.example.examplemod.network.DanceNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.VanillaGameEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 服务端只跟踪已播放的唱片机，不扫描世界、不冻结生物，也不强制加载区块。 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID)
public final class DanceJukeboxHandler {
    private static final Map<ServerLevel, Map<BlockPos, DanceSource>> ACTIVE = new HashMap<>();
    private DanceJukeboxHandler() { }

    public static boolean isDanceRecordPlaying(ServerLevel level, BlockPos position) {
        return level.hasChunkAt(position)
                && level.getBlockEntity(position) instanceof JukeboxBlockEntity jukebox
                && jukebox.isRecordPlaying()
                && jukebox.getFirstItem().is(ModMusic.DANCE_DISC.get());
    }

    public static List<DanceSource> sources(ServerLevel level) {
        var entries = ACTIVE.get(level);
        return entries == null ? List.of() : List.copyOf(entries.values());
    }

    public static void refresh(ServerLevel level, BlockPos position) {
        if (!isDanceRecordPlaying(level, position)) {
            stop(level, position);
            return;
        }
        var entries = ACTIVE.computeIfAbsent(level, ignored -> new HashMap<>());
        var immutable = position.immutable();
        if (!entries.containsKey(immutable)) {
            entries.put(immutable, new DanceSource(immutable, level.getGameTime()));
            broadcast(level);
        }
    }

    private static void stop(ServerLevel level, BlockPos position) {
        var entries = ACTIVE.get(level);
        if (entries != null && entries.remove(position) != null) broadcast(level);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onGameEvent(VanillaGameEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        BlockPos position = BlockPos.containing(event.getEventPosition());
        if (event.getVanillaEvent() == GameEvent.JUKEBOX_PLAY) refresh(level, position);
        if (event.getVanillaEvent() == GameEvent.JUKEBOX_STOP_PLAY) stop(level, position);
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) return;
        var entries = ACTIVE.get(level);
        boolean changed = entries != null && entries.entrySet().removeIf(
                entry -> !isDanceRecordPlaying(level, entry.getKey()));
        // 每 tick 检查停止；定期完整同步让新加入、切换维度、走近的玩家恢复正确状态。
        if (changed || level.getGameTime() % 40 == 0) broadcast(level);
    }

    private static void broadcast(ServerLevel level) {
        for (ServerPlayer player : level.players()) DanceNetwork.sendSnapshot(player, sources(level));
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DanceNetwork.sendSnapshot(player, sources(player.serverLevel()));
        }
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DanceNetwork.sendSnapshot(player, sources(player.serverLevel()));
        }
    }

    @SubscribeEvent
    public static void onUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) ACTIVE.remove(level);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) { ACTIVE.clear(); }
}
