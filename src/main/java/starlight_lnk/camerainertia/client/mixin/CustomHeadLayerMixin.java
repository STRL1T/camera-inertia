package starlight_lnk.camerainertia.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import starlight_lnk.camerainertia.client.CameraFirstPersonBody;

@Mixin(CustomHeadLayer.class)
public class CustomHeadLayerMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void cameraInertia$hidePumpkinsAndSkulls(PoseStack poseStack, MultiBufferSource buffer, int packedLight, LivingEntity entity, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        // Если сейчас рисуется тело от 1-го лица — отменяем рендер тыкв/черепов
        if (CameraFirstPersonBody.isRenderingBody) {
            ci.cancel();
        }
    }
}