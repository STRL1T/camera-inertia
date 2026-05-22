package starlight_lnk.camerainertia.client.mixin;

import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 🔧 Инвокер для доступа к protected-методу Camera.move().
 *
 * Camera.move(dz, dy, dx) — сдвигает позицию камеры по её локальным осям
 * (dz — вперёд/назад относительно направления взгляда).
 *
 * Используется из CameraDistanceMixin через каст:
 *   ((CameraMoveInvoker) (Object) cameraInstance).cameraInertia$invokeMove(...)
 */
@Mixin(Camera.class)
public interface CameraMoveInvoker {

    @Invoker("move")
    void cameraInertia$invokeMove(double dz, double dy, double dx);
}