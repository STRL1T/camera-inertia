package starlight_lnk.camerainertia.client;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;

/**
 * Утилитный класс для проверки текущего вида камеры.
 *
 * Все эффекты мода работают ТОЛЬКО от первого лица.
 * Когда игрок переключается в 3rd person — вся инерция, тряска,
 * FOV-эффекты и блюр должны мгновенно отключаться, иначе камера
 * будет странно дёргаться вокруг модельки игрока.
 */
public final class CameraViewUtils {

    private CameraViewUtils() {}

    /**
     * @return true только если камера в режиме FIRST_PERSON.
     *         Возвращает false для THIRD_PERSON_BACK, THIRD_PERSON_FRONT,
     *         а также если Minecraft/options ещё не инициализированы.
     */
    public static boolean isFirstPerson() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.options == null) return false;
            return mc.options.getCameraType() == CameraType.FIRST_PERSON;
        } catch (Throwable t) {
            return false;
        }
    }
}