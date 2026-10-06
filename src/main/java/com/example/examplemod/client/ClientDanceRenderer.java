package com.example.examplemod.client;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.dance.DanceMotion;
import com.example.examplemod.dance.DanceSource;
import com.example.examplemod.network.DanceNetwork;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.IdentityHashMap;
import java.util.Map;

/** 仅客户端加载：临时变换模型矩阵，绝不写入生物坐标、AI 或 NBT。 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class ClientDanceRenderer {
    private static DanceNetwork.Snapshot snapshot;
    private static long receivedAt;
    private static final Map<LivingEntity, SavedPose> SAVED = new IdentityHashMap<>();

    private record SavedPose(PoseStack.Pose target, Matrix4f position, Matrix3f normal) {
        private void restore() {
            target.pose().set(position);
            target.normal().set(normal);
        }
    }
    private ClientDanceRenderer() { }

    public static void accept(DanceNetwork.Snapshot message) {
        var level = Minecraft.getInstance().level;
        if (level != null && level.dimension().location().equals(message.dimension())) {
            snapshot = message;
            receivedAt = level.getGameTime();
        }
    }

    private static DanceSource nearest(Mob mob) {
        var level = Minecraft.getInstance().level;
        if (snapshot == null || level == null || !level.dimension().location().equals(snapshot.dimension())
                || level.getGameTime() - receivedAt > 100) return null;
        DanceSource nearest = null;
        double best = snapshot.radius() * snapshot.radius();
        for (var source : snapshot.sources()) {
            var pos = source.position();
            if (!level.hasChunkAt(pos)) continue;
            var state = level.getBlockState(pos);
            // 拔出或破坏后的方块更新可先于同步包到达，先做客户端兜底检查。
            if (!state.is(Blocks.JUKEBOX) || !state.getValue(JukeboxBlock.HAS_RECORD)) continue;
            double distance = pos.distToCenterSqr(mob.position());
            if (distance < best) { best = distance; nearest = source; }
        }
        return nearest;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void beforeRender(RenderLivingEvent.Pre<?, ?> event) {
        if (!(event.getEntity() instanceof Mob mob) || !mob.isAlive() || mob.isSleeping()
                || mob.isPassenger() || mob.isVehicle() || mob instanceof EnderDragon
                || mob instanceof WitherBoss || SAVED.containsKey(mob)) return;
        var source = nearest(mob);
        if (source == null) return;
        double elapsed = snapshot.serverTime() - source.startedAt()
                + mob.level().getGameTime() - receivedAt + event.getPartialTick();
        var motion = DanceMotion.sample(elapsed, snapshot.bpm());
        var pose = event.getPoseStack();
        // 不额外 push：若其他 Mod 在 Pre 后取消渲染，Post 不会触发，额外入栈会破坏后续渲染。
        // 保存当前矩阵，正常路径在 Post 恢复；取消路径由实体渲染器自身出栈，帧末清理引用。
        var original = pose.last();
        SAVED.put(mob, new SavedPose(original, new Matrix4f(original.pose()), new Matrix3f(original.normal())));
        // 实体渲染原点位于脚底；不平移、不倾斜，只旋转和竖直缩放，脚底保持贴地。
        // 不改模型宽度、实体真实位置和碰撞箱。
        pose.mulPose(Axis.YP.rotationDegrees(motion.turn()));
        pose.scale(1, motion.heightScale(), 1);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void afterRender(RenderLivingEvent.Post<?, ?> event) {
        var saved = SAVED.remove(event.getEntity());
        if (saved != null) saved.restore();
    }

    @SubscribeEvent
    public static void afterEntities(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) restoreRemaining();
    }

    private static void restoreRemaining() {
        SAVED.values().forEach(SavedPose::restore);
        SAVED.clear();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) { clear(); }

    @SubscribeEvent
    public static void onUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) clear();
    }

    private static void clear() {
        snapshot = null;
        restoreRemaining();
    }
}
