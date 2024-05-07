package com.android.settings.applications.specialaccess.sensor;

import android.annotation.Nullable;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageItemInfo;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.ArraySet;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.TextView;
import android.widget.Toast;

import androidx.preference.Preference;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreference;

import com.android.settings.R;
import com.android.settings.core.instrumentation.InstrumentedDialogFragment;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.settings.SettingsPreferenceFragment;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SensorAccess extends SettingsPreferenceFragment {

    private final SettingObserver mObserver = new SettingObserver();

    static final String TAG = "SensorAccess";

    private Context mContext;
    private PackageManager mPackageManager;
    private AppOpsManager mAppOpsManager;
    private TextView mEmpty;

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.VIEW_UNKNOWN;
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        mContext = getActivity();
        mPackageManager = mContext.getPackageManager();
        mAppOpsManager = (AppOpsManager) mContext.getSystemService(Context.APP_OPS_SERVICE);
        setPreferenceScreen(getPreferenceManager().createPreferenceScreen(mContext));
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mEmpty = new TextView(getContext());
        mEmpty.setGravity(Gravity.CENTER);
        mEmpty.setText(R.string.sensor_access_title_empty_text);
        TypedValue value = new TypedValue();
        getContext().getTheme().resolveAttribute(android.R.attr.textAppearanceMedium, value, true);
        mEmpty.setTextAppearance(value.resourceId);
        ((ViewGroup) view.findViewById(android.R.id.list_container)).addView(mEmpty,
                new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        setEmptyView(mEmpty);
        reloadList();
    }

    @Override
    public void onResume() {
        super.onResume();
        getActivity().getActionBar().setTitle(R.string.sensor_access_title);
        reloadList();
    }

    private void reloadList() {
        final PreferenceScreen screen = getPreferenceScreen();
        screen.removeAll();

        final ArrayList<ApplicationInfo> apps = new ArrayList<>();
        final List<ApplicationInfo> installed = mPackageManager.getInstalledApplications(0);
        if (installed != null) {
            for (ApplicationInfo app : installed) {
                // Skip system apps
                if (isUserApp(app.packageName)) {
                    // Only apps effectively having the Op OTHER_SENSORS
                    if (mAppOpsManager.getOpsForPackage(getPackageUid(app.packageName),
                            app.packageName, new int[]{AppOpsManager.OP_OTHER_SENSORS}) != null)
                        apps.add(app);
                }
            }
        }
        Collections.sort(apps, new PackageItemInfo.DisplayNameComparator(mPackageManager));
        for (ApplicationInfo app : apps) {
            final String pkg = app.packageName;
            final CharSequence label = app.loadLabel(mPackageManager);
            final SwitchPreference pref = new SwitchPreference(getPrefContext());
            pref.setPersistent(false);
            pref.setIcon(app.loadIcon(mPackageManager));
            pref.setTitle(label);
            updateState(pref, pkg);
            pref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    boolean switchOn = (Boolean) newValue;
                    mAppOpsManager.setMode(AppOpsManager.OP_OTHER_SENSORS, getPackageUid(pkg), pkg,
                            switchOn ? AppOpsManager.MODE_ALLOWED : AppOpsManager.MODE_IGNORED);
                    pref.setChecked(switchOn);
                    return false;
                }
            });
            screen.addPreference(pref);
        }
    }

    public void updateState(SwitchPreference preference, String pkg) {
        final int mode = mAppOpsManager
                .checkOpNoThrow(AppOpsManager.OP_OTHER_SENSORS, getPackageUid(pkg), pkg);
        if (mode == AppOpsManager.MODE_ERRORED) {
            preference.setChecked(false);
        } else {
            final boolean checked = mode != AppOpsManager.MODE_IGNORED;
            preference.setChecked(checked);
        }
    }

    private boolean isUserApp(String pkg) {
        ApplicationInfo appInfo;
        try {
            appInfo = mPackageManager.getApplicationInfo(pkg,
                        PackageManager.GET_DISABLED_COMPONENTS
                        | PackageManager.GET_UNINSTALLED_PACKAGES);
        } catch (PackageManager.NameNotFoundException e) {
                Log.w(TAG, "Unable to find info for package " + pkg);
                return false;
        }
        return ((appInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0);
    }

    private int getPackageUid(String pkg) {
        int uid;
        try {
            uid = mPackageManager.getPackageUid(pkg, 0);
        } catch (PackageManager.NameNotFoundException e) {
            // We shouldn't hit this, ever. What can we even do after this?
            uid = -1;
        }
        return uid;
    }

    private final class SettingObserver extends ContentObserver {
        public SettingObserver() {
            super(new Handler(Looper.getMainLooper()));
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            reloadList();
        }
    }
}
