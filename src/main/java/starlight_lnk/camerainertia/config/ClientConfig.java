package starlight_lnk.camerainertia.config;

import net.minecraftforge.common.ForgeConfigSpec;
import java.util.Arrays;
import java.util.List;

public class ClientConfig {
    public static final ForgeConfigSpec SPEC;

    public enum Preset {
        CLASSIC, SHOOTER, REALISM, DRUNKARD, CUSTOM
    }

    public static final ForgeConfigSpec.EnumValue<Preset> ACTIVE_PRESET;
    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.BooleanValue FIRST_PERSON_BODY_ENABLED;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> ITEM_BLACKLIST;

    public static final ForgeConfigSpec.BooleanValue MOVEMENT_ANIMATIONS_ENABLED;
    public static final ForgeConfigSpec.DoubleValue MOVEMENT_INTENSITY;
    public static final ForgeConfigSpec.DoubleValue STRAFE_ROLL_INTENSITY;

    public static final ForgeConfigSpec.BooleanValue FALL_SHAKE_ENABLED;
    public static final ForgeConfigSpec.IntValue FALL_SHAKE_THRESHOLD_TICKS;
    public static final ForgeConfigSpec.DoubleValue FALL_SHAKE_STRENGTH;

    public static final ForgeConfigSpec.BooleanValue TURN_ANIMATIONS_ENABLED;
    public static final ForgeConfigSpec.DoubleValue TURN_INTENSITY;

    public static final ForgeConfigSpec.BooleanValue ITEM_ANIMATIONS_ENABLED;

    // === ОБЩИЙ ЗУМ ДЛЯ ОРУЖИЯ (Лук, Арбалет, Трезубец) ===
    public static final ForgeConfigSpec.BooleanValue WEAPON_ZOOM_ENABLED;
    public static final ForgeConfigSpec.DoubleValue WEAPON_ZOOM_STRENGTH;

    public static final ForgeConfigSpec.BooleanValue MINING_INERTIA_ENABLED;
    public static final ForgeConfigSpec.DoubleValue MINING_INERTIA_STRENGTH;
    public static final ForgeConfigSpec.BooleanValue VEHICLE_INERTIA_ENABLED;
    public static final ForgeConfigSpec.DoubleValue VEHICLE_INERTIA_STRENGTH;
    public static final ForgeConfigSpec.BooleanValue VEHICLE_BOAT_WAVES;
    public static final ForgeConfigSpec.BooleanValue VEHICLE_BOAT_ROWING;
    public static final ForgeConfigSpec.DoubleValue VEHICLE_BOAT_ROWING_STRENGTH;

    public static final ForgeConfigSpec.BooleanValue PERSPECTIVE_TRANSITION_ENABLED;
    public static final ForgeConfigSpec.IntValue PERSPECTIVE_TRANSITION_DURATION;

    public static final ForgeConfigSpec.BooleanValue MOTION_BLUR_ENABLED;
    public static final ForgeConfigSpec.DoubleValue MOTION_BLUR_SENSITIVITY;
    public static final ForgeConfigSpec.DoubleValue MOTION_BLUR_MAX_INTENSITY;
    public static final ForgeConfigSpec.DoubleValue MOTION_BLUR_SMOOTHING;
    public static final ForgeConfigSpec.DoubleValue MOTION_BLUR_DEADZONE;
    public static final ForgeConfigSpec.DoubleValue MOTION_BLUR_FULL_MOTION;
    public static final ForgeConfigSpec.DoubleValue MOTION_BLUR_CURVE_POWER;
    public static final ForgeConfigSpec.BooleanValue MOTION_BLUR_OPPOSITE_SIDE;
    public static final ForgeConfigSpec.DoubleValue MOTION_BLUR_ATTACK_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue MOTION_BLUR_RELEASE_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue MOTION_BLUR_SIDE_START;
    public static final ForgeConfigSpec.DoubleValue MOTION_BLUR_SIDE_END;
    public static final ForgeConfigSpec.DoubleValue MOTION_BLUR_EDGE_POWER;
    public static final ForgeConfigSpec.DoubleValue MOTION_BLUR_PIXELS;
    public static final ForgeConfigSpec.DoubleValue MOTION_BLUR_CHROMA_PIXELS;

