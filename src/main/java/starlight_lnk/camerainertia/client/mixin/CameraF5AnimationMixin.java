package starlight_lnk.camerainertia.client.mixin;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import starlight_lnk.camerainertia.client.CameraPerspectiveController;

@Mixin(Camera.class)
public abstract class CameraF5AnimationMixin {

    @Shadow public abstract float getYRot();
    @Shadow public abstract float getXRot();
    @Shadow public abstract Vec3 getPosition();
    @Shadow protected abstract void setPosition(double x, double y, double z);

    @Unique
    private float currentPartialTick;

    @Inject(method = "setup", at = @At("HEAD"))
    private void capturePartialTick(BlockGetter level, Entity entity, boolean detached, boolean thirdPersonReverse, float partialTick, CallbackInfo ci) {
        this.currentPartialTick = partialTick;
    }

    // 1. Анимация переключения F5
    @ModifyArg(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getMaxZoom(D)D"), index = 0)
    private double cameraInertia$animateF5Distance(double originalDistance) {
        if (CameraPerspectiveController.isTransitioning()) {
            return CameraPerspectiveController.getInterpolatedDistance(this.currentPartialTick);
        }
        return originalDistance;
    }

    // 2. Эффект Mirror's Edge (с полной блокировкой у стен)
    @Inject(method = "setup", at = @At("TAIL"))
    private void cameraInertia$applyMirrorsEdgeOffset(BlockGetter level, Entity entity, boolean detached, boolean thirdPersonReverse, float partialTick, CallbackInfo ci) {
        if (!detached && level != null) {
            float yaw = this.getYRot();
            float pitch = this.getXRot();

            float lookDownFactor = Math.max(0.0F, pitch) / 90.0F;

            // Максимальные значения, на которые мы хотим сдвинуть камеру
            double maxForward = 0.15 + (lookDownFactor * 0.25);
            double maxDown = lookDownFactor * 0.30;

            double rad = Math.toRadians(yaw);
            double dx = -Math.sin(rad) * maxForward;
            double dz = Math.cos(rad) * maxForward;

            Vec3 startPos = this.getPosition();

            // Вектор желаемого смещения и его длина
            Vec3 offsetVec = new Vec3(dx, -maxDown, dz);
            double maxMoveDist = offsetVec.length();
            Vec3 moveDir = offsetVec.normalize();

            // Пускаем луч ДАЛЬШЕ, чем двигается камера (добавляем буфер 0.35 блока)
            double checkDist = maxMoveDist + 0.35;
            Vec3 checkTarget = startPos.add(moveDir.scale(checkDist));

            ClipContext context = new ClipContext(startPos, checkTarget, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, entity);
            HitResult hit = level.clip(context);

            double finalMoveDist = maxMoveDist;

            // Если луч во что-то врезался
            if (hit.getType() != HitResult.Type.MISS) {
                double hitDist = startPos.distanceTo(hit.getLocation());

                // Нам нужно как минимум 0.3 блока свободного пространства (толщина головы)
                // Если стена ближе, availableSpace станет <= 0, и эффект полностью отключится
                double availableSpace = hitDist - 0.30;

                if (availableSpace <= 0.0) {
                    finalMoveDist = 0.0; // Полностью отключаем сдвиг вплотную к стене
                } else {
                    finalMoveDist = Math.min(maxMoveDist, availableSpace); // Плавно уменьшаем сдвиг
                }
            }

            // Применяем финальную позицию, только если нам разрешили сдвинуться
            if (finalMoveDist > 0.0) {
                Vec3 finalPos = startPos.add(moveDir.scale(finalMoveDist));
                this.setPosition(finalPos.x, finalPos.y, finalPos.z);
            }
        }
    }
}