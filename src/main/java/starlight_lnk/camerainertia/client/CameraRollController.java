package starlight_lnk.camerainertia.client;

import net.minecraft.client.Minecraft;

public class CameraRollController {

    private static float currentRoll = 0.0F;
    private static float prevRoll    = 0.0F;
    private static float prevYaw     = 0.0F;
    private static boolean initialized = false;

    public static void tick() {
        try {
            Minecraft mc = Minecraft.getInstance();

            if (mc == null || mc.player == null || mc.level == null || mc.isPaused()) {
                prevRoll = currentRoll;
                currentRoll *= 0.8F;
                if (Math.abs(currentRoll) < 0.001F) currentRoll = 0.0F;
                initialized = false;
                return;
            }

            // 🎥 Только в 1st person — плавно гасим крен в 3rd person
            if (!CameraViewUtils.isFirstPerson()) {
                prevRoll = currentRoll;
                currentRoll *= 0.8F;
                if (Math.abs(currentRoll) < 0.001F) currentRoll = 0.0F;
                initialized = false;
                return;
            }

            float yaw = mc.player.getYRot();

            if (Float.isNaN(yaw) || Float.isInfinite(yaw)) {
                initialized = false;
                return;
            }

            if (!initialized) {
                prevYaw = yaw;
                initialized = true;
            }

            float yawDelta = yaw - prevYaw;
            prevYaw = yaw;

            if (yawDelta >  180.0F) yawDelta -= 360.0F;
            if (yawDelta < -180.0F) yawDelta += 360.0F;

            if (Math.abs(yawDelta) > 90.0F) yawDelta = 0.0F;

            float strafe = 0.0F;
            if (mc.player.input != null) {
                strafe = mc.player.input.leftImpulse;
                if (Float.isNaN(strafe) || Float.isInfinite(strafe)) strafe = 0.0F;
            }

            // 🌀 Инерция уменьшена на 50%
            // yawDelta:  было 1.5  → теперь 0.75 (поворот мышью)
            // strafe:    было 5.0  → теперь 2.5  (клавиши A/D)
            float targetRoll = 0.0F;
            targetRoll += -yawDelta * 0.75F;
            targetRoll += -strafe   * 2.5F;

            // Лимит тоже снижаем в 2 раза (было ±12 → ±6)
            if (targetRoll >  6.0F) targetRoll =  6.0F;
            if (targetRoll < -6.0F) targetRoll = -6.0F;

            prevRoll = currentRoll;
            currentRoll += (targetRoll - currentRoll) * 0.2F;
            currentRoll *= 0.9F;

            if (Float.isNaN(currentRoll) || Float.isInfinite(currentRoll)) {
                currentRoll = 0.0F;
                prevRoll    = 0.0F;
            }
        } catch (Throwable t) {
            currentRoll = 0.0F;
            prevRoll    = 0.0F;
            initialized = false;
        }
    }

    public static float getRoll(float partialTick) {
        if (Float.isNaN(partialTick) || Float.isInfinite(partialTick)) partialTick = 0.0F;
        if (partialTick < 0.0F) partialTick = 0.0F;
        if (partialTick > 1.0F) partialTick = 1.0F;

        float roll = prevRoll + (currentRoll - prevRoll) * partialTick;

        if (Float.isNaN(roll) || Float.isInfinite(roll)) return 0.0F;
        return roll;
    }
}