package com.monbureau.launcher;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class AppDrawerActivity extends Activity {

    private List<AppInfo> apps;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_drawer);

        GridView gridView = findViewById(R.id.grid_apps);
        apps = loadInstalledApps();

        gridView.setAdapter(new AppAdapter(this, apps));

        gridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                AppInfo app = apps.get(position);
                try {
                    Intent launchIntent = getPackageManager().getLaunchIntentForPackage(app.packageName);
                    if (launchIntent != null) {
                        startActivity(launchIntent);
                    } else {
                        Toast.makeText(AppDrawerActivity.this,
                                "Cette application ne peut pas etre lancee", Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    Toast.makeText(AppDrawerActivity.this,
                            "Impossible d'ouvrir " + app.label, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private List<AppInfo> loadInstalledApps() {
        List<AppInfo> result = new ArrayList<>();
        try {
            PackageManager pm = getPackageManager();

            Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
            mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);

            List<ResolveInfo> resolveInfos = pm.queryIntentActivities(mainIntent, 0);
            String ownPackage = getPackageName();

            for (ResolveInfo info : resolveInfos) {
                ApplicationInfo appInfo = info.activityInfo.applicationInfo;
                String packageName = appInfo.packageName;

                // On n'affiche pas Mon Bureau dans son propre tiroir
                if (packageName.equals(ownPackage)) continue;

                result.add(new AppInfo(
                        info.loadLabel(pm).toString(),
                        packageName,
                        info.loadIcon(pm)));
            }

            Collections.sort(result, new Comparator<AppInfo>() {
                @Override
                public int compare(AppInfo a, AppInfo b) {
                    return a.label.compareToIgnoreCase(b.label);
                }
            });
        } catch (Exception e) {
            Toast.makeText(this, "Erreur au chargement des applications", Toast.LENGTH_LONG).show();
        }
        return result;
    }

    @Override
    public void onBackPressed() {
        finish();
    }
}
