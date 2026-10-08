package com.rotate.xposed;

import android.graphics.drawable.Drawable;

/** 列表里的一行：一个可启动的第三方应用 */
public class AppEntry {

    public final String packageName;
    public final String label;
    public final Drawable icon;

    /** {Config.NONE, 0, 90, 180, 270} */
    public int orientation = Config.NONE;

    public AppEntry(String packageName, String label, Drawable icon) {
        this.packageName = packageName;
        this.label = label;
        this.icon = icon;
    }
}
