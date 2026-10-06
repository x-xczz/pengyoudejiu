package com.example.examplemod.dance;

import net.minecraftforge.common.ForgeConfigSpec;

/** 舞蹈半径可配置；吸引半径固定为 32 格。 */
public final class DanceConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.DoubleValue RADIUS;
    static {
        var builder = new ForgeConfigSpec.Builder();
        RADIUS = builder.comment("原创唱片让附近生物跳舞的球形半径，单位为方块。")
                .defineInRange("danceRadius", 8.0, 1.0, 64.0);
        // 不再定义 attractionRadius，避免旧存档中的 16 格配置覆盖新的 32 格吸引范围。
        SPEC = builder.build();
    }
    private DanceConfig() { }
}
