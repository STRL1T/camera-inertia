package starlight_lnk.camerainertia;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import starlight_lnk.camerainertia.config.ClientConfig;
import starlight_lnk.camerainertia.config.CameraInertiaConfigScreen;

@Mod(CameraInertia.MODID)
public final class CameraInertia {
    public static final String MODID = "camera_inertia";

    public CameraInertia() {
        // Регистрируем сам файл конфига (.toml)
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);

        // Регистрируем графическое меню настроек (откроется по кнопке "Config" в списке модов)
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, previousScreen) -> {
                    return new CameraInertiaConfigScreen(previousScreen);
                })
        );
    }
}