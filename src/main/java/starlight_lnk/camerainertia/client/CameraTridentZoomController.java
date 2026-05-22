package starlight_lnk.camerainertia.client;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import starlight_lnk.camerainertia.config.ClientConfig;

/**
 * Плавный FOV-зум при заряде трезубца.
 * Аналогичен зуму при заряде лука, но со своим характером.
 *
 * - Чем дольше держишь — тем сильнее зум (до максимума за ~0.7 сек)
 * - При Riptide-режиме (дождь/вода) зум слабее, т.к. ты готовишься к полёту
 * - Плавно возвращается при отпускании
 *
 * 🎥 Работает ТОЛЬКО в 1st person. В 3rd person плавно сбрасывается до 1.0.
 */
public class CameraTridentZoomController {

    // Текущий множитель зума (1.0 = без зума, <1.0 = сужение FOV)
    private static float currentZoom = 1.0F;
    private static float prevZoom = 1.0F;

    public static void tick() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.player == null || mc.level == null || mc.isPaused()) {
                fadeOut();
                return;
            }

            // 🎥 В 3rd person зум прицеливания не имеет смысла — плавно гасим.
            if (!CameraViewUtils.isFirstPerson()) {
                fadeOut();
                return;
            }

            if (!ClientConfig.TRIDENT_ZOOM_ENABLED.get()) {
                fadeOut();
                return;
            }

            Player player = mc.player;

            // === Проверяем, заряжает ли игрок трезубец ===
            if (!player.isUsingItem()) {
                fadeOut();
                return;
            }

            ItemStack using = player.getUseItem();
            if (using.isEmpty() || !(using.getItem() instanceof TridentItem)) {
                fadeOut();
                return;
            }

            // === Считаем длительность зарядки ===
            int useTicks = player.getTicksUsingItem();
            // Полный заряд за ~14 тиков (как у трезубца перед броском)
            float chargeProgress = Mth.clamp(useTicks / 14.0F, 0.0F, 1.0F);

            // === Определяем, готовится ли Riptide-полёт ===
            // Riptide активен только если есть зачарование И игрок в воде/под дождём
            int riptideLevel = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.RIPTIDE, using);
            boolean riptideReady = riptideLevel > 0 && (player.isInWaterOrRain());

            // === Целевая сила зума ===
            float maxZoomStrength = ClientConfig.TRIDENT_ZOOM_STRENGTH.get().floatValue();

            // При Riptide зум слабее (игрок готовится к полёту, не к броску)
            if (riptideReady) {
                maxZoomStrength *= 0.35F;
            }

            // Зум: 1.0 → (1.0 - maxZoomStrength)
            // Например, при maxZoomStrength = 0.25 → FOV сожмётся до 75% от обычного
            float targetZoom = 1.0F - (maxZoomStrength * chargeProgress);

            prevZoom = currentZoom;

            // Плавное приближение к целевому значению
            currentZoom += (targetZoom - currentZoom) * 0.25F;

            // Лимиты безопасности
            currentZoom = Mth.clamp(currentZoom, 0.3F, 1.0F);
            if (Float.isNaN(currentZoom) || Float.isInfinite(currentZoom)) currentZoom = 1.0F;

        } catch (Throwable t) {
            t.printStackTrace();
            fadeOut();
        }
    }

    private static void fadeOut() {
        prevZoom = currentZoom;
        // Плавное возвращение к 1.0
        currentZoom += (1.0F - currentZoom) * 0.20F;
        if (Math.abs(currentZoom - 1.0F) < 0.001F) currentZoom = 1.0F;
    }

    /**
     * Возвращает множитель FOV (1.0 = без изменений, <1.0 = зум).
     */
    public static float getFovMultiplier(float partialTick) {
        return Mth.lerp(partialTick, prevZoom, currentZoom);
    }

    /**
     * true, если зум сейчас активен (для проверок).
     */
    public static boolean isZooming() {
        return currentZoom < 0.995F;
    }
}