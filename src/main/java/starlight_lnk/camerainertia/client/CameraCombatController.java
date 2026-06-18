package starlight_lnk.camerainertia.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CameraCombatController {

    // === ИТОГОВОЕ СМЕЩЕНИЕ ===
    private static float attackPitch = 0.0F;
    private static float attackYaw   = 0.0F;
    private static float attackRoll  = 0.0F;

    private static float prevAttackPitch = 0.0F;
    private static float prevAttackYaw   = 0.0F;
    private static float prevAttackRoll  = 0.0F;

    // === СКОРОСТИ ===
    private static float attackPitchVelocity = 0.0F;
    private static float attackYawVelocity   = 0.0F;
    private static float attackRollVelocity  = 0.0F;

    // === СОСТОЯНИЕ ===
    private static boolean wasAttackDown      = false;
    private static int     attackCooldownTicks = 0;
    private static int     attackShakeTicks    = 0;

    // === КОНСТАНТЫ ===
    private static final float WEAK_MISSED_ATTACK_STRENGTH_MULTIPLIER = 0.4F;
    private static final float OBJECT_ATTACK_STRENGTH_MULTIPLIER      = 0.28F;

    private static final int HIT_ATTACK_COOLDOWN_TICKS    = 3;
    private static final int MISSED_ATTACK_COOLDOWN_TICKS = 5;
    private static final int OBJECT_ATTACK_COOLDOWN_TICKS = 3;

    // ⚔️ Бонус меча при попадании по живому мобу: +20%
    private static final float SWORD_HIT_FACTOR = 1.20F;

    private static final float VISUAL_STRENGTH = 1.0F;

    // === КЭШ МЕЧЕЙ ДЛЯ ОПТИМИЗАЦИИ ===
    private static final Map<Item, Boolean> MELEE_WEAPON_CACHE = new ConcurrentHashMap<>();

    public static void tick() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.player == null || mc.level == null || mc.isPaused()) {
                resetSmoothly();
                return;
            }

            if (!CameraViewUtils.isFirstPerson()) {
                resetSmoothly();
                return;
            }

            LocalPlayer player = mc.player;

            if (attackCooldownTicks > 0) {
                attackCooldownTicks--;
            }

            boolean attackDown        = mc.options.keyAttack.isDown();
            boolean justPressedAttack = attackDown && !wasAttackDown;

            boolean lookingAtLivingEntity  = isLookingAtLivingEntity(mc);
            boolean lookingAtBlockOrObject = isLookingAtBlockOrObject(mc);
            boolean missedAirAttack        = isMissedAirAttack(mc);

            ItemStack held = player.getMainHandItem();

            // Используем новую универсальную проверку на рукопашное оружие
            boolean isSword     = isMeleeWeapon(held);
            boolean generalItem = isGeneralItem(held);

            boolean allowAnimation = generalItem || isSword;

            if (allowAnimation && attackCooldownTicks <= 0) {
                if (justPressedAttack && lookingAtLivingEntity) {
                    // 🩸 Попадание по живому существу
                    float baseMul   = 1.0F;
                    float itemBonus = isSword ? SWORD_HIT_FACTOR : 1.0F;
                    triggerAttackInertia(player, VISUAL_STRENGTH, baseMul, itemBonus);
                    attackCooldownTicks = HIT_ATTACK_COOLDOWN_TICKS;
                } else if (justPressedAttack && lookingAtBlockOrObject) {
                    // 🧱 Удар по блоку / неживому объекту
                    triggerAttackInertia(player, VISUAL_STRENGTH, OBJECT_ATTACK_STRENGTH_MULTIPLIER, 1.0F);
                    attackCooldownTicks = OBJECT_ATTACK_COOLDOWN_TICKS;
                } else if (justPressedAttack && !lookingAtLivingEntity
                        && !lookingAtBlockOrObject && missedAirAttack) {
                    // 💨 Удар по воздуху
                    triggerAttackInertia(player, VISUAL_STRENGTH, WEAK_MISSED_ATTACK_STRENGTH_MULTIPLIER, 1.0F);
                    attackCooldownTicks = MISSED_ATTACK_COOLDOWN_TICKS;
                }
            }

            wasAttackDown = attackDown;

            prevAttackPitch = attackPitch;
            prevAttackYaw   = attackYaw;
            prevAttackRoll  = attackRoll;

            // VELOCITY → POSITION
            attackPitch += attackPitchVelocity;
            attackYaw   += attackYawVelocity;
            attackRoll  += attackRollVelocity;

            attackPitchVelocity *= 0.58F;
            attackYawVelocity   *= 0.60F;
            attackRollVelocity  *= 0.60F;

            attackPitch *= 0.76F;
            attackYaw   *= 0.78F;
            attackRoll  *= 0.78F;

            // Микро-тряска
            if (attackShakeTicks > 0) {
                float life = attackShakeTicks / 4.0F;
                float shakeStrength = 0.22F * life * VISUAL_STRENGTH;
                float t = player.tickCount * 2.37F;

                attackPitch += (float) Math.sin(t)         * shakeStrength;
                attackYaw   += (float) Math.cos(t * 1.31F) * shakeStrength * 0.7F;
                attackRoll  += (float) Math.sin(t * 1.71F) * shakeStrength * 0.9F;

                attackShakeTicks--;
            }

            attackPitch = Mth.clamp(attackPitch, -4.50F, 5.30F);
            attackYaw   = Mth.clamp(attackYaw,   -3.30F, 3.30F);
            attackRoll  = Mth.clamp(attackRoll,  -2.90F, 2.90F);

            if (Math.abs(attackPitch) < 0.0005F) attackPitch = 0.0F;
            if (Math.abs(attackYaw)   < 0.0005F) attackYaw   = 0.0F;
            if (Math.abs(attackRoll)  < 0.0005F) attackRoll  = 0.0F;
            if (Math.abs(attackPitchVelocity) < 0.0005F) attackPitchVelocity = 0.0F;
            if (Math.abs(attackYawVelocity)   < 0.0005F) attackYawVelocity   = 0.0F;
            if (Math.abs(attackRollVelocity)  < 0.0005F) attackRollVelocity  = 0.0F;

        } catch (Throwable t) {
            t.printStackTrace();
            attackPitch = attackYaw = attackRoll = 0.0F;
            attackPitchVelocity = attackYawVelocity = attackRollVelocity = 0.0F;
            attackShakeTicks = 0;
        }
    }

    private static void triggerAttackInertia(LocalPlayer player, float visualStrength, float strengthMultiplier, float itemBonus) {
        if (strengthMultiplier <= 0.0F) return;

        float side = (player.tickCount & 1) == 0 ? 1.0F : -1.0F;
        float finalStrength = visualStrength * itemBonus * strengthMultiplier;

        attackPitchVelocity += 1.45F * finalStrength;
        attackYawVelocity   += side * 0.82F * finalStrength;
        attackRollVelocity  -= side * 0.66F * finalStrength;

        if (strengthMultiplier >= 0.99F) {
            attackShakeTicks = 4;
        } else {
            attackShakeTicks = Math.max(attackShakeTicks, 2);
        }
    }

    private static void resetSmoothly() {
        attackPitch = Mth.lerp(0.16F, attackPitch, 0.0F);
        attackYaw   = Mth.lerp(0.16F, attackYaw,   0.0F);
        attackRoll  = Mth.lerp(0.16F, attackRoll,  0.0F);

        attackPitchVelocity *= 0.55F;
        attackYawVelocity   *= 0.55F;
        attackRollVelocity  *= 0.55F;

        attackShakeTicks    = 0;
        wasAttackDown       = false;
        attackCooldownTicks = 0;

        if (Math.abs(attackPitch) < 0.0005F) attackPitch = 0.0F;
        if (Math.abs(attackYaw)   < 0.0005F) attackYaw   = 0.0F;
        if (Math.abs(attackRoll)  < 0.0005F) attackRoll  = 0.0F;
    }

    // =================================================================

    // --- УНИВЕРСАЛЬНАЯ ПРОВЕРКА ОРУЖИЯ БЛИЖНЕГО БОЯ ---
    private static boolean isMeleeWeapon(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Item item = stack.getItem();

        return MELEE_WEAPON_CACHE.computeIfAbsent(item, i -> {
            if (i instanceof SwordItem || i instanceof TridentItem || i instanceof AxeItem) {
                return true;
            }

            // Проверка интерфейсов для модов (например LrTactical IMeleeWeapon)
            for (Class<?> iface : i.getClass().getInterfaces()) {
                if (iface.getName().contains("IMeleeWeapon")) {
                    return true;
                }
            }
            return false;
        });
    }

    private static boolean isLookingAtLivingEntity(Minecraft mc) {
        HitResult hr = mc.hitResult;
        if (hr == null || hr.getType() != HitResult.Type.ENTITY) return false;
        if (!(hr instanceof EntityHitResult ehr)) return false;
        return ehr.getEntity() instanceof LivingEntity;
    }

    private static boolean isLookingAtBlockOrObject(Minecraft mc) {
        HitResult hr = mc.hitResult;
        if (hr == null) return false;
        if (hr.getType() == HitResult.Type.BLOCK) {
            return hr instanceof BlockHitResult;
        }
        if (hr.getType() == HitResult.Type.ENTITY && hr instanceof EntityHitResult ehr) {
            return !(ehr.getEntity() instanceof LivingEntity);
        }
        return false;
    }

    private static boolean isMissedAirAttack(Minecraft mc) {
        HitResult hr = mc.hitResult;
        return hr == null || hr.getType() == HitResult.Type.MISS;
    }

    private static boolean isGeneralItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return true;
        Item item = stack.getItem();

        // Проверяем, не оружие ли это? (Используем наш кэшированный метод)
        if (isMeleeWeapon(stack)) return false;

        if (item instanceof DiggerItem)     return false;
        if (item instanceof BowItem)        return false;
        if (item instanceof CrossbowItem)   return false;
        if (item instanceof ShieldItem)     return false;
        if (item instanceof FishingRodItem) return false;

        return true;
    }

    // =================================================================

    public static float getPitchOffset(float partialTick) {
        return Mth.lerp(partialTick, prevAttackPitch, attackPitch);
    }
    public static float getYawOffset(float partialTick) {
        return Mth.lerp(partialTick, prevAttackYaw, attackYaw);
    }
    public static float getRollOffset(float partialTick) {
        return Mth.lerp(partialTick, prevAttackRoll, attackRoll);
    }
}