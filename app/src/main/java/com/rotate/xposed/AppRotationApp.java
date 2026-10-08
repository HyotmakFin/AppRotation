package com.rotate.xposed;

import android.app.Application;

import com.google.android.material.color.DynamicColors;

public class AppRotationApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        // Android 12+ 跟随壁纸取色；低版本回退到 themes.xml 中定义的配色
        DynamicColors.applyToActivitiesIfAvailable(this);
    }
}
