package starlight_lnk.camerainertia.client;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import starlight_lnk.camerainertia.config.ClientConfig;

public final class CameraPerspectiveController {

    private static final float FULL_DISTANCE = 4.0F;
    private static final float MIN_SAFE_DISTANCE = 0.5F;

    private static CameraType targetType = null;
    private static int tickCounter = 0;
    private static int animationDuration = 5;
    private static CameraType lastSeenType = CameraType.FIRST_PERSON;

    // ДОБАВЛЕНО: Предыдущая дистанция для интерполяции (плавности)
    private static float prevDistance = 0.0F;
    private static float currentDistance = 0.0F;

    private static boolean finishAsFirstPerson = false;
    private static float currentAlpha = 1.0F;
    private static float prevAlpha = 1.0F;

    private CameraPerspectiveController() {}

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null) return;

        if (!ClientConfig.PERSPECTIVE_TRANSITION_ENABLED.get()) {
            lastSeenType = mc.options.getCameraType();
            targetType = null;
            return;
        }

        prevAlpha = currentAlpha;
        // Запоминаем дистанцию с ПРОШЛОГО тика
        prevDistance = currentDistance;

        CameraType actual = mc.options.getCameraType();

        if (actual != lastSeenType && targetType == null) {
            startTransition(mc, lastSeenType, actual);
            recomputeForCurrentProgress();
        }

        if (targetType != null) {
            tickCounter++;
            recomputeForCurrentProgress();

            if (tickCounter >= animationDuration) {
                if (finishAsFirstPerson) {
                    mc.options.setCameraType(CameraType.FIRST_PERSON);
                    lastSeenType = CameraType.FIRST_PERSON;
                } else {
                    lastSeenType = targetType;
                }
                targetType = null;
                tickCounter = 0;

                // Фиксируем финальные значения
                currentDistance = finishAsFirstPerson ? 0.0F : FULL_DISTANCE;
                prevDistance = currentDistance;

                finishAsFirstPerson = false;
                currentAlpha = 1.0F;
            }
            return;
        }

        currentAlpha = 1.0F;
        lastSeenType = mc.options.getCameraType();
    }

    private static void startTransition(Minecraft mc, CameraType from, CameraType to) {
        animationDuration = ClientConfig.PERSPECTIVE_TRANSITION_DURATION.get();
        tickCounter = 0;

        boolean fromFirst = (from == CameraType.FIRST_PERSON);
        boolean toFirst   = (to   == CameraType.FIRST_PERSON);

        if (fromFirst && !toFirst) {
            targetType = to;
            finishAsFirstPerson = false;
            mc.options.setCameraType(to);
            currentDistance = MIN_SAFE_DISTANCE;
            prevDistance = MIN_SAFE_DISTANCE;
            prevAlpha = 1.0F; currentAlpha = 1.0F;
        } else if (!fromFirst && toFirst) {
            targetType = from;
            finishAsFirstPerson = true;
            mc.options.setCameraType(from);
            currentDistance = FULL_DISTANCE;
            prevDistance = FULL_DISTANCE;
            prevAlpha = 1.0F; currentAlpha = 1.0F;
        } else {
            targetType = null;
            lastSeenType = to;
            currentDistance = FULL_DISTANCE;
            prevDistance = FULL_DISTANCE;
            prevAlpha = 1.0F; currentAlpha = 1.0F;
        }
    }

    private static void recomputeForCurrentProgress() {
        float progress = Mth.clamp((float) tickCounter / (float) animationDuration, 0.0F, 1.0F);

        float eased = 1.0F - (float) Math.pow(2.0, -10.0 * progress);
        if (progress >= 1.0F) eased = 1.0F;

        if (finishAsFirstPerson) {
            currentDistance = FULL_DISTANCE + (MIN_SAFE_DISTANCE - FULL_DISTANCE) * eased;
        } else {
            currentDistance = MIN_SAFE_DISTANCE + (FULL_DISTANCE - MIN_SAFE_DISTANCE) * eased;
        }

        if (finishAsFirstPerson) {
            float alphaEased = 1.0F - (1.0F - Math.min(1.0F, progress * 1.6F)) * (1.0F - Math.min(1.0F, progress * 1.6F));
            currentAlpha = Mth.clamp(1.0F - alphaEased, 0.0F, 1.0F);
        } else {
            currentAlpha = 1.0F;
        }
    }

    // ДОБАВЛЕНО: Метод, который отдает дистанцию с частотой кадров монитора (FPS)
    public static double getInterpolatedDistance(float partialTick) {
        return Mth.lerp(partialTick, prevDistance, currentDistance);
    }

    public static boolean isTransitioning() {
        return targetType != null;
    }

    public static boolean shouldFullyHideOwnPlayer(float partialTick) {
        if (targetType == null || !finishAsFirstPerson) return false;
        return getOwnPlayerAlpha(partialTick) < 0.05F;
    }

    public static float getOwnPlayerAlpha(float partialTick) {
        if (targetType == null || !finishAsFirstPerson) return 1.0F;
        float pt = Mth.clamp(partialTick, 0.0F, 1.0F);
        return Mth.clamp(prevAlpha + (currentAlpha - prevAlpha) * pt, 0.0F, 1.0F);
    }

    public static boolean shouldHideOwnPlayer() {
        return targetType != null && finishAsFirstPerson;
    }
}