    public static final ForgeConfigSpec.BooleanValue FALL_BLUR_ENABLED;
    public static final ForgeConfigSpec.DoubleValue FALL_BLUR_STRENGTH;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        ACTIVE_PRESET = builder.comment("Current active preset").defineEnum("activePreset", Preset.REALISM);
        ENABLED = builder.define("enabled", true);

        builder.push("general");
        FIRST_PERSON_BODY_ENABLED = builder.define("firstPersonBodyEnabled", true);
        ITEM_BLACKLIST = builder.defineList("item_blacklist", Arrays.asList(
                "minecraft:filled_map", "create:potato_cannon", "create:handheld_worldshaper",
                "twilightforest:filled_ore_map", "map_atlases:atlas", "twilightforest:filled_magic_map",
                "twilightforest:filled_maze_map", "antiqueatlas:antique_atlas", "create:extendo_grip",
                "tacz:modern_kinetic_gun"
        ), obj -> obj instanceof String);
        builder.pop();

        builder.push("movement");
        MOVEMENT_ANIMATIONS_ENABLED = builder.define("movementAnimationsEnabled", true);
        MOVEMENT_INTENSITY = builder.defineInRange("movementIntensity", 1.0, 0.0, 3.0);
        STRAFE_ROLL_INTENSITY = builder.defineInRange("strafeRollIntensity", 1.0, 0.0, 3.0);
        FALL_SHAKE_ENABLED = builder.define("fallShakeEnabled", true);
        FALL_SHAKE_THRESHOLD_TICKS = builder.defineInRange("fallShakeThresholdTicks", 15, 0, 100);
        FALL_SHAKE_STRENGTH = builder.defineInRange("fallShakeStrength", 1.0, 0.0, 5.0);
        builder.pop();

        builder.push("turning");
        TURN_ANIMATIONS_ENABLED = builder.define("turnAnimationsEnabled", true);
        TURN_INTENSITY = builder.defineInRange("turnIntensity", 1.0, 0.0, 3.0);
        builder.pop();

        builder.push("items");
        ITEM_ANIMATIONS_ENABLED = builder.define("itemAnimationsEnabled", true);
        WEAPON_ZOOM_ENABLED = builder.define("weaponZoomEnabled", true);
        // Лимит 1.0 (ощущается приятно, не читерно)
        WEAPON_ZOOM_STRENGTH = builder.defineInRange("weaponZoomStrength", 0.5, 0.0, 1.0);
        builder.pop();

        builder.push("actions");
        MINING_INERTIA_ENABLED = builder.define("miningInertiaEnabled", true);
        MINING_INERTIA_STRENGTH = builder.defineInRange("miningInertiaStrength", 1.0, 0.0, 3.0);
        VEHICLE_INERTIA_ENABLED = builder.define("vehicleInertiaEnabled", true);
        VEHICLE_INERTIA_STRENGTH = builder.defineInRange("vehicleInertiaStrength", 1.0, 0.0, 3.0);
        VEHICLE_BOAT_WAVES = builder.define("vehicleBoatWaves", true);
        VEHICLE_BOAT_ROWING = builder.define("vehicleBoatRowing", true);
        VEHICLE_BOAT_ROWING_STRENGTH = builder.defineInRange("vehicleBoatRowingStrength", 1.0, 0.0, 3.0);
        builder.pop();

        builder.push("perspective");
        PERSPECTIVE_TRANSITION_ENABLED = builder.define("perspectiveTransitionEnabled", true);
        PERSPECTIVE_TRANSITION_DURATION = builder.defineInRange("perspectiveTransitionDuration", 10, 1, 100);
        builder.pop();

