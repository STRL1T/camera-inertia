package starlight_lnk.camerainertia.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import starlight_lnk.camerainertia.CameraInertia;

import java.io.IOException;

@Mod.EventBusSubscriber(
        modid = CameraInertia.MODID,
        bus = Mod.EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT
)
public final class ClientModEvents {

    private ClientModEvents() {
    }

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) throws IOException {
        ShaderInstance shader = new ShaderInstance(
                event.getResourceProvider(),
                new ResourceLocation(CameraInertia.MODID, "camera_motion_blur"),
                DefaultVertexFormat.POSITION_TEX
        );

        event.registerShader(shader, CameraTurnBlur::setShader);
    }
}