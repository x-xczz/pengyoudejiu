package com.example.examplemod.music;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.dance.DanceJukeboxHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.RecordItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import java.util.List;

/** 独立管理唱片和声音，避免将所有功能堆在 Mod 入口类。 */
public final class ModMusic {
    private static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, ExampleMod.MODID);
    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, ExampleMod.MODID);
    public static final RegistryObject<SoundEvent> DANCE_SONG = SOUNDS.register("music_disc.dance_song",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(ExampleMod.MODID, "music_disc.dance_song")));
    public static final RegistryObject<Item> DANCE_DISC = ITEMS.register("dance_disc", DanceRecordItem::new);

    private ModMusic() { }

    public static void register(IEventBus bus) {
        SOUNDS.register(bus);
        ITEMS.register(bus);
    }

    private static final class DanceRecordItem extends RecordItem {
        private DanceRecordItem() {
            // Forge 1.20.1 的 Supplier 构造方法接收 tick，不是秒；20 tick = 1 秒。
            super(12, DANCE_SONG, new Item.Properties().stacksTo(1).rarity(Rarity.RARE),
                    DanceTrack.CURRENT.lengthTicks());
        }

        @Override
        public InteractionResult useOn(UseOnContext context) {
            InteractionResult result = super.useOn(context);
            if (result.consumesAction() && context.getLevel() instanceof ServerLevel level) {
                // 手动插入后立即同步；漏斗或其他方式插入由原版游戏事件兜底。
                DanceJukeboxHandler.refresh(level, context.getClickedPos());
            }
            return result;
        }

        @Override
        public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
            super.appendHoverText(stack, level, tooltip, flag);
            tooltip.add(Component.translatable("item.examplemod.dance_disc.hint").withStyle(ChatFormatting.AQUA));
            if (!DanceTrack.CURRENT.imported()) {
                tooltip.add(Component.translatable("item.examplemod.dance_disc.missing_audio")
                        .withStyle(ChatFormatting.YELLOW));
            }
        }
    }
}
