package starlight_lnk.camerainertia.config;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class SubMenuBlurScreen extends BaseConfigScreen {
    public SubMenuBlurScreen(Screen previousScreen) {
        super(Component.translatable("camerainertia.config.menu_blur"), previousScreen);
    }

    @Override
    protected void init() {
        int startY = 40;
        int btnW = 150;
        int btnH = 20;
        int leftCol = this.width / 2 - btnW - 4;
        int rightCol = this.width / 2 + 4;

        this.addRenderableWidget(Button.builder(getToggleText("camerainertia.config.blur_enabled", ClientConfig.MOTION_BLUR_ENABLED.get()), btn -> {
            ClientConfig.MOTION_BLUR_ENABLED.set(!ClientConfig.MOTION_BLUR_ENABLED.get());
            ClientConfig.ACTIVE_PRESET.set(ClientConfig.Preset.CUSTOM);
            btn.setMessage(getToggleText("camerainertia.config.blur_enabled", ClientConfig.MOTION_BLUR_ENABLED.get()));
        }).bounds(this.width / 2 - 100, startY, 200, btnH).build());
        startY += 24;

        // ИСПРАВЛЕНЫ ЛИМИТЫ (250 для пикселей, 50 для хромы)
        this.addRenderableWidget(new ConfigSlider(leftCol, startY, btnW, btnH, ClientConfig.MOTION_BLUR_PIXELS, 0.0, 250.0, "camerainertia.config.blur_pixels"));
        this.addRenderableWidget(new ConfigSlider(rightCol, startY, btnW, btnH, ClientConfig.MOTION_BLUR_CHROMA_PIXELS, 0.0, 50.0, "camerainertia.config.blur_chroma"));
        startY += 24;

        // ИСПРАВЛЕН ЛИМИТ СЕНСЫ (до 20)
        this.addRenderableWidget(new ConfigSlider(leftCol, startY, btnW, btnH, ClientConfig.MOTION_BLUR_SENSITIVITY, 0.0, 20.0, "camerainertia.config.blur_sens"));
        this.addRenderableWidget(new ConfigSlider(rightCol, startY, btnW, btnH, ClientConfig.MOTION_BLUR_MAX_INTENSITY, 0.0, 1.0, "camerainertia.config.blur_max"));

        addBackButton();
    }
}