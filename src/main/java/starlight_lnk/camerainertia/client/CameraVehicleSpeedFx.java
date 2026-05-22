package starlight_lnk.camerainertia.client;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.monster.Strider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.phys.Vec3;
import starlight_lnk.camerainertia.config.ClientConfig;

/**
 * 🚀 Эффект скорости при езде на транспорте.
 *
 * Считает плавный нормализованный фактор скорости (0..1) с учётом типа ТС.
 * Этот фактор потребляют:
 *   - CameraFovController (через getFovBoost / getFactorRaw)
 *     → лёгкое отдаление FOV на разгоне
 *   - CameraTurnBlur      (через getBlurIntensity)
 *     → симметричный side-blur на высокой скорости
 *
 * Двухступенчатое сглаживание:
 *   1) rawSpeed → smoothedSpeed — фильтр от тиковых рывков физики;
 *   2) target → factor          — основной фильтр плавности для FX (медленный).
 *
 * Поддерживаются: лодки, лошади, верблюды, вагонетки + fallback для моддовых ТС.
 * Свинья/Стрейдер сознательно исключены (слишком медленные).
 *
 * 🎥 Работает только в 1st person:
 *   - в 3rd person tick() выполняет decay() (мягкое затухание),
 *   - все геттеры возвращают 0, чтобы FOV и блюр в 3rd person не подмешивались.
 */
public final class CameraVehicleSpeedFx {

    // ============================================================
    //                       НАСТРОЙКИ
    // ============================================================

    /** Глобальный switch. */
    private static final boolean ENABLED = true;

    /** Включён ли FOV-канал. */
    private static final boolean FOV_ENABLED = true;
    /** Максимальный вклад в FOV-множитель на пике (0.12 = +12% FOV). */
    private static final float FOV_STRENGTH = 0.12F;

    /** Включён ли side-blur канал. */
    private static final boolean BLUR_ENABLED = true;
    /** Максимальная интенсивность блюра на пике (0..1). */
    private static final float BLUR_STRENGTH = 0.55F;

    /** Скорость интерполяции factor → target. Меньше = плавнее. */
    private static final float FX_SMOOTHING = 0.06F;
    /** Скорость сглаживания самой rawSpeed. */
    private static final float SPEED_SMOOTHING = 0.20F;

    // === Пиковые скорости в блоках/тик (при них factor = 1.0) ===
    private static final float PEAK_BOAT     = 1.10F; // лёд = пик, вода = ~30%
    private static final float PEAK_HORSE    = 0.50F; // полный галоп
    private static final float PEAK_CAMEL    = 0.45F; // дэш
    private static final float PEAK_MINECART = 0.80F; // powered rails на максимуме
    private static final float PEAK_DEFAULT  = 0.60F; // моддовые ТС

    // ============================================================
    //                       СОСТОЯНИЕ
    // ============================================================

    private static float smoothedSpeed = 0.0F;
    private static float factor = 0.0F;
    private static float prevFactor = 0.0F;
    private static Class<?> prevVehicleType = null;

    private CameraVehicleSpeedFx() {}

    // ============================================================
    //                     ПУБЛИЧНОЕ API
    // ============================================================

    /** Нормализованный фактор 0..1 с учётом partialTick. В 3rd person → 0. */
    public static float getFactor(float partialTick) {
        if (!CameraViewUtils.isFirstPerson()) return 0.0F;
        return Mth.lerp(Mth.clamp(partialTick, 0.0F, 1.0F), prevFactor, factor);
    }

    /** Сырой текущий фактор (для tick-логики). В 3rd person → 0. */
    public static float getFactorRaw() {
        if (!CameraViewUtils.isFirstPerson()) return 0.0F;
        return factor;
    }

    /**
     * Вклад в FOV-множитель.
     * Возвращает значение для прибавления к итоговому множителю FOV.
     * На пике скорости вернёт ~+0.12 → итоговый FOV = base * 1.12.
     * В 3rd person → 0.
     */
    public static float getFovBoost(float partialTick) {
        if (!ENABLED || !FOV_ENABLED) return 0.0F;
        if (!CameraViewUtils.isFirstPerson()) return 0.0F;
        float f = getFactor(partialTick);
        // Мягкий ease-in: низкие скорости почти не отдаляют.
        float curved = (float) Math.pow(f, 1.5);
        return curved * FOV_STRENGTH;
    }

