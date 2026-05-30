package starlight_lnk.camerainertia.client.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import starlight_lnk.camerainertia.client.CameraPerspectiveController;
import starlight_lnk.camerainertia.client.CameraState;
import starlight_lnk.camerainertia.client.ThirdPersonConfig;

@Mixin(Camera.class)
public abstract class CameraDistanceMixin {

    @Shadow public abstract float getYRot();
    @Shadow public abstract float getXRot();
    @Shadow public abstract Vec3 getPosition();
    @Shadow protected abstract void setPosition(double x, double y, double z);

    // 1. Применяем дистанцию
    @ModifyArg(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getMaxZoom(D)D"), index = 0)
    private double cameraInertia$changeDistance(double originalDistance) {
        return ThirdPersonConfig.cameraDistance;
    }

    // =======================================================
    // 2. ЗАСТАВЛЯЕМ КАМЕРУ СМОТРЕТЬ НЕЗАВИСИМО ОТ ТЕЛА ИГРОКА
    // Эти Redirect работают на 100% безотказно в любых версиях
    // =======================================================
    @Redirect(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewYRot(F)F"))
    private float cameraInertia$redirectYaw(Entity entity, float partialTicks) {
        if (entity instanceof LocalPlayer && !Minecraft.getInstance().options.getCameraType().isFirstPerson()) {
            return CameraState.yaw; // Камера берет наш независимый поворот
        }
        return entity.getViewYRot(partialTicks);
    }

    @Redirect(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewXRot(F)F"))
    private float cameraInertia$redirectPitch(Entity entity, float partialTicks) {
        if (entity instanceof LocalPlayer && !Minecraft.getInstance().options.getCameraType().isFirstPerson()) {
            return CameraState.pitch; // Камера берет наш независимый наклон
        }
        return entity.getViewXRot(partialTicks);
    }

    // 3. Смещение камеры (сдвиг за плечо)
    @Inject(method = "setup(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/world/entity/Entity;ZZF)V", at = @At("TAIL"))
    private void cameraInertia$applyPerspectiveSettings(BlockGetter level, Entity entity, boolean detached, boolean thirdPersonReverse, float partialTick, CallbackInfo ci) {
        try {
            CameraMoveInvoker invoker = (CameraMoveInvoker) (Object) this;

            if (detached) {
                double offsetY = ThirdPersonConfig.cameraOffsetY;
                double offsetX = ThirdPersonConfig.cameraOffsetX;
                invoker.cameraInertia$invokeMove(0.0D, offsetY, offsetX);

                float pullback = CameraPerspectiveController.getPullbackAmount(partialTick);
                if (pullback > 0.001F && !Float.isNaN(pullback)) {
                    invoker.cameraInertia$invokeMove(pullback, 0.0D, 0.0D);
                }
            } else {
                float yaw = this.getYRot();
                float pitch = this.getXRot();
                float lookDownFactor = Math.max(0.0F, pitch) / 90.0F;

                double forwardDistance = 0.15 + (lookDownFactor * 0.25);
                double downDistance = lookDownFactor * 0.30;

                double rad = Math.toRadians(yaw);
                double dx = -Math.sin(rad) * forwardDistance;
                double dz = Math.cos(rad) * forwardDistance;

                Vec3 pos = this.getPosition();
                this.setPosition(pos.x + dx, pos.y - downDistance, pos.z + dz);
            }
        } catch (Throwable ignored) {}
    }
}