package starlight_lnk.camerainertia.config;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class SubMenuActionsScreen extends BaseConfigScreen {

    public SubMenuActionsScreen(Screen previousScreen) {
        super(Component.translatable("camerainertia.config.menu_actions"), previousScreen);
    }

    @Override
    protected void init() {
        int startY = 40;
        int btnW = 150;
        int btnH = 20;
        int leftCol = this.width / 2 - btnW - 4;
        int rightCol = this.width / 2 + 4;

        // --- ДОБЫЧА БЛОКОВ ---
        this.addRenderableWidget(Button.builder(getToggleText("camerainertia.config.mining", ClientConfig.MINING_INERTIA_ENABLED.get()), btn -> {
            ClientConfig.MINING_INERTIA_ENABLED.set(!ClientConfig.MINING_INERTIA_ENABLED.get());
            ClientConfig.ACTIVE_PRESET.set(ClientConfig.Preset.CUSTOM);
            btn.setMessage(getToggleText("camerainertia.config.mining", ClientConfig.MINING_INERTIA_ENABLED.get()));
        }).bounds(leftCol, startY, btnW, btnH).build());

        this.addRenderableWidget(new ConfigSlider(rightCol, startY, btnW, btnH,
                ClientConfig.MINING_INERTIA_STRENGTH, 0.0, 3.0, "camerainertia.config.mining_str"));
        startY += 24;

        // --- ТРАНСПОРТ ---
        this.addRenderableWidget(Button.builder(getToggleText("camerainertia.config.vehicle", ClientConfig.VEHICLE_INERTIA_ENABLED.get()), btn -> {
            ClientConfig.VEHICLE_INERTIA_ENABLED.set(!ClientConfig.VEHICLE_INERTIA_ENABLED.get());
            ClientConfig.ACTIVE_PRESET.set(ClientConfig.Preset.CUSTOM);
            btn.setMessage(getToggleText("camerainertia.config.vehicle", ClientConfig.VEHICLE_INERTIA_ENABLED.get()));
        }).bounds(leftCol, startY, btnW, btnH).build());

        this.addRenderableWidget(new ConfigSlider(rightCol, startY, btnW, btnH,
                ClientConfig.VEHICLE_INERTIA_STRENGTH, 0.0, 3.0, "camerainertia.config.vehicle_str"));
        startY += 24;

        // --- ПАДЕНИЕ ---
        this.addRenderableWidget(Button.builder(getToggleText("camerainertia.config.fall", ClientConfig.FALL_SHAKE_ENABLED.get()), btn -> {
            ClientConfig.FALL_SHAKE_ENABLED.set(!ClientConfig.FALL_SHAKE_ENABLED.get());
            ClientConfig.ACTIVE_PRESET.set(ClientConfig.Preset.CUSTOM);
            btn.setMessage(getToggleText("camerainertia.config.fall", ClientConfig.FALL_SHAKE_ENABLED.get()));
        }).bounds(leftCol, startY, btnW, btnH).build());

        this.addRenderableWidget(new ConfigSlider(rightCol, startY, btnW, btnH,
                ClientConfig.FALL_SHAKE_STRENGTH, 0.0, 5.0, "camerainertia.config.fall_str"));
        startY += 24;

        // --- F5 ПЕРЕКЛЮЧЕНИЕ ---
        this.addRenderableWidget(Button.builder(getToggleText("camerainertia.config.f5", ClientConfig.PERSPECTIVE_TRANSITION_ENABLED.get()), btn -> {
            ClientConfig.PERSPECTIVE_TRANSITION_ENABLED.set(!ClientConfig.PERSPECTIVE_TRANSITION_ENABLED.get());
            ClientConfig.ACTIVE_PRESET.set(ClientConfig.Preset.CUSTOM);
            btn.setMessage(getToggleText("camerainertia.config.f5", ClientConfig.PERSPECTIVE_TRANSITION_ENABLED.get()));
        }).bounds(this.width / 2 - 100, startY, 200, btnH).build());

        addBackButton();
    }
}