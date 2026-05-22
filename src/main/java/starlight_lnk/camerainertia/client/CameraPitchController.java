package starlight_lnk.camerainertia.client;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.phys.Vec3;

/**
 * 🎥 Контроллер pitch-смещения камеры.
 *
 * Физика приземления:
 *   - Удар об землю → камера резко уходит ВВЕРХ (отрицательный pitch)
 *   - Пружина тянет обратно → камера идёт вниз с перелётом
 *   - Один-два колебания → покой
 *
 * Модель: классическая пружина с вязким трением (Хук + демпфирование).
 *   vel  += -pos * spring
 *   vel  *= damping
 *   pos  += vel
 */
public class CameraPitchController {

    private static float currentPitch = 0.0F;
    private static float prevPitch    = 0.0F;

    // ============================================================
    //   КАНАЛ ПРУЖИНЫ
    // ============================================================

    private static float springPos = 0.0F;
    private static float springVel = 0.0F;

    /** Жёсткость пружины. Выше = быстрее достигает пика, кик «вовремя». */
    private static final float SPRING = 0.55F;
    /** Демпфирование. Чуть ниже = острее реакция, меньше «вязкости». */
    private static final float DAMPING = 0.78F;

    private static final float SPRING_MAX_DEG = 8.0F;

    // ============================================================
    //   ОСТАЛЬНЫЕ КАНАЛЫ
    // ============================================================

    private static float attackPitch  = 0.0F;
    private static float attackTarget = 0.0F;

    private static float swimPitch = 0.0F;
    private static float swimPhase = 0.0F;

    private static float usePitch  = 0.0F;
    private static float useTarget = 0.0F;
    private static int   useTickCounter = 0;

    // ============================================================
    //   СОСТОЯНИЕ ИГРОКА
    // ============================================================

    private static boolean wasOnGround = true;
    private static boolean wasInWater  = false;
    private static double  prevMotionY = 0.0;

    private static double  takeoffY       = 0.0;
    private static boolean takeoffWasJump = false;
    private static boolean tracking       = false;

    private static boolean wasSwinging = false;
    private static float   prevHealth  = -1.0F;

    // ============================================================
    //   ОСНОВНОЙ ТИК
    // ============================================================

