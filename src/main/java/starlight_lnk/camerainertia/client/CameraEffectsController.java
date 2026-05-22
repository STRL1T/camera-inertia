package starlight_lnk.camerainertia.client;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

public class CameraEffectsController {

    // === ИМПУЛЬСНАЯ ЧАСТЬ ===
    private static float pitchImpulse = 0.0F;
    private static float yawImpulse   = 0.0F;
    private static float rollImpulse  = 0.0F;

    private static float prevPitchImpulse = 0.0F;
    private static float prevYawImpulse   = 0.0F;
    private static float prevRollImpulse  = 0.0F;

    // === СИНУСОИДАЛЬНАЯ ЧАСТЬ ===
    private static float pitchOscillation = 0.0F;
    private static float yawOscillation   = 0.0F;
    private static float rollOscillation  = 0.0F;

    private static float prevPitchOsc = 0.0F;
    private static float prevYawOsc   = 0.0F;
    private static float prevRollOsc  = 0.0F;

    public static void tick() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.player == null || mc.level == null || mc.isPaused()) {
                damp();
                return;
            }

            // 🎥 Только в 1st person — в 3rd person плавно гасим все каналы
            if (!CameraViewUtils.isFirstPerson()) {
                damp();

                if (Math.abs(pitchImpulse)     < 0.001F) pitchImpulse     = 0.0F;
                if (Math.abs(yawImpulse)       < 0.001F) yawImpulse       = 0.0F;
                if (Math.abs(rollImpulse)      < 0.001F) rollImpulse      = 0.0F;
                if (Math.abs(pitchOscillation) < 0.001F) pitchOscillation = 0.0F;
                if (Math.abs(yawOscillation)   < 0.001F) yawOscillation   = 0.0F;
                if (Math.abs(rollOscillation)  < 0.001F) rollOscillation  = 0.0F;
                return;
            }

            Player player = mc.player;
            long t        = mc.level.getGameTime();

            // ❌ УБРАНО: телепорт, смерть, левел-ап, низкое HP, зелье скорости (FOV-pulse)

            // ============================================================
            // ПОСТОЯННЫЕ КОЛЕБАНИЯ
            // ============================================================
            float targetPitchOsc = 0.0F;
            float targetYawOsc   = 0.0F;
            float targetRollOsc  = 0.0F;

            // 🪂 ЛЕВИТАЦИЯ — оставляем как есть
            if (player.hasEffect(MobEffects.LEVITATION)) {
                float phase = t * 0.05F;
                targetPitchOsc += Mth.sin(phase)        * 0.6F;
                targetRollOsc  += Mth.sin(phase * 0.7F) * 0.8F;
                targetYawOsc   += Mth.cos(phase * 0.5F) * 0.3F;
            }

            // 🪶 МЕДЛЕННОЕ ПАДЕНИЕ
            if (player.hasEffect(MobEffects.SLOW_FALLING) && !player.onGround()) {
                float phase = t * 0.04F;
                targetRollOsc  += Mth.sin(phase) * 0.4F;
                targetPitchOsc += Mth.cos(phase * 0.8F) * 0.2F;
            }

            // 😵 СЛАБОСТЬ
            if (player.hasEffect(MobEffects.WEAKNESS)) {
                targetPitchOsc += Mth.sin(t * 0.08F) * 0.15F;
                targetRollOsc  += Mth.cos(t * 0.06F) * 0.15F;
            }

            // 🍖 ГОЛОД — в 2× слабее (амплитуды ×0.5)
            int food = player.getFoodData().getFoodLevel();
            if (food < 6) {
                float w = (6 - food) / 6.0F;
                targetRollOsc  += Mth.sin(t * 0.05F) * 0.125F * w; // было 0.25
                targetPitchOsc += Mth.cos(t * 0.04F) * 0.075F * w; // было 0.15
            }

            prevPitchOsc = pitchOscillation;
            prevYawOsc   = yawOscillation;
            prevRollOsc  = rollOscillation;

            pitchOscillation += (targetPitchOsc - pitchOscillation) * 0.25F;
            yawOscillation   += (targetYawOsc   - yawOscillation)   * 0.25F;
            rollOscillation  += (targetRollOsc  - rollOscillation)  * 0.25F;

            // ============================================================
            // ЗАТУХАНИЕ ИМПУЛЬСНОЙ ЧАСТИ (на всякий случай, если impulse-каналом
            // позже будет кто-то пользоваться)
            // ============================================================
            prevPitchImpulse = pitchImpulse;
            prevYawImpulse   = yawImpulse;
            prevRollImpulse  = rollImpulse;

            pitchImpulse *= 0.82F;
            yawImpulse   *= 0.82F;
            rollImpulse  *= 0.82F;

            if (Math.abs(pitchImpulse) < 0.001F) pitchImpulse = 0.0F;
            if (Math.abs(yawImpulse)   < 0.001F) yawImpulse   = 0.0F;
            if (Math.abs(rollImpulse)  < 0.001F) rollImpulse  = 0.0F;

        } catch (Throwable t) {
            t.printStackTrace();
            pitchImpulse = yawImpulse = rollImpulse = 0.0F;
            pitchOscillation = yawOscillation = rollOscillation = 0.0F;
        }
    }

    private static void damp() {
        prevPitchImpulse = pitchImpulse;
        prevYawImpulse   = yawImpulse;
        prevRollImpulse  = rollImpulse;
        pitchImpulse *= 0.82F;
        yawImpulse   *= 0.82F;
        rollImpulse  *= 0.82F;

        prevPitchOsc = pitchOscillation;
        prevYawOsc   = yawOscillation;
        prevRollOsc  = rollOscillation;
        pitchOscillation *= 0.9F;
        yawOscillation   *= 0.9F;
        rollOscillation  *= 0.9F;
    }

    public static float getPitchOffset(float partialTick) {
        return Mth.lerp(partialTick, prevPitchImpulse, pitchImpulse)
                + Mth.lerp(partialTick, prevPitchOsc,     pitchOscillation);
    }

    public static float getYawOffset(float partialTick) {
        return Mth.lerp(partialTick, prevYawImpulse, yawImpulse)
                + Mth.lerp(partialTick, prevYawOsc,     yawOscillation);
    }

    public static float getRollOffset(float partialTick) {
        return Mth.lerp(partialTick, prevRollImpulse, rollImpulse)
                + Mth.lerp(partialTick, prevRollOsc,     rollOscillation);
    }
}