package com.monbureau.launcher;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Tiroir d'applications.
 *
 * Deux modes :
 *  - normal  : taper une application la lance
 *  - choix   : taper une application renvoie son nom de paquet
 *                     a l'ecran d'accueil, pour l'affecter a une case du dock
 */
public class AppDrawerActivity extends Activity {

    public static final String EXTRA_MODE_CHOIX = "mode_choix";
    public static final String EXTRA_INDEX_CASE = "index_case";
    public static final String RESULTAT_PACKAGE = "package_choisi";

    private final List<AppInfo> toutes = new ArrayList<>();
    private final List<AppInfo> affichees = new ArrayList<>();
    private AppAdapter adapter;

    private boolean modeChoix;
    private int indexCase;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_drawer);

        modeChoix = getIntent().getBooleanExtra(EXTRA_MODE_CHOIX, false);
        indexCase = getIntent().getIntExtra(EXTRA_INDEX_CASE, -1);

        TextView titre = findViewById(R.id.titre);
        titre.setText(modeChoix
                ? "Choisis l'application pour cette case"
                : "Applications");

        chargerApplications();
        affichees.addAll(toutes);

        GridView grille = findViewById(R.id.grid_apps);
        adapter = new AppAdapter(this, affichees);
        grille.setAdapter(adapter);

        grille.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                AppInfo app = affichees.get(position);
                if (modeChoix) {
                    Intent resultat = new Intent();
                    resultat.putExtra(RESULTAT_PACKAGE, app.packageName);
                    resultat.putExtra(EXTRA_INDEX_CASE, indexCase);
                    setResult(RESULT_OK, resultat);
                    finish();
                } else {
                    lancer(app);
                }
            }
        });

        EditText recherche = findViewById(R.id.recherche);
        recherche.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                filtrer(s.toString());
            }
            @Override public void afterTextChanged(Editable s) { }
        });
    }

    private void filtrer(String texte) {
        String q = texte.trim().toLowerCase(Locale.getDefault());
        affichees.clear();
        if (q.isEmpty()) {
            affichees.addAll(toutes);
        } else {
            for (AppInfo a : toutes) {
                if (a.label.toLowerCase(Locale.getDefault()).contains(q)) {
                    affichees.add(a);
                }
            }
        }
        adapter.notifyDataSetChanged();
    }

    private void lancer(AppInfo app) {
        try {
            Intent lancement = getPackageManager().getLaunchIntentForPackage(app.packageName);
            if (lancement != null) {
                startActivity(lancement);
            } else {
                Toast.makeText(this, "Cette application ne peut pas etre lancee",
                        Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Impossible d'ouvrir " + app.label, Toast.LENGTH_SHORT).show();
        }
    }

    private void chargerApplications() {
        try {
            PackageManager pm = getPackageManager();

            Intent principal = new Intent(Intent.ACTION_MAIN, null);
            principal.addCategory(Intent.CATEGORY_LAUNCHER);

            List<ResolveInfo> resultats = pm.queryIntentActivities(principal, 0);
            String monPackage = getPackageName();

            for (ResolveInfo info : resultats) {
                ApplicationInfo appInfo = info.activityInfo.applicationInfo;
                String pkg = appInfo.packageName;
                if (pkg.equals(monPackage)) continue;

                toutes.add(new AppInfo(info.loadLabel(pm).toString(), pkg, info.loadIcon(pm)));
            }

            Collections.sort(toutes, new Comparator<AppInfo>() {
                @Override
                public int compare(AppInfo a, AppInfo b) {
                    return a.label.compareToIgnoreCase(b.label);
                }
            });
        } catch (Exception e) {
            Toast.makeText(this, "Erreur au chargement des applications", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onBackPressed() {
        finish();
    }
}
