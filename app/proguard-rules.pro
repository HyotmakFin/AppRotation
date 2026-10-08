# Xposed 入口类必须保留，混淆会导致框架找不到入口
-keep class com.rotate.xposed.RotationModule { *; }
-keep class de.robv.android.xposed.** { *; }
