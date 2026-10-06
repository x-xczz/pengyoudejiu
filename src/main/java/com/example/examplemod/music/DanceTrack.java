package com.example.examplemod.music;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** 原创音乐的元数据；导入脚本会更新资源文件，不需要修改 Java 中的时长。 */
public record DanceTrack(int lengthTicks, double bpm, boolean imported) {
    public static final DanceTrack CURRENT = load();

    private static DanceTrack load() {
        try (var stream = DanceTrack.class.getResourceAsStream("/dance_track.json")) {
            if (stream == null) throw new IllegalStateException("缺少 dance_track.json");
            var json = new Gson().fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8),
                    com.google.gson.JsonObject.class);
            double seconds = json.get("durationSeconds").getAsDouble();
            double bpm = json.get("bpm").getAsDouble();
            if (!Double.isFinite(seconds) || seconds <= 0 || seconds > 86400
                    || !Double.isFinite(bpm) || bpm < 30 || bpm > 300) {
                throw new IllegalArgumentException("音乐时长或 BPM 超出支持范围");
            }
            return new DanceTrack((int) Math.ceil(seconds * 20), bpm, json.get("imported").getAsBoolean());
        } catch (Exception exception) {
            throw new IllegalStateException("无法读取原创唱片元数据", exception);
        }
    }
}
