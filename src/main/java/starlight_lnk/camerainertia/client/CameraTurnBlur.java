package starlight_lnk.camerainertia.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import starlight_lnk.camerainertia.config.ClientConfig;

public final class CameraTurnBlur {
    private static final Logger LOGGER = LoggerFactory.getLogger("CameraInertia/CameraTurnBlur");

    private static ShaderInstance shader;
    private static RenderTarget blurTarget;

    private static int lastWidth = -1;
    private static int lastHeight = -1;

    private static boolean initialized = false;
    private static float lastYaw = 0.0f;

    // === Канал поворота камеры ===
    private static float currentIntensity = 0.0f;
    private static float blurDirection = 0.0f;

    // === Канал падения (симметричный) ===
    private static float fallIntensity = 0.0f;

    // 🆕 === Канал скорости транспорта (симметричный) ===
    private static float vehicleIntensity = 0.0f;

    private static boolean blurRenderedThisFrame = false;

    private CameraTurnBlur() {
    }

    public static void setShader(ShaderInstance shaderInstance) {
        shader = shaderInstance;
        LOGGER.info("Camera turn blur shader registered");
    }

    public static void reset() {
        resetStateOnly();

        blurRenderedThisFrame = false;

        if (blurTarget != null) {
            blurTarget.destroyBuffers();
            blurTarget = null;
        }

        lastWidth = -1;
        lastHeight = -1;
    }

    public static void onClientTick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;

        if (player == null || mc.level == null || mc.isPaused()) {
            resetStateOnly();
            return;
        }

        if (!CameraViewUtils.isFirstPerson()) {
            resetStateOnly();
            return;
        }

        if (!ClientConfig.ENABLED.get() || !ClientConfig.MOTION_BLUR_ENABLED.get()) {
            resetStateOnly();
            return;
        }

        float yaw = player.getYRot();

        if (!initialized) {
            initialized = true;
            lastYaw = yaw;
            currentIntensity = 0.0f;
            blurDirection = 0.0f;
            fallIntensity = 0.0f;
            vehicleIntensity = 0.0f;
            return;
        }

        float deltaYaw = wrapDegrees(yaw - lastYaw);
        lastYaw = yaw;

        float absYaw = Math.abs(deltaYaw);

        float sensitivity = ClientConfig.MOTION_BLUR_SENSITIVITY.get().floatValue();
        float maxIntensity = Mth.clamp(ClientConfig.MOTION_BLUR_MAX_INTENSITY.get().floatValue(), 0.0f, 1.0f);
        float smoothing = Mth.clamp(ClientConfig.MOTION_BLUR_SMOOTHING.get().floatValue(), 0.01f, 1.0f);

        float deadzone = Math.max(0.0f, ClientConfig.MOTION_BLUR_DEADZONE.get().floatValue());
        float fullBlurMotion = Math.max(deadzone + 0.001f, ClientConfig.MOTION_BLUR_FULL_MOTION.get().floatValue());
        float curvePower = Math.max(0.1f, ClientConfig.MOTION_BLUR_CURVE_POWER.get().floatValue());

        float motion = absYaw * sensitivity;
        float targetIntensity = 0.0f;

        if (motion > deadzone) {
            float t = (motion - deadzone) / (fullBlurMotion - deadzone);
            t = Mth.clamp(t, 0.0f, 1.0f);

            targetIntensity = (float) Math.pow(t, curvePower) * maxIntensity;

            float turnDirection = Math.signum(deltaYaw);
            if (Math.abs(turnDirection) > 0.001f) {
                if (ClientConfig.MOTION_BLUR_OPPOSITE_SIDE.get()) {
                    blurDirection = -turnDirection;
                } else {
                    blurDirection = turnDirection;
                }
            }
        }

        float attackMultiplier = ClientConfig.MOTION_BLUR_ATTACK_MULTIPLIER.get().floatValue();
        float releaseMultiplier = ClientConfig.MOTION_BLUR_RELEASE_MULTIPLIER.get().floatValue();

        float attackSmoothing = Mth.clamp(smoothing * attackMultiplier, 0.01f, 1.0f);
        float releaseSmoothing = Mth.clamp(smoothing * releaseMultiplier, 0.01f, 1.0f);

