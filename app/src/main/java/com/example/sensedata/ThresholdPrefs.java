package com.example.sensedata;

import android.content.Context;
import android.content.SharedPreferences;

public final class ThresholdPrefs {

    private ThresholdPrefs() {}

    // Назва сховища
    private static final String PREFS = "threshold_prefs";

    // Ключі
    private static final String KEY_SET   = "thresholds_set";
    private static final String KEY_T_MIN = "t_min";
    private static final String KEY_T_MAX = "t_max";
    private static final String KEY_H_MIN = "h_min";
    private static final String KEY_H_MAX = "h_max";

    // Дефолтні значення (мають співпадати з тим, що показуєш у UI)
    private static final float DEF_T_MIN = 20.0f;
    private static final float DEF_T_MAX = 24.0f;
    private static final int   DEF_H_MIN = 40;
    private static final int   DEF_H_MAX = 60;

    // Межі слайдерів (для валідації)
    private static final float SLIDER_T_MIN = -10f;
    private static final float SLIDER_T_MAX =  40f;
    private static final int   SLIDER_H_MIN =   0;
    private static final int   SLIDER_H_MAX = 100;

    private static SharedPreferences sp(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Чи задані валідні пороги користувачем */
    public static boolean isSet(Context ctx) {
        SharedPreferences p = sp(ctx);
        boolean flag = p.getBoolean(KEY_SET, false);

        float tMin = p.getFloat(KEY_T_MIN, Float.NaN);
        float tMax = p.getFloat(KEY_T_MAX, Float.NaN);
        int   hMin = p.getInt(KEY_H_MIN, Integer.MIN_VALUE);
        int   hMax = p.getInt(KEY_H_MAX, Integer.MIN_VALUE);

        boolean haveAllValues =
                !Float.isNaN(tMin) &&
                        !Float.isNaN(tMax) &&
                        hMin != Integer.MIN_VALUE &&
                        hMax != Integer.MIN_VALUE;

        boolean valid =
                (tMin >= SLIDER_T_MIN && tMax <= SLIDER_T_MAX && tMin < tMax) &&
                        (hMin >= SLIDER_H_MIN && hMax <= SLIDER_H_MAX && hMin < hMax);

        // Якщо ключа KEY_SET немає, але збережені валідні значення — вважаємо, що задано.
        if (!flag && haveAllValues && valid) {
            p.edit().putBoolean(KEY_SET, true).apply();
            return true;
        }
        return flag && haveAllValues && valid;
    }

    /** Зберегти пороги (із валідацією) */
    public static void save(Context ctx, float tMin, float tMax, int hMin, int hMax) {
        // простий захист від некоректних даних
        if (!(tMin >= SLIDER_T_MIN && tMax <= SLIDER_T_MAX && tMin < tMax)) {
            throw new IllegalArgumentException("Некоректні межі температури");
        }
        if (!(hMin >= SLIDER_H_MIN && hMax <= SLIDER_H_MAX && hMin < hMax)) {
            throw new IllegalArgumentException("Некоректні межі вологості");
        }

        sp(ctx).edit()
                .putFloat(KEY_T_MIN, tMin)
                .putFloat(KEY_T_MAX, tMax)
                .putInt(KEY_H_MIN, hMin)
                .putInt(KEY_H_MAX, hMax)
                .putBoolean(KEY_SET, true)
                .apply();
    }

    /** Скинути все (зручно для тестів) */
    public static void clear(Context ctx) {
        sp(ctx).edit().clear().apply();
    }

    // ---- Геттери з дефолтами, якщо ще не задано ----

    public static float getTempMin(Context ctx) {
        SharedPreferences p = sp(ctx);
        if (!p.contains(KEY_T_MIN) || !p.contains(KEY_T_MAX)) return DEF_T_MIN;
        return p.getFloat(KEY_T_MIN, DEF_T_MIN);
    }

    public static float getTempMax(Context ctx) {
        SharedPreferences p = sp(ctx);
        if (!p.contains(KEY_T_MIN) || !p.contains(KEY_T_MAX)) return DEF_T_MAX;
        return p.getFloat(KEY_T_MAX, DEF_T_MAX);
    }

    public static int getHumMin(Context ctx) {
        SharedPreferences p = sp(ctx);
        if (!p.contains(KEY_H_MIN) || !p.contains(KEY_H_MAX)) return DEF_H_MIN;
        return p.getInt(KEY_H_MIN, DEF_H_MIN);
    }

    public static int getHumMax(Context ctx) {
        SharedPreferences p = sp(ctx);
        if (!p.contains(KEY_H_MIN) || !p.contains(KEY_H_MAX)) return DEF_H_MAX;
        return p.getInt(KEY_H_MAX, DEF_H_MAX);
    }

    // (опційно) повернути дефолти, не торкаючись сховища
    public static float getDefaultTempMin() { return DEF_T_MIN; }
    public static float getDefaultTempMax() { return DEF_T_MAX; }
    public static int   getDefaultHumMin()  { return DEF_H_MIN; }
    public static int   getDefaultHumMax()  { return DEF_H_MAX; }
}
