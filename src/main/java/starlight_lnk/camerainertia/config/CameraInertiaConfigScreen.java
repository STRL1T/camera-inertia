package starlight_lnk.camerainertia.config;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class CameraInertiaConfigScreen extends BaseConfigScreen {

    private Button resetButton;
    private boolean isResetHeld = false;
    private int resetHoldTicks = 0;

    public CameraInertiaConfigScreen(Screen previousScreen) {
        super(Component.empty(), previousScreen);
    }

    @Override
    protected void init() {
        // ОПУСКАЕМ КНОПКИ НИЖЕ, ЧТОБЫ НЕ НАЛЕЗАЛИ НА ЛОГОТИП
        int startY = 70;
        int btnWidth = 200;
        int btnHeight = 20;
        int centerX = this.width / 2 - btnWidth / 2;

        this.addRenderableWidget(Button.builder(
                getPresetText(),
                btn -> {
                    cyclePreset();
                    btn.setMessage(getPresetText());
                }).bounds(centerX, startY, btnWidth, btnHeight).build());

        startY += 30;

        this.addRenderableWidget(Button.builder(Component.translatable("camerainertia.config.menu_motion"),
                btn -> this.minecraft.setScreen(new SubMenuMotionScreen(this))).bounds(centerX, startY, btnWidth, btnHeight).build());
        startY += 24;

        this.addRenderableWidget(Button.builder(Component.translatable("camerainertia.config.menu_actions"),
                btn -> this.minecraft.setScreen(new SubMenuActionsScreen(this))).bounds(centerX, startY, btnWidth, btnHeight).build());
        startY += 24;

        this.addRenderableWidget(Button.builder(Component.translatable("camerainertia.config.menu_items"),
                btn -> this.minecraft.setScreen(new CameraItemsConfigScreen(this))).bounds(centerX, startY, btnWidth, btnHeight).build());
        startY += 24;

        this.addRenderableWidget(Button.builder(Component.translatable("camerainertia.config.menu_blur"),
                btn -> this.minecraft.setScreen(new SubMenuBlurScreen(this))).bounds(centerX, startY, btnWidth, btnHeight).build());
        startY += 30;

        // --- ПРИМЕНИТЬ И СБРОСИТЬ (ИДЕАЛЬНО ВРОВЕНЬ) ---
        int halfBtnWidth = 98; // 98 + 4 (зазор) + 98 = 200 пикселей общей ширины!

        this.addRenderableWidget(Button.builder(Component.translatable("camerainertia.config.apply"), btn -> {
            ClientConfig.SPEC.save();
            this.minecraft.setScreen(null);
        }).bounds(this.width / 2 - 100, startY, halfBtnWidth, btnHeight).build()); // Левый край ровно по линии

        resetButton = Button.builder(Component.translatable("camerainertia.config.reset"), btn -> {})
                .bounds(this.width / 2 + 2, startY, halfBtnWidth, btnHeight).build(); // Правый край ровно по линии
        this.addRenderableWidget(resetButton);

        addBackButton();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (resetButton != null && resetButton.isMouseOver(mouseX, mouseY) && button == 0) {
            isResetHeld = true;
            resetHoldTicks = 0;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && isResetHeld) {
            isResetHeld = false;
            resetHoldTicks = 0;
            resetButton.setMessage(Component.translatable("camerainertia.config.reset"));
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void tick() {
        super.tick();
        if (isResetHeld) {
            resetHoldTicks++;
            int maxTicks = 20;
            int progress = (resetHoldTicks * 100) / maxTicks;

            if (progress < 100) {
                resetButton.setMessage(Component.translatable("camerainertia.config.resetting", progress));
            } else {
                isResetHeld = false;
                resetHoldTicks = 0;
                ClientConfig.applyPreset(ClientConfig.Preset.REALISM);
                resetButton.setMessage(Component.translatable("camerainertia.config.reset_done"));
            }
        }
    }

    private Component getPresetText() {
        return Component.translatable("camerainertia.config.preset").append(": ")
                .append(Component.translatable("camerainertia.preset." + ClientConfig.ACTIVE_PRESET.get().name().toLowerCase()));
    }

    private void cyclePreset() {
        ClientConfig.Preset[] presets = ClientConfig.Preset.values();
        int currentIndex = ClientConfig.ACTIVE_PRESET.get().ordinal();
        int nextIndex = (currentIndex + 1) % (presets.length - 1);
        ClientConfig.applyPreset(presets[nextIndex]);
    }
}