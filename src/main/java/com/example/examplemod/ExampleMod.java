package com.example.examplemod;

import com.example.examplemod.dance.DanceConfig;
import com.example.examplemod.music.ModMusic;
import com.example.examplemod.network.DanceNetwork;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** Mod 入口：只接入原创唱片、舞蹈配置、网络同步和创造模式分页。 */
@Mod(ExampleMod.MODID)
public class ExampleMod {
    // 保留内部 ID，避免改变已有唱片、声音资源和存档中的注册名称。
    public static final String MODID = "examplemod";
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    // 保留原分页 ID 和唱片入口；仅将已移除的苹果糖图标替换为唱片。
    public static final RegistryObject<CreativeModeTab> MUSIC_TAB = CREATIVE_MODE_TABS.register("example_tab",
            () -> CreativeModeTab.builder()
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .title(Component.literal("pengyoudejiu"))
                    .icon(() -> ModMusic.DANCE_DISC.get().getDefaultInstance())
                    .displayItems((parameters, output) -> output.accept(ModMusic.DANCE_DISC.get()))
                    .build());

    public ExampleMod(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();

        // 唱片专属注册、配置文件名与同步通道保持原样。
        ModMusic.register(modEventBus);
        DanceNetwork.register();
        context.registerConfig(ModConfig.Type.SERVER, DanceConfig.SPEC, "examplemod-dance-server.toml");
        CREATIVE_MODE_TABS.register(modEventBus);
        modEventBus.addListener(this::addCreative);
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        // 保留唱片在原版“工具与实用物品”分页中的入口。
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ModMusic.DANCE_DISC);
        }
    }
}
