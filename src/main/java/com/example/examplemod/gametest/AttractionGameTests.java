package com.example.examplemod.gametest;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.dance.DanceJukeboxHandler;
import com.example.examplemod.dance.MusicAttractionGoal;
import com.example.examplemod.music.ModMusic;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import java.util.EnumSet;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.RecordItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** 用真实实体 tick 与寻路验证吸引；每项测试独立分批，避免 32 格吸引范围互相干扰。 */
@GameTestHolder(ExampleMod.MODID)
@PrefixGameTestTemplate(false)
public final class AttractionGameTests {
    private static final BlockPos FIRST = new BlockPos(2, 1, 5);
    private static final BlockPos SECOND = new BlockPos(26, 1, 5);

    private static JukeboxBlockEntity play(GameTestHelper helper, BlockPos position) {
        if (position.equals(FIRST)) {
            // 显式铺设地板并校验，避免测试世界残留或放置阶段移除模板地板导致从墙底绕行。
            for (int x = 0; x < 40; x++) {
                for (int z = 0; z < 40; z++) helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
        helper.setBlock(position, Blocks.JUKEBOX);
        var jukebox = (JukeboxBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(position));
        jukebox.setFirstItem(new ItemStack(ModMusic.DANCE_DISC.get()));
        DanceJukeboxHandler.refresh(helper.getLevel(), helper.absolutePos(position));
        return jukebox;
    }

    private static Pig pig(GameTestHelper helper, float x, float z) {
        helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(new BlockPos((int) x, 0, (int) z)))
                .is(Blocks.STONE), "测试猪脚下应有完整地板");
        var pig = helper.spawn(EntityType.PIG, x, 1, z);
        helper.assertTrue(pig.goalSelector.getAvailableGoals().stream()
                .filter(goal -> goal.getGoal() instanceof MusicAttractionGoal).count() == 1,
                "服务端新生物应且只应挂载一个音乐吸引目标");
        // 排除闲逛随机性，只保留自动挂载的吸引目标；仍使用真实 AI 和导航 tick。
        pig.goalSelector.removeAllGoals(goal -> !(goal instanceof MusicAttractionGoal));
        return pig;
    }

    private static boolean running(PathfinderMob mob) {
        return mob.goalSelector.getAvailableGoals().stream()
                .anyMatch(goal -> goal.getGoal() instanceof MusicAttractionGoal && goal.isRunning());
    }

    @GameTest(batch = "music_attraction_walksCloserAndStopsWhenEjected", template = "attraction_arena", templateNamespace = ExampleMod.MODID, timeoutTicks = 100)
    public static void walksCloserAndStopsWhenEjected(GameTestHelper helper) {
        var jukebox = play(helper, FIRST);
        var pig = pig(helper, 14.5f, 5.5f);
        double original = helper.absolutePos(FIRST).distToCenterSqr(pig.position());
        helper.runAfterDelay(50, () -> {
            helper.assertTrue(running(pig), "12 格外的猪应进入吸引目标");
            helper.assertTrue(helper.absolutePos(FIRST).distToCenterSqr(pig.position()) < original - 20,
                    "生物应通过导航实际走近，不只是被标记为吸引");
            jukebox.removeFirstItem();
            helper.runAfterDelay(5, () -> {
                helper.assertTrue(!running(pig), "取出唱片后应释放吸引目标");
                helper.assertTrue(pig.getNavigation().isDone(), "取出唱片后应取消吸引产生的路径");
                pig.discard();
                helper.succeed();
            });
        });
    }

