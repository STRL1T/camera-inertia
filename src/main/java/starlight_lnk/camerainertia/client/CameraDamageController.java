package starlight_lnk.camerainertia.client;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

public class CameraDamageController {

    private static final Random RNG = new Random();

    private static float pitchOffset = 0.0F;
    private static float yawOffset   = 0.0F;
    private static float rollOffset  = 0.0F;

    private static float prevPitchOffset = 0.0F;
    private static float prevYawOffset   = 0.0F;
    private static float prevRollOffset  = 0.0F;

    // Затухание базовое
    private static float decay = 0.78F;

    // Долгие эффекты
    private static int   sustainedTicks = 0;
    private static byte  sustainedType  = 0; // 1=огонь, 2=утопление, 3=шипы, 4=послемолнии, 5=послевзрыва

    private static float prevHealth = -1.0F;

    public static void tick() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.player == null || mc.level == null || mc.isPaused()) {
                prevPitchOffset = pitchOffset;
                prevYawOffset   = yawOffset;
                prevRollOffset  = rollOffset;
                pitchOffset *= decay;
                yawOffset   *= decay;
                rollOffset  *= decay;
                return;
            }

            // 🎥 Только в 1st person — в 3rd person плавно гасим тряску
            if (!CameraViewUtils.isFirstPerson()) {
                prevPitchOffset = pitchOffset;
                prevYawOffset   = yawOffset;
                prevRollOffset  = rollOffset;
                pitchOffset *= decay;
                yawOffset   *= decay;
                rollOffset  *= decay;

                if (Math.abs(pitchOffset) < 0.001F) pitchOffset = 0.0F;
                if (Math.abs(yawOffset)   < 0.001F) yawOffset   = 0.0F;
                if (Math.abs(rollOffset)  < 0.001F) rollOffset  = 0.0F;

                // Текущее здоровье запоминаем, чтобы при возврате в 1st person
                // не получить ложный «удар» из-за разницы за время вида от 3-го лица
                if (mc.player != null) {
                    prevHealth = mc.player.getHealth();
                }
                // Сбрасываем поддерживаемые эффекты — их продолжать в 3rd person смысла нет
                sustainedTicks = 0;
                sustainedType  = 0;
                return;
            }

            Player player = mc.player;
            float curHealth = player.getHealth();
            if (prevHealth < 0) prevHealth = curHealth;

            if (curHealth < prevHealth) {
                float damage = prevHealth - curHealth;
                DamageSource source = player.getLastDamageSource();
                System.out.println("[CameraInertia] DAMAGE! amount=" + damage
                        + " source=" + (source != null ? source.type().msgId() : "null"));
                applyDamageKick(player, source, damage);
            }
            prevHealth = curHealth;

            // Поддерживаемые эффекты
            if (sustainedTicks > 0) {
                sustainedTicks--;
                applySustained(sustainedType);
            } else {
                sustainedType = 0;
            }

            prevPitchOffset = pitchOffset;
            prevYawOffset   = yawOffset;
            prevRollOffset  = rollOffset;

            pitchOffset *= decay;
            yawOffset   *= decay;
            rollOffset  *= decay;

            // Постепенно возвращаем decay к нормальному
            if (decay < 0.78F) decay = Math.min(0.78F, decay + 0.01F);

            if (Math.abs(pitchOffset) < 0.001F) pitchOffset = 0.0F;
            if (Math.abs(yawOffset)   < 0.001F) yawOffset   = 0.0F;
            if (Math.abs(rollOffset)  < 0.001F) rollOffset  = 0.0F;

            if (Float.isNaN(pitchOffset) || Float.isInfinite(pitchOffset)) pitchOffset = 0.0F;
            if (Float.isNaN(yawOffset)   || Float.isInfinite(yawOffset))   yawOffset   = 0.0F;
            if (Float.isNaN(rollOffset)  || Float.isInfinite(rollOffset))  rollOffset  = 0.0F;

        } catch (Throwable t) {
            t.printStackTrace();
            pitchOffset = yawOffset = rollOffset = 0.0F;
            prevPitchOffset = prevYawOffset = prevRollOffset = 0.0F;
        }
    }

    private static void applyDamageKick(Player player, DamageSource source, float damage) {
        if (source == null) {
            pitchOffset += randSigned(3.0F);
            yawOffset   += randSigned(3.0F);
            return;
        }

        String id = source.type().msgId();
        System.out.println("[CameraInertia] Damage type msgId: " + id + " | damage=" + damage);

        // ⚡ МОЛНИЯ
        if (source.is(DamageTypes.LIGHTNING_BOLT) || id.contains("lightning")) {
            pitchOffset += -20.0F;
            yawOffset   += randSigned(12.0F);
            rollOffset  += randSigned(25.0F);
            decay = 0.92F;
            sustainedType  = 4;
            sustainedTicks = 15;

            // 🎬 FOV: резкое сужение (шок), медленное восстановление
            CameraFovController.addSlowImpulse(-0.12F, 0.94F);
            System.out.println("[CameraInertia] -> LIGHTNING kick");
            return;
        }

        // 💥 ВЗРЫВ
        if (source.is(DamageTypes.EXPLOSION) || source.is(DamageTypes.PLAYER_EXPLOSION)
                || id.contains("explosion") || id.contains("explode")) {
            float mag = Math.min(Math.max(damage * 2.5F, 10.0F), 25.0F);
            pitchOffset += randSigned(mag);
            yawOffset   += randSigned(mag);
            rollOffset  += randSigned(mag * 1.5F);
            decay = 0.93F;
            sustainedType  = 5;
            sustainedTicks = 20;

            // 🎬 FOV: ударная волна (расширение) + долгое сужение
            float fovKick = Math.min(damage * 0.015F, 0.15F);
            CameraFovController.addSlowImpulse(-fovKick, 0.94F);
            CameraFovController.addImpulse(+0.05F);
            System.out.println("[CameraInertia] -> EXPLOSION kick mag=" + mag);
            return;
        }

        // 🌵 КАКТУС / ШИПЫ
        if (source.is(DamageTypes.CACTUS) || source.is(DamageTypes.SWEET_BERRY_BUSH)
                || source.is(DamageTypes.THORNS)) {
            sustainedType  = 3;
            sustainedTicks = 4;
            pitchOffset += randSigned(2.0F);
            return;
        }

        // 🌊 УТОПЛЕНИЕ — не сбрасываем фазу при повторных уронах
        if (source.is(DamageTypes.DROWN)) {
            if (sustainedType != 2) {
                sustainedType  = 2;
                sustainedTicks = 20;
            } else {
                sustainedTicks = Math.max(sustainedTicks, 20);
            }
            return;
        }

        // 🔥 ОГОНЬ / ЛАВА — начальный кик только при первом ожоге
        if (source.is(DamageTypes.IN_FIRE) || source.is(DamageTypes.ON_FIRE)
                || source.is(DamageTypes.LAVA) || source.is(DamageTypes.HOT_FLOOR)) {
            if (sustainedType != 1) {
                pitchOffset += randSigned(2.5F);
                rollOffset  += randSigned(3.5F);
                sustainedType  = 1;
                sustainedTicks = 12;

                // 🎬 FOV: лёгкое сжатие (жар) — только при первом ожоге
                CameraFovController.addImpulse(-0.015F);
            } else {
                sustainedTicks = Math.max(sustainedTicks, 12);
            }
            return;
        }

        // 🏹 СНАРЯДЫ
        if (source.is(DamageTypes.ARROW) || source.is(DamageTypes.TRIDENT)
                || source.is(DamageTypes.MOB_PROJECTILE) || source.is(DamageTypes.THROWN)
                || id.contains("arrow") || id.contains("trident")) {
            Entity attacker = source.getDirectEntity();
            if (attacker == null) attacker = source.getEntity();
            if (attacker != null) {
                directionalKick(player, attacker, damage, 3.0F, 5.0F);
            } else {
                pitchOffset += -8.0F;
                rollOffset  += randSigned(5.0F);
            }

            // 🎬 FOV: быстрый зум (вздрагивание)
            CameraFovController.addImpulse(-0.025F);
            System.out.println("[CameraInertia] -> PROJECTILE kick");
            return;
        }

        // 🗡 АТАКА МОБА / ИГРОКА
        if (source.is(DamageTypes.MOB_ATTACK) || source.is(DamageTypes.PLAYER_ATTACK)
                || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO)
                || id.contains("mob") || id.contains("player")) {
            Entity attacker = source.getEntity();
            if (attacker != null) {
                directionalKick(player, attacker, damage, 4.0F, 5.0F);
            } else {
                pitchOffset += randSigned(5.0F);
                yawOffset   += randSigned(5.0F);
            }

            // 🎬 FOV: тяжёлый удар (>4 урона) — заметный кик
            if (damage > 4.0F) {
                float fovKick = Math.min(damage * 0.008F, 0.06F);
                CameraFovController.addSlowImpulse(-fovKick, 0.90F);
            }
            System.out.println("[CameraInertia] -> MOB_ATTACK kick");
            return;
        }

        if (source.is(DamageTypes.FALL)) return;

        // ❓ Дефолт
        System.out.println("[CameraInertia] Unrecognized damage type: " + id);
        pitchOffset += randSigned(4.0F);
        yawOffset   += randSigned(4.0F);
        rollOffset  += randSigned(4.0F);
    }

    private static void directionalKick(Player player, Entity attacker, float damage,
                                        float yawScale, float pitchScale) {
        Vec3 toAttacker = attacker.position().subtract(player.position());
        if (toAttacker.lengthSqr() < 0.0001) {
            pitchOffset += randSigned(damage * pitchScale);
            yawOffset   += randSigned(damage * yawScale);
            rollOffset  += randSigned(damage);
            return;
        }
        toAttacker = toAttacker.normalize();

        float playerYawRad = (float) Math.toRadians(player.getYRot());
        Vec3 right   = new Vec3(-Math.cos(playerYawRad), 0, -Math.sin(playerYawRad));
        Vec3 forward = new Vec3(-Math.sin(playerYawRad), 0, Math.cos(playerYawRad));

        double rightDot   = toAttacker.dot(right);
        double forwardDot = toAttacker.dot(forward);

        float mag = Math.max(damage * 2.0F, 4.0F);
        mag = Math.min(mag, 15.0F);

        yawOffset   += (float)(-rightDot * mag * yawScale);
        pitchOffset += (float)(-forwardDot * mag * pitchScale);
        rollOffset  += randSigned(mag * 0.8F);
    }

    private static void applySustained(byte type) {
        switch (type) {
            case 1 -> { // 🔥 Огонь — дрожащая паника
                long t = Minecraft.getInstance().level.getGameTime();
                pitchOffset += Mth.sin(t * 2.1F) * 1.2F + randSigned(0.5F);
                rollOffset  += Mth.cos(t * 1.7F) * 1.5F + randSigned(0.6F);
                yawOffset   += randSigned(0.4F);
            }
            case 2 -> { // 🌊 Утопление — плавное мягкое покачивание
                long t = Minecraft.getInstance().level.getGameTime();
                pitchOffset += Mth.sin(t * 0.15F) * 0.4F;
                rollOffset  += Mth.cos(t * 0.12F) * 0.5F;
                yawOffset   += Mth.sin(t * 0.08F) * 0.2F;
            }
            case 3 -> { // 🌵 Шипы
                pitchOffset += randSigned(1.2F);
                yawOffset   += randSigned(1.2F);
            }
            case 4 -> { // ⚡ После молнии — высокочастотная дрожь
                pitchOffset += randSigned(2.5F);
                yawOffset   += randSigned(2.5F);
                rollOffset  += randSigned(3.5F);
            }
            case 5 -> { // 💥 После взрыва — затухающее оглушение
                long t = Minecraft.getInstance().level.getGameTime();
                float intensity = sustainedTicks / 20.0F;
                pitchOffset += (Mth.sin(t * 1.3F) * 2.0F + randSigned(1.5F)) * intensity;
                yawOffset   += (Mth.cos(t * 1.1F) * 2.0F + randSigned(1.5F)) * intensity;
                rollOffset  += (Mth.sin(t * 0.9F) * 3.0F + randSigned(2.0F)) * intensity;
            }
        }
    }

    private static float randSigned(float mag) {
        return (RNG.nextFloat() * 2.0F - 1.0F) * mag;
    }

    public static float getPitchOffset(float partialTick) {
        return Mth.lerp(partialTick, prevPitchOffset, pitchOffset);
    }

    public static float getYawOffset(float partialTick) {
        return Mth.lerp(partialTick, prevYawOffset, yawOffset);
    }

    public static float getRollOffset(float partialTick) {
        return Mth.lerp(partialTick, prevRollOffset, rollOffset);
    }
}