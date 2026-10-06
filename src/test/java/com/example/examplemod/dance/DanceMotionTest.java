package com.example.examplemod.dance;

/** 不依赖游戏启动的数学测试，可使用 Java 17 直接执行。 */
public final class DanceMotionTest {
    public static void main(String[] args) {
        for (double bpm : new double[]{30, 120, 300}) {
            double period = 1200.0 / bpm;
            for (int tick = 0; tick < 10000; tick++) {
                var motion = DanceMotion.sample(tick + 0.5, bpm);
                check(Float.isFinite(motion.turn()) && motion.turn() >= 0 && motion.turn() < 360, "旋转范围");
                check(Float.isFinite(motion.heightScale()) && motion.heightScale() >= 0.4499
                        && motion.heightScale() <= 1.5501, "伸缩范围");
            }
            close(DanceMotion.sample(0, bpm).heightScale(), 1, "开始时为正常高度");
            close(DanceMotion.sample(period / 4, bpm).heightScale(), 1.55, "每拍有拉长峰值");
            close(DanceMotion.sample(period * 3 / 4, bpm).heightScale(), 0.45, "每拍有压缩谷值");
            close(DanceMotion.sample(period, bpm).heightScale(), 1, "一拍伸缩循环");
            close(DanceMotion.sample(period * 4, bpm).turn(), 0, "四拍旋转一周");
            close(DanceMotion.sample(period, bpm).turn(), 90, "每拍自转四分之一周");
            var before = DanceMotion.sample(period * 4 - 0.0001, bpm);
            var after = DanceMotion.sample(period * 4 + 0.0001, bpm);
            close(before.heightScale(), after.heightScale(), "跨循环伸缩连续");
            close((float) Math.sin(Math.toRadians(before.turn())),
                    (float) Math.sin(Math.toRadians(after.turn())), "跨循环旋转连续");
        }
        check(DanceMotion.sample(-10, 120).equals(DanceMotion.sample(0, 120)), "负时间保护");
        System.out.println("PASS: 30000 个弹簧舞采样、伸缩峰谷、自转周期和连续性检查通过。");
    }
    private static void close(double actual, double expected, String name) {
        check(Math.abs(actual - expected) < 0.001, name);
    }
    private static void check(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
    }
}
