package starlight_lnk.camerainertia.config;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class CameraItemsConfigScreen extends BaseConfigScreen {

    public CameraItemsConfigScreen(Screen previousScreen) {
        super(Component.translatable("camerainertia.config.menu_items"), previousScreen);
    }

    @Override
    protected void init() {
        int startY = 40;
        int btnW = 150;
        int btnH = 20;
        int leftCol = this.width / 2 - btnW - 4;
        int rightCol = this.width / 2 + 4;

        // --- ГЛАВНЫЙ ВЫКЛЮЧАТЕЛЬ ПРЕДМЕТОВ ---
        this.addRenderableWidget(Button.builder(getToggleText("camerainertia.config.items_anim", ClientConfig.ITEM_ANIMATIONS_ENABLED.get()), btn -> {
            ClientConfig.ITEM_ANIMATIONS_ENABLED.set(!ClientConfig.ITEM_ANIMATIONS_ENABLED.get());
            ClientConfig.ACTIVE_PRESET.set(ClientConfig.Preset.CUSTOM);
            btn.setMessage(getToggleText("camerainertia.config.items_anim", ClientConfig.ITEM_ANIMATIONS_ENABLED.get()));
        }).bounds(this.width / 2 - 100, startY, 200, btnH).build());
        startY += 24;

        // --- ОБЩИЙ ЗУМ ОРУЖИЯ (Лук, Арбалет, Трезубец) ---
        this.addRenderableWidget(Button.builder(getToggleText("camerainertia.config.weapon_zoom", ClientConfig.WEAPON_ZOOM_ENABLED.get()), btn -> {
            ClientConfig.WEAPON_ZOOM_ENABLED.set(!ClientConfig.WEAPON_ZOOM_ENABLED.get());
            ClientConfig.ACTIVE_PRESET.set(ClientConfig.Preset.CUSTOM);
            btn.setMessage(getToggleText("camerainertia.config.weapon_zoom", ClientConfig.WEAPON_ZOOM_ENABLED.get()));
        }).bounds(leftCol, startY, btnW, btnH).build());

        this.addRenderableWidget(new ConfigSlider(rightCol, startY, btnW, btnH,
                ClientConfig.WEAPON_ZOOM_STRENGTH, 0.0, 1.0, "camerainertia.config.weapon_zoom_str"));
        startY += 24;

        addBackButton();
    }
}