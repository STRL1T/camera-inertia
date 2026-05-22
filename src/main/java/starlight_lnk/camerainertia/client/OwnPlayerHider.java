package starlight_lnk.camerainertia.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import starlight_lnk.camerainertia.CameraInertia;

/**
 * 🙈 Плавное «таяние» модели собственного игрока во время анимации
 * переключения перспективы (F5).
 *
 * Алгоритм:
 *  - На Pre  — выставляем RenderSystem.setShaderColor(1, 1, 1, alpha),
 *              где alpha берётся из CameraPerspectiveController.
 *              Все entity-translucent слои модели становятся прозрачными.
 *  - На Post — возвращаем shader color в (1, 1, 1, 1), чтобы не повлиять
 *              на последующие рендеры (HUD, рука, частицы и т.д.).
 *  - Если альфа < 5% — полностью отменяем рендер (чистое «нет модели»,
 *              а не «грязный полупрозрачный силуэт»).
 *
 * Других игроков в мультиплеере это НЕ затрагивает — проверка строго по
 * mc.player == event.getEntity(). На игровую логику, хитбоксы и тени
 * других сущностей эффект не влияет.
 */
@Mod.EventBusSubscriber(
        modid = CameraInertia.MODID,
        bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT
)
public final class OwnPlayerHider {

    private OwnPlayerHider() {}

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        if (!CameraPerspectiveController.shouldHideOwnPlayer()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer self = mc.player;
        if (self == null) return;
        if (event.getEntity() != self) return;

        float partial = (float) event.getPartialTick();

        // Полностью прозрачно → не тратим GPU на грязный «призрак»
        if (CameraPerspectiveController.shouldFullyHideOwnPlayer(partial)) {
            event.setCanceled(true);
            return;
        }

        float alpha = CameraPerspectiveController.getOwnPlayerAlpha(partial);

        // Подменяем глобальный shader color → все translucent-слои модели
        // отрисуются с этой альфой.
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        // Всегда восстанавливаем — даже если на Pre не вмешивались,
        // лишний reset безопасен.
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer self = mc.player;
        if (self == null) return;
        if (event.getEntity() != self) return;

        if (!CameraPerspectiveController.shouldHideOwnPlayer()) {
            return;
        }

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }
}