package starlight_lnk.camerainertia.client;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import starlight_lnk.camerainertia.config.ClientConfig;

/**
 * Эффект "воздушной тряски" при свободном падении.
 *
 * Идея: чем дольше игрок падает с высоты, тем сильнее камеру потряхивает —
 * как будто воздушный поток мешает удерживать голову ровно.
 *
 * - Не срабатывает на короткие прыжки (есть порог в тиках падения).
 * - Не работает в воде, на лестнице, при элитрах в режиме полёта,
 *   при Slow Falling, в Creative-полёте, при Levitation.
 * - Тряска состоит из суммы синусоид с разными частотами + лёгкий шум,
 *   чтобы движение ощущалось органично, а не механически.
 * - При приземлении интенсивность плавно угасает.
 * - Работает только в 1st person.
 */
public class CameraFallShakeController {

    // === Состояние ===
    private static int fallTicks = 0;          // сколько тиков подряд игрок падает
    private static float intensity = 0.0F;     // текущая интенсивность тряски [0..1]
    private static float prevIntensity = 0.0F; // для интерполяции

    // Накопленный "временной" счётчик для синусоид (растёт, пока есть тряска)
    private static float shakeTime = 0.0F;

    // Текущие смещения (вычисляются раз в тик, интерполируются по partial)
    private static float pitchNow = 0F, pitchPrev = 0F;
    private static float yawNow   = 0F, yawPrev   = 0F;
    private static float rollNow  = 0F, rollPrev  = 0F;

    // === Константы характера ===
    private static final float RAMP_TICKS    = 30.0F; // за сколько тиков выходим на максимум
    private static final float FADE_SPEED    = 0.12F; // скорость спада при приземлении
    private static final float ATTACK_SPEED  = 0.06F; // скорость нарастания intensity

    // Базовые амплитуды (в градусах) при intensity = 1.0
    private static final float BASE_PITCH_AMP = 0.825F;
    private static final float BASE_YAW_AMP   = 0.825F;
    private static final float BASE_ROLL_AMP  = 1.65F;

    // Терминальная скорость падения в Minecraft ≈ -3.92 блока/тик
    private static final float TERMINAL_SPEED = 3.5F;

