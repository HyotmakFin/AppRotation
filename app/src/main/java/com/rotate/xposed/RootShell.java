package com.rotate.xposed;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * 执行 root 命令。
 *
 * su 的位置因 root 方案和 ROM 而异，因此逐个尝试常见路径，任一成功即返回。
 */
final class RootShell {

    private static final String[] SU_PATHS = {
            "su",
            "/system/bin/su",
            "/system/xbin/su",
            "/debug_ramdisk/su",
            "/sbin/su",
            "/data/adb/ksu/bin/su",
            "/data/adb/magisk/su",
    };

    private RootShell() {
    }

    /** @return 命令输出；未获得 root 时返回 null */
    static String run(String... commands) {
        StringBuilder script = new StringBuilder();
        for (String command : commands) {
            script.append(command).append("; ");
        }

        for (String su : SU_PATHS) {
            Process process = null;
            try {
                process = new ProcessBuilder(su, "-c", script.toString())
                        .redirectErrorStream(true)
                        .start();

                StringBuilder output = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append('\n');
                    }
                }

                if (process.waitFor() == 0) {
                    return output.toString().trim();
                }
            } catch (Throwable ignored) {
                // 该路径不可用，继续尝试下一个
            } finally {
                if (process != null) {
                    process.destroy();
                }
            }
        }
        return null;
    }
}