    public static void tick() {
        try {
            Minecraft mc = Minecraft.getInstance();

            if (mc == null || mc.player == null || mc.level == null || mc.isPaused()) {
                dampAllChannels();
                return;
            }

            if (!CameraViewUtils.isFirstPerson()) {
                dampAllChannels();
                if (Math.abs(currentPitch) < 0.001F) currentPitch = 0.0F;
                if (Math.abs(springPos)    < 0.001F) springPos    = 0.0F;
                if (Math.abs(swimPitch)    < 0.001F) swimPitch    = 0.0F;
                if (Math.abs(attackPitch)  < 0.001F) attackPitch  = 0.0F;
                if (Math.abs(usePitch)     < 0.001F) usePitch     = 0.0F;
                useTickCounter = 0;
                return;
            }

            Player player = mc.player;

            boolean onGround = player.onGround();
            boolean inWater  = player.isInWater()
                    || player.isUnderWater()
                    || player.isInWaterOrBubble()
                    || player.isSwimming();
            double  motionY  = player.getDeltaMovement().y;
            double  posY     = player.getY();

            // ========== УДАР ==========
            boolean swinging = player.swinging;
            if (swinging && !wasSwinging) {
                attackTarget = -1.0F;
            }
            wasSwinging = swinging;

            // ========== УРОН ==========
            float curHealth = player.getHealth();
            if (prevHealth < 0) prevHealth = curHealth;
            if (curHealth < prevHealth) {
                float damage = prevHealth - curHealth;
                float kick = Math.min(damage * 0.65F, 3.5F);
                if (Math.random() < 0.5) kick = -kick;
                attackTarget += kick;
            }
            prevHealth = curHealth;

            // ========== ИСПОЛЬЗОВАНИЕ ПРЕДМЕТА ==========
            if (player.isUsingItem()) {
                ItemStack using = player.getUseItem();
                UseAnim anim = using.getUseAnimation();
                int kind = getUseKind(using, anim);

                if (kind != 0) {
                    useTickCounter++;
                    if (useTickCounter >= 4) {
                        useTickCounter = 0;
                        float step;
                        if (kind == 1)      step = -0.9F;
                        else if (kind == 2) step = -0.9F * 1.30F;
                        else                step = 0.9F;

                        useTarget += step;
                        if (useTarget >  6.0F) useTarget =  6.0F;
                        if (useTarget < -6.0F) useTarget = -6.0F;
                    }
                } else {
                    useTickCounter = 0;
                }
            } else {
                useTickCounter = 0;
            }

            // ========== ОТСЛЕЖИВАНИЕ ПОЛЁТА ==========
            if (!onGround && !inWater) {
                if (!tracking) {
                    tracking = true;
                    takeoffY = posY;
                    takeoffWasJump = motionY > 0.08;
                }
            }

            // ========== ПРЫЖОК — голова отстаёт, камера вниз ==========
            if (wasOnGround && !onGround && motionY > 0.1) {
                applySpringImpulse(1.0F);
            }

            // ========== ПРИЗЕМЛЕНИЕ — главное событие ==========
            if (!wasOnGround && onGround) {
                handleLanding(player, posY);
                tracking = false;
            }

            // ========== ВХОД В ВОДУ ==========
            if (!wasInWater && inWater && prevMotionY < -0.2) {
                float splash = (float) Math.min(Math.abs(prevMotionY) * 4.0F, 3.0F);
                applySpringImpulse(-splash);
                tracking = false;
            }

            // ========== ВЫХОД ИЗ ВОДЫ ==========
            if (wasInWater && !inWater) {
                applySpringImpulse(0.75F);
            }

            // ========== ШАГ ПРУЖИНЫ ==========
            stepSpring();

            // ========== УДАРЫ ==========
            attackPitch  += (attackTarget - attackPitch) * 0.18F;
            attackTarget *= 0.82F;

            // ========== ИСПОЛЬЗОВАНИЕ ==========
            usePitch  += (useTarget - usePitch) * 0.22F;
            useTarget *= 0.93F;

            // ========== ПЛАВАНИЕ ==========
            if (inWater && player.isSwimming()) {
                Vec3 v = player.getDeltaMovement();
                double horizSpeed = Math.sqrt(v.x * v.x + v.z * v.z);

                swimPhase += 0.35F;
                if (swimPhase > (float)(Math.PI * 2)) {
                    swimPhase -= (float)(Math.PI * 2);
                }

                float amplitude  = (float) Math.min(horizSpeed * 12.5F, 4.0F);
                float swimTarget = (float) Math.sin(swimPhase) * amplitude;
                swimPitch += (swimTarget - swimPitch) * 0.20F;
            } else {
                swimPitch *= 0.88F;
                swimPhase = 0.0F;
            }

            // ========== ИТОГ ==========
            prevPitch = currentPitch;
            currentPitch = springPos + attackPitch + swimPitch + usePitch;

            if (Float.isNaN(currentPitch) || Float.isInfinite(currentPitch)) {
                hardReset();
            }

            wasOnGround = onGround;
            wasInWater  = inWater;
            prevMotionY = motionY;
        } catch (Throwable t) {
            t.printStackTrace();
            hardReset();
            tracking = false;
        }
    }

    // ============================================================
    //   ПРИЗЕМЛЕНИЕ
    // ============================================================

