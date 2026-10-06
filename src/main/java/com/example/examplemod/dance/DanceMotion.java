package com.example.examplemod.dance;

/** 脚底固定的弹簧舞：仅沿竖直方向反复拉伸、压缩，同时绕竖直轴自转。 */
public record DanceMotion(float turn, float heightScale) {
    public static DanceMotion sample(double ticks, double bpm) {
        double beats = Math.max(0, ticks) * bpm / 1200.0;
        double phase = beats * Math.PI * 2;
        // 每拍完成一次伸缩，高度在原高的 45%～155% 之间；四拍自转一周。
        // 从正常高度开始，不加入后仰、侧摆或整体上下位移。
        return new DanceMotion((float) ((beats * 90) % 360),
                (float) (1 + 0.55 * Math.sin(phase)));
    }
}
