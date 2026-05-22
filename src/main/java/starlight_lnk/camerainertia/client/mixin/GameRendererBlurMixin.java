package starlight_lnk.camerainertia.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import starlight_lnk.camerainertia.client.CameraTurnBlur;

@Mixin(GameRenderer.class)
public class GameRendererBlurMixin {

    @Inject(
            method = "renderLevel(FJLcom/mojang/blaze3d/vertex/PoseStack;)V",
            at = @At("HEAD")
    )
    private void cameraInertia$beginFrame(float partialTick, long nanoTime, PoseStack poseStack, CallbackInfo ci) {
        CameraTurnBlur.beginFrame();
    }

    @Inject(
            method = "renderLevel(FJLcom/mojang/blaze3d/vertex/PoseStack;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/GameRenderer;renderItemInHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/Camera;F)V",
                    shift = At.Shift.AFTER
            )
    )
    private void cameraInertia$afterRenderHand(float partialTick, long nanoTime, PoseStack poseStack, CallbackInfo ci) {
        CameraTurnBlur.renderAfterHand();
    }

    @Inject(
            method = "render(FJZ)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/Gui;render(Lnet/minecraft/client/gui/GuiGraphics;F)V",
                    shift = At.Shift.BEFORE
            )
    )
    private void cameraInertia$beforeGui(float partialTick, long nanoTime, boolean renderLevel, CallbackInfo ci) {
        CameraTurnBlur.beforeGuiRender();
    }
}