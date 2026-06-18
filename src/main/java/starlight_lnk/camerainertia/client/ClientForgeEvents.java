package starlight_lnk.camerainertia.client;

import starlight_lnk.camerainertia.CameraInertia;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ComputeFovModifierEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(
        modid = CameraInertia.MODID,
        bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT
)
public final class ClientForgeEvents {

    private ClientForgeEvents() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;

        try {
            // Тикаем все наши крутые эффекты 1-го лица
            CameraPerspectiveController.tick();
            CameraTurnBlur.onClientTick();
            CameraRollController.tick();
            CameraPitchController.tick();
            CameraFovController.tick();
            CameraDamageController.tick();
            CameraMovementController.tick();
            CameraCombatController.tick();
            CameraEffectsController.tick();
            CameraVehicleController.tick();
            CameraWalkController.tick();
            CameraVehicleSpeedFx.tick();
            CameraMiningController.tick();
            CameraTridentZoomController.tick();
            CameraFallShakeController.tick();
        } catch (Throwable ignored) {}
    }

    @SubscribeEvent
    public static void onComputeFov(ComputeFovModifierEvent event) {
        try {
            if (!CameraViewUtils.isFirstPerson()) return;
            float multiplier = CameraFovController.getFovMultiplier(1.0F);
            multiplier *= CameraTridentZoomController.getFovMultiplier(1.0F);
            event.setNewFovModifier(event.getNewFovModifier() * multiplier);
        } catch (Throwable ignored) {}
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        try {
            // Если мы от 3-го лица, мод ВООБЩЕ ничего не делает! Ванила работает идеально.
            if (!CameraViewUtils.isFirstPerson()) return;

            float partial = (float) event.getPartialTick();
            float pedestrianMul = CameraVehicleController.getPedestrianMultiplier();

            // 🎥 Складываем все эффекты инерции для 1-ГО ЛИЦА
            float pitchKick =
                    CameraDamageController.getPitchOffset(partial)
                            + CameraMovementController.getPitchOffset(partial) * pedestrianMul
                            + CameraCombatController.getPitchOffset(partial)
                            + CameraEffectsController.getPitchOffset(partial)
                            + CameraVehicleController.getPitchOffset(partial)
                            + CameraMiningController.getPitchOffset(partial)
                            + CameraFallShakeController.getPitchOffset(partial)
                            + CameraWalkController.getPitch(partial);

            float yawKick =
                    CameraDamageController.getYawOffset(partial)
                            + CameraMovementController.getYawOffset(partial) * pedestrianMul
                            + CameraCombatController.getYawOffset(partial)
                            + CameraEffectsController.getYawOffset(partial)
                            + CameraVehicleController.getYawOffset(partial)
                            + CameraMiningController.getYawOffset(partial)
                            + CameraFallShakeController.getYawOffset(partial)
                            + CameraWalkController.getYaw(partial);

            float rollKick =
                    CameraDamageController.getRollOffset(partial)
                            + CameraMovementController.getRollOffset(partial) * pedestrianMul
                            + CameraCombatController.getRollOffset(partial)
                            + CameraEffectsController.getRollOffset(partial)
                            + CameraVehicleController.getRollOffset(partial)
                            + CameraMiningController.getRollOffset(partial)
                            + CameraFallShakeController.getRollOffset(partial)
                            + CameraWalkController.getRoll(partial);

            event.setPitch(event.getPitch() + pitchKick);
            event.setYaw(event.getYaw()     + yawKick);
            event.setRoll(event.getRoll()   + rollKick);

        } catch (Throwable ignored) {}
    }
}