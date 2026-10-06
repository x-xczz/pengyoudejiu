package com.example.examplemod.gametest;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.music.ModMusic;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.List;

/** 独立验证示例内容的移除，不修改原有唱片和吸引测试。 */
@GameTestHolder(ExampleMod.MODID)
@PrefixGameTestTemplate(false)
public final class CleanupGameTests {
    @GameTest(template = "empty", templateNamespace = ExampleMod.MODID, timeoutTicks = 20)
    public static void onlyMusicDiscRemainsRegistered(GameTestHelper helper) {
        var items = ForgeRegistries.ITEMS.getKeys().stream()
                .filter(id -> id.getNamespace().equals(ExampleMod.MODID)).toList();
        helper.assertTrue(items.equals(List.of(new ResourceLocation(ExampleMod.MODID, "dance_disc"))),
                "本 Mod 应只注册原有唱片，不再注册苹果糖或方块物品");
        helper.assertTrue(ForgeRegistries.BLOCKS.getKeys().stream()
                .noneMatch(id -> id.getNamespace().equals(ExampleMod.MODID)), "不再注册示例方块");
        helper.assertTrue(ForgeRegistries.SOUND_EVENTS.containsKey(
                new ResourceLocation(ExampleMod.MODID, "music_disc.dance_song")), "原有唱片声音必须仍然注册");
        helper.assertTrue(ExampleMod.MUSIC_TAB.get().getIconItem().is(ModMusic.DANCE_DISC.get()),
                "创造模式分页不能依赖已删除的苹果糖，应使用原有唱片作为图标");
        helper.succeed();
    }
}
