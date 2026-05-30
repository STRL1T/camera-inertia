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
        if (event.phase != TickEvent.Phase.END) return;

        // ⚙️ Тики выполняются ВСЕГДА — каждый контроллер сам корректно
        // гасит свои значения в 3rd person (через CameraViewUtils.isFirstPerson()).
        // Это нужно, чтобы при возврате в 1st person не было резких «скачков»
        // от накопленной инерции.
        try {
            // 🎬 Плавное переключение перспективы (F5) — должно идти ПЕРВЫМ,
            // чтобы остальные контроллеры корректно увидели "отображаемый" режим камеры.
            // Этот контроллер сам управляет mc.options.cameraType во время анимации.
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

            // Процедурная анимация ходьбы
            CameraWalkController.tick();

            // 🆕 Эффект скорости транспорта (FOV-boost + симметричный side-blur)
            CameraVehicleSpeedFx.tick();

            // Инерция при копании киркой
            CameraMiningController.tick();

            // Зум при заряде трезубца (FOV-канал)
            CameraTridentZoomController.tick();

            // 🆕 Тряска при свободном падении
            CameraFallShakeController.tick();
        } catch (Throwable ignored) {}
    }

    @SubscribeEvent
    public static void onComputeFov(ComputeFovModifierEvent event) {
        try {
            // 🎥 FOV-эффекты применяем ТОЛЬКО в 1st person.
            // В 3rd person игрок видит свою модель — зум/FOV-boost смотрятся как баг.
            if (!CameraViewUtils.isFirstPerson()) return;

            float multiplier = CameraFovController.getFovMultiplier(1.0F);

            // Перемножаем с зумом трезубца
            multiplier *= CameraTridentZoomController.getFovMultiplier(1.0F);

            event.setNewFovModifier(event.getNewFovModifier() * multiplier);
        } catch (Throwable ignored) {}
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        try {
            // 🎥 ГЛАВНЫЙ ФИЛЬТР 1st PERSON.
            // В 3rd person НЕ применяем НИКАКИЕ pitch/yaw/roll-смещения камеры.
            // Иначе вся инерция, отдача от урона, тряска и т.п. начнут вращать
            // камеру вокруг модельки игрока — это выглядит абсолютно сломано.
            //
            // Сами контроллеры в это время продолжают тикать и плавно гасят
            // свои значения, так что при возврате в 1st person не будет рывка.
            if (!CameraViewUtils.isFirstPerson()) return;

            float partial = (float) event.getPartialTick();

            // 🔧 Вместо «вкл/выкл» — мягкое ослабление инерции игрока на транспорте.
            // На лошади ~30%, в лодке ~10%, на верблюде ~25% и т.д.
            // Если игрок не на транспорте — вернёт 1.0 (без ослабления).
            float pedestrianMul = CameraVehicleController.getPedestrianMultiplier();

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