    public static void tick() {
        try {
            // Сдвигаем "прошлые" значения для интерполяции
            prevIntensity = intensity;
            pitchPrev = pitchNow;
            yawPrev   = yawNow;
            rollPrev  = rollNow;

            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.player == null || mc.level == null || mc.isPaused()) {
                fadeOut();
                return;
            }

            // 🎥 Только в 1st person — в 3rd person полностью отключаем тряску
            if (!CameraViewUtils.isFirstPerson()) {
                fadeOut();
                return;
            }

            if (!ClientConfig.FALL_SHAKE_ENABLED.get()) {
                fadeOut();
                return;
            }

            Player player = mc.player;

            // === Исключающие условия ===
            // Креативный/спектатор-полёт
            if (player.getAbilities().flying) {
                fadeOut();
                return;
            }
            // Элитры в активном полёте
            if (player.isFallFlying()) {
                fadeOut();
                return;
            }
            // Slow Falling — игрок словно парит, никакого ветра
            if (player.hasEffect(MobEffects.SLOW_FALLING)) {
                fadeOut();
                return;
            }
            // Levitation — обратное движение, не падение
            if (player.hasEffect(MobEffects.LEVITATION)) {
                fadeOut();
                return;
            }
            // В жидкости — там своя физика
            if (player.isInWater() || player.isInLava() || player.isSwimming()) {
                fadeOut();
                return;
            }
            // Висит на лестнице / лозе / scaffolding
            if (player.onClimbable()) {
                fadeOut();
                return;
            }
            // На земле
            if (player.onGround()) {
                fadeOut();
                return;
            }
            // В транспорте
            if (player.isPassenger()) {
                fadeOut();
                return;
            }

            // === Проверяем, реально ли падает ===
            Vec3 motion = player.getDeltaMovement();
            // Берём именно скорость падения (отрицательная Y → положительная величина)
            float fallSpeed = (float) -motion.y;

            // Если игрок ещё на подъёме (после прыжка) — это не падение
            if (fallSpeed < 0.25F) {
                // Прыжок только-только / подъём — мягко гасим
                fadeOut();
                return;
            }

            // === Накапливаем тики падения ===
            fallTicks++;

            int threshold = ClientConfig.FALL_SHAKE_THRESHOLD_TICKS.get();

            // Пока не достигли порога — никакой тряски
            if (fallTicks < threshold) {
                // Но плавно гасим, если что-то ещё осталось
                intensity = Math.max(0F, intensity - FADE_SPEED);
                computeOffsets(0F);
                return;
            }

            // === Считаем целевую интенсивность ===
            // Прогресс по длительности падения после порога
            float durationFactor = Mth.clamp((fallTicks - threshold) / RAMP_TICKS, 0F, 1F);

            // Прогресс по скорости (0 при малой скорости → 1 на терминальной)
            float speedFactor = Mth.clamp(fallSpeed / TERMINAL_SPEED, 0F, 1F);

            // Комбинируем: длительность — основа, скорость — модификатор
            float target = durationFactor * (0.55F + 0.45F * speedFactor);

            // Применяем глобальный множитель из конфига
            float strength = ClientConfig.FALL_SHAKE_STRENGTH.get().floatValue();
            target *= strength;
            target = Mth.clamp(target, 0F, 1.5F); // даём чуть выше 1.0 при большом strength

            // Плавный подгон intensity → target
            if (intensity < target) {
                intensity += ATTACK_SPEED;
                if (intensity > target) intensity = target;
            } else {
                intensity -= FADE_SPEED * 0.5F;
                if (intensity < target) intensity = target;
            }

            // Время для синусоид
            shakeTime += 1.0F;

            computeOffsets(intensity);

        } catch (Throwable t) {
            t.printStackTrace();
            fadeOut();
        }
    }

    private static void fadeOut() {
        fallTicks = 0;
        intensity = Math.max(0F, intensity - FADE_SPEED);
        if (intensity < 0.001F) {
            intensity = 0F;
            shakeTime = 0F;
        }
        computeOffsets(intensity);
    }

    /**
     * Вычисляет смещения по трём осям на основе текущей интенсивности.
     * Используем сумму синусоид с разными частотами и фазами + лёгкий "шум"
     * через ещё одну быструю синусоиду — это даёт органичное движение.
     */
    private static void computeOffsets(float i) {
        if (i <= 0.0001F) {
            pitchNow = 0F;
            yawNow   = 0F;
            rollNow  = 0F;
            return;
        }

        float t = shakeTime;

        // Pitch: медленные качания + быстрая мелкая дрожь
        float pitch =
                Mth.sin(t * 0.31F)        * 0.6F
                        + Mth.sin(t * 0.77F + 1.7F) * 0.4F
                        + Mth.sin(t * 1.43F + 0.5F) * 0.15F;

        // Yaw: другие частоты и фазы → не синхронен с pitch
        float yaw =
                Mth.sin(t * 0.27F + 1.3F) * 0.55F
                        + Mth.sin(t * 0.83F + 0.7F) * 0.45F
                        + Mth.sin(t * 1.61F + 2.2F) * 0.12F;

        // Roll: самая выраженная ось — "болтает" сильнее всего
        float roll =
                Mth.sin(t * 0.23F + 2.1F) * 0.7F
                        + Mth.sin(t * 0.61F + 1.1F) * 0.3F
                        + Mth.sin(t * 1.17F + 0.3F) * 0.15F;

        pitchNow = pitch * BASE_PITCH_AMP * i;
        yawNow   = yaw   * BASE_YAW_AMP   * i;
        rollNow  = roll  * BASE_ROLL_AMP  * i;

        // Безопасные лимиты
        pitchNow = Mth.clamp(pitchNow, -3F, 3F);
        yawNow   = Mth.clamp(yawNow,   -3F, 3F);
        rollNow  = Mth.clamp(rollNow,  -5F, 5F);
    }

    // === Публичные геттеры (вызывает ClientForgeEvents) ===

    public static float getPitchOffset(float partialTick) {
        return Mth.lerp(partialTick, pitchPrev, pitchNow);
    }

    public static float getYawOffset(float partialTick) {
        return Mth.lerp(partialTick, yawPrev, yawNow);
    }

    public static float getRollOffset(float partialTick) {
        return Mth.lerp(partialTick, rollPrev, rollNow);
    }

    public static float getIntensity(float partialTick) {
        return Mth.lerp(partialTick, prevIntensity, intensity);
    }

    public static boolean isActive() {
        return intensity > 0.005F;
    }
}