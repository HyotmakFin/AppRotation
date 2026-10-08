package com.rotate.xposed;

import android.content.ContentResolver;
import android.content.Context;
import android.os.SystemClock;
import android.provider.Settings;

import java.util.HashMap;
import java.util.Map;

import de.robv.android.xposed.XposedBridge;

/**
 * 在被 Hook 的进程中读取配置。
 *
 * 配置走 Settings.System，原因：
 *
 * 1. ContentProvider 不可用。Android 11 起包可见性会拦截跨应用访问，
 *    除非调用方在自己的 manifest 里用 &lt;queries&gt; 声明该 authority，
 *    而调用方是第三方应用，无法修改。
 *
 * 2. XSharedPreferences 不可用。LSPosed 会把模块的 SharedPreferences 重定位到
 *    /data/misc/apexdata/&lt;uuid&gt;/prefs/&lt;pkg&gt;/config.xml，该文件为 0660，
 *    其他应用没有读权限；同时 LSPosed 内部的文件服务返回的文件大小为 0，
 *    最终得到一个空 Map 并静默返回默认值。
 *
 * 3. Settings.System 的读取不需要任何权限，也不受包可见性或 SELinux 限制。
 *    写入需要 root（系统只允许白名单内的设置项被普通应用写入），
 *    见 MainActivity.writeSetting。
 *
 * 读取结果按包名缓存，TTL 1.5 秒。
 */
final class RemoteConfig {

    private static final long TTL_MS = 1500L;

    private static final Map<String, Integer> CACHE = new HashMap<>();
    private static final Map<String, Long> STAMP = new HashMap<>();

    private RemoteConfig() {
    }

    /** @return {Config.NONE, 0, 90, 180, 270} */
    static int orientationFor(Context context, String packageName) {
        if (context == null || packageName == null || packageName.isEmpty()) {
            return Config.NONE;
        }

        long now = SystemClock.elapsedRealtime();
        synchronized (CACHE) {
            Long stamp = STAMP.get(packageName);
            if (stamp != null && now - stamp < TTL_MS) {
                Integer cached = CACHE.get(packageName);
                if (cached != null) {
                    return cached;
                }
            }
        }

        int value = read(context, packageName);

        synchronized (CACHE) {
            CACHE.put(packageName, value);
            STAMP.put(packageName, now);
        }
        return value;
    }

    private static int read(Context context, String packageName) {
        try {
            ContentResolver resolver = context.getContentResolver();

            if ("0".equals(Settings.System.getString(resolver, Config.SETTING_ENABLED))) {
                return Config.NONE;
            }

            String raw = Settings.System.getString(resolver,
                    Config.SETTING_PREFIX + packageName);
            if (raw == null || raw.trim().isEmpty()) {
                return Config.NONE;
            }
            return Config.normalize(Integer.parseInt(raw.trim()));
        } catch (Throwable t) {
            XposedBridge.log("AppRotation: failed to read settings for " + packageName + ": " + t);
            return Config.NONE;
        }
    }
}
