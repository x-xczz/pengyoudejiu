package com.example.examplemod.dance;

import com.example.examplemod.ExampleMod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.Comparator;
import java.util.EnumSet;

/** 服务端寻路目标：听到专属唱片后走近，不瞬移、不拉扯、不永久修改原有 AI。 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID)
public final class MusicAttractionGoal extends Goal {
    public static final double ATTRACTION_RADIUS = 32.0;
    private static final double ARRIVAL_DISTANCE_SQUARED = 2.5 * 2.5;
    private final PathfinderMob mob;
    private BlockPos source;
    private Path ownedPath;
    private long nextSearch;
    private long nextPathUpdate;
    private boolean failed;

    public MusicAttractionGoal(PathfinderMob mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel)
                || !(event.getEntity() instanceof PathfinderMob mob) || mob instanceof WitherBoss) return;
        // 同一个实体重新加入世界时不重复挂载；浮水、逃命等更高优先级行为仍优先。
        if (mob.goalSelector.getAvailableGoals().stream().noneMatch(goal -> goal.getGoal() instanceof MusicAttractionGoal)) {
            mob.goalSelector.addGoal(2, new MusicAttractionGoal(mob));
        }
    }

    private static boolean eligible(PathfinderMob mob) {
        return mob.isAlive() && !mob.isNoAi() && !mob.isSleeping() && !mob.isPassenger()
                && !mob.isVehicle() && !mob.isLeashed() && mob.getTarget() == null
                && !(mob instanceof WitherBoss)
                && !(mob instanceof TamableAnimal animal && animal.isOrderedToSit());
    }

    /** 只查询已跟踪的活动唱片机；使用球形距离，不遍历方块或强制加载区块。 */
    public static BlockPos nearestSource(PathfinderMob mob) {
        if (!(mob.level() instanceof ServerLevel level) || !eligible(mob)) return null;
        double radius = ATTRACTION_RADIUS;
        return DanceJukeboxHandler.sources(level).stream().map(DanceSource::position)
                .filter(pos -> pos.distToCenterSqr(mob.position()) < radius * radius)
                .filter(pos -> DanceJukeboxHandler.isDanceRecordPlaying(level, pos))
                .min(Comparator.comparingDouble(pos -> pos.distToCenterSqr(mob.position())))
                .orElse(null);
    }

    @Override
    public boolean canUse() {
        long now = mob.level().getGameTime();
        if (now < nextSearch) return false;
        nextSearch = now + 20;
        source = nearestSource(mob);
        failed = false;
        ownedPath = null;
        if (source == null) return false;
        if (arrived()) return true;
        // 目标允许落在唱片机旁边，不要求生物走进这个实心方块。
        ownedPath = createMusicPath();
        return ownedPath != null && ownedPath.canReach();
    }

    @Override
    public boolean canContinueToUse() {
        double radius = ATTRACTION_RADIUS;
        return source != null && !failed && eligible(mob)
                && source.distToCenterSqr(mob.position()) < radius * radius
                && mob.level() instanceof ServerLevel level
                && DanceJukeboxHandler.isDanceRecordPlaying(level, source);
    }

    @Override
    public void start() {
        // MOVE 标志已取得控制权，先取消被替代的闲逛路径。
        mob.getNavigation().stop();
        if (ownedPath != null) followPath(ownedPath);
        nextPathUpdate = mob.level().getGameTime() + 20;
    }

    @Override
    public boolean requiresUpdateEveryTick() { return true; }

    @Override
    public void tick() {
        if (source == null) return;
        long now = mob.level().getGameTime();
        if (now >= nextPathUpdate) {
            nextPathUpdate = now + 20;
            var nearest = nearestSource(mob);
            if (nearest == null) { failed = true; return; }
            source = nearest;
            if (!arrived()) {
                var path = createMusicPath();
                if (path == null || !path.canReach()) { failed = true; return; }
                followPath(path);
            }
        }
        mob.getLookControl().setLookAt(source.getX() + 0.5, source.getY() + 0.5, source.getZ() + 0.5);
        // 抵达后留出约 2.5 格空隙，避免所有生物硬挤到同一个实心方块上。
        if (arrived()) stopOwnedPath();
    }

    private Path createMusicPath() {
        // 三参数重载的最后一个值是本次寻路距离，不是到达误差。
        // 普通动物的 FOLLOW_RANGE 通常只有 16 格；只扩大本次搜索，不修改生物属性或其他 AI。
        // 额外留出 8 格绕路余量；Forge 导航区域只读取已加载区块，不强制生成远处地形。
        int searchRange = (int) Math.ceil(ATTRACTION_RADIUS) + 8;
        return mob.getNavigation().createPath(source, 2, searchRange);
    }

    private void followPath(Path path) {
        failed = !mob.getNavigation().moveTo(path, 1.0);
        // 导航器可能复用旧路径；记录它实际接受的路径，以确认本目标已发起导航。
        ownedPath = mob.getNavigation().getPath();
    }

    private boolean arrived() { return source.distToCenterSqr(mob.position()) <= ARRIVAL_DISTANCE_SQUARED; }

    private void stopOwnedPath() {
        // 方块变化会让原版导航器重新计算并替换 Path 对象（例如拆掉唱片机）。
        // 不能只比较对象身份，否则新路径会在音乐停止后继续执行。
        if (ownedPath != null) mob.getNavigation().stop();
        ownedPath = null;
    }

    @Override
    public void stop() {
        // GoalSelector 会先停止本目标，再启动接管 MOVE 的目标，因此不会清除后续 AI 的新路径。
        stopOwnedPath();
        source = null;
        failed = false;
        nextSearch = 0;
    }
}
