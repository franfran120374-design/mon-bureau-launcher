package com.monbureau.launcher;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.widget.GridView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class AppDrawerActivity extends Activity {

    // Si present et vrai dans l'intent de lancement : au lieu d'ouvrir
    // l'appli choisie, on renvoie son nom de paquet a l'ecran appelant
    // (utilise par DockManager pour assigner un raccourci).
    public static final String EXTRA_MODE_CHOIX = "mode_choix";
    public static final String EXTRA_PACKAGE_CHOISI = "package_choisi";
    public static final String EXTRA_LABEL_CHOISI = "label_choisi";

    private boolean modeChoix = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_drawer);

        modeChoix = getIntent().getBooleanExtra(EXTRA_MODE_CHOIX, false);
        if (modeChoix) {
            Toast.makeText(this, "Choisis une application pour ce raccourci", Toast.LENGTH_SHORT).show();
        }

        GridView gridView = findViewById(R.id.grid_apps);
        List<AppInfo> apps = loadInstalledApps();

        AppAdapter adapter = new AppAdapter(this, apps);
        gridView.setAdapter(adapter);

        gridView.setOnItemClickListener((parent, view, position, id) -> {
            AppInfo app = apps.get(position);
            if (modeChoix) {
                Intent resultat = new Intent();
                resultat.putExtra(EXTRA_PACKAGE_CHOISI, app.packageName);
                resultat.putExtra(EXTRA_LABEL_CHOISI, app.label);
                setResult(Activity.RESULT_OK, resultat);
                finish();
            } else {
                Intent launchIntent = getPackageManager().getLaunchIntentForPackage(app.packageName);
                if (launchIntent != null) {
                    startActivity(launchIntent);
                }
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
        if (modeChoix) {
            setResult(Activity.RESULT_CANCELED);
        }
        // Retour = fermer le tiroir et revenir à Mon Bureau
        finish();
    }
}
