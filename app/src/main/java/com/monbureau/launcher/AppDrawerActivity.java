package com.monbureau.launcher;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.widget.GridView;
import android.app.Activity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class AppDrawerActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_drawer);

        GridView gridView = findViewById(R.id.grid_apps);
        List<AppInfo> apps = loadInstalledApps();

        AppAdapter adapter = new AppAdapter(this, apps);
        gridView.setAdapter(adapter);

        gridView.setOnItemClickListener((parent, view, position, id) -> {
            AppInfo app = apps.get(position);
            Intent launchIntent = getPackageManager().getLaunchIntentForPackage(app.packageName);
            if (launchIntent != null) {
                startActivity(launchIntent);
            }
        });
    }

    private List<AppInfo> loadInstalledApps() {
        PackageManager pm = getPackageManager();

        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> resolveInfos = pm.queryIntentActivities(mainIntent, 0);
        List<AppInfo> apps = new ArrayList<>();

        String ownPackage = getPackageName();

        for (ResolveInfo info : resolveInfos) {
            ApplicationInfo appInfo = info.activityInfo.applicationInfo;
            String packageName = appInfo.packageName;

            if (packageName.equals(ownPackage)) {
                // On n'affiche pas Mon Bureau lui-même dans son propre tiroir
                continue;
            }

            String label = info.loadLabel(pm).toString();
            apps.add(new AppInfo(label, packageName, info.loadIcon(pm)));
        }

        Collections.sort(apps, new Comparator<AppInfo>() {
            @Override
            public int compare(AppInfo a, AppInfo b) {
                return a.label.compareToIgnoreCase(b.label);
            }
        });

        return apps;
    }

    @Override
    public void onBackPressed() {
        // Retour = fermer le tiroir et revenir à Mon Bureau
        finish();
    }
}