        builder.push("motion_blur");
        MOTION_BLUR_ENABLED = builder.define("motionBlurEnabled", true);
        MOTION_BLUR_SENSITIVITY = builder.defineInRange("motionBlurSensitivity", 1.0, 0.0, 20.0);
        MOTION_BLUR_MAX_INTENSITY = builder.defineInRange("motionBlurMaxIntensity", 1.0, 0.0, 1.0);
        MOTION_BLUR_SMOOTHING = builder.defineInRange("motionBlurSmoothing", 0.1, 0.01, 1.0);
        MOTION_BLUR_DEADZONE = builder.defineInRange("motionBlurDeadzone", 0.5, 0.0, 10.0);
        MOTION_BLUR_FULL_MOTION = builder.defineInRange("motionBlurFullMotion", 15.0, 0.1, 100.0);
        MOTION_BLUR_CURVE_POWER = builder.defineInRange("motionBlurCurvePower", 1.0, 0.1, 5.0);
        MOTION_BLUR_OPPOSITE_SIDE = builder.define("motionBlurOppositeSide", false);
        MOTION_BLUR_ATTACK_MULTIPLIER = builder.defineInRange("motionBlurAttackMultiplier", 1.0, 0.0, 5.0);
        MOTION_BLUR_RELEASE_MULTIPLIER = builder.defineInRange("motionBlurReleaseMultiplier", 0.5, 0.0, 5.0);
        MOTION_BLUR_SIDE_START = builder.defineInRange("motionBlurSideStart", 0.2, 0.0, 1.0);
        MOTION_BLUR_SIDE_END = builder.defineInRange("motionBlurSideEnd", 1.0, 0.0, 1.0);
        MOTION_BLUR_EDGE_POWER = builder.defineInRange("motionBlurEdgePower", 1.5, 0.1, 5.0);
        MOTION_BLUR_PIXELS = builder.defineInRange("motionBlurPixels", 16.0, 0.0, 250.0);
        MOTION_BLUR_CHROMA_PIXELS = builder.defineInRange("motionBlurChromaPixels", 2.0, 0.0, 50.0);
        FALL_BLUR_ENABLED = builder.define("fallBlurEnabled", true);
        FALL_BLUR_STRENGTH = builder.defineInRange("fallBlurStrength", 1.0, 0.0, 5.0);
        builder.pop();