        float usedSmoothing = targetIntensity > currentIntensity ? attackSmoothing : releaseSmoothing;
        currentIntensity += (targetIntensity - currentIntensity) * usedSmoothing;

        if (currentIntensity < 0.001f) {
            currentIntensity = 0.0f;
            blurDirection = 0.0f;
        }

        // === Канал падения ===
        updateFallChannel(maxIntensity);

        // 🆕 === Канал скорости транспорта ===
        updateVehicleChannel(maxIntensity);
    }

    /**
     * Блюр при падении (симметричный).
     * Берёт интенсивность из CameraFallShakeController и масштабирует
     * по доле от max side-blur (ratio из конфига fall_blur).
     */
    private static void updateFallChannel(float maxIntensity) {
        boolean enabled = ClientConfig.FALL_BLUR_ENABLED.get();

        if (!enabled) {
            fallIntensity = 0.0f;
            return;
        }

        float shakeI = CameraFallShakeController.getIntensity(1.0F);
        shakeI = Mth.clamp(shakeI, 0.0f, 1.0f);

        float ratio = ClientConfig.FALL_BLUR_STRENGTH.get().floatValue();
        float target = shakeI * maxIntensity * ratio;

        // Свой ритм сглаживания, отличный от поворотного канала
        float k = (target > fallIntensity) ? 0.10f : 0.06f;
        fallIntensity += (target - fallIntensity) * k;

        if (fallIntensity < 0.001f) {
            fallIntensity = 0.0f;
        }
    }

    /**
     * 🆕 Блюр от скорости транспорта (симметричный).
     * Источник — CameraVehicleSpeedFx.getBlurIntensity(), он уже отдаёт
     * нормализованное значение 0..1 с учётом кривой и STRENGTH.
     * Здесь только домножаем на общий maxIntensity (ограничитель шейдера).
     */
    private static void updateVehicleChannel(float maxIntensity) {
        float vehI = CameraVehicleSpeedFx.getBlurIntensity(1.0F);
        vehI = Mth.clamp(vehI, 0.0f, 1.0f);

        float target = vehI * maxIntensity;

        // Очень мягкое сглаживание — эффект должен «плыть» вместе с разгоном,
        // а CameraVehicleSpeedFx уже сам гладко считает factor.
        float k = (target > vehicleIntensity) ? 0.12f : 0.08f;
        vehicleIntensity += (target - vehicleIntensity) * k;

        if (vehicleIntensity < 0.001f) {
            vehicleIntensity = 0.0f;
        }
    }

    private static void resetStateOnly() {
        initialized = false;
        lastYaw = 0.0f;
        currentIntensity = 0.0f;
        blurDirection = 0.0f;
        fallIntensity = 0.0f;
        vehicleIntensity = 0.0f;
    }

    public static void beginFrame() {
        blurRenderedThisFrame = false;
    }

    public static void beforeGuiRender() {
        blurRenderedThisFrame = false;
    }

    public static void renderAfterHand() {
        if (shader == null) {
            return;
        }

        if (blurRenderedThisFrame) {
            return;
        }

        boolean hasTurn    = currentIntensity > 0.001f && Math.abs(blurDirection) > 0.001f;
        boolean hasFall    = fallIntensity > 0.001f;
        boolean hasVehicle = vehicleIntensity > 0.001f;

        if (!hasTurn && !hasFall && !hasVehicle) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();

        if (mc.level == null || mc.player == null) {
            return;
        }

        RenderTarget mainTarget = mc.getMainRenderTarget();

        if (mainTarget == null) {
            return;
        }

        int width = mainTarget.width;
        int height = mainTarget.height;

        ensureTarget(width, height);

        if (blurTarget == null) {
            return;
        }

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

        // ================================================================
        // Логика проходов:
        //  - Поворот (turn): один асимметричный проход в blurDirection
        //  - Падение (fall) + Скорость (vehicle): оба симметричны, поэтому
        //    их интенсивности СУММИРУЮТСЯ и рендерятся одним симметричным
        //    парным проходом (слева + справа). Так мы экономим 2 прохода.
        //
        // Комбинации:
        //   only turn          → 1 проход
        //   only sym (fall|veh)→ 2 прохода (left + right)
        //   turn + sym         → 1 turn + 2 sym = 3 прохода
        // ================================================================

        float symIntensity = Math.min(1.0f, fallIntensity + vehicleIntensity);
        boolean hasSym = symIntensity > 0.001f;

        if (hasTurn && !hasSym) {
            singleBlurPass(mainTarget, width, height, currentIntensity, blurDirection);
        } else if (!hasTurn && hasSym) {
            singleBlurPass(mainTarget, width, height, symIntensity, -1.0f);
            singleBlurPass(mainTarget, width, height, symIntensity, +1.0f);
        } else {
            // И поворот, и симметричный канал — рендерим всё
            singleBlurPass(mainTarget, width, height, currentIntensity, blurDirection);
            singleBlurPass(mainTarget, width, height, symIntensity, -blurDirection);
            singleBlurPass(mainTarget, width, height, symIntensity,  blurDirection);
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();

        mainTarget.bindWrite(false);

        blurRenderedThisFrame = true;
    }

    /**
     * Один полный цикл блюра: рендер в офскрин-буфер с шейдером,
     * затем копирование результата обратно в main target.
     */
    private static void singleBlurPass(
            RenderTarget mainTarget,
            int width,
            int height,
            float intensity,
            float direction
    ) {
        blurTarget.bindWrite(true);
        RenderSystem.viewport(0, 0, blurTarget.width, blurTarget.height);

        renderFullscreen(
                mainTarget.getColorTextureId(),
                intensity,
                direction,
                width,
                height,
                true
        );

        mainTarget.bindWrite(true);
        RenderSystem.viewport(0, 0, mainTarget.width, mainTarget.height);

        renderFullscreen(
                blurTarget.getColorTextureId(),
                0.0f,
                0.0f,
                width,
                height,
                false
        );
    }

    private static void ensureTarget(int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }

        if (blurTarget != null && width == lastWidth && height == lastHeight) {
            return;
        }

        if (blurTarget != null) {
            blurTarget.destroyBuffers();
            blurTarget = null;
        }

        blurTarget = new TextureTarget(width, height, false, Minecraft.ON_OSX);
        lastWidth = width;
        lastHeight = height;

        LOGGER.info("Created camera blur target: {}x{}", width, height);
    }

    private static void renderFullscreen(
            int textureId,
            float intensity,
            float direction,
            int width,
            int height,
            boolean applyBlur
    ) {
        RenderSystem.setShader(() -> shader);
        RenderSystem.setShaderTexture(0, textureId);

        shader.setSampler("DiffuseSampler", textureId);

        setUniform("Intensity", applyBlur ? Mth.clamp(intensity, 0.0f, 1.0f) : 0.0f);
        setUniform("Direction", applyBlur ? direction : 0.0f);

        setUniform2("InSize", width, height);
        setUniform2("OutSize", width, height);

        setUniform("SideStart", ClientConfig.MOTION_BLUR_SIDE_START.get().floatValue());
        setUniform("SideEnd", ClientConfig.MOTION_BLUR_SIDE_END.get().floatValue());
        setUniform("EdgePower", ClientConfig.MOTION_BLUR_EDGE_POWER.get().floatValue());
        setUniform("BlurPixels", ClientConfig.MOTION_BLUR_PIXELS.get().floatValue());
        setUniform("ChromaPixels", ClientConfig.MOTION_BLUR_CHROMA_PIXELS.get().floatValue());

        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);

        builder.vertex(-1.0, -1.0, 0.0).uv(0.0f, 1.0f).endVertex();
        builder.vertex(1.0, -1.0, 0.0).uv(1.0f, 1.0f).endVertex();
        builder.vertex(1.0, 1.0, 0.0).uv(1.0f, 0.0f).endVertex();
        builder.vertex(-1.0, 1.0, 0.0).uv(0.0f, 0.0f).endVertex();

        BufferUploader.drawWithShader(builder.end());
    }

    private static void setUniform(String name, float value) {
        Uniform uniform = shader.getUniform(name);

        if (uniform != null) {
            uniform.set(value);
        }
    }

    private static void setUniform2(String name, int x, int y) {
        Uniform uniform = shader.getUniform(name);

        if (uniform != null) {
            uniform.set((float) x, (float) y);
        }
    }

    private static float wrapDegrees(float value) {
        value = value % 360.0f;

        if (value >= 180.0f) {
            value -= 360.0f;
        }

        if (value < -180.0f) {
            value += 360.0f;
        }

        return value;
    }
}