package com.monbureau.launcher;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.provider.Settings;
import android.webkit.JavascriptInterface;

import org.json.JSONObject;

import java.util.List;

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

    // =====================================================================
    // POSITION NATIVE
    // ---------------------------------------------------------------------
    // La geolocalisation d'une page web dans un WebView passe par plusieurs
    // couches (page -> WebView -> systeme) et echoue souvent sans expliquer
    // pourquoi. On demande donc la position DIRECTEMENT au systeme Android,
    // ce qui est nettement plus fiable, puis on la transmet a la page.
    //
    // Deux mecanismes complementaires :
    //   - positionConnue()   : reponse immediate, derniere position en memoire
    //   - demanderPosition() : demande une mesure fraiche en tache de fond,
    //                          que positionConnue() renverra quelques secondes
    //                          plus tard
    // =====================================================================

    private Location derniereMesure = null;

    private boolean permissionAccordee() {
        return activite.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED
            || activite.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED;
    }

    /** La localisation du telephone est-elle allumee ? */
    @JavascriptInterface
    public boolean positionActivee() {
        try {
            LocationManager lm = (LocationManager) activite.getSystemService(Context.LOCATION_SERVICE);
            if (lm == null) return false;
            return lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        } catch (Throwable t) {
            return false;
        }
    }

    /** L'application a-t-elle le droit d'acceder a la position ? */
    @JavascriptInterface
    public boolean positionAutorisee() {
        try { return permissionAccordee(); } catch (Throwable t) { return false; }
    }

    /** Redemande la permission a l'utilisateur (fenetre systeme). */
    @JavascriptInterface
    public void demanderPermission() {
        try {
            activite.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    activite.requestPermissions(new String[]{
                            android.Manifest.permission.ACCESS_FINE_LOCATION,
                            android.Manifest.permission.ACCESS_COARSE_LOCATION
                    }, 102);
                }
            });
        } catch (Throwable ignored) {
        }
    }

    /** Ouvre l'ecran Android ou l'on allume la localisation. */
    @JavascriptInterface
    public void ouvrirReglagesPosition() {
        try {
            Intent i = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activite.startActivity(i);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Derniere position connue du systeme, immediatement.
     * Renvoie du JSON, ou une chaine vide si rien n'est disponible.
     * On interroge tous les fournisseurs et on garde la mesure la plus recente.
     */
    @JavascriptInterface
    public String positionConnue() {
        try {
            if (!permissionAccordee()) return "";
            LocationManager lm = (LocationManager) activite.getSystemService(Context.LOCATION_SERVICE);
            if (lm == null) return "";

            Location meilleure = derniereMesure;
            List<String> fournisseurs = lm.getProviders(true);
            if (fournisseurs != null) {
                for (String f : fournisseurs) {
                    Location l;
                    try { l = lm.getLastKnownLocation(f); } catch (SecurityException e) { continue; }
                    if (l == null) continue;
                    if (meilleure == null || l.getTime() > meilleure.getTime()) meilleure = l;
                }
            }
            if (meilleure == null) return "";

            JSONObject o = new JSONObject();
            o.put("lat", meilleure.getLatitude());
            o.put("lon", meilleure.getLongitude());
            o.put("precision", Math.round(meilleure.getAccuracy()));
            o.put("t", meilleure.getTime());
            o.put("source", meilleure.getProvider());
            return o.toString();
        } catch (Throwable t) {
            return "";
        }
    }

    /**
     * Lance une mesure fraiche. La reponse n'est pas immediate : elle sera
     * visible quelques secondes plus tard via positionConnue(). Le capteur
     * est coupe des la premiere mesure recue, pour ne pas vider la batterie.
     */
    @JavascriptInterface
    public void demanderPosition() {
        try {
            if (!permissionAccordee()) return;
            activite.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        final LocationManager lm =
                                (LocationManager) activite.getSystemService(Context.LOCATION_SERVICE);
                        if (lm == null) return;

                        final LocationListener ecouteur = new LocationListener() {
                            @Override
                            public void onLocationChanged(Location location) {
                                derniereMesure = location;
                                try { lm.removeUpdates(this); } catch (Throwable ignored) { }
                            }

                            // Ces trois methodes sont obligatoires sur les
                            // anciennes versions d'Android, meme si elles ne
                            // servent a rien ici.
                            @Override public void onStatusChanged(String p, int s, Bundle e) { }
                            @Override public void onProviderEnabled(String p) { }
                            @Override public void onProviderDisabled(String p) { }
                        };

                        // Le fournisseur reseau repond en quelques secondes,
                        // meme en interieur ; le GPS est plus precis mais
                        // souvent muet sous un toit. On demande les deux.
                        if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                            lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, ecouteur);
                        }
                        if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, ecouteur);
                        }

                        // Filet de securite : on coupe au bout de 25 secondes
                        // meme si aucune mesure n'est arrivee.
                        activite.getWindow().getDecorView().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                try { lm.removeUpdates(ecouteur); } catch (Throwable ignored) { }
                            }
                        }, 25000);
                    } catch (Throwable ignored) {
                    }
                }
            });
        } catch (Throwable ignored) {
        }
    }

    /** Annotation decorative : signale une methode d'aide au diagnostic. */
    private @interface TestOnlyHelper {
    }
}
