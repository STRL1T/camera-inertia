package starlight_lnk.camerainertia.client;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import starlight_lnk.camerainertia.config.ClientConfig;

/**
 * Отзывчивая процедурная анимация ходьбы.
 * Амплитуда снижена на 50% для более деликатного эффекта.
 * ТЕПЕРЬ СИНХРОНИЗИРОВАНА С КОНФИГОМ (ОТКЛЮЧАЕТСЯ В КЛАССИЧЕСКОМ РЕЖИМЕ).
 */
public class CameraWalkController {

    private static float walkPhase = 0.0F;

    // Плавная скорость для интерполяции
    private static float smoothedSpeed = 0.0F;

    private static float currentPitch = 0.0F;
    private static float currentRoll  = 0.0F;
    private static float currentYaw   = 0.0F;

    private static float prevPitch = 0.0F;
    private static float prevRoll  = 0.0F;
    private static float prevYaw   = 0.0F;

    public static void tick() {
        try {
            Minecraft mc = Minecraft.getInstance();

            if (mc == null || mc.player == null || mc.level == null || mc.isPaused()) {
                damp();
                return;
            }

            if (!CameraViewUtils.isFirstPerson() || mc.player.isFallFlying() || mc.player.isPassenger()) {
                damp();
                return;
            }

            // ЖЕСТКИЙ БЛОК: Отключаем покачивание в Классическом режиме (или если ползунок на нуле)
            if (!ClientConfig.MOVEMENT_ANIMATIONS_ENABLED.get() || ClientConfig.MOVEMENT_INTENSITY.get() == 0.0) {
                damp();
                return;
            }

            Player player = mc.player;
            Vec3 motion = player.getDeltaMovement();

            // Считаем только горизонтальную скорость (XZ)
            double horizSpeed = Math.sqrt(motion.x * motion.x + motion.z * motion.z);

            // Если игрок летит в креативе или плывет, эффект ходьбы отключаем
            if (!player.onGround() && !player.isInWater()) {
                horizSpeed = 0.0;
            }

            // Быстрая интерполяция скорости
            smoothedSpeed += (float) (horizSpeed - smoothedSpeed) * 0.4F;

            // Двигаем фазу шага в зависимости от скорости.
            walkPhase += smoothedSpeed * 3.5F;
            if (walkPhase > (float) Math.PI * 2) {
                walkPhase -= (float) Math.PI * 2;
            }

            // Рассчитываем целевые значения, если игрок движется
            float targetPitch = 0.0F;
            float targetRoll  = 0.0F;
            float targetYaw   = 0.0F;

            if (smoothedSpeed > 0.01F) {
                // Получаем множитель из ползунка конфига
                float configMultiplier = ClientConfig.MOVEMENT_INTENSITY.get().floatValue();

                // Базовая амплитуда умножается на 0.5F (снижение эффекта на 50%) И на ползунок конфига!
                float amplitude = Math.min(smoothedSpeed * 6.0F, 1.5F) * 0.5F * configMultiplier;

                // PITCH: Кивок головой
                targetPitch = Mth.sin(walkPhase * 2.0F) * amplitude * 1.2F;

                // ROLL: Наклон тела влево/вправо при переносе веса
                targetRoll = Mth.cos(walkPhase) * amplitude * 1.5F;

                // YAW: Легкое покачивание головы влево-вправо
                targetYaw = Mth.sin(walkPhase + 1.0F) * amplitude * 0.4F;
            }

            prevPitch = currentPitch;
            prevRoll  = currentRoll;
            prevYaw   = currentYaw;

            // Мягкая, но быстрая привязка к цели (snappy)
            currentPitch += (targetPitch - currentPitch) * 0.5F;
            currentRoll  += (targetRoll - currentRoll)   * 0.5F;
            currentYaw   += (targetYaw - currentYaw)     * 0.5F;

        } catch (Throwable t) {
            damp();
        }
    }

    private static void damp() {
        prevPitch = currentPitch;
        prevRoll  = currentRoll;
        prevYaw   = currentYaw;

        currentPitch *= 0.6F;
        currentRoll  *= 0.6F;
        currentYaw   *= 0.6F;
        smoothedSpeed *= 0.6F;
    }

    public static float getPitch(float partialTick) { return Mth.lerp(partialTick, prevPitch, currentPitch); }
    public static float getRoll(float partialTick)  { return Mth.lerp(partialTick, prevRoll, currentRoll); }
    public static float getYaw(float partialTick)   { return Mth.lerp(partialTick, prevYaw, currentYaw); }
}