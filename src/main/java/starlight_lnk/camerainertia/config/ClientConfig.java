package starlight_lnk.camerainertia.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class ClientConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue ENABLED;

    public static final ForgeConfigSpec.BooleanValue MOTION_BLUR_ENABLED;
    public static final ForgeConfigSpec.DoubleValue MOTION_BLUR_SENSITIVITY;
    public static final ForgeConfigSpec.DoubleValue MOTION_BLUR_MAX_INTENSITY;
    public static final ForgeConfigSpec.DoubleValue MOTION_BLUR_SMOOTHING;

    public static final ForgeConfigSpec.DoubleValue MOTION_BLUR_DEADZONE;
    public static final ForgeConfigSpec.DoubleValue MOTION_BLUR_FULL_MOTION;
    public static final ForgeConfigSpec.DoubleValue MOTION_BLUR_CURVE_POWER;

    public static final ForgeConfigSpec.DoubleValue MOTION_BLUR_ATTACK_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue MOTION_BLUR_RELEASE_MULTIPLIER;

    public static final ForgeConfigSpec.BooleanValue MOTION_BLUR_OPPOSITE_SIDE;

    public static final ForgeConfigSpec.DoubleValue MOTION_BLUR_SIDE_START;
    public static final ForgeConfigSpec.DoubleValue MOTION_BLUR_SIDE_END;
    public static final ForgeConfigSpec.DoubleValue MOTION_BLUR_EDGE_POWER;
    public static final ForgeConfigSpec.DoubleValue MOTION_BLUR_PIXELS;
    public static final ForgeConfigSpec.DoubleValue MOTION_BLUR_CHROMA_PIXELS;

    public static final ForgeConfigSpec.BooleanValue CAMERA_ROLL_TURN_ENABLED;
    public static final ForgeConfigSpec.BooleanValue CAMERA_ROLL_STRAFE_ENABLED;

    // === Perspective transition (F5) ===
    public static final ForgeConfigSpec.BooleanValue PERSPECTIVE_TRANSITION_ENABLED;
    public static final ForgeConfigSpec.IntValue     PERSPECTIVE_TRANSITION_DURATION;

    // === Vehicle inertia ===
    public static final ForgeConfigSpec.BooleanValue VEHICLE_INERTIA_ENABLED;
    public static final ForgeConfigSpec.DoubleValue  VEHICLE_INERTIA_STRENGTH;
    public static final ForgeConfigSpec.BooleanValue VEHICLE_BOAT_WAVES;
    public static final ForgeConfigSpec.BooleanValue VEHICLE_BOAT_ROWING;
    public static final ForgeConfigSpec.DoubleValue  VEHICLE_BOAT_ROWING_STRENGTH;

    // === Mining inertia ===
    public static final ForgeConfigSpec.BooleanValue MINING_INERTIA_ENABLED;
    public static final ForgeConfigSpec.DoubleValue  MINING_INERTIA_STRENGTH;

    // === Trident zoom ===
    public static final ForgeConfigSpec.BooleanValue TRIDENT_ZOOM_ENABLED;
    public static final ForgeConfigSpec.DoubleValue  TRIDENT_ZOOM_STRENGTH;

    // === Fall shake ===
    public static final ForgeConfigSpec.BooleanValue FALL_SHAKE_ENABLED;
    public static final ForgeConfigSpec.DoubleValue  FALL_SHAKE_STRENGTH;
    public static final ForgeConfigSpec.IntValue     FALL_SHAKE_THRESHOLD_TICKS;

    // === Fall blur ===
    public static final ForgeConfigSpec.BooleanValue FALL_BLUR_ENABLED;
    public static final ForgeConfigSpec.DoubleValue  FALL_BLUR_STRENGTH;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.push("general");

        ENABLED = builder
                .comment("Master switch for all Camera Inertia client effects.")
                .define("enabled", true);

        CAMERA_ROLL_TURN_ENABLED = builder
                .comment("Enable camera roll when turning left or right.")
                .define("cameraRollTurnEnabled", true);

        CAMERA_ROLL_STRAFE_ENABLED = builder
                .comment("Enable camera roll when moving left or right.")
                .define("cameraRollStrafeEnabled", true);

        PERSPECTIVE_TRANSITION_ENABLED = builder
                .comment("Enable smooth animated transition when switching camera perspective (F5).",
                        "When disabled, camera switches instantly like in vanilla.")
                .define("perspectiveTransitionEnabled", true);

        PERSPECTIVE_TRANSITION_DURATION = builder
                .comment("Duration of the perspective transition animation in ticks.",
                        "20 ticks = 1 second. Default 5 (~0.25s). Range: 2-30.",
                        "Lower = snappier animation. Combined with easeOutExpo curve",
                        "this gives a lively 'camera shoots out of head' feel.")
                .defineInRange("perspectiveTransitionDuration", 5, 2, 30);

        builder.pop();

        builder.push("motion_blur");

        MOTION_BLUR_ENABLED = builder
                .comment("Enable side motion blur when camera turns left or right.")
                .define("enabled", true);

        MOTION_BLUR_SENSITIVITY = builder
                .comment("How strongly camera yaw movement affects blur intensity.")
                .defineInRange("sensitivity", 1.0, 0.0, 10.0);

        // 🔧 Усилено: 0.75 → 1.0 (потолок интенсивности)
        MOTION_BLUR_MAX_INTENSITY = builder
                .comment("Maximum blur intensity passed to shader.")
                .defineInRange("maxIntensity", 1.0, 0.0, 1.0);

        MOTION_BLUR_SMOOTHING = builder
                .comment("Base smoothing for blur intensity changes.")
                .defineInRange("smoothing", 0.22, 0.01, 1.0);

        MOTION_BLUR_DEADZONE = builder
                .comment("Yaw motion below this value will not trigger blur.")
                .defineInRange("deadzone", 1.75, 0.0, 90.0);

        // 🔧 Усилено: 18.0 → 14.0 (блюр раньше доходит до максимума)
        MOTION_BLUR_FULL_MOTION = builder
                .comment("Yaw motion value at which blur reaches maximum intensity.")
                .defineInRange("fullBlurMotion", 14.0, 0.1, 180.0);

        // 🔧 Усилено: 2.6 → 2.1 (более плавная, менее «избирательная» кривая)
        MOTION_BLUR_CURVE_POWER = builder
                .comment("Intensity curve power. Higher values make blur appear only on faster turns.")
                .defineInRange("curvePower", 2.1, 0.1, 8.0);

        MOTION_BLUR_ATTACK_MULTIPLIER = builder
                .comment("Multiplier for how quickly blur appears.")
                .defineInRange("attackSmoothingMultiplier", 1.5, 0.1, 5.0);

        MOTION_BLUR_RELEASE_MULTIPLIER = builder
                .comment("Multiplier for how quickly blur disappears.")
                .defineInRange("releaseSmoothingMultiplier", 2.0, 0.1, 5.0);

        MOTION_BLUR_OPPOSITE_SIDE = builder
                .comment("If true, blur appears on the opposite side of camera turn.")
                .define("oppositeSide", true);

        builder.push("shader");

        // 🔧 Усилено: 0.18 → 0.10 (маска шире, блюр захватывает больше экрана)
        MOTION_BLUR_SIDE_START = builder
                .comment("Where side blur mask starts. Lower = blur covers more of the screen.")
                .defineInRange("sideStart", 0.10, 0.0, 1.0);

        MOTION_BLUR_SIDE_END = builder
                .comment("Where side blur mask reaches full strength.")
                .defineInRange("sideEnd", 0.98, 0.0, 1.0);

        MOTION_BLUR_EDGE_POWER = builder
                .comment("Side mask curve power. Higher = stronger concentration near screen edge.")
                .defineInRange("edgePower", 1.45, 0.1, 8.0);

        // 🔧 Усилено: 84.0 → 126.0 (+50% радиус размытия)
        // Также увеличен верхний предел до 500 для будущего регулирования
        MOTION_BLUR_PIXELS = builder
                .comment("Maximum blur radius in pixels.")
                .defineInRange("blurPixels", 126.0, 0.0, 500.0);

        // 🔧 Усилено: 3.0 → 4.5 (+50% хроматической аберрации)
        // Верхний предел поднят до 80
        MOTION_BLUR_CHROMA_PIXELS = builder
                .comment("Chromatic aberration offset in pixels.")
                .defineInRange("chromaPixels", 4.5, 0.0, 80.0);

        builder.pop(); // shader
        builder.pop(); // motion_blur

        // ===== VEHICLE INERTIA =====
        builder.push("vehicle_inertia");

        VEHICLE_INERTIA_ENABLED = builder
                .comment("Enable camera inertia when riding boats, horses, camels, pigs, minecarts, etc.")
                .define("enabled", true);

        VEHICLE_INERTIA_STRENGTH = builder
                .comment("Overall strength multiplier for vehicle inertia (1.0 = default, 0.0 = off, 2.0 = double).")
                .defineInRange("strength", 1.0, 0.0, 2.0);

        VEHICLE_BOAT_WAVES = builder
                .comment("Enable continuous wave-like rocking while sitting in a boat (even when not moving).")
                .define("boatWaves", true);

        VEHICLE_BOAT_ROWING = builder
                .comment("Enable rowing rhythm effect when boat is moving (camera nods in sync with paddles).")
                .define("boatRowing", true);

        VEHICLE_BOAT_ROWING_STRENGTH = builder
                .comment("Rowing effect strength multiplier (1.0 = default, 0.5 = subtle, 2.0 = exaggerated).")
                .defineInRange("boatRowingStrength", 1.0, 0.0, 3.0);

        builder.pop(); // vehicle_inertia

        // ===== MINING INERTIA =====
        builder.push("mining_inertia");

        MINING_INERTIA_ENABLED = builder
                .comment("Enable camera kick when mining blocks with a pickaxe (feels like real strikes).")
                .define("enabled", true);

        MINING_INERTIA_STRENGTH = builder
                .comment("Mining kick strength multiplier (1.0 = default, 0.5 = subtle, 2.0 = strong recoil).")
                .defineInRange("strength", 1.0, 0.0, 3.0);

        builder.pop(); // mining_inertia

        // ===== TRIDENT ZOOM =====
        builder.push("trident_zoom");

        TRIDENT_ZOOM_ENABLED = builder
                .comment("Enable smooth FOV zoom while charging a trident (similar to bow zoom).")
                .define("enabled", true);

        TRIDENT_ZOOM_STRENGTH = builder
                .comment("Max FOV zoom strength while charging trident (0.25 = FOV shrinks to 75% of normal).")
                .defineInRange("strength", 0.25, 0.0, 0.7);

        builder.pop(); // trident_zoom

        // ===== FALL SHAKE =====
        builder.push("fall_shake");

        FALL_SHAKE_ENABLED = builder
                .comment("Enable subtle camera shake while free-falling from height (air turbulence feel).")
                .define("enabled", true);

        FALL_SHAKE_STRENGTH = builder
                .comment("Overall strength multiplier for fall shake (1.0 = default, 0.5 = subtle, 2.0 = strong).")
                .defineInRange("strength", 1.0, 0.0, 3.0);

        FALL_SHAKE_THRESHOLD_TICKS = builder
                .comment("How many ticks of continuous falling before shake starts.",
                        "20 ticks = 1 second. Default 12 (~0.6s of falling, roughly 5-6 blocks).")
                .defineInRange("thresholdTicks", 12, 4, 60);

        builder.pop(); // fall_shake

        // ===== FALL BLUR =====
        builder.push("fall_blur");

        FALL_BLUR_ENABLED = builder
                .comment("Enable symmetric side motion blur while free-falling.",
                        "Uses the same shader as turn blur, but applies on both sides.")
                .define("enabled", true);

        // 🔧 Усилено: 0.45 → 0.70 (блюр при падении ближе к максимуму)
        FALL_BLUR_STRENGTH = builder
                .comment("How strong fall blur is relative to max side blur intensity.",
                        "0.70 = at peak fall, blur reaches 70% of full turn-blur strength.")
                .defineInRange("strength", 0.70, 0.0, 1.0);

        builder.pop(); // fall_blur

        SPEC = builder.build();
    }

    private ClientConfig() {
    }
}