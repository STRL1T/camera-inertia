package starlight_lnk.camerainertia.client.mixin;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import starlight_lnk.camerainertia.client.CameraPerspectiveController;

/**
 * 🎬 Плавная анимация дистанции при переключении перспективы (F5).
 *
 * Логика:
 *  - В 3rd person MC сам отодвигает камеру на 4 блока назад через свой
 *    внутренний getMaxZoom + move в Camera.setup.
 *  - Мы инжектимся в TAIL setup() и ПРИДВИГАЕМ камеру обратно к игроку
 *    на (4 - текущая_анимированная_дистанция).
 *  - В начале анимации 1st→3rd: pullback = 4 (камера прямо в голове).
 *  - В конце анимации: pullback = 0 (нормальная 3rd дистанция).
 *  - Эффект: камера плавно «вылетает» из головы игрока назад.
 *
 * Camera.move(dz, dy, dx):
 *   dz — НАЗАД (отрицательный = вперёд, к игроку).
 */
@Mixin(Camera.class)
public class CameraDistanceMixin {

    @Inject(
            method = "setup(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/world/entity/Entity;ZZF)V",
            at = @At("TAIL")
    )
    private void cameraInertia$applyPerspectiveDistance(
            BlockGetter level,
            Entity entity,
            boolean detached,
            boolean thirdPersonReverse,
            float partialTick,
            CallbackInfo ci
    ) {
        try {
            // Работаем ТОЛЬКО в 3rd person (когда detached=true).
            // В 1st person камера в голове и так — двигать не надо.
            if (!detached) return;

            float pullback = CameraPerspectiveController.getPullbackAmount(partialTick);
            if (pullback <= 0.001F) return;

            if (Float.isNaN(pullback) || Float.isInfinite(pullback)) return;

            // Придвигаем камеру вперёд (к игроку) — Camera.move(dz, dy, dx),
            // где положительный dz = вперёд по направлению взгляда.
            CameraMoveInvoker invoker = (CameraMoveInvoker) (Object) this;
            invoker.cameraInertia$invokeMove(pullback, 0.0D, 0.0D);
        } catch (Throwable ignored) {
        }
    }
}