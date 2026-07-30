package com.monbureau.launcher;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.provider.MediaStore;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.List;

/**
 * Gere les cases du dock.
 *
 * Chaque case a une application PAR DEFAUT resolue aupres du systeme
 * (l'appli telephone de l'appareil, son appli SMS, etc.), mais l'utilisateur
 * peut la remplacer par n'importe quelle application installee.
 * Ce choix est memorise dans les preferences et survit aux redemarrages
 * comme aux mises a jour de l'application.
 */
public class Dock {

    public static final int NB_CASES = 6;

    private static final String PREFS = "dock";
    private static final String CLE_CASE = "case_";

    /** Une case telle qu'affichee a l'ecran. */
    public static class Case {
        public final int index;
        public final String label;
        public final Intent intent;
        public final Drawable icone;       // icone de l'app resolue, sinon null
        public final int iconeSecours;     // dessin vectoriel de repli
        public final boolean personnalisee;

        Case(int index, String label, Intent intent, Drawable icone,
             int iconeSecours, boolean personnalisee) {
            this.index = index;
            this.label = label;
            this.intent = intent;
            this.icone = icone;
            this.iconeSecours = iconeSecours;
            this.personnalisee = personnalisee;
        }
    }

    // ------------------------------------------------------- Preferences

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Enregistre l'application choisie pour une case. */
    public static void definir(Context ctx, int index, String packageName) {
        prefs(ctx).edit().putString(CLE_CASE + index, packageName).apply();
    }

    /** Remet la case sur son application par defaut. */
    public static void reinitialiser(Context ctx, int index) {
        prefs(ctx).edit().remove(CLE_CASE + index).apply();
    }

    /** Remet toutes les cases par defaut. */
    public static void toutReinitialiser(Context ctx) {
        prefs(ctx).edit().clear().apply();
    }

    private static String personnalisation(Context ctx, int index) {
        return prefs(ctx).getString(CLE_CASE + index, null);
    }

    // ------------------------------------------------------- Construction

    public static List<Case> construire(Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        List<Case> cases = new ArrayList<>();

        for (int i = 0; i < NB_CASES; i++) {
            Case c = null;

            String perso = personnalisation(ctx, i);
            if (perso != null) {
                c = depuisPackage(ctx, pm, i, perso);
                if (c == null) {
                    // L'application a ete desinstallee : on repart sur le defaut
                    reinitialiser(ctx, i);
                }
            }
            if (c == null) {
                c = parDefaut(ctx, pm, i);
            }
            if (c != null) {
                cases.add(c);
            }
        }
        return cases;
    }

    /** Construit une case a partir d'une application choisie par l'utilisateur. */
    private static Case depuisPackage(Context ctx, PackageManager pm, int index, String pkg) {
        try {
            Intent lancement = pm.getLaunchIntentForPackage(pkg);
            if (lancement == null) return null;

            ResolveInfo info = pm.resolveActivity(lancement, 0);
            String label = (info != null) ? info.loadLabel(pm).toString() : pkg;
            Drawable icone = (info != null) ? info.loadIcon(pm) : null;

            return new Case(index, label, lancement, icone, R.drawable.ic_apps, true);
        } catch (Exception e) {
            return null;
        }
    }

    /** Construit la case avec l'application par defaut du systeme. */
    private static Case parDefaut(Context ctx, PackageManager pm, int index) {
        String label;
        Intent intent;
        int secours;

        switch (index) {
            case 0:
                label = "Telephone";
                intent = new Intent(Intent.ACTION_DIAL);
                secours = R.drawable.ic_phone;
                break;
            case 1:
                label = "Messages";
                intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING);
                secours = R.drawable.ic_message;
                break;
            case 2:
                label = "Contacts";
                intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CONTACTS);
                secours = R.drawable.ic_contacts;
                break;
            case 3:
                label = "Photo";
                intent = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
                secours = R.drawable.ic_camera;
                break;
            case 4:
                label = "Navigateur";
                intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_BROWSER);
                secours = R.drawable.ic_browser;
                break;
            case 5:
                // Les reglages sont toujours disponibles, pas de resolution necessaire
                return new Case(index, "Reglages", new Intent(Settings.ACTION_SETTINGS),
                        null, R.drawable.ic_settings, false);
            default:
                return null;
        }

        Drawable icone = null;
        try {
            ResolveInfo info = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
            if (info == null) {
                // Aucune application ne sait faire ca sur cet appareil :
                // la case reste utilisable, l'utilisateur pourra y mettre ce qu'il veut
                return new Case(index, label, null, null, secours, false);
            }
            if (!"android".equals(info.activityInfo.packageName)) {
                icone = info.loadIcon(pm);
            }
        } catch (Exception e) {
            // on garde l'icone de repli
        }
        return new Case(index, label, intent, icone, secours, false);
    }
}
