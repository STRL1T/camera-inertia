package starlight_lnk.camerainertia.config;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class SubMenuMotionScreen extends BaseConfigScreen {
    public SubMenuMotionScreen(Screen previousScreen) {
        super(Component.translatable("camerainertia.config.menu_motion"), previousScreen);
    }

    @Override
    protected void init() {
        int startY = 40;
        int btnW = 150;
        int btnH = 20;
        int leftCol = this.width / 2 - btnW - 4;
        int rightCol = this.width / 2 + 4;

        // Тело от 1-го лица
        this.addRenderableWidget(Button.builder(getToggleText("camerainertia.config.body", ClientConfig.FIRST_PERSON_BODY_ENABLED.get()), btn -> {
            ClientConfig.FIRST_PERSON_BODY_ENABLED.set(!ClientConfig.FIRST_PERSON_BODY_ENABLED.get());
            ClientConfig.ACTIVE_PRESET.set(ClientConfig.Preset.CUSTOM);
            btn.setMessage(getToggleText("camerainertia.config.body", ClientConfig.FIRST_PERSON_BODY_ENABLED.get()));
        }).bounds(this.width / 2 - 100, startY, 200, btnH).build());
        startY += 24;

        // Общее движение
        this.addRenderableWidget(Button.builder(getToggleText("camerainertia.config.move", ClientConfig.MOVEMENT_ANIMATIONS_ENABLED.get()), btn -> {
            ClientConfig.MOVEMENT_ANIMATIONS_ENABLED.set(!ClientConfig.MOVEMENT_ANIMATIONS_ENABLED.get());
            ClientConfig.ACTIVE_PRESET.set(ClientConfig.Preset.CUSTOM);
            btn.setMessage(getToggleText("camerainertia.config.move", ClientConfig.MOVEMENT_ANIMATIONS_ENABLED.get()));
        }).bounds(leftCol, startY, btnW, btnH).build());
        this.addRenderableWidget(new ConfigSlider(rightCol, startY, btnW, btnH, ClientConfig.MOVEMENT_INTENSITY, 0.0, 3.0, "camerainertia.config.move_intensity"));
        startY += 24;

        // Наклоны (A/D)
        this.addRenderableWidget(new ConfigSlider(this.width / 2 - 100, startY, 200, btnH, ClientConfig.STRAFE_ROLL_INTENSITY, 0.0, 3.0, "camerainertia.config.strafe_roll"));
        startY += 24;

        // Повороты
        this.addRenderableWidget(Button.builder(getToggleText("camerainertia.config.turn", ClientConfig.TURN_ANIMATIONS_ENABLED.get()), btn -> {
            ClientConfig.TURN_ANIMATIONS_ENABLED.set(!ClientConfig.TURN_ANIMATIONS_ENABLED.get());
            ClientConfig.ACTIVE_PRESET.set(ClientConfig.Preset.CUSTOM);
            btn.setMessage(getToggleText("camerainertia.config.turn", ClientConfig.TURN_ANIMATIONS_ENABLED.get()));
        }).bounds(leftCol, startY, btnW, btnH).build());
        this.addRenderableWidget(new ConfigSlider(rightCol, startY, btnW, btnH, ClientConfig.TURN_INTENSITY, 0.0, 3.0, "camerainertia.config.turn_intensity"));

        addBackButton();
    }
}