package starlight_lnk.camerainertia.client;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import starlight_lnk.camerainertia.config.ClientConfig;

/**
 * 🎬 Плавная анимация переключения перспективы (F5).
 *
 * Логика:
 *  - 1st → 3rd: камера выпрыгивает из головы наружу.
 *                Модель ВИДНА с первого тика, без fade (так выглядит естественно).
 *  - 3rd → 1st: камера летит к голове. Модель плавно тает к нулю, потом
 *                переключаемся на 1st person.
 *  - Скрытие модели — только для своей камеры (RenderPlayerEvent.Pre).
 *  - Дистанция интерполируется по easeOutExpo, альфа — отдельной кривой,
 *    чтобы fade ощущался как часть движения, а не как «выключатель».
 */
public final class CameraPerspectiveController {

    private static final float FULL_DISTANCE = 4.0F;
    private static final float MIN_SAFE_DISTANCE = 0.7F;

    private static CameraType targetType = null;
    private static int tickCounter = 0;
    private static int animationDuration = 5;
    private static CameraType lastSeenType = CameraType.FIRST_PERSON;
    private static float prevDistance = 0.0F;
    private static float currentDistance = 0.0F;
    private static boolean finishAsFirstPerson = false;

    /**
     * Текущая альфа собственного игрока в этот тик.
     *  - 0.0 = полностью прозрачен
     *  - 1.0 = полностью видим
     * Меняется ТОЛЬКО при переходе 3rd → 1st. В обратном направлении
     * остаётся 1.0 на всю анимацию (модель видна сразу).
     */
    private static float currentAlpha = 1.0F;
    private static float prevAlpha = 1.0F;

