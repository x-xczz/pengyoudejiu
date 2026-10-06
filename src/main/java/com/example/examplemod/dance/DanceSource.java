package com.example.examplemod.dance;

import net.minecraft.core.BlockPos;

/** 每台正在播放原创唱片的唱片机都有自己的播放起点。 */
public record DanceSource(BlockPos position, long startedAt) { }
