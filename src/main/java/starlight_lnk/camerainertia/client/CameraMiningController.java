package starlight_lnk.camerainertia.client;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import starlight_lnk.camerainertia.config.ClientConfig;

public class CameraMiningController {

    // === Камера ===
    private static float pitchOffset = 0.0F, prevPitchOffset = 0.0F;
    private static float yawOffset   = 0.0F, prevYawOffset   = 0.0F;
    private static float rollOffset  = 0.0F, prevRollOffset  = 0.0F;

    private static float strikePhase = 0.0F;
    private static float activity = 0.0F;
    private static boolean wasMining = false;

    /** Период удара в тиках. */
    private static final float STRIKE_PERIOD_TICKS = 11.0F;

    /** Счётчик ударов — для чередования направления yaw/roll. */
    private static int strikeCounter = 0;
    private static float lastPhase = 0.0F;

    public static void tick() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.player == null || mc.level == null || mc.isPaused()) {
                fadeOut(); resetState(); return;
            }
            if (!ClientConfig.MINING_INERTIA_ENABLED.get()) { fadeOut(); resetState(); return; }
            if (!CameraViewUtils.isFirstPerson())            { fadeOut(); resetState(); return; }

            Player player = mc.player;

            if (!mc.options.keyAttack.isDown())              { fadeOut(); resetState(); return; }

            ItemStack mainHand = player.getMainHandItem();
            if (!(mainHand.getItem() instanceof PickaxeItem)) { fadeOut(); resetState(); return; }

            if (mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.BLOCK) {
                fadeOut(); resetState(); return;
            }
            if (player.isSpectator())                         { fadeOut(); resetState(); return; }

            BlockHitResult bhr = (BlockHitResult) mc.hitResult;
            BlockState state = mc.level.getBlockState(bhr.getBlockPos());
            if (state.isAir())                                { fadeOut(); resetState(); return; }

            // ============================================================
            // === АНИМАЦИЯ УДАРА =========================================
            // ============================================================

            activity += (1.0F - activity) * 0.35F;
            if (activity > 1.0F) activity = 1.0F;

            if (!wasMining) {
                strikePhase = 0.0F;
                lastPhase = 0.0F;
            }
            wasMining = true;

            lastPhase = strikePhase;
            strikePhase += 1.0F / STRIKE_PERIOD_TICKS;

            if (strikePhase >= 1.0F) {
                strikePhase -= 1.0F;
                strikeCounter++;
            }

            // === ПРОФИЛЬ УДАРА ===
            float pitchProfile;
            float impactShake = 0.0F;

            if (strikePhase < 0.35F) {
                // ЗАМАХ — easeOutQuad от 0 до -0.5
                float t = strikePhase / 0.35F;
                pitchProfile = -0.5F * (1.0F - (1.0F - t) * (1.0F - t));
            }
            else if (strikePhase < 0.55F) {
                // УДАР — резкий рывок от -0.5 до +1.0 (easeInQuad)
                float t = (strikePhase - 0.35F) / 0.20F;
                float eased = t * t;
                pitchProfile = Mth.lerp(eased, -0.5F, 1.0F);

                // Высокочастотная тряска в момент контакта
                float shakeIntensity = (1.0F - t);
                impactShake = (float) Math.sin(strikePhase * 180.0F) * 0.25F * shakeIntensity;
            }
            else {
                // ВОЗВРАТ — easeOutCubic от +1.0 до 0
                float t = (strikePhase - 0.55F) / 0.45F;
                float eased = 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
                pitchProfile = Mth.lerp(eased, 1.0F, 0.0F);
            }

            float strength = ClientConfig.MINING_INERTIA_STRENGTH.get().floatValue();
            float amplitude = 0.75F * strength * activity;

            // Yaw + Roll — чередование лево/право
            float side = ((strikeCounter & 1) == 0) ? 1.0F : -1.0F;

            float yawProfile;
            float rollProfile;
            if (strikePhase < 0.35F) {
                float t = strikePhase / 0.35F;
                yawProfile  = side * -0.3F * t;
                rollProfile = side *  0.4F * t;
            } else if (strikePhase < 0.55F) {
                float t = (strikePhase - 0.35F) / 0.20F;
                yawProfile  = Mth.lerp(t, side * -0.3F, side *  0.2F);
                rollProfile = Mth.lerp(t, side *  0.4F, side * -0.3F);
            } else {
                float t = (strikePhase - 0.55F) / 0.45F;
                float eased = 1.0F - (1.0F - t) * (1.0F - t);
                yawProfile  = Mth.lerp(eased, side *  0.2F, 0.0F);
                rollProfile = Mth.lerp(eased, side * -0.3F, 0.0F);
            }

            prevPitchOffset = pitchOffset;
            prevYawOffset   = yawOffset;
            prevRollOffset  = rollOffset;

            pitchOffset = (pitchProfile + impactShake) * amplitude;
            yawOffset   = yawProfile  * amplitude * 0.6F;
            rollOffset  = rollProfile * amplitude * 0.7F;

            pitchOffset = Mth.clamp(pitchOffset, -1.5F, 2.2F);
            yawOffset   = Mth.clamp(yawOffset,   -1.0F, 1.0F);
            rollOffset  = Mth.clamp(rollOffset,  -1.2F, 1.2F);

            if (Float.isNaN(pitchOffset) || Float.isInfinite(pitchOffset)) pitchOffset = 0.0F;
            if (Float.isNaN(yawOffset)   || Float.isInfinite(yawOffset))   yawOffset   = 0.0F;
            if (Float.isNaN(rollOffset)  || Float.isInfinite(rollOffset))  rollOffset  = 0.0F;

        } catch (Throwable t) {
            t.printStackTrace();
            pitchOffset = prevPitchOffset = 0.0F;
            yawOffset   = prevYawOffset   = 0.0F;
            rollOffset  = prevRollOffset  = 0.0F;
            activity = 0.0F;
            strikePhase = 0.0F;
            strikeCounter = 0;
            wasMining = false;
        }
    }

    private static void resetState() {
        wasMining = false;
    }

    private static void fadeOut() {
        prevPitchOffset = pitchOffset;
        prevYawOffset   = yawOffset;
        prevRollOffset  = rollOffset;

        activity *= 0.75F;
        pitchOffset *= 0.75F;
        yawOffset   *= 0.75F;
        rollOffset  *= 0.75F;

        if (Math.abs(pitchOffset) < 0.001F) pitchOffset = 0.0F;
        if (Math.abs(yawOffset)   < 0.001F) yawOffset   = 0.0F;
        if (Math.abs(rollOffset)  < 0.001F) rollOffset  = 0.0F;
        if (activity < 0.01F) activity = 0.0F;
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