    /**
     * Импульс направлен ВВЕРХ (отрицательный pitch):
     * тело остановилось → голова по инерции уходит вверх относительно
     * тела → возврат вниз с одним-двумя колебаниями.
     *
     * Амплитуды уменьшены вдвое — более реалистичный, ненавязчивый эффект.
     */
    private static void handleLanding(Player player, double currentY) {
        double rawFallHeight = takeoffY - currentY;
        if (rawFallHeight < 0) rawFallHeight = 0;

        double impactSpeed = Math.max(0, -prevMotionY);

        float slowFallMul = 1.0F;
        if (player.hasEffect(MobEffects.SLOW_FALLING)) {
            slowFallMul = 0.3F;
        }

        float impulse;

        if (rawFallHeight < 0.6) {
            // === AUTO-STEP / мини-прыжок ===
            if (takeoffWasJump) {
                impulse = 1.25F;
            } else {
                impulse = 0.75F;
            }
        } else if (rawFallHeight < 2.0) {
            // === SHORT ===
            float t = (float) ((rawFallHeight - 0.6) / 1.4);
            impulse = Mth.lerp(t, 1.5F, 2.25F);
        } else if (rawFallHeight < 4.0) {
            // === MEDIUM ===
            float t = (float) ((rawFallHeight - 2.0) / 2.0);
            impulse = Mth.lerp(t, 2.25F, 3.25F);
        } else {
            // === HARD ===
            float over = (float) (rawFallHeight - 4.0);
            impulse = 3.25F + (float) Math.sqrt(over) * 0.75F;
            if (impulse > 6.0F) impulse = 6.0F;
        }

        // Бонус за скорость удара
        if (impactSpeed > 0.8) {
            float speedBonus = (float) ((impactSpeed - 0.8) * 0.5);
            impulse += Math.min(speedBonus, 1.25F);
        }

        impulse *= slowFallMul;

        // ОТРИЦАТЕЛЬНЫЙ → камера вверх → пружина возвращает вниз
        applySpringImpulse(-impulse);
    }

    // ============================================================
    //   ПРУЖИНА (классическая модель)
    // ============================================================

    private static void applySpringImpulse(float deltaVel) {
        springVel += deltaVel;
        if (springVel >  50.0F) springVel =  50.0F;
        if (springVel < -50.0F) springVel = -50.0F;
    }

    /**
     * Классическая пружина:
     *   vel += -pos * SPRING   (сила тянет к нулю, пропорционально смещению)
     *   vel *= DAMPING         (вязкое трение)
     *   pos += vel             (интеграция)
     */
    private static void stepSpring() {
        springVel += -springPos * SPRING;
        springVel *= DAMPING;
        springPos += springVel;

        if (springPos >  SPRING_MAX_DEG) { springPos =  SPRING_MAX_DEG; springVel = 0.0F; }
        if (springPos < -SPRING_MAX_DEG) { springPos = -SPRING_MAX_DEG; springVel = 0.0F; }

        if (Math.abs(springPos) < 0.005F && Math.abs(springVel) < 0.01F) {
            springPos = 0.0F;
            springVel = 0.0F;
        }
    }

    // ============================================================
    //   СЛУЖЕБНОЕ
    // ============================================================

    private static void dampAllChannels() {
        prevPitch    = currentPitch;
        currentPitch *= 0.7F;
        springPos    *= 0.7F;
        springVel    *= 0.7F;
        swimPitch    *= 0.7F;
        attackPitch  *= 0.7F;
        attackTarget *= 0.7F;
        usePitch     *= 0.7F;
        useTarget    *= 0.7F;
    }

    private static void hardReset() {
        currentPitch = 0.0F;
        prevPitch    = 0.0F;
        springPos    = 0.0F;
        springVel    = 0.0F;
        attackPitch  = 0.0F;
        attackTarget = 0.0F;
        swimPitch    = 0.0F;
        swimPhase    = 0.0F;
        usePitch     = 0.0F;
        useTarget    = 0.0F;
    }

    private static int getUseKind(ItemStack stack, UseAnim anim) {
        if (stack.isEmpty()) return 0;

        if (stack.is(Items.MILK_BUCKET)) return 2;
        if (stack.getItem() instanceof BucketItem && anim == UseAnim.DRINK) return 2;
        if (stack.getItem() instanceof PotionItem) return 1;
        if (anim == UseAnim.DRINK) return 1;
        if (anim == UseAnim.EAT) return 3;

        FoodProperties food = stack.getFoodProperties(null);
        if (food != null) return 3;

        return 0;
    }

    public static float getPitch(float partialTick) {
        if (Float.isNaN(partialTick) || Float.isInfinite(partialTick)) partialTick = 0.0F;
        if (partialTick < 0.0F) partialTick = 0.0F;
        if (partialTick > 1.0F) partialTick = 1.0F;

        float pitch = prevPitch + (currentPitch - prevPitch) * partialTick;
        if (Float.isNaN(pitch) || Float.isInfinite(pitch)) return 0.0F;
        return pitch;
    }
}