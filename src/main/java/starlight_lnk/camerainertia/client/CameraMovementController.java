package starlight_lnk.camerainertia.client;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * 🎥 Контроллер ДОПОЛНИТЕЛЬНЫХ движений камеры при перемещении игрока.
 *
 * ⚠️ ВАЖНО: вся вертикальная инерция (pitch при прыжке, приземлении,
 * входе/выходе из воды) теперь живёт в {@link CameraPitchController}.
 * Этот контроллер раньше дублировал её, что давало двойной кик и
 * характерную «дёрганость» — теперь это полностью убрано.
 *
 * Здесь остался ТОЛЬКО один эффект:
 *   - Лёгкий ДЕТЕРМИНИРОВАННЫЙ roll-shake при тяжёлом приземлении
 *     (4+ блока высоты). Это добавляет ощущение веса, не создавая
 *     случайной болтанки.
 *
 * Случайный {@code randSigned}, который раньше дёргал камеру в стороны,
 * полностью убран — он и создавал «укачивающее» ощущение.
 */
public class CameraMovementController {

    private static float pitchOffset = 0.0F;
    private static float yawOffset   = 0.0F;
    private static float rollOffset  = 0.0F;

    private static float prevPitchOffset = 0.0F;
    private static float prevYawOffset   = 0.0F;
    private static float prevRollOffset  = 0.0F;

    // ============================================================
    //   СОСТОЯНИЕ ИГРОКА (для детекта приземления)
    // ============================================================

    private static boolean wasOnGround = true;
    private static double  takeoffY    = 0.0;
    private static boolean tracking    = false;

    /**
     * Знак следующего roll-импульса. Чередуется (+1 → -1 → +1 …),
     * чтобы при последовательных тяжёлых приземлениях не было одинакового
     * наклона в одну сторону. Это даёт «правдоподобную» вариативность
     * без случайности.
     */
    private static float nextRollSign = 1.0F;

    private static final float DAMP = 0.78F; // мягче, чем раньше (было 0.62)

    public static void tick() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.player == null || mc.level == null || mc.isPaused()) {
                damp();
                return;
            }

            // 🎥 Только в 1st person — в 3rd person плавно гасим и обновляем состояния,
            // чтобы при возврате не было ложного «приземления».
            if (!CameraViewUtils.isFirstPerson()) {
                Player p = mc.player;
                wasOnGround = p.onGround();
                tracking    = false;
                takeoffY    = 0.0;

                damp();
                if (Math.abs(pitchOffset) < 0.001F) pitchOffset = 0.0F;
                if (Math.abs(yawOffset)   < 0.001F) yawOffset   = 0.0F;
                if (Math.abs(rollOffset)  < 0.001F) rollOffset  = 0.0F;
                return;
            }

            Player player = mc.player;
            boolean onGround = player.onGround();
            double  posY     = player.getY();
            Vec3    motion   = player.getDeltaMovement();

            // ============================================================
            //   ОТСЛЕЖИВАНИЕ ПОЛЁТА
            // ============================================================
            // Запоминаем Y отрыва, чтобы знать реальную высоту падения
            // (а не просто скорость в момент удара).
            if (!onGround) {
                if (!tracking) {
                    tracking = true;
                    takeoffY = posY;
                }
            }

            // ============================================================
            //   ПРИЗЕМЛЕНИЕ — только roll-shake для тяжёлых случаев
            // ============================================================
            if (!wasOnGround && onGround && tracking) {
                double fallHeight = takeoffY - posY;
                if (fallHeight < 0) fallHeight = 0;

                // Тяжёлое падение (4+ блока) → лёгкий боковой наклон.
                // Подбирали так, чтобы НЕ перетягивать с пружиной pitch:
                //   - на 4 блоках  → ~0.6°
                //   - на 10 блоках → ~1.2°
                //   - на 23 блоках → ~2.0°
                if (fallHeight > 4.0) {
                    float over = (float) (fallHeight - 4.0);
                    float magnitude = 0.6F + (float) Math.sqrt(over) * 0.25F;
                    if (magnitude > 2.0F) magnitude = 2.0F;

                    rollOffset += magnitude * nextRollSign;
                    // Чередуем сторону для следующего раза
                    nextRollSign = -nextRollSign;
                }

                tracking = false;
                takeoffY = 0.0;
            }

            // Сбрасываем отслеживание, если игрок снова на земле
            // (например, после плавания в воде, где приземления как такового нет)
            if (onGround) {
                tracking = false;
            }

            wasOnGround = onGround;

            // ============================================================
            //   ЗАТУХАНИЕ
            // ============================================================
            prevPitchOffset = pitchOffset;
            prevYawOffset   = yawOffset;
            prevRollOffset  = rollOffset;

            pitchOffset *= DAMP;
            yawOffset   *= DAMP;
            rollOffset  *= DAMP;

            if (Math.abs(pitchOffset) < 0.001F) pitchOffset = 0.0F;
            if (Math.abs(yawOffset)   < 0.001F) yawOffset   = 0.0F;
            if (Math.abs(rollOffset)  < 0.001F) rollOffset  = 0.0F;

        } catch (Throwable t) {
            t.printStackTrace();
            pitchOffset = yawOffset = rollOffset = 0.0F;
        }
    }

    private static void damp() {
        prevPitchOffset = pitchOffset;
        prevYawOffset   = yawOffset;
        prevRollOffset  = rollOffset;
        pitchOffset *= DAMP;
        yawOffset   *= DAMP;
        rollOffset  *= DAMP;
    }

    public static float getPitchOffset(float partialTick) {
        return Mth.lerp(partialTick, prevPitchOffset, pitchOffset);
    }
    public static float getYawOffset(float partialTick) {
        return Mth.lerp(partialTick, prevYawOffset, yawOffset);
    }
    public static float getRollOffset(float partialTick) {
        return Mth.lerp(partialTick, prevRollOffset, rollOffset);
    }
}