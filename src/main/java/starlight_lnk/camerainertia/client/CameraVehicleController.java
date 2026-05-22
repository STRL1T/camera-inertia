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
 * Инерция камеры при езде на транспорте:
 *  - Лодка     → волны + ритм гребли вёслами
 *  - Лошадь    → RDR2-style FPP: двойной такт галопа, без крена, лёгкий yaw-свинг
 *  - Верблюд   → мягче, амплитуднее
 *  - Свинья/Стрейдер → лёгкое покачивание
 *  - Вагонетка → толчки на поворотах
 *  - Иное      → универсальная инерция
 *
 * 🆕 Также предоставляет getPedestrianMultiplier() — коэффициент, на который
 * домножается «пешеходная» инерция игрока (CameraMovementController) на транспорте.
 *
 * 🎥 Работает только в 1st person — в 3rd person весь эффект полностью отключается.
 */
public class CameraVehicleController {

    // 🔧 Глобальный множитель крена для ЛОДКИ
    private static final float BOAT_ROLL_SCALE = 0.25F;

    // 🆕 === КОЭФФИЦИЕНТЫ ОСЛАБЛЕНИЯ "ПЕШЕХОДНОЙ" ИНЕРЦИИ ИГРОКА ===
    private static final float PEDESTRIAN_MUL_HORSE    = 0.30F;
    private static final float PEDESTRIAN_MUL_CAMEL    = 0.25F;
    private static final float PEDESTRIAN_MUL_BOAT     = 0.10F;
    private static final float PEDESTRIAN_MUL_MINECART = 0.20F;
    private static final float PEDESTRIAN_MUL_PIGLIKE  = 0.40F;
    private static final float PEDESTRIAN_MUL_DEFAULT  = 0.35F;

    // === ИТОГОВОЕ СМЕЩЕНИЕ ===
    private static float pitchOffset = 0.0F;
    private static float yawOffset   = 0.0F;
    private static float rollOffset  = 0.0F;

    private static float prevPitchOffset = 0.0F;
    private static float prevYawOffset   = 0.0F;
    private static float prevRollOffset  = 0.0F;

    // === ВНУТРЕННИЕ КАНАЛЫ ===
    private static float pitchVel = 0.0F;
    private static float yawVel   = 0.0F;
    private static float rollVel  = 0.0F;

    // === ОСЦИЛЛЯЦИЯ (волны/общая) ===
    private static float oscPhase = 0.0F;

    // === ФАЗА ГРЕБЛИ (лодка) ===
    private static float rowPhase    = 0.0F;
    private static float rowSpeed    = 0.0F;

    // 🐎 === ФАЗА АЛЛЮРА (лошадь, RDR2-style) ===
    private static float gaitPhase   = 0.0F;
    private static float gaitSpeed   = 0.0F;

    // === ПАМЯТЬ ===
    private static Vec3    prevMotion       = Vec3.ZERO;
    private static float   prevVehicleYaw   = 0.0F;
    private static boolean prevHadVehicle   = false;
    private static boolean prevVehicleOnGnd = true;
    private static double  prevVehicleY     = 0.0;

