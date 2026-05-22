package starlight_lnk.camerainertia;

import starlight_lnk.camerainertia.config.ClientConfig;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;

@Mod(CameraInertia.MODID)
public final class CameraInertia {
    public static final String MODID = "camera_inertia";

    public CameraInertia() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
    }
}