package com.example.examplemod.gametest;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.dance.DanceJukeboxHandler;
import com.example.examplemod.music.ModMusic;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.RecordItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** 运行 runGameTestServer，验证原版唱片机与舞蹈状态的联动；不需要音频或图形界面。 */
@GameTestHolder(ExampleMod.MODID)
@PrefixGameTestTemplate(false)
public final class DanceGameTests {
    private static final BlockPos FIRST = new BlockPos(0, 1, 0);
    private static final BlockPos SECOND = new BlockPos(2, 1, 2);

    private static JukeboxBlockEntity place(GameTestHelper helper, BlockPos position) {
        helper.setBlock(position, Blocks.JUKEBOX);
        return (JukeboxBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(position));
    }

    private static boolean tracked(GameTestHelper helper, BlockPos position) {
        return DanceJukeboxHandler.sources(helper.getLevel()).stream()
                .anyMatch(source -> source.position().equals(helper.absolutePos(position)));
    }

    @GameTest(template = "empty", templateNamespace = ExampleMod.MODID, timeoutTicks = 60)
    public static void insertionAndEjection(GameTestHelper helper) {
        var jukebox = place(helper, FIRST);
        jukebox.setFirstItem(new ItemStack(ModMusic.DANCE_DISC.get()));
        // 模拟非手动插入，验证原版 JUKEBOX_PLAY 事件能自动发现播放。
        helper.runAfterDelay(22, () -> {
            helper.assertTrue(tracked(helper, FIRST), "插入原创唱片后应开始舞蹈");
            jukebox.removeFirstItem();
            helper.assertTrue(!tracked(helper, FIRST), "取出唱片后应立即停止舞蹈");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", templateNamespace = ExampleMod.MODID, timeoutTicks = 60)
    public static void destroyedJukeboxStops(GameTestHelper helper) {
        var jukebox = place(helper, FIRST);
        jukebox.setFirstItem(new ItemStack(ModMusic.DANCE_DISC.get()));
        DanceJukeboxHandler.refresh(helper.getLevel(), helper.absolutePos(FIRST));
        helper.assertTrue(tracked(helper, FIRST), "原创唱片应被跟踪");
        helper.getLevel().destroyBlock(helper.absolutePos(FIRST), false);
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(!tracked(helper, FIRST), "破坏唱片机后必须清理播放状态");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", templateNamespace = ExampleMod.MODID, timeoutTicks = 60)
    public static void vanillaDiscDoesNotTriggerDance(GameTestHelper helper) {
        var jukebox = place(helper, FIRST);
        jukebox.setFirstItem(new ItemStack(Items.MUSIC_DISC_13));
        helper.runAfterDelay(22, () -> {
            helper.assertTrue(!tracked(helper, FIRST), "原版唱片不应触发专属舞蹈");
            jukebox.removeFirstItem();
            helper.succeed();
        });
    }

    @GameTest(template = "empty", templateNamespace = ExampleMod.MODID, timeoutTicks = 60)
    public static void naturalEndStopsWithoutEjecting(GameTestHelper helper) {
        var jukebox = place(helper, FIRST);
        jukebox.setFirstItem(new ItemStack(ModMusic.DANCE_DISC.get()));
        DanceJukeboxHandler.refresh(helper.getLevel(), helper.absolutePos(FIRST));
        var saved = jukebox.saveWithoutMetadata();
        saved.putLong("RecordStartTick", 0);
        saved.putLong("TickCount", ((RecordItem) ModMusic.DANCE_DISC.get()).getLengthInTicks() + 21L);
        jukebox.load(saved);
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(!jukebox.isRecordPlaying(), "原版唱片机应自然结束播放");
            helper.assertTrue(!jukebox.getFirstItem().isEmpty(), "自然结束时唱片仍在唱片机中");
            helper.assertTrue(!tracked(helper, FIRST), "不能只凭唱片仍在机器中就继续舞蹈");
            jukebox.removeFirstItem();
            helper.succeed();
        });
    }

    @GameTest(template = "empty", templateNamespace = ExampleMod.MODID, timeoutTicks = 60)
    public static void jukeboxesStopIndependently(GameTestHelper helper) {
        var first = place(helper, FIRST);
        var second = place(helper, SECOND);
        first.setFirstItem(new ItemStack(ModMusic.DANCE_DISC.get()));
        second.setFirstItem(new ItemStack(ModMusic.DANCE_DISC.get()));
        DanceJukeboxHandler.refresh(helper.getLevel(), helper.absolutePos(FIRST));
        DanceJukeboxHandler.refresh(helper.getLevel(), helper.absolutePos(SECOND));
        first.removeFirstItem();
        helper.assertTrue(!tracked(helper, FIRST), "第一台应停止");
        helper.assertTrue(tracked(helper, SECOND), "第二台不应被第一台的停止影响");
        second.removeFirstItem();
        helper.succeed();
    }
}