    /**
     * Интенсивность side-blur (0..1) для подмешивания в шейдер.
     * В 3rd person → 0.
     */
    public static float getBlurIntensity(float partialTick) {
        if (!ENABLED || !BLUR_ENABLED) return 0.0F;
        if (!CameraViewUtils.isFirstPerson()) return 0.0F;
        float f = getFactor(partialTick);
        // Квадратичная кривая: до 50% скорости почти нет блюра.
        float curved = f * f;
        return curved * BLUR_STRENGTH;
    }

    // ============================================================
    //                          ТИК
    // ============================================================

    public static void tick() {
        try {
            prevFactor = factor;

            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.player == null || mc.level == null || mc.isPaused()) {
                decay();
                return;
            }
            if (!ENABLED) { decay(); return; }

            // 🎥 В 3rd person — мягко гасим эффект, чтобы при возврате в 1st person
            // он плавно наростал с нуля, а не «прыгал» от ранее накопленной скорости.
            if (!CameraViewUtils.isFirstPerson()) {
                decay();
                // тип ТС всё же запоминаем, чтобы не было ложного «пересел на другой»
                Entity v = mc.player.getVehicle();
                prevVehicleType = (v != null) ? v.getClass() : null;
                return;
            }

            // Завязка на master switch инерции транспорта
            if (!ClientConfig.VEHICLE_INERTIA_ENABLED.get()) {
                decay();
                return;
            }

            Player player = mc.player;
            Entity vehicle = player.getVehicle();
            if (vehicle == null) {
                decay();
                prevVehicleType = null;
                return;
            }

            // Пересел на другой транспорт → жёсткий сброс сглаживания
            Class<?> currentType = vehicle.getClass();
            if (prevVehicleType != null && prevVehicleType != currentType) {
                smoothedSpeed = 0.0F;
                factor = 0.0F;
            }
            prevVehicleType = currentType;

            // === Сырая горизонтальная скорость ===
            Vec3 motion = vehicle.getDeltaMovement();
            double rawSpeed = Math.sqrt(motion.x * motion.x + motion.z * motion.z);

            // === Пик под тип транспорта ===
            float peak = getPeakSpeedFor(vehicle);
            if (peak <= 0.0F) {
                decay();
                return;
            }

            // === Этап 1: сглаживаем сырую скорость ===
            smoothedSpeed += ((float) rawSpeed - smoothedSpeed) * SPEED_SMOOTHING;

            // === Этап 2: считаем целевой фактор и плавно тянемся к нему ===
            float target = Mth.clamp(smoothedSpeed / peak, 0.0F, 1.0F);

            // Асимметрия: разгон чуть медленнее, торможение быстрее.
            float lerpSpeed = (target > factor) ? FX_SMOOTHING : FX_SMOOTHING * 1.4F;
            factor += (target - factor) * lerpSpeed;

            if (factor < 0.001F) factor = 0.0F;
            if (Float.isNaN(factor) || Float.isInfinite(factor)) factor = 0.0F;

        } catch (Throwable t) {
            t.printStackTrace();
            smoothedSpeed = 0.0F;
            factor = 0.0F;
            prevFactor = 0.0F;
            prevVehicleType = null;
        }
    }

    // ============================================================
    //                       ВНУТРЕННЕЕ
    // ============================================================

    /** Плавный спад при отсутствии транспорта / в 3rd person. */
    private static void decay() {
        smoothedSpeed *= 0.85F;
        factor *= 0.88F;
        if (smoothedSpeed < 0.001F) smoothedSpeed = 0.0F;
        if (factor < 0.001F) factor = 0.0F;
    }

    /** Пиковая скорость bps под тип ТС. 0 = эффект отключён для этого ТС. */
    private static float getPeakSpeedFor(Entity vehicle) {
        if (vehicle instanceof Boat)             return PEAK_BOAT;
        if (vehicle instanceof AbstractMinecart) return PEAK_MINECART;
        if (vehicle instanceof Camel)            return PEAK_CAMEL;
        if (vehicle instanceof AbstractHorse)    return PEAK_HORSE;
        if (vehicle instanceof Pig || vehicle instanceof Strider) {
            return 0.0F; // отключаем
        }
        return PEAK_DEFAULT;
    }
}