    public static boolean isRiding() {
        try {
            Minecraft mc = Minecraft.getInstance();
            return mc != null && mc.player != null && mc.player.getVehicle() != null;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 🆕 Возвращает коэффициент, на который надо домножать «пешеходную» инерцию игрока
     * (CameraMovementController), когда тот сидит на транспорте.
     *
     * @return 1.0 если не на транспорте; иначе значение из PEDESTRIAN_MUL_*
     */
    public static float getPedestrianMultiplier() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.player == null) return 1.0F;

            Entity vehicle = mc.player.getVehicle();
            if (vehicle == null) return 1.0F;

            if (vehicle instanceof Boat)             return PEDESTRIAN_MUL_BOAT;
            if (vehicle instanceof AbstractMinecart) return PEDESTRIAN_MUL_MINECART;
            if (vehicle instanceof Camel)            return PEDESTRIAN_MUL_CAMEL;
            if (vehicle instanceof AbstractHorse)    return PEDESTRIAN_MUL_HORSE;
            if (vehicle instanceof Pig || vehicle instanceof Strider) return PEDESTRIAN_MUL_PIGLIKE;

            return PEDESTRIAN_MUL_DEFAULT;
        } catch (Throwable t) {
            return 0.0F;
        }
    }

    public static void tick() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.player == null || mc.level == null || mc.isPaused()) {
                damp();
                return;
            }

            if (!ClientConfig.VEHICLE_INERTIA_ENABLED.get()) {
                damp();
                prevHadVehicle = false;
                return;
            }

            // 🎥 Только в 1st person — в 3rd person полностью гасим эффект транспорта.
            // Память состояния (prevMotion / prevVehicleYaw / prevVehicleOnGnd / prevVehicleY)
            // всё равно обновляем, чтобы при возврате в 1st person не «дёрнуло» от старых данных.
            if (!CameraViewUtils.isFirstPerson()) {
                Player p = mc.player;
                Entity v = p.getVehicle();
                if (v != null) {
                    prevMotion       = v.getDeltaMovement();
                    prevVehicleYaw   = v.getYRot();
                    prevVehicleOnGnd = v.onGround();
                    prevVehicleY     = v.getY();
                    prevHadVehicle   = true;
                } else {
                    prevHadVehicle = false;
                }
                damp();
                return;
            }

            Player player = mc.player;
            Entity vehicle = player.getVehicle();

            if (vehicle == null) {
                damp();
                prevHadVehicle = false;
                return;
            }

            float strength = ClientConfig.VEHICLE_INERTIA_STRENGTH.get().floatValue();
            boolean isBoat  = vehicle instanceof Boat;
            boolean isHorse = vehicle instanceof AbstractHorse && !(vehicle instanceof Camel);

            Vec3 motion = vehicle.getDeltaMovement();
            double horizSpeed = Math.sqrt(motion.x * motion.x + motion.z * motion.z);

            Vec3 dMotion = motion.subtract(prevMotion);
            double dHoriz = Math.sqrt(dMotion.x * dMotion.x + dMotion.z * dMotion.z);

            float vehYaw = vehicle.getYRot();
            float dYaw = wrapDegrees(vehYaw - prevVehicleYaw);
            if (!prevHadVehicle) dYaw = 0.0F;

            boolean vehOnGround = vehicle.onGround();
            double  vehY        = vehicle.getY();

            // ===========================================================
            // КИВОК ПРИ УСКОРЕНИИ/ТОРМОЖЕНИИ (общее)
            // ===========================================================
            if (horizSpeed > 0.01 && dHoriz > 0.005) {
                Vec3 dir = motion.normalize();
                double accelAlong = dMotion.x * dir.x + dMotion.z * dir.z;
                float accelMul = isHorse ? 10.0F : 18.0F;
                pitchVel += (float)(-accelAlong * accelMul) * strength;
            }

            // ===========================================================
            // ПОВОРОТ → ROLL
            // ===========================================================
            if (Math.abs(dYaw) > 0.05F) {
                if (isHorse) {
                    yawVel += dYaw * 0.04F * strength;
                } else {
                    float rollFromYaw = dYaw * 0.20F * strength;
                    if (isBoat) {
                        rollFromYaw *= BOAT_ROLL_SCALE;
                    }
                    rollVel += rollFromYaw;
                }
            }

            // ===========================================================
            // СПЕЦИФИКА ПО ТИПУ ТРАНСПОРТА
            // ===========================================================
            float oscPitchAmp = 0.0F;
            float oscRollAmp  = 0.0F;
            float oscSpeed    = 0.0F;

            float rowPitchAdd = 0.0F;
            float rowRollAdd  = 0.0F;

            float gaitPitchAdd = 0.0F;
            float gaitYawAdd   = 0.0F;

            if (isBoat) {
                if (ClientConfig.VEHICLE_BOAT_WAVES.get()) {
                    float speedFactor = (float) Math.min(horizSpeed * 3.5, 1.0);
                    oscPitchAmp = (0.35F + speedFactor * 0.35F);
                    oscRollAmp  = (0.45F + speedFactor * 0.55F) * BOAT_ROLL_SCALE;
                    oscSpeed    = 0.05F + speedFactor * 0.03F;
                }

                if (ClientConfig.VEHICLE_BOAT_ROWING.get()) {
                    float targetRowSpeed = (float) Math.min(horizSpeed, 0.5);
                    rowSpeed += (targetRowSpeed - rowSpeed) * 0.15F;

                    if (rowSpeed > 0.015F) {
                        float rowFreq = 0.08F + rowSpeed * 0.55F;
                        rowPhase += rowFreq;
                        if (rowPhase > (float)(Math.PI * 2)) rowPhase -= (float)(Math.PI * 2);

                        float rowAmp = ClientConfig.VEHICLE_BOAT_ROWING_STRENGTH.get().floatValue();
                        float ampPitch = (0.45F + rowSpeed * 1.8F) * rowAmp * strength;
                        float ampRoll  = (0.10F + rowSpeed * 0.30F) * rowAmp * strength * BOAT_ROLL_SCALE;

                        float s = Mth.sin(rowPhase);
                        float profile;
                        if (s < 0) {
                            profile = -(float)Math.pow(-s, 0.7);
                        } else {
                            profile = (float)Math.pow(s, 1.3) * 0.6F;
                        }

                        rowPitchAdd = profile * ampPitch;
                        rowRollAdd  = Mth.sin(rowPhase * 2.0F + 0.7F) * ampRoll * 0.5F;
                    } else {
                        rowPhase *= 0.92F;
                    }
                }
            }
            else if (isHorse) {
                float gaitTarget = (float) Math.min(horizSpeed / 0.45, 1.0);
                gaitSpeed += (gaitTarget - gaitSpeed) * 0.10F;

                if (gaitSpeed > 0.02F) {
                    float gaitFreq = 0.10F + gaitSpeed * 0.40F;
                    gaitPhase += gaitFreq;
                    if (gaitPhase > (float)(Math.PI * 2)) gaitPhase -= (float)(Math.PI * 2);

                    float main = Mth.sin(gaitPhase);
                    float second = Mth.sin(gaitPhase * 2.0F - 0.6F) * 0.45F;
                    float gaitWave = main + second;

                    float pitchProfile;
                    if (gaitWave > 0) {
                        pitchProfile = (float)Math.pow(gaitWave, 0.85) * 0.7F;
                    } else {
                        pitchProfile = -(float)Math.pow(-gaitWave, 0.75);
                    }

                    float ampPitch = (0.20F + gaitSpeed * 1.30F) * strength;
                    gaitPitchAdd = pitchProfile * ampPitch;

                    float yawWave = Mth.sin(gaitPhase * 0.5F + 0.3F);
                    float ampYaw  = (0.08F + gaitSpeed * 0.45F) * strength;
                    gaitYawAdd = yawWave * ampYaw;
                } else {
                    gaitPhase *= 0.92F;
                }

                if (prevVehicleOnGnd && !vehOnGround && motion.y > 0.15) {
                    pitchVel += -1.8F * strength;
                }
                if (!prevVehicleOnGnd && vehOnGround) {
                    double fall = Math.max(0.0, prevVehicleY - vehY);
                    float impact = (float) Math.min(fall * 2.2, 3.0);
                    pitchVel += (1.2F + impact) * strength;
                    yawVel += (float)((Math.random() - 0.5) * 0.6 * strength);
                }
            }
            else if (vehicle instanceof Camel) {
                float speedFactor = (float) Math.min(horizSpeed * 3.5, 1.0);
                oscPitchAmp = speedFactor * 1.10F;
                oscRollAmp  = speedFactor * 0.65F;
                oscSpeed    = 0.18F + speedFactor * 0.10F;

                if (!prevVehicleOnGnd && vehOnGround) {
                    pitchVel += 1.2F * strength;
                }
            }
            else if (vehicle instanceof Pig || vehicle instanceof Strider) {
                float speedFactor = (float) Math.min(horizSpeed * 5.0, 1.0);
                oscPitchAmp = speedFactor * 0.55F;
                oscRollAmp  = speedFactor * 0.40F;
                oscSpeed    = 0.22F + speedFactor * 0.10F;
            }
            else if (vehicle instanceof AbstractMinecart) {
                if (Math.abs(dYaw) > 1.5F) {
                    pitchVel += 0.15F * strength;
                }
            }
            else {
                float speedFactor = (float) Math.min(horizSpeed * 4.0, 1.0);
                oscPitchAmp = speedFactor * 0.40F;
                oscRollAmp  = speedFactor * 0.30F;
                oscSpeed    = 0.20F;
            }

            // ===========================================================
            // ОСЦИЛЛЯЦИЯ (общая, НЕ для лошади)
            // ===========================================================
            oscPhase += oscSpeed;
            if (oscPhase > (float)(Math.PI * 2)) oscPhase -= (float)(Math.PI * 2);

            float oscPitch = Mth.sin(oscPhase)         * oscPitchAmp * strength;
            float oscRoll  = Mth.sin(oscPhase * 0.5F)  * oscRollAmp  * strength;

            // ===========================================================
            // ИНТЕГРАЦИЯ
            // ===========================================================
            prevPitchOffset = pitchOffset;
            prevYawOffset   = yawOffset;
            prevRollOffset  = rollOffset;

            pitchOffset += pitchVel;
            yawOffset   += yawVel;
            rollOffset  += rollVel;

            pitchVel *= 0.70F;
            yawVel   *= 0.75F;
            rollVel  *= 0.72F;

            pitchOffset *= 0.82F;
            yawOffset   *= 0.85F;
            rollOffset  *= 0.84F;

            pitchOffset += oscPitch * 0.25F;
            rollOffset  += oscRoll  * 0.25F;

            pitchOffset += rowPitchAdd * 0.35F;
            rollOffset  += rowRollAdd  * 0.35F;

            pitchOffset += gaitPitchAdd * 0.40F;
            yawOffset   += gaitYawAdd   * 0.35F;

            if (isHorse) {
                rollOffset *= 0.5F;
                rollVel    *= 0.5F;
                if (Math.abs(rollOffset) < 0.01F) rollOffset = 0.0F;
            }

            pitchOffset = Mth.clamp(pitchOffset, -4.0F, 4.0F);
            yawOffset   = Mth.clamp(yawOffset,   -2.5F, 2.5F);
            rollOffset  = Mth.clamp(rollOffset,  -5.0F, 5.0F);

            if (Math.abs(pitchOffset) < 0.001F) pitchOffset = 0.0F;
            if (Math.abs(yawOffset)   < 0.001F) yawOffset   = 0.0F;
            if (Math.abs(rollOffset)  < 0.001F) rollOffset  = 0.0F;

            if (Float.isNaN(pitchOffset) || Float.isInfinite(pitchOffset)) pitchOffset = 0.0F;
            if (Float.isNaN(yawOffset)   || Float.isInfinite(yawOffset))   yawOffset   = 0.0F;
            if (Float.isNaN(rollOffset)  || Float.isInfinite(rollOffset))  rollOffset  = 0.0F;

            prevMotion       = motion;
            prevVehicleYaw   = vehYaw;
            prevVehicleOnGnd = vehOnGround;
            prevVehicleY     = vehY;
            prevHadVehicle   = true;

        } catch (Throwable t) {
            t.printStackTrace();
            pitchOffset = yawOffset = rollOffset = 0.0F;
            pitchVel = yawVel = rollVel = 0.0F;
            rowPhase = 0.0F;
            rowSpeed = 0.0F;
            gaitPhase = 0.0F;
            gaitSpeed = 0.0F;
            prevMotion = Vec3.ZERO;
            prevHadVehicle = false;
        }
    }

    private static void damp() {
        prevPitchOffset = pitchOffset;
        prevYawOffset   = yawOffset;
        prevRollOffset  = rollOffset;

        pitchOffset *= 0.80F;
        yawOffset   *= 0.80F;
        rollOffset  *= 0.80F;

        pitchVel *= 0.70F;
        yawVel   *= 0.70F;
        rollVel  *= 0.70F;

        rowSpeed *= 0.85F;
        rowPhase *= 0.92F;

        gaitSpeed *= 0.85F;
        gaitPhase *= 0.92F;

        if (Math.abs(pitchOffset) < 0.001F) pitchOffset = 0.0F;
        if (Math.abs(yawOffset)   < 0.001F) yawOffset   = 0.0F;
        if (Math.abs(rollOffset)  < 0.001F) rollOffset  = 0.0F;

        oscPhase = 0.0F;
        prevMotion = Vec3.ZERO;
    }

    private static float wrapDegrees(float v) {
        v = v % 360.0F;
        if (v >= 180.0F) v -= 360.0F;
        if (v < -180.0F) v += 360.0F;
        return v;
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