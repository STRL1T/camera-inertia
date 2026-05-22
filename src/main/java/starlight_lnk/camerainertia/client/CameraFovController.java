package starlight_lnk.camerainertia.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;

public class CameraFovController {

    private static float currentFovMultiplier = 1.0F;
    private static float prevFovMultiplier    = 1.0F;
    private static float targetFovMultiplier  = 1.0F;

    private static float impulseFov = 0.0F;

    private static float slowImpulseFov   = 0.0F;
    private static float slowImpulseDecay = 0.92F;

    public static void addImpulse(float amount) {
        impulseFov += amount;
    }

    public static void addSlowImpulse(float amount, float decay) {
        slowImpulseFov += amount;
        slowImpulseDecay = decay;
    }

    public static void tick() {
        try {
            Minecraft mc = Minecraft.getInstance();

            if (mc == null || mc.player == null || mc.level == null || mc.isPaused()) {
                prevFovMultiplier = currentFovMultiplier;
                currentFovMultiplier += (1.0F - currentFovMultiplier) * 0.15F;
                impulseFov     *= 0.7F;
                slowImpulseFov *= slowImpulseDecay;
                return;
            }

            // 🎥 Только в 1st person — в 3rd person плавно возвращаем FOV к 1.0
            if (!CameraViewUtils.isFirstPerson()) {
                prevFovMultiplier = currentFovMultiplier;
                targetFovMultiplier = 1.0F;
                currentFovMultiplier += (1.0F - currentFovMultiplier) * 0.15F;
                impulseFov     *= 0.7F;
                slowImpulseFov *= slowImpulseDecay;

                if (Math.abs(currentFovMultiplier - 1.0F) < 0.001F) {
                    currentFovMultiplier = 1.0F;
                }
                if (Math.abs(impulseFov)     < 0.001F) impulseFov     = 0.0F;
                if (Math.abs(slowImpulseFov) < 0.001F) slowImpulseFov = 0.0F;
                return;
            }

            Player player = mc.player;

            // ====== БАЗОВЫЙ TARGET ======
            float target = 1.0F;

            ItemStack using = player.getUseItem();
            boolean usingItem = player.isUsingItem();

            if (usingItem && (using.getItem() instanceof BowItem || using.getItem() instanceof CrossbowItem)) {
                int useTicks = player.getTicksUsingItem();
                float progress = Math.min(useTicks / 20.0F, 1.0F);
                target = 1.0F - 0.10F * progress;
            }
            else if (usingItem && using.getUseAnimation() == UseAnim.BLOCK) {
                int useTicks = player.getTicksUsingItem();
                float progress = Math.min(useTicks / 8.0F, 1.0F);
                target = 1.0F - 0.08F * progress;
            }
            else if (usingItem && using.getUseAnimation() == UseAnim.DRINK) {
                target = 0.97F;
            }
            else if (usingItem && using.getUseAnimation() == UseAnim.EAT) {
                target = 0.98F;
            }
            else if (player.isFallFlying()) {
                target = 1.08F;
            }
            else if (player.isSwimming()) {
                target = 1.03F;
            }
            else if (player.isUnderWater()) {
                target = 0.97F;
            }
            else if (player.isSprinting()) {
                target = 1.05F;
            }

            // 🚀 === Вклад скорости транспорта (плавное отдаление на разгоне) ===
            // Логика: берём МАКСИМУМ из текущего target и vehicleTarget,
            // чтобы не дрался со спринтом/полётом. Когда сидишь в лодке/лошади
            // — все ситуативные эффекты выше дают target = 1.0, и vehicle берёт верх.
            // Когда не на ТС — vehicleBoost = 0, ничего не меняется.
            float vehicleBoost = CameraVehicleSpeedFx.getFovBoost(1.0F);
            if (vehicleBoost > 0.0F) {
                float vehicleTarget = 1.0F + vehicleBoost;
                if (vehicleTarget > target) {
                    target = vehicleTarget;
                }
            }

            targetFovMultiplier = target;

            // Затухание импульсов
            impulseFov     *= 0.85F;
            slowImpulseFov *= slowImpulseDecay;

            if (slowImpulseDecay < 0.92F) {
                slowImpulseDecay = Math.min(0.92F, slowImpulseDecay + 0.005F);
            }

            // ====== ПЛАВНАЯ ИНТЕРПОЛЯЦИЯ ======
            prevFovMultiplier = currentFovMultiplier;
            float speed = (targetFovMultiplier < currentFovMultiplier) ? 0.20F : 0.14F;
            currentFovMultiplier += (targetFovMultiplier - currentFovMultiplier) * speed;

            if (Float.isNaN(currentFovMultiplier) || Float.isInfinite(currentFovMultiplier)) {
                currentFovMultiplier = 1.0F;
                prevFovMultiplier    = 1.0F;
                impulseFov           = 0.0F;
                slowImpulseFov       = 0.0F;
            }
        } catch (Throwable t) {
            currentFovMultiplier = 1.0F;
            prevFovMultiplier    = 1.0F;
            targetFovMultiplier  = 1.0F;
            impulseFov           = 0.0F;
            slowImpulseFov       = 0.0F;
        }
    }

    public static float getFovMultiplier(float partialTick) {
        if (Float.isNaN(partialTick) || Float.isInfinite(partialTick)) partialTick = 0.0F;
        if (partialTick < 0.0F) partialTick = 0.0F;
        if (partialTick > 1.0F) partialTick = 1.0F;

        float fov = prevFovMultiplier + (currentFovMultiplier - prevFovMultiplier) * partialTick;
        fov += impulseFov;
        fov += slowImpulseFov;

        if (Float.isNaN(fov) || Float.isInfinite(fov)) return 1.0F;

        if (fov < 0.5F) fov = 0.5F;
        if (fov > 1.5F) fov = 1.5F;

        return fov;
    }
}