    private CameraPerspectiveController() {}

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null) return;

        if (!ClientConfig.PERSPECTIVE_TRANSITION_ENABLED.get()) {
            CameraType actual = mc.options.getCameraType();
            lastSeenType = actual;
            targetType = null;
            tickCounter = 0;
            prevDistance = 0.0F;
            currentDistance = 0.0F;
            finishAsFirstPerson = false;
            prevAlpha = currentAlpha = 1.0F;
            return;
        }

        // Сдвигаем prev для интерполяции
        prevAlpha = currentAlpha;

        CameraType actual = mc.options.getCameraType();

        if (actual != lastSeenType && targetType == null) {
            startTransition(mc, lastSeenType, actual);
            // 🎯 СРАЗУ в этот же тик считаем дистанцию и альфу для нулевого
            // прогресса — чтобы игрок не успел увидеть «вспышку» первого кадра.
            recomputeForCurrentProgress();
        }

        if (targetType != null) {
            tickCounter++;

            prevDistance = currentDistance;
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
                prevDistance = 0.0F;
                currentDistance = 0.0F;
                finishAsFirstPerson = false;
                currentAlpha = 1.0F;
                return;
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
            // 1st → 3rd: МОДЕЛЬ ВИДНА СРАЗУ, без fade.
            // Только дистанция камеры анимируется.
            targetType = to;
            finishAsFirstPerson = false;
            mc.options.setCameraType(to);
            prevDistance = MIN_SAFE_DISTANCE;
            currentDistance = MIN_SAFE_DISTANCE;
            prevAlpha = 1.0F;
            currentAlpha = 1.0F;
        } else if (!fromFirst && toFirst) {
            // 3rd → 1st: модель сейчас видна, начинаем таять
            targetType = from;
            finishAsFirstPerson = true;
            mc.options.setCameraType(from);
            prevDistance = FULL_DISTANCE;
            currentDistance = FULL_DISTANCE;
            prevAlpha = 1.0F;
            currentAlpha = 1.0F;
        } else {
            // 3rd_back ↔ 3rd_front — мгновенно
            targetType = null;
            lastSeenType = to;
            prevDistance = FULL_DISTANCE;
            currentDistance = FULL_DISTANCE;
            prevAlpha = currentAlpha = 1.0F;
        }
    }

    private static void recomputeForCurrentProgress() {
        float progress = (float) tickCounter / (float) animationDuration;
        if (progress > 1.0F) progress = 1.0F;
        if (progress < 0.0F) progress = 0.0F;

        currentDistance = computeDistance(progress);
        currentAlpha    = computeAlpha(progress);
    }

    private static float computeDistance(float progress) {
        float eased = easeOutExpo(progress);
        if (finishAsFirstPerson) {
            return FULL_DISTANCE + (MIN_SAFE_DISTANCE - FULL_DISTANCE) * eased;
        } else {
            return MIN_SAFE_DISTANCE + (FULL_DISTANCE - MIN_SAFE_DISTANCE) * eased;
        }
    }

    /**
     * Альфа модели игрока в зависимости от прогресса анимации.
     *
     * Поведение:
     *  - 3rd → 1st (finishAsFirstPerson = true):
     *      Быстрое таяние — в первой трети анимации модель уже почти невидима.
     *  - 1st → 3rd (finishAsFirstPerson = false):
     *      ВСЕГДА 1.0 — модель видна сразу, никакого fade.
     */
    private static float computeAlpha(float progress) {
        if (!finishAsFirstPerson) {
            // 1st → 3rd: модель полностью видна на протяжении всей анимации
            return 1.0F;
        }

        float p = Mth.clamp(progress, 0.0F, 1.0F);
        // 3rd → 1st: тает БЫСТРО, к 60% прогресса уже почти прозрачна
        float eased = easeOutQuad(Math.min(1.0F, p * 1.6F));
        return Mth.clamp(1.0F - eased, 0.0F, 1.0F);
    }

    private static float easeOutExpo(float t) {
        if (t >= 1.0F) return 1.0F;
        if (t <= 0.0F) return 0.0F;
        return 1.0F - (float) Math.pow(2.0, -10.0 * t);
    }

    private static float easeOutQuad(float t) {
        return 1.0F - (1.0F - t) * (1.0F - t);
    }

    public static float getPullbackAmount(float partialTick) {
        if (targetType == null) return 0.0F;
        float interp = prevDistance + (currentDistance - prevDistance) * partialTick;
        return FULL_DISTANCE - interp;
    }

    public static boolean isTransitioning() {
        return targetType != null;
    }

    /**
     * Должна ли модель собственного игрока быть полностью скрыта
     * на этом кадре? Используется, чтобы избежать «полупрозрачного силуэта»
     * в самом конце затухания при переходе 3rd → 1st.
     */
    public static boolean shouldFullyHideOwnPlayer(float partialTick) {
        if (targetType == null) return false;
        if (!finishAsFirstPerson) return false; // 1st → 3rd: никогда не скрываем
        float a = getOwnPlayerAlpha(partialTick);
        return a < 0.05F;
    }

    /**
     * Альфа модели локального игрока на этом кадре.
     * Возвращает 1.0 вне анимации и во время 1st → 3rd.
     */
    public static float getOwnPlayerAlpha(float partialTick) {
        if (targetType == null) return 1.0F;
        if (!finishAsFirstPerson) return 1.0F; // 1st → 3rd: всегда видна
        float pt = Mth.clamp(partialTick, 0.0F, 1.0F);
        return Mth.clamp(prevAlpha + (currentAlpha - prevAlpha) * pt, 0.0F, 1.0F);
    }

    /**
     * true пока идёт анимация, в которой модель надо как-то прятать.
     * Для 1st → 3rd возвращает false — там вмешиваться в рендер не нужно.
     */
    public static boolean shouldHideOwnPlayer() {
        // Вмешиваемся в рендер игрока ТОЛЬКО при переходе 3rd → 1st.
        // При 1st → 3rd модель видна сразу, поэтому OwnPlayerHider просто
        // ничего не должен делать.
        return targetType != null && finishAsFirstPerson;
    }
}