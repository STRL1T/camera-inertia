package starlight_lnk.camerainertia.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import starlight_lnk.camerainertia.CameraInertia;
import starlight_lnk.camerainertia.config.ClientConfig;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = CameraInertia.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class CameraFirstPersonBody {

    public static boolean isRenderingBody = false;
    public static boolean hideArms = false; // Флаг для синхронизации

    // === КЭШ ДЛЯ МАКСИМАЛЬНОЙ ОПТИМИЗАЦИИ (чтобы не нагружать игру каждый кадр) ===
    private static final Map<Item, Boolean> TACZ_ADDON_CACHE = new ConcurrentHashMap<>();

    // --- УНИВЕРСАЛЬНАЯ ПРОВЕРКА ПРЕДМЕТОВ ---
    private static boolean isHoldingBlacklistedItem(LocalPlayer player) {
        if (player == null) return false;
        return isItemHidden(player.getMainHandItem()) || isItemHidden(player.getOffhandItem());
    }

    private static boolean isItemHidden(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Item item = stack.getItem();

        ResourceLocation regName = ForgeRegistries.ITEMS.getKey(item);
        
        // --- DEBUG LOG ---
        if (regName != null) {
            System.out.println("[CameraInertia DEBUG] Held item: " + regName + ", Class: " + item.getClass().getName());
        }
        
        if (regName != null) {
            List<? extends String> blacklist = ClientConfig.ITEM_BLACKLIST.get();
            if (blacklist != null && blacklist.contains(regName.toString())) {
                return true;
            }
        }

        return isUniversalTacZWeapon(item, regName);
    }

    private static boolean isUniversalTacZWeapon(Item item, ResourceLocation regName) {
        return TACZ_ADDON_CACHE.computeIfAbsent(item, i -> {
            if (regName != null) {
                String namespace = regName.getNamespace().toLowerCase();
                if (namespace.equals("tacz") || namespace.equals("lrtactical") || namespace.equals("lr_tactical") || namespace.equals("lesraisins")) return true;
                if (regName.getPath().contains("whip") || regName.getPath().contains("lasso")) return true;
            }

            // ПРОВЕРКА НА КАСТОМНЫЙ РЕНДЕРЕР (как просил юзер)
            try {
                net.minecraftforge.client.extensions.common.IClientItemExtensions ext = net.minecraftforge.client.extensions.common.IClientItemExtensions.of(i);
                if (ext != net.minecraftforge.client.extensions.common.IClientItemExtensions.DEFAULT) {
                    net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer renderer = ext.getCustomRenderer();
                    if (renderer != null) {
                        String className = renderer.getClass().getName();
                        // Если рендерер предмета не ванильный (например, из lr_tactical)
                        if (!className.startsWith("net.minecraft.") && !className.startsWith("com.mojang.")) {
                            return true;
                        }
                    }
                }
            } catch (Throwable t) {
                // Игнорируем ошибки при получении экстеншенов
            }

            Class<?> clazz = i.getClass();
            for (Class<?> iface : clazz.getInterfaces()) {
                String name = iface.getName().toLowerCase();
                if (name.contains("tacz") || name.contains("lrtactical") || name.contains("lr_tactical")) return true;
            }

            while (clazz != null && clazz != Object.class) {
                String name = clazz.getName().toLowerCase();
                if (name.contains("tacz") || name.contains("lrtactical") || name.contains("lr_tactical")) return true;
                clazz = clazz.getSuperclass();
            }

            return false;
        });
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (!ClientConfig.ENABLED.get() || !ClientConfig.FIRST_PERSON_BODY_ENABLED.get()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();

        if (CameraViewUtils.isFirstPerson()) {
            // Разрешаем ванильную отрисовку рук и предметов, если предмет в черном списке
            if (isHoldingBlacklistedItem(mc.player)) {
                return;
            }
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (!ClientConfig.ENABLED.get() || !ClientConfig.FIRST_PERSON_BODY_ENABLED.get()) {
            return;
        }

        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }

        if (!CameraViewUtils.isFirstPerson()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        isRenderingBody = true;
        hideArms = isHoldingBlacklistedItem(player);

        // --- УЛЬТИМАТИВНЫЙ ХАК ---
        // Сохраняем текущие предметы из рук
        ItemStack savedMain = player.getMainHandItem();
        ItemStack savedOff = player.getOffhandItem();

        // Если надо спрятать руки, мы "забираем" предметы из рук игрока прямо перед рендером
        if (hideArms) {
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        }

        try {
            PoseStack poseStack = event.getPoseStack();
            Camera camera = event.getCamera();
            MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
            EntityRenderDispatcher renderDispatcher = mc.getEntityRenderDispatcher();

            float partialTick = event.getPartialTick();

            double camX = camera.getPosition().x;
            double camY = camera.getPosition().y;
            double camZ = camera.getPosition().z;

            double playerX = Mth.lerp((double) partialTick, player.xo, player.getX());
            double playerY = Mth.lerp((double) partialTick, player.yo, player.getY());
            double playerZ = Mth.lerp((double) partialTick, player.zo, player.getZ());

            double renderX = playerX - camX;
            double renderY = playerY - camY;
            double renderZ = playerZ - camZ;

            // === ФИКС ПЛАВАНИЯ И ПОЛЗАНЬЯ ===
            float swimAmount = player.getSwimAmount(partialTick);
            Pose pose = player.getPose();

            if (pose == Pose.FALL_FLYING || pose == Pose.SPIN_ATTACK) {
                swimAmount = 1.0f;
            }

            if (swimAmount > 0.0f) {
                float bodyYaw = Mth.lerp(partialTick, player.yBodyRotO, player.yBodyRot);
                float rad = bodyYaw * ((float) Math.PI / 180F);

                double backward = 0.9 * swimAmount;
                double downward = 0.6 * swimAmount;

                renderX += Math.sin(rad) * backward;
                renderY -= downward;
                renderZ -= Math.cos(rad) * backward;
            }
            // ==================================

            float yaw = Mth.lerp(partialTick, player.yRotO, player.getYRot());

            poseStack.pushPose();

            renderDispatcher.render(
                    player,
                    renderX, renderY, renderZ,
                    yaw,
                    partialTick,
                    poseStack,
                    bufferSource,
                    renderDispatcher.getPackedLightCoords(player, partialTick)
            );

            poseStack.popPose();

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // --- ВОЗВРАЩАЕМ ВСЕ КАК БЫЛО ---
            if (hideArms) {
                player.setItemInHand(InteractionHand.MAIN_HAND, savedMain);
                player.setItemInHand(InteractionHand.OFF_HAND, savedOff);
            }
            isRenderingBody = false;
            hideArms = false;
        }
    }

    @SubscribeEvent
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        if (isRenderingBody && event.getEntity() == Minecraft.getInstance().player) {
            PlayerModel<?> model = event.getRenderer().getModel();

            model.head.visible = false;
            model.hat.visible = false;

            if (hideArms) {
                model.leftArm.visible = false;
                model.leftSleeve.visible = false;
                model.rightArm.visible = false;
                model.rightSleeve.visible = false;
            }
        }
    }

    @SubscribeEvent
    public static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        if (isRenderingBody && event.getEntity() == Minecraft.getInstance().player) {
            PlayerModel<?> model = event.getRenderer().getModel();

            model.head.visible = true;
            model.hat.visible = true;

            model.leftArm.visible = true;
            model.leftSleeve.visible = true;
            model.rightArm.visible = true;
            model.rightSleeve.visible = true;
        }
    }
}