        SPEC = builder.build();
    }

    public static void applyPreset(Preset preset) {
        ACTIVE_PRESET.set(preset);

        switch (preset) {
            case CLASSIC:
                FIRST_PERSON_BODY_ENABLED.set(false);
                PERSPECTIVE_TRANSITION_ENABLED.set(false);
                MOVEMENT_ANIMATIONS_ENABLED.set(false);
                TURN_ANIMATIONS_ENABLED.set(false);
                FALL_SHAKE_ENABLED.set(false);
                MINING_INERTIA_ENABLED.set(false);
                VEHICLE_INERTIA_ENABLED.set(false);
                MOTION_BLUR_ENABLED.set(false);
                FALL_BLUR_ENABLED.set(false);
                ITEM_ANIMATIONS_ENABLED.set(false);

                WEAPON_ZOOM_ENABLED.set(false);

                // ЖЕЛЕЗОБЕТОННЫЙ ФИКС КЛАССИКИ (все силы в 0.0)
                MOVEMENT_INTENSITY.set(0.0);
                STRAFE_ROLL_INTENSITY.set(0.0);
                TURN_INTENSITY.set(0.0);
                MINING_INERTIA_STRENGTH.set(0.0);
                VEHICLE_INERTIA_STRENGTH.set(0.0);
                WEAPON_ZOOM_STRENGTH.set(0.0);
                break;

            case SHOOTER:
                FIRST_PERSON_BODY_ENABLED.set(true); PERSPECTIVE_TRANSITION_ENABLED.set(true);
                ITEM_ANIMATIONS_ENABLED.set(true);

                WEAPON_ZOOM_ENABLED.set(true);
                WEAPON_ZOOM_STRENGTH.set(0.2); // Легкий зум

                MOVEMENT_ANIMATIONS_ENABLED.set(true); MOVEMENT_INTENSITY.set(0.2);
                STRAFE_ROLL_INTENSITY.set(0.25);
                TURN_ANIMATIONS_ENABLED.set(true); TURN_INTENSITY.set(0.35);
                FALL_SHAKE_ENABLED.set(true); FALL_SHAKE_STRENGTH.set(0.3);
                MINING_INERTIA_ENABLED.set(true); MINING_INERTIA_STRENGTH.set(0.3);
                VEHICLE_INERTIA_ENABLED.set(true); VEHICLE_INERTIA_STRENGTH.set(0.3);

                MOTION_BLUR_ENABLED.set(true); MOTION_BLUR_MAX_INTENSITY.set(0.8);
                MOTION_BLUR_PIXELS.set(12.0); MOTION_BLUR_CHROMA_PIXELS.set(1.5);
                MOTION_BLUR_SENSITIVITY.set(2.0); MOTION_BLUR_DEADZONE.set(1.5);
                MOTION_BLUR_EDGE_POWER.set(2.0); FALL_BLUR_ENABLED.set(true); FALL_BLUR_STRENGTH.set(0.5);
                break;

            case REALISM:
                FIRST_PERSON_BODY_ENABLED.set(true); PERSPECTIVE_TRANSITION_ENABLED.set(true);
                ITEM_ANIMATIONS_ENABLED.set(true);

                WEAPON_ZOOM_ENABLED.set(true);
                WEAPON_ZOOM_STRENGTH.set(0.5); // Средний зум

                MOVEMENT_ANIMATIONS_ENABLED.set(true); MOVEMENT_INTENSITY.set(0.15);
                STRAFE_ROLL_INTENSITY.set(0.15);
                TURN_ANIMATIONS_ENABLED.set(true); TURN_INTENSITY.set(0.8);
                FALL_SHAKE_ENABLED.set(true); FALL_SHAKE_STRENGTH.set(0.8);
                MINING_INERTIA_ENABLED.set(true); MINING_INERTIA_STRENGTH.set(0.8);
                VEHICLE_INERTIA_ENABLED.set(true); VEHICLE_INERTIA_STRENGTH.set(0.8);

                MOTION_BLUR_ENABLED.set(true); MOTION_BLUR_MAX_INTENSITY.set(1.0);
                MOTION_BLUR_PIXELS.set(24.0); MOTION_BLUR_CHROMA_PIXELS.set(3.0);
                MOTION_BLUR_SENSITIVITY.set(1.2); MOTION_BLUR_DEADZONE.set(0.5);
                MOTION_BLUR_EDGE_POWER.set(1.5); FALL_BLUR_ENABLED.set(true); FALL_BLUR_STRENGTH.set(1.0);
                break;

            case DRUNKARD:
                FIRST_PERSON_BODY_ENABLED.set(true); PERSPECTIVE_TRANSITION_ENABLED.set(true);
                ITEM_ANIMATIONS_ENABLED.set(true);

                WEAPON_ZOOM_ENABLED.set(true);
                WEAPON_ZOOM_STRENGTH.set(0.5); // ТАКОЙ ЖЕ КАК В РЕАЛИЗМЕ! (Раньше было 1.0)

                MOVEMENT_ANIMATIONS_ENABLED.set(true); MOVEMENT_INTENSITY.set(2.0);
                STRAFE_ROLL_INTENSITY.set(3.0);
                TURN_ANIMATIONS_ENABLED.set(true); TURN_INTENSITY.set(3.0);
                FALL_SHAKE_ENABLED.set(true); FALL_SHAKE_STRENGTH.set(3.0);
                MINING_INERTIA_ENABLED.set(true); MINING_INERTIA_STRENGTH.set(3.0);
                VEHICLE_INERTIA_ENABLED.set(true); VEHICLE_INERTIA_STRENGTH.set(2.0);

                MOTION_BLUR_ENABLED.set(true); MOTION_BLUR_MAX_INTENSITY.set(1.0);
                MOTION_BLUR_PIXELS.set(150.0); MOTION_BLUR_CHROMA_PIXELS.set(40.0);
                MOTION_BLUR_SENSITIVITY.set(15.0); MOTION_BLUR_DEADZONE.set(0.0);
                MOTION_BLUR_EDGE_POWER.set(0.5); FALL_BLUR_ENABLED.set(true); FALL_BLUR_STRENGTH.set(3.0);
                break;

            case CUSTOM:
                break;
        }
        SPEC.save();
    }
}