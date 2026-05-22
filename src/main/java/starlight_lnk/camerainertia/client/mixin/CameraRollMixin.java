package starlight_lnk.camerainertia.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import starlight_lnk.camerainertia.client.CameraPitchController;
import starlight_lnk.camerainertia.client.CameraRollController;
import starlight_lnk.camerainertia.client.CameraViewUtils;

@Mixin(GameRenderer.class)
public class CameraRollMixin {

    @Inject(
            method = "renderLevel(FJLcom/mojang/blaze3d/vertex/PoseStack;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/Camera;setup(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/world/entity/Entity;ZZF)V",
                    shift = At.Shift.AFTER
            )
    )
    private void cameraInertia$applyInertia(float partialTick, long nanoTime, PoseStack poseStack, CallbackInfo ci) {
        try {
            if (poseStack == null) return;

            // 🎥 Только в 1st person. В 3rd person никаких наклонов мира — иначе камера
            // будет вращаться вокруг модельки игрока, что выглядит как баг.
            if (!CameraViewUtils.isFirstPerson()) return;

            float roll  = CameraRollController.getRoll(partialTick);
            float pitch = CameraPitchController.getPitch(partialTick);

            if (Float.isNaN(roll)  || Float.isInfinite(roll))  roll  = 0.0F;
            if (Float.isNaN(pitch) || Float.isInfinite(pitch)) pitch = 0.0F;

            if (roll == 0.0F && pitch == 0.0F) return;

            if (roll != 0.0F) {
                poseStack.mulPose(new Quaternionf().rotationZ((float) Math.toRadians(roll)));
            }
            if (pitch != 0.0F) {
                poseStack.mulPose(new Quaternionf().rotationX((float) Math.toRadians(pitch)));
            }
        } catch (Throwable ignored) {
        }
    }
}