package com.rotate.xposed;

import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;

/**
 * 配置存放于 Settings.System：键为 app_rotation_enabled 和 app_rotation_&lt;包名&gt;。
 * 详见 RemoteConfig 的说明。
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "AppRotation";

    private final List<AppEntry> allApps = new ArrayList<>();
    private final List<AppEntry> shownApps = new ArrayList<>();

    private AppAdapter adapter;
    private RecyclerView listView;
    private View loading;
    private View emptyView;
    private TextInputEditText search;
    private MaterialSwitch globalSwitch;

    private final Handler main = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        listView = findViewById(R.id.list_apps);
        loading = findViewById(R.id.loading);
        emptyView = findViewById(R.id.empty);
        search = findViewById(R.id.search);
        globalSwitch = findViewById(R.id.switch_global);

        adapter = new AppAdapter(this, shownApps, this::showOrientationDialog);
        listView.setLayoutManager(new LinearLayoutManager(this));
        Drawable dividerDrawable = ContextCompat.getDrawable(this, R.drawable.divider_list);
        if (dividerDrawable != null) {
            DividerItemDecoration divider =
                    new DividerItemDecoration(this, DividerItemDecoration.VERTICAL);
            divider.setDrawable(dividerDrawable);
            listView.addItemDecoration(divider);
        }
        listView.setAdapter(adapter);

        globalSwitch.setChecked(isGlobalEnabled());
        globalSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> setGlobalEnabled(isChecked));

        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                filter(s == null ? "" : s.toString());
            }
        });

        loadApps();
        checkRootAccess();
    }

    @Override
    protected void onResume() {
        super.onResume();
        globalSwitch.setChecked(isGlobalEnabled());
    }

    // ------------------------------------------------------------ root 权限

    /**
     * 普通应用无法向 Settings.System 写入自定义键（系统只允许白名单内的设置项），
     * 因此写入需要借助 root 执行 settings 命令。LSPosed 本身也要求 root。
     */
    private boolean probeRoot() {
        return RootShell.run(
                "settings put system " + Config.SETTING_PROBE + " 1",
                "settings delete system " + Config.SETTING_PROBE) != null;
    }

    private void checkRootAccess() {
        new Thread(() -> {
            final boolean ok = probeRoot();
            main.post(() -> {
                if (!ok) {
                    showRootSnackbar();
                }
            });
        }, "root-probe").start();
    }

    private void showRootSnackbar() {
        Snackbar.make(listView, R.string.perm_message, Snackbar.LENGTH_INDEFINITE)
                .setAction(R.string.perm_grant, v -> requestRoot())
                .show();
    }

    private void requestRoot() {
        Snackbar.make(listView, R.string.root_running, Snackbar.LENGTH_SHORT).show();
        new Thread(() -> {
            final boolean ok = probeRoot();
            main.post(() -> {
                Snackbar.make(listView, ok ? R.string.root_ok : R.string.root_failed,
                        Snackbar.LENGTH_LONG).show();
                if (ok) {
                    refreshList();
                }
            });
        }, "root-request").start();
    }

    // ------------------------------------------------------------ 读写配置

    private boolean isGlobalEnabled() {
        try {
            return !"0".equals(Settings.System.getString(getContentResolver(),
                    Config.SETTING_ENABLED));
        } catch (Throwable t) {
            return true;
        }
    }

    private void setGlobalEnabled(boolean enabled) {
        writeSetting(Config.SETTING_ENABLED, enabled ? "1" : "0");
    }

    private int storedOrientation(String packageName) {
        try {
            String raw = Settings.System.getString(getContentResolver(),
                    Config.SETTING_PREFIX + packageName);
            if (raw == null || raw.trim().isEmpty()) {
                return Config.NONE;
            }
            return Config.normalize(Integer.parseInt(raw.trim()));
        } catch (Throwable t) {
            return Config.NONE;
        }
    }

    private void writeSetting(String key, String value) {
        ContentResolver resolver = getContentResolver();
        try {
            Settings.System.putString(resolver, key, value);
            return;
        } catch (Throwable t) {
            Log.w(TAG, "direct write rejected, falling back to root", t);
        }
        final String command = (value == null || value.isEmpty())
                ? "settings delete system " + key
                : "settings put system " + key + " " + value;
        new Thread(() -> {
            if (RootShell.run(command) == null) {
                main.post(this::showRootSnackbar);
            }
        }, "root-write").start();
    }

    private void refreshList() {
        globalSwitch.setChecked(isGlobalEnabled());
        for (AppEntry entry : allApps) {
            entry.orientation = storedOrientation(entry.packageName);
        }
        adapter.notifyDataSetChanged();
    }

    // ------------------------------------------------------------ 应用列表

    private void loadApps() {
        loading.setVisibility(View.VISIBLE);
        new Thread(() -> {
            final List<AppEntry> result = new ArrayList<>();
            try {
                PackageManager pm = getPackageManager();
                Intent launcherIntent = new Intent(Intent.ACTION_MAIN);
                launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);

                for (ResolveInfo info : pm.queryIntentActivities(launcherIntent, 0)) {
                    if (info.activityInfo == null) {
                        continue;
                    }
                    String pkg = info.activityInfo.packageName;
                    if (pkg == null || pkg.equals(getPackageName()) || contains(result, pkg)) {
                        continue;
                    }
                    result.add(new AppEntry(pkg,
                            String.valueOf(info.loadLabel(pm)), info.loadIcon(pm)));
                }
            } catch (Throwable t) {
                Log.w(TAG, "failed to enumerate launcher activities", t);
            }

            for (AppEntry entry : result) {
                entry.orientation = storedOrientation(entry.packageName);
            }
            result.sort((a, b) -> a.label.compareToIgnoreCase(b.label));

            main.post(() -> {
                allApps.clear();
                allApps.addAll(result);
                loading.setVisibility(View.GONE);
                filter(search.getText() == null ? "" : search.getText().toString());
            });
        }, "app-list-loader").start();
    }

    private static boolean contains(List<AppEntry> list, String packageName) {
        for (AppEntry entry : list) {
            if (entry.packageName.equals(packageName)) {
                return true;
            }
        }
        return false;
    }

    private void filter(String keyword) {
        String query = keyword.trim().toLowerCase();
        shownApps.clear();
        for (AppEntry entry : allApps) {
            if (query.isEmpty()
                    || entry.label.toLowerCase().contains(query)
                    || entry.packageName.toLowerCase().contains(query)) {
                shownApps.add(entry);
            }
        }
        adapter.notifyDataSetChanged();
        emptyView.setVisibility(
                (!allApps.isEmpty() && shownApps.isEmpty()) ? View.VISIBLE : View.GONE);
    }

    // ------------------------------------------------------------ 选择角度

    private void showOrientationDialog(AppEntry entry) {
        final String[] options = getResources().getStringArray(R.array.rotation_options);

        new MaterialAlertDialogBuilder(this)
                .setTitle(entry.label)
                .setSingleChoiceItems(options, Config.indexOfDegrees(entry.orientation),
                        (dialog, which) -> {
                            entry.orientation = Config.DEGREES[which];
                            writeSetting(Config.SETTING_PREFIX + entry.packageName,
                                    entry.orientation == Config.NONE
                                            ? "" : String.valueOf(entry.orientation));
                            adapter.notifyDataSetChanged();
                            dialog.dismiss();

                            String tip = entry.orientation == Config.NONE
                                    ? getString(R.string.tip_follow_system)
                                    : getString(R.string.tip_applied, options[which])
                                            + "，" + getString(R.string.tip_restart_target);
                            Snackbar.make(listView, tip, Snackbar.LENGTH_SHORT).show();
                        })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
