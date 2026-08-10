package com.monbureau.launcher;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.webkit.JavascriptInterface;

import org.json.JSONArray;

/**
 * PontVelo
 * ========
 * Rend le service de notifications utilisable depuis la page web.
 *
 * Une fois branche par MainActivity, le JavaScript de Mon Bureau peut appeler
 * ces methodes sous le nom window.MBVelo. Exemple cote site :
 *
 *     if (window.MBVelo && MBVelo.autorise()) { ... }
 *     const evts = JSON.parse(MBVelo.evenements());
 *
 * Toutes les methodes portent l'annotation @JavascriptInterface : sans elle,
 * Android les rend invisibles au JavaScript (mesure de securite depuis
 * Android 4.2). Aucune ne prend de decision toute seule, chacune se contente
 * de lire ou d'ecrire une preference locale.
 */
public class PontVelo {

    private final Activity activite;

    public PontVelo(Activity activite) {
        this.activite = activite;
    }

    private SharedPreferences prefs() {
        return activite.getSharedPreferences(VeloNotificationService.PREFS, Context.MODE_PRIVATE);
    }

    /** Permet au site de savoir qu'il tourne bien dans le launcher. */
    @JavascriptInterface
    public String version() {
        return "1.0";
    }

    /**
     * L'acces aux notifications a-t-il ete accorde ?
     * Android stocke la liste des services autorises dans un reglage systeme.
     */
    @JavascriptInterface
    public boolean autorise() {
        try {
            String actifs = Settings.Secure.getString(
                    activite.getContentResolver(), "enabled_notification_listeners");
            return actifs != null && actifs.contains(activite.getPackageName());
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Ouvre l'ecran Android d'autorisation. C'est le seul endroit ou la
     * permission peut etre accordee : aucune application ne peut se
     * l'attribuer elle-meme.
     */
    @JavascriptInterface
    public void ouvrirReglages() {
        try {
            Intent i = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activite.startActivity(i);
        } catch (Throwable t) {
            try {
                Intent i = new Intent(Settings.ACTION_SETTINGS);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                activite.startActivity(i);
            } catch (Throwable ignored) {
            }
        }
    }

    /** Journal des dernieres notifications observees (mode apprentissage). */
    @JavascriptInterface
    public String journal() {
        return prefs().getString(VeloNotificationService.CLE_JOURNAL, "[]");
    }

    @JavascriptInterface
    public void viderJournal() {
        prefs().edit().putString(VeloNotificationService.CLE_JOURNAL, "[]").apply();
    }

    /** Application actuellement surveillee ("" tant qu'aucune n'est designee). */
    @JavascriptInterface
    public String paquet() {
        return prefs().getString(VeloNotificationService.CLE_PACKAGE,
                VeloNotificationService.PACKAGE_DEFAUT);
    }

    @JavascriptInterface
    public void definirPaquet(String p) {
        prefs().edit().putString(VeloNotificationService.CLE_PACKAGE, p == null ? "" : p).apply();
    }

    /** Evenements DEBUT / FIN en attente, au format JSON. */
    @JavascriptInterface
    public String evenements() {
        return prefs().getString(VeloNotificationService.CLE_EVENTS, "[]");
    }

    /**
     * A appeler apres traitement, sinon les memes evenements seraient rejoues
     * indefiniment a chaque passage.
     */
    @JavascriptInterface
    public void viderEvenements() {
        prefs().edit().putString(VeloNotificationService.CLE_EVENTS, "[]").apply();
    }

    /** Mots declencheurs, separes par des barres verticales. */
    @JavascriptInterface
    public String motsDebut() {
        return prefs().getString(VeloNotificationService.CLE_MOTS_DEBUT,
                VeloNotificationService.MOTS_DEBUT_DEFAUT);
    }

    @JavascriptInterface
    public String motsFin() {
        return prefs().getString(VeloNotificationService.CLE_MOTS_FIN,
                VeloNotificationService.MOTS_FIN_DEFAUT);
    }

    @JavascriptInterface
    public void definirMots(String debut, String fin) {
        SharedPreferences.Editor e = prefs().edit();
        if (debut != null && !debut.trim().isEmpty()) {
            e.putString(VeloNotificationService.CLE_MOTS_DEBUT, debut.trim());
        }
        if (fin != null && !fin.trim().isEmpty()) {
            e.putString(VeloNotificationService.CLE_MOTS_FIN, fin.trim());
        }
        e.apply();
    }

    @TestOnlyHelper
    @JavascriptInterface
    public void reinitialiserMots() {
        prefs().edit()
                .putString(VeloNotificationService.CLE_MOTS_DEBUT, VeloNotificationService.MOTS_DEBUT_DEFAUT)
                .putString(VeloNotificationService.CLE_MOTS_FIN, VeloNotificationService.MOTS_FIN_DEFAUT)
                .apply();
    }

    /**
     * Fabrique un faux evenement, uniquement pour verifier que la chaine
     * complete fonctionne sans devoir aller emprunter un velo.
     */
    @JavascriptInterface
    public void simuler(String type) {
        try {
            JSONArray a = new JSONArray(evenements());
            org.json.JSONObject o = new org.json.JSONObject();
            o.put("type", "FIN".equals(type) ? "FIN" : "DEBUT");
            o.put("titre", "Test Mon Bureau");
            o.put("texte", "Evenement simule depuis les reglages");
            o.put("t", System.currentTimeMillis());
            a.put(o);
            prefs().edit().putString(VeloNotificationService.CLE_EVENTS, a.toString()).apply();
        } catch (Throwable ignored) {
        }
    }

    /** Annotation decorative : signale une methode d'aide au diagnostic. */
    private @interface TestOnlyHelper {
    }
}
