package com.rotate.xposed;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.os.Bundle;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Hook 入口。对作用域内的每个应用替换 Activity 的方向。
 *
 * 三处 Hook：
 *
 * 1. {@code Activity.setRequestedOrientation(int)} —— 把应用设置的方向替换为目标值。
 *    这一处是防止重建循环的关键：若只在下游纠正方向，应用在 onCreate 里改回原值后
 *    会再次触发方向变化，形成反复重建。
 *
 * 2. {@code Activity.onCreate(Bundle)} —— 同时写入 ActivityInfo.screenOrientation，
 *    让系统在创建窗口时就采用目标方向。
 *
 * 3. {@code Activity.onResume()} —— 兜底，处理应用在 onResume 之后才设置方向的情况。
 */
public class RotationModule implements IXposedHookLoadPackage {

    private static final String TAG = "AppRotation: ";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (lpparam == null || lpparam.packageName == null) {
            return;
        }

        final String packageName = lpparam.packageName;
        if ("android".equals(packageName) || BuildConfig.APPLICATION_ID.equals(packageName)) {
            return;
        }

        try {
            XposedHelpers.findAndHookMethod(Activity.class, "setRequestedOrientation",
                    int.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            Activity activity = (Activity) param.thisObject;
                            if (activity == null) {
                                return;
                            }
                            int target = targetOrientation(activity);
                            if (target != Config.NONE) {
                                param.args[0] = target;
                            }
                        }
                    });

            XposedHelpers.findAndHookMethod(Activity.class, "onCreate",
                    Bundle.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            Activity activity = (Activity) param.thisObject;
                            if (activity != null) {
                                applyOrientation(activity);
                            }
                        }
                    });

            XposedHelpers.findAndHookMethod(Activity.class, "onResume",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            Activity activity = (Activity) param.thisObject;
                            if (activity != null) {
                                applyOrientation(activity);
                            }
                        }
                    });

        } catch (Throwable t) {
            XposedBridge.log(TAG + "failed to hook " + packageName + ": " + t);
        }
    }

    /** 返回该 Activity 所属应用的目标方向（ActivityInfo 常量），未配置时返回 {@link Config#NONE} */
    private static int targetOrientation(Activity activity) {
        try {
            Context context = activity.getApplicationContext();
            if (context == null) {
                context = activity;
            }
            int degrees = RemoteConfig.orientationFor(context, activity.getPackageName());
            return degrees == Config.NONE ? Config.NONE : Config.toScreenOrientation(degrees);
        } catch (Throwable t) {
            XposedBridge.log(TAG + "failed to resolve orientation: " + t);
            return Config.NONE;
        }
    }

    private static void applyOrientation(Activity activity) {
        try {
            int target = targetOrientation(activity);
            if (target == Config.NONE) {
                return;
            }

            // Android 12 起，targetSdk 31+ 的应用在最小宽度 >= 600dp 的屏幕上会被忽略
            // setRequestedOrientation，改写该字段以绕过此限制。
            try {
                ActivityInfo info = (ActivityInfo) XposedHelpers
                        .getObjectField(activity, "mActivityInfo");
                if (info != null && info.screenOrientation != target) {
                    info.screenOrientation = target;
                }
            } catch (Throwable ignored) {
                // 部分 ROM 中该字段不可用，忽略即可，setRequestedOrientation 仍会生效
            }

            // 与当前值相同时不会触发重建，因此不会循环
            if (activity.getRequestedOrientation() != target) {
                XposedBridge.log(TAG + "apply " + activity.getClass().getName() + " -> " + target);
                activity.setRequestedOrientation(target);
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "failed to apply orientation: " + t);
        }
    }
}
