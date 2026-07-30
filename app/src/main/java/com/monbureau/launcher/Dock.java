package com.monbureau.launcher;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.MediaStore;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.List;

/**
 * Construit la liste des raccourcis du dock en resolvant les applications
 * PAR DEFAUT du systeme, plutot qu'en codant en dur des noms de paquets.
 * Ainsi le dock fonctionne quelle que soit l'application telephone / SMS
 * installee sur l'appareil.
 */
public class Dock {

    public static class Raccourci {
        public final String label;
        public final Intent intent;
        public final Drawable icone;      // icone de l'app resolue, ou null
        public final int iconeSecours;    // dessin vectoriel de repli

        Raccourci(String label, Intent intent, Drawable icone, int iconeSecours) {
            this.label = label;
            this.intent = intent;
            this.icone = icone;
            this.iconeSecours = iconeSecours;
        }
    }

    public static List<Raccourci> construire(Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        List<Raccourci> liste = new ArrayList<>();

        // 1. Telephone
        Intent tel = new Intent(Intent.ACTION_DIAL);
        ajouter(liste, pm, "Telephone", tel, R.drawable.ic_phone);

        // 2. Messages (SMS)
        Intent sms = new Intent(Intent.ACTION_MAIN);
        sms.addCategory(Intent.CATEGORY_APP_MESSAGING);
        ajouter(liste, pm, "Messages", sms, R.drawable.ic_message);

        // 3. Contacts
        Intent contacts = new Intent(Intent.ACTION_MAIN);
        contacts.addCategory(Intent.CATEGORY_APP_CONTACTS);
        ajouter(liste, pm, "Contacts", contacts, R.drawable.ic_contacts);

        // 4. Appareil photo
        Intent photo = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
        ajouter(liste, pm, "Photo", photo, R.drawable.ic_camera);

        // 5. Navigateur
        Intent nav = new Intent(Intent.ACTION_MAIN);
        nav.addCategory(Intent.CATEGORY_APP_BROWSER);
        ajouter(liste, pm, "Navigateur", nav, R.drawable.ic_browser);

        // 6. Parametres (toujours present, pas besoin de resolution)
        liste.add(new Raccourci("Reglages",
                new Intent(Settings.ACTION_SETTINGS), null, R.drawable.ic_settings));

        return liste;
    }

    /**
     * Ajoute le raccourci uniquement si le systeme sait le gerer,
     * et recupere au passage l'icone de l'application par defaut.
     */
    private static void ajouter(List<Raccourci> liste, PackageManager pm,
                                String label, Intent intent, int iconeSecours) {
        Drawable icone = null;
        try {
            ResolveInfo info = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
            if (info == null) {
                return; // aucune app pour gerer ca : on n'affiche pas le raccourci
            }
            // On evite d'afficher l'icone du selecteur systeme
            if (!"android".equals(info.activityInfo.packageName)) {
                icone = info.loadIcon(pm);
            }
        } catch (Exception e) {
            // On garde le raccourci avec son icone de repli
        }
        liste.add(new Raccourci(label, intent, icone, iconeSecours));
    }
}
