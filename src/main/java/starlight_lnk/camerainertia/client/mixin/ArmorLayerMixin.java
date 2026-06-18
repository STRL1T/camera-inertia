package starlight_lnk.camerainertia.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import starlight_lnk.camerainertia.client.CameraFirstPersonBody;

@Mixin(HumanoidArmorLayer.class)
public class ArmorLayerMixin {

    @Inject(method = "renderArmorPiece", at = @At("HEAD"), cancellable = true)
    private void cameraInertia$hideHelmet(PoseStack poseStack, MultiBufferSource bufferSource, LivingEntity entity, EquipmentSlot slot, int light, HumanoidModel model, CallbackInfo ci) {
        if (CameraFirstPersonBody.isRenderingBody) {
            if (slot == EquipmentSlot.HEAD) {
                ci.cancel();
            } else if (slot == EquipmentSlot.CHEST && CameraFirstPersonBody.hideArms) {
                model.leftArm.visible = false;
                model.rightArm.visible = false;
            }
        }
    }

    @Inject(method = "renderArmorPiece", at = @At("RETURN"))
    private void cameraInertia$restoreArms(PoseStack poseStack, MultiBufferSource bufferSource, LivingEntity entity, EquipmentSlot slot, int light, HumanoidModel model, CallbackInfo ci) {
        if (CameraFirstPersonBody.isRenderingBody && slot == EquipmentSlot.CHEST && CameraFirstPersonBody.hideArms) {
            model.leftArm.visible = true;
            model.rightArm.visible = true;
        }
    }
}