    @GameTest(batch = "music_attraction_sphereRangeAndBoundary", template = "attraction_arena", templateNamespace = ExampleMod.MODID, timeoutTicks = 60)
    public static void sphereRangeAndBoundary(GameTestHelper helper) {
        var jukebox = play(helper, FIRST);
        var outside = pig(helper, 35.5f, 5.5f);
        var diagonal = pig(helper, 32.5f, 17.5f);
        var edge = pig(helper, 34.49f, 5.5f);
        // 脚底高度对齐中心，检查 32 格吸引边界；不把立方体角落算进球形半径。
        edge.setPos(edge.getX(), edge.getY() + 0.5, edge.getZ());
        helper.assertTrue(MusicAttractionGoal.nearestSource(edge) != null, "32 格以内应在范围内");
        helper.assertTrue(MusicAttractionGoal.nearestSource(outside) == null, "33 格不应吸引");
        helper.assertTrue(MusicAttractionGoal.nearestSource(diagonal) == null, "球形范围外的斜角不应吸引");
        helper.assertTrue(MusicAttractionGoal.ATTRACTION_RADIUS == 32.0, "吸引半径应固定为 32 格");
        edge.setPos(helper.absolutePos(FIRST).getX() + 32.5, edge.getY(), edge.getZ());
        helper.assertTrue(MusicAttractionGoal.nearestSource(edge) == null, "到达 32 格边界，不再吸引");
        edge.discard();
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(!running(outside) && !running(diagonal), "范围外不能启动吸引寻路");
            outside.discard(); diagonal.discard(); jukebox.removeFirstItem(); helper.succeed();
        });
    }

    @GameTest(batch = "music_attraction_choosesNearestAndSwitchesAfterStop", template = "attraction_arena", templateNamespace = ExampleMod.MODID, timeoutTicks = 80)
    public static void choosesNearestAndSwitchesAfterStop(GameTestHelper helper) {
        var first = play(helper, FIRST);
        var second = play(helper, SECOND);
        var pig = pig(helper, 16.5f, 5.5f);
        helper.assertTrue(helper.absolutePos(SECOND).equals(MusicAttractionGoal.nearestSource(pig)), "应选择最近的唱片机");
        helper.runAfterDelay(26, () -> {
            helper.assertTrue(running(pig), "最近的唱片机应开始吸引");
            second.removeFirstItem();
            double x = pig.getX();
            helper.assertTrue(helper.absolutePos(FIRST).equals(MusicAttractionGoal.nearestSource(pig)),
                    "最近的停止后应选择仍在范围内播放的另一台");
            helper.runAfterDelay(30, () -> {
                helper.assertTrue(running(pig) && pig.getX() < x, "应改变方向，走向仍在播放的唱片机");
                first.removeFirstItem(); pig.discard(); helper.succeed();
            });
        });
    }

    @GameTest(batch = "music_attraction_destructionReleasesAttraction", template = "attraction_arena", templateNamespace = ExampleMod.MODID, timeoutTicks = 60)
    public static void destructionReleasesAttraction(GameTestHelper helper) {
        play(helper, FIRST);
        var pig = pig(helper, 14.5f, 5.5f);
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(running(pig), "应先开始吸引");
            helper.getLevel().destroyBlock(helper.absolutePos(FIRST), false);
            helper.runAfterDelay(5, () -> {
                helper.assertTrue(!running(pig) && pig.getNavigation().isDone(), "破坏后必须释放目标和路径: running=" + running(pig) + ", source=" + MusicAttractionGoal.nearestSource(pig) + ", original=" + helper.absolutePos(FIRST) + ", pig=" + pig.position());
                pig.discard(); helper.succeed();
            });
        });
    }

    @GameTest(batch = "music_attraction_naturalEndReleasesAttraction", template = "attraction_arena", templateNamespace = ExampleMod.MODID, timeoutTicks = 60)
    public static void naturalEndReleasesAttraction(GameTestHelper helper) {
        var jukebox = play(helper, FIRST);
        var pig = pig(helper, 14.5f, 5.5f);
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(running(pig), "应先开始吸引");
            var saved = jukebox.saveWithoutMetadata();
            saved.putLong("RecordStartTick", 0);
            saved.putLong("TickCount", ((RecordItem) ModMusic.DANCE_DISC.get()).getLengthInTicks() + 21L);
            jukebox.load(saved);
            helper.runAfterDelay(6, () -> {
                helper.assertTrue(!running(pig) && pig.getNavigation().isDone(), "自然结束后必须取消吸引");
                helper.assertTrue(!jukebox.getFirstItem().isEmpty(), "唱片留在机器里也不能继续吸引");
                pig.discard(); jukebox.removeFirstItem(); helper.succeed();
            });
        });
    }

    @GameTest(batch = "music_attraction_vanillaRecordDoesNotAttract", template = "attraction_arena", templateNamespace = ExampleMod.MODID, timeoutTicks = 60)
    public static void vanillaRecordDoesNotAttract(GameTestHelper helper) {
        var jukebox = play(helper, FIRST);
        jukebox.removeFirstItem();
        jukebox.setFirstItem(new ItemStack(Items.MUSIC_DISC_13));
        var pig = pig(helper, 14.5f, 5.5f);
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(!running(pig) && MusicAttractionGoal.nearestSource(pig) == null,
                    "原版唱片不应吸引生物");
            pig.discard(); jukebox.removeFirstItem(); helper.succeed();
        });
    }

    @GameTest(batch = "music_attraction_respectsDisabledAiSittingAndCombat", template = "attraction_arena", templateNamespace = ExampleMod.MODID, timeoutTicks = 60)
    public static void respectsDisabledAiSittingAndCombat(GameTestHelper helper) {
        var jukebox = play(helper, FIRST);
        var pig = pig(helper, 14.5f, 5.5f);
        pig.setNoAi(true);
        helper.assertTrue(MusicAttractionGoal.nearestSource(pig) == null, "NoAI 生物不应被吸引");
        pig.setNoAi(false);
        var wolf = helper.spawn(EntityType.WOLF, 12.5f, 1, 7.5f);
        wolf.setOrderedToSit(true);
        helper.assertTrue(MusicAttractionGoal.nearestSource(wolf) == null, "命令坐下的宠物不应被拉走");
        pig.setTarget(wolf);
        helper.assertTrue(MusicAttractionGoal.nearestSource(pig) == null, "已有战斗目标时不抢占行为");
        pig.setTarget(null);
        pig.startRiding(wolf, true);
        helper.assertTrue(MusicAttractionGoal.nearestSource(pig) == null, "骑乘中的生物不应被吸引");
        helper.assertTrue(MusicAttractionGoal.nearestSource(wolf) == null, "被骑乘的生物不应被吸引");
        pig.discard(); wolf.discard(); jukebox.removeFirstItem(); helper.succeed();
    }

    @GameTest(batch = "music_attraction_arrivesThenRestoresOtherGoals", template = "attraction_arena", templateNamespace = ExampleMod.MODID, timeoutTicks = 200)
    public static void arrivesThenRestoresOtherGoals(GameTestHelper helper) {
        var jukebox = play(helper, FIRST);
        var pig = pig(helper, 10.5f, 5.5f);
        int[] ordinaryTicks = {0};
        pig.goalSelector.addGoal(5, new Goal() {
            { setFlags(EnumSet.of(Flag.MOVE)); }
            @Override public boolean canUse() { return true; }
            @Override public void tick() { ordinaryTicks[0]++; }
        });
        helper.runAfterDelay(150, () -> {
            helper.assertTrue(running(pig), "抵达后应继续停留听歌，而非立即恢复闲逛");
            helper.assertTrue(helper.absolutePos(FIRST).distToCenterSqr(pig.position()) <= 6.5,
                    "应到达唱片机约 2.5 格范围内");
            helper.assertTrue(pig.getNavigation().isDone(), "抵达后应停止推进，不持续挤向方块");
            int before = ordinaryTicks[0];
            jukebox.removeFirstItem();
            helper.runAfterDelay(8, () -> {
                helper.assertTrue(!running(pig), "停播后应释放吸引");
                helper.assertTrue(ordinaryTicks[0] > before, "低优先级的原有 AI 应恢复执行");
                pig.discard(); helper.succeed();
            });
        });
    }

    @GameTest(batch = "music_attraction_blockedPathDoesNotPullThroughWalls", template = "attraction_arena", templateNamespace = ExampleMod.MODID, timeoutTicks = 60)
    public static void blockedPathDoesNotPullThroughWalls(GameTestHelper helper) {
        var jukebox = play(helper, FIRST);
        // 四面实墙围住测试猪，不能靠跳跃、绕路或攀爬到达唱片机。
        for (int y = 1; y <= 4; y++) {
            for (int x = 13; x <= 15; x++) {
                for (int z = 4; z <= 6; z++) {
                    if (x != 14 || z != 5) helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
                }
            }
        }
        var pig = pig(helper, 14.5f, 5.5f);
        var initial = pig.position();
        helper.runAfterDelay(35, () -> {
            helper.assertTrue(!running(pig), "无可达路径时不能占用其他 AI 的移动目标: source=" + MusicAttractionGoal.nearestSource(pig) + ", original=" + helper.absolutePos(FIRST) + ", pig=" + pig.position());
            helper.assertTrue(pig.position().distanceToSqr(initial) < 0.25, "不能穿墙或瞬移拉走生物");
            pig.discard(); jukebox.removeFirstItem(); helper.succeed();
        });
    }

    @GameTest(batch = "music_attraction_walksFromThirtyBlocksWithoutChangingAttributes", template = "attraction_arena", templateNamespace = ExampleMod.MODID, timeoutTicks = 130)
    public static void walksFromThirtyBlocksWithoutChangingAttributes(GameTestHelper helper) {
        var jukebox = play(helper, FIRST);
        var pig = pig(helper, 32.5f, 5.5f);
        double followRange = pig.getAttributeValue(Attributes.FOLLOW_RANGE);
        double original = pig.getX();
        helper.assertTrue(followRange < 30, "使用默认寻路距离不足 30 格的普通猪验证");
        helper.runAfterDelay(80, () -> {
            helper.assertTrue(running(pig), "30 格外应能启动并持续寻路，不能只扩大检测范围");
            helper.assertTrue(pig.getX() < original - 5, "应通过真实导航走近至少 5 格");
            helper.assertTrue(pig.getAttributeValue(Attributes.FOLLOW_RANGE) == followRange,
                    "音乐不能修改原本的追踪距离属性");
            jukebox.removeFirstItem();
            helper.runAfterDelay(6, () -> {
                helper.assertTrue(!running(pig) && pig.getNavigation().isDone(), "远距离吸引也必须随停播结束");
                pig.discard(); helper.succeed();
            });
        });
    }

    @GameTest(batch = "music_attraction_leavingAttractionRangeReleasesGoal", template = "attraction_arena", templateNamespace = ExampleMod.MODID, timeoutTicks = 70)
    public static void leavingAttractionRangeReleasesGoal(GameTestHelper helper) {
        var jukebox = play(helper, FIRST);
        var pig = pig(helper, 14.5f, 5.5f);
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(running(pig), "应先开始吸引");
            // 此处仅用传送构造离开范围的测试条件，远距离主动行走另有独立测试。
            pig.setPos(helper.absolutePos(FIRST).getX() + 33.5, pig.getY(), pig.getZ());
            helper.runAfterDelay(6, () -> {
                helper.assertTrue(!running(pig) && pig.getNavigation().isDone(), "离开吸引范围后应释放目标与路径");
                pig.discard(); jukebox.removeFirstItem(); helper.succeed();
            });
        });
    }

}
