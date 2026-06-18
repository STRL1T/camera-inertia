package starlight_lnk.camerainertia.config;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.common.ForgeConfigSpec;
import starlight_lnk.camerainertia.CameraInertia;

public abstract class BaseConfigScreen extends Screen {
    protected final Screen previousScreen;

    private static final ResourceLocation LOGO_TEXTURE = new ResourceLocation(CameraInertia.MODID, "textures/gui/logo.png");

    protected BaseConfigScreen(Component title, Screen previousScreen) {
        super(title);
        this.previousScreen = previousScreen;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);

        // ПРОПОРЦИИ И ОТСТУПЫ ЛОГОТИПА
        int logoWidth = 150;
        int logoHeight = 50; // Идеальная пропорция для 370x123
        int logoX = (this.width - logoWidth) / 2;
        int logoY = 10;      // Отступ от верхнего края экрана

        guiGraphics.blit(LOGO_TEXTURE, logoX, logoY, 0, 0, logoWidth, logoHeight, logoWidth, logoHeight);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        ClientConfig.SPEC.save();
        this.minecraft.setScreen(this.previousScreen);
    }

    protected void addBackButton() {
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, btn -> this.minecraft.setScreen(this.previousScreen))
                .bounds(this.width / 2 - 100, this.height - 30, 200, 20).build());
    }

    protected Component getToggleText(String key, boolean state) {
        return Component.translatable(key).append(": ").append(
                state ? Component.translatable("options.on") : Component.translatable("options.off")
        );
    }

    public class ConfigSlider extends AbstractSliderButton {
        private final ForgeConfigSpec.DoubleValue configValue;
        private final String translationKey;
        private final double min, max;

        public ConfigSlider(int x, int y, int w, int h, ForgeConfigSpec.DoubleValue configValue, double min, double max, String translationKey) {
            super(x, y, w, h, Component.empty(), 0.0);
            this.configValue = configValue;
            this.translationKey = translationKey;
            this.min = min;
            this.max = max;
            this.value = Mth.clamp((configValue.get() - min) / (max - min), 0.0, 1.0);
            this.updateMessage();
        }

        @Override
        protected void updateMessage() {
            double realValue = min + (max - min) * this.value;
            realValue = Math.round(realValue * 10.0) / 10.0;

            if (max > 10.0) {
                this.setMessage(Component.translatable(this.translationKey).append(": " + (int) realValue));
            } else {
                this.setMessage(Component.translatable(this.translationKey).append(String.format(": %.1f", realValue)));
            }
        }

        @Override
        protected void applyValue() {
            double realValue = min + (max - min) * this.value;
            realValue = Math.round(realValue * 10.0) / 10.0;
            this.configValue.set(realValue);
            ClientConfig.ACTIVE_PRESET.set(ClientConfig.Preset.CUSTOM);
        }
    }
}