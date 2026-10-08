package com.rotate.xposed;

import android.content.pm.ActivityInfo;

/** 角度与系统常量之间的换算，以及配置在 Settings.System 中的键名。 */
public final class Config {

    /** -1 表示不干预 */
    public static final int NONE = -1;

    public static final String SETTING_ENABLED = "app_rotation_enabled";
    public static final String SETTING_PREFIX = "app_rotation_";
    /** 仅用于探测 root 写入权限 */
    public static final String SETTING_PROBE = "app_rotation_probe";

    /** 下拉选项对应的角度，顺序与 strings.xml 中的 rotation_options 一致 */
    public static final int[] DEGREES = {NONE, 0, 90, 180, 270};

    private Config() {
    }

    public static int toScreenOrientation(int degrees) {
        switch (degrees) {
            case 0:
                return ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
            case 90:
                return ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
            case 180:
                return ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
            case 270:
                return ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
            default:
                return ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
        }
    }

    /** 把读到的任意值收敛到 {NONE, 0, 90, 180, 270} */
    public static int normalize(int value) {
        switch (value) {
            case 0:
            case 90:
            case 180:
            case 270:
                return value;
            default:
                return NONE;
        }
    }

    public static int indexOfDegrees(int degrees) {
        for (int i = 0; i < DEGREES.length; i++) {
            if (DEGREES[i] == degrees) {
                return i;
            }
        }
        return 0;
    }
}
