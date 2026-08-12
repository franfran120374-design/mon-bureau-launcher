package com.monbureau.launcher;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.DialogInterface;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.webkit.ConsoleMessage;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;
import android.widget.Toast;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    // URL de Mon Bureau - change ici si ton adresse evolue
    private static final String MON_BUREAU_URL = "https://franfran120374-design.github.io/mon-bureau/";
    private static final long TIMEOUT_MS = 20000;
    private static final int DEMANDE_CHOIX_APP = 101;
    private static final int DEMANDE_GEOLOC = 102;

    private WebView webView;
    private TextView statusBar;
    private LinearLayout dock;

    private final List<String> journal = new ArrayList<>();
    private boolean pageChargee = false;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webview);
        statusBar = findViewById(R.id.status_bar);
        dock = findViewById(R.id.dock);

        configurerWebView();
        construireDock();

        // Pont vers la page web : expose window.MBVelo au JavaScript de Mon Bureau.
        webView.addJavascriptInterface(new PontVelo(this), "MBVelo");

        // La geolocalisation d'un WebView exige DEUX choses : la permission
        // systeme, demandee ici, et une reponse a la demande du WebView,
        // donnee plus bas dans onGeolocationPermissionsShowPrompt. Il manque
        // l'une ou l'autre, et la position n'arrive jamais, sans erreur.
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            }, DEMANDE_GEOLOC);
        }

        if (savedInstanceState == null) {
            webView.loadUrl(MON_BUREAU_URL);
        }

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!pageChargee) {
                    statut("Aucune reponse apres 20s - appui long sur la grille pour le detail");
                }
            }
        }, TIMEOUT_MS);
    }

    // ---------------------------------------------------------------- WebView

    private void configurerWebView() {
        webView.setBackgroundColor(Color.parseColor("#1c1c1e"));

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView v, String url, android.graphics.Bitmap favicon) {
                note("Chargement demarre: " + url);
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                pageChargee = true;
                note("Page terminee: " + url);
                masquerStatut();
            }

            @Override
            public void onReceivedError(WebView v, WebResourceRequest req, WebResourceError err) {
                String msg = "Erreur " + err.getErrorCode() + " : " + err.getDescription();
                note(msg + " sur " + req.getUrl());
                if (req.isForMainFrame()) statut(msg);
            }

            @Override
            public void onReceivedHttpError(WebView v, WebResourceRequest req, WebResourceResponse resp) {
                note("HTTP " + resp.getStatusCode() + " sur " + req.getUrl());
                if (req.isForMainFrame()) statut("HTTP " + resp.getStatusCode());
            }

            @Override
            public void onReceivedSslError(WebView v, SslErrorHandler h, android.net.http.SslError e) {
                note("Erreur SSL: " + e.toString());
                statut("Erreur SSL - certificat refuse");
                h.cancel();
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage cm) {
                note("JS[" + cm.messageLevel() + "] " + cm.message() + " (ligne " + cm.lineNumber() + ")");
                return true;
            }

            /**
             * Sans cette methode, Android refuse la geolocalisation en silence.
             * Mon Bureau etant charge par nos soins, on accorde directement,
             * a condition que la permission systeme soit la.
             */
            @Override
            public void onGeolocationPermissionsShowPrompt(String origine,
                                                           GeolocationPermissions.Callback rappel) {
                boolean ok = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED;
                if (rappel != null) rappel.invoke(origine, ok, false);
                if (!ok) note("Geoloc refusee : permission systeme absente");
            }
        });

        webView.getSettings().setGeolocationEnabled(true);
    }

    // ------------------------------------------------------------------- Dock

    private void construireDock() {
        dock.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);

        for (final Dock.Case c : Dock.construire(this)) {
            View item = creerCaseDock(inflater, dock, c.label, c.icone, c.iconeSecours);
            item.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (c.intent == null) {
                        Toast.makeText(MainActivity.this,
                                "Appui long pour choisir une application", Toast.LENGTH_SHORT).show();
                    } else {
                        lancer(c.intent, c.label);
                    }
                }
            });
            item.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    menuCase(c);
                    return true;
                }
            });
            dock.addView(item);
        }

        // Derniere case, non modifiable : le tiroir de toutes les applications
        View tiroir = creerCaseDock(inflater, dock, "Apps", null, R.drawable.ic_apps);
        tiroir.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                lancer(new Intent(MainActivity.this, AppDrawerActivity.class), "le tiroir");
            }
        });
        tiroir.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                menuGeneral();
                return true;
            }
        });
        dock.addView(tiroir);
    }

    /** Appui long sur une case : la changer ou la remettre par defaut. */
    private void menuCase(final Dock.Case c) {
        String[] options = c.personnalisee
                ? new String[]{"Changer l'application", "Remettre par defaut"}
                : new String[]{"Changer l'application"};

        new AlertDialog.Builder(this)
                .setTitle(c.label)
                .setItems(options, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int quelle) {
                        if (quelle == 0) {
                            choisirApplication(c.index);
                        } else {
                            Dock.reinitialiser(MainActivity.this, c.index);
                            construireDock();
                            Toast.makeText(MainActivity.this,
                                    "Case remise par defaut", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    /** Appui long sur la grille : diagnostic et remise a zero globale. */
    private void menuGeneral() {
        new AlertDialog.Builder(this)
                .setTitle("Mon Bureau")
                .setItems(new String[]{"Recharger (vider le cache)",
                                       "Remettre tout le dock par defaut",
                                       "Diagnostic"},
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int quelle) {
                                if (quelle == 0) {
                                    rechargerSansCache();
                                } else if (quelle == 1) {
                                    Dock.toutReinitialiser(MainActivity.this);
                                    construireDock();
                                    Toast.makeText(MainActivity.this,
                                            "Dock remis par defaut", Toast.LENGTH_SHORT).show();
                                } else {
                                    afficherDiagnostic();
                                }
                            }
                        })
                .setNegativeButton("Fermer", null)
                .show();
    }

    private void choisirApplication(int indexCase) {
        Intent i = new Intent(this, AppDrawerActivity.class);
        i.putExtra(AppDrawerActivity.EXTRA_MODE_CHOIX, true);
        i.putExtra(AppDrawerActivity.EXTRA_INDEX_CASE, indexCase);
        startActivityForResult(i, DEMANDE_CHOIX_APP);
    }

    @Override
    protected void onActivityResult(int requete, int resultat, Intent data) {
        super.onActivityResult(requete, resultat, data);
        if (requete == DEMANDE_CHOIX_APP && resultat == RESULT_OK && data != null) {
            String pkg = data.getStringExtra(AppDrawerActivity.RESULTAT_PACKAGE);
            int index = data.getIntExtra(AppDrawerActivity.EXTRA_INDEX_CASE, -1);
            if (pkg != null && index >= 0) {
                Dock.definir(this, index, pkg);
                construireDock();
            }
        }
    }

    private View creerCaseDock(LayoutInflater inflater, ViewGroup parent, String label,
                               android.graphics.drawable.Drawable icone, int iconeSecours) {
        View item = inflater.inflate(R.layout.item_dock, parent, false);
        ImageView iv = item.findViewById(R.id.dock_icon);
        TextView tv = item.findViewById(R.id.dock_label);

        if (icone != null) {
            iv.setImageDrawable(icone);
        } else {
            iv.setImageResource(iconeSecours);
        }
        tv.setText(label);
        return item;
    }

    private void lancer(Intent intent, String quoi) {
        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Impossible d'ouvrir " + quoi, Toast.LENGTH_SHORT).show();
            note("Echec lancement " + quoi + " : " + e.getMessage());
        }
    }

    // -------------------------------------------------------------- Diagnostic

    private void note(String ligne) {
        journal.add(ligne);
    }

    private void statut(String texte) {
        if (statusBar != null) {
            statusBar.setVisibility(View.VISIBLE);
            statusBar.setText(texte);
        }
    }

    private void masquerStatut() {
        if (statusBar != null) statusBar.setVisibility(View.GONE);
    }

    private void afficherDiagnostic() {
        StringBuilder sb = new StringBuilder();
        sb.append("URL : ").append(MON_BUREAU_URL).append("\n");
        sb.append("Page chargee : ").append(pageChargee).append("\n\n");
        sb.append("--- Journal (").append(journal.size()).append(") ---\n");
        for (String l : journal) sb.append("\n").append(l).append("\n");
        final String texte = sb.toString();

        new AlertDialog.Builder(this)
                .setTitle("Diagnostic")
                .setMessage(texte)
                .setPositiveButton("Recharger", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        rechargerSansCache();
                    }
                })
                .setNeutralButton("Copier", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                        cm.setPrimaryClip(ClipData.newPlainText("diagnostic", texte));
                        Toast.makeText(MainActivity.this, "Copie", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Fermer", null)
                .show();
    }

    /** Vide le cache puis recharge : utile apres une mise a jour du site. */
    private void rechargerSansCache() {
        pageChargee = false;
        journal.clear();
        webView.clearCache(true);
        webView.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
        webView.loadUrl(MON_BUREAU_URL);
        // On repasse en cache normal pour les navigations suivantes
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                webView.getSettings().setCacheMode(WebSettings.LOAD_DEFAULT);
            }
        }, 3000);
        Toast.makeText(this, "Cache vide, rechargement...", Toast.LENGTH_SHORT).show();
    }

    // ----------------------------------------------------------------- Cycle

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Appui sur le bouton Accueil alors qu'on est deja la : on remonte en haut
        if (webView != null) webView.scrollTo(0, 0);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if (webView != null) webView.saveState(out);
    }

    @Override
    protected void onRestoreInstanceState(Bundle in) {
        super.onRestoreInstanceState(in);
        if (webView != null) webView.restoreState(in);
    }

    // =====================================================================
    // PONT VELO  -  classe interne, exposee au JavaScript sous window.MBVelo
    // ---------------------------------------------------------------------
    // Elle est ecrite ici plutot que dans un fichier separe : un fichier
    // Java oublie lors d'un envoi sur GitHub fait echouer toute la
    // compilation, avec des erreurs qui semblent venir d'ailleurs. En la
    // gardant dans MainActivity, il n'y a plus qu'un seul fichier a envoyer.
    //
    // Toutes les methodes portent @JavascriptInterface : sans cette
    // annotation, Android les rend invisibles au JavaScript.
    // =====================================================================
    public static class PontVelo {

        private static final String PREFS = "mon_bureau_velo";
        private final Activity activite;
        private Location derniereMesure = null;

        public PontVelo(Activity activite) {
            this.activite = activite;
        }

        private SharedPreferences prefs() {
            return activite.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        }

        private boolean permissionAccordee() {
            return activite.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED
                || activite.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED;
        }

        /** Permet a la page de savoir qu'elle tourne dans le launcher. */
        @JavascriptInterface
        public String version() {
            return "1.1";
        }

        // ---------------------------------------------------------- POSITION

        /** L'application a-t-elle le droit d'acceder a la position ? */
        @JavascriptInterface
        public boolean positionAutorisee() {
            try { return permissionAccordee(); } catch (Throwable t) { return false; }
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

        /** Redemande la permission a l'utilisateur (fenetre systeme). */
        @JavascriptInterface
        public void demanderPermission() {
            try {
                activite.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        activite.requestPermissions(new String[]{
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                        }, DEMANDE_GEOLOC);
                    }
                });
            } catch (Throwable ignored) { }
        }

        /** Ouvre l'ecran Android ou l'on allume la localisation. */
        @JavascriptInterface
        public void ouvrirReglagesPosition() {
            try {
                Intent i = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                activite.startActivity(i);
            } catch (Throwable ignored) { }
        }

        /**
         * Derniere position connue du systeme, immediatement, au format JSON.
         * On interroge tous les fournisseurs et on garde la mesure la plus
         * recente. Chaine vide si rien n'est disponible.
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
         * Lance une mesure fraiche. La reponse arrive quelques secondes plus
         * tard via positionConnue(). Le capteur est coupe des la premiere
         * mesure recue, pour menager la batterie.
         */
        @JavascriptInterface
        public void demanderPosition() {
            try {
                if (!permissionAccordee()) return;
                activite.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            final LocationManager lm = (LocationManager)
                                    activite.getSystemService(Context.LOCATION_SERVICE);
                            if (lm == null) return;

                            final LocationListener ecouteur = new LocationListener() {
                                @Override
                                public void onLocationChanged(Location location) {
                                    derniereMesure = location;
                                    try { lm.removeUpdates(this); } catch (Throwable ignored) { }
                                }
                                @Override public void onStatusChanged(String p, int st, Bundle e) { }
                                @Override public void onProviderEnabled(String p) { }
                                @Override public void onProviderDisabled(String p) { }
                            };

                            // Le reseau repond meme en interieur, le GPS est
                            // plus precis mais souvent muet sous un toit :
                            // on demande les deux.
                            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, ecouteur);
                            }
                            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, ecouteur);
                            }

                            // Filet de securite : coupure au bout de 25 secondes.
                            activite.getWindow().getDecorView().postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    try { lm.removeUpdates(ecouteur); } catch (Throwable ignored) { }
                                }
                            }, 25000);
                        } catch (Throwable ignored) { }
                    }
                });
            } catch (Throwable ignored) { }
        }

        // ------------------------------------------------------ NOTIFICATIONS
        // Le chronometre automatique arrivera dans une seconde etape. Ces
        // methodes existent deja pour que la page web ne trouve pas le vide :
        // elles repondent simplement "pas disponible".

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

        @JavascriptInterface
        public void ouvrirReglages() {
            try {
                Intent i = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                activite.startActivity(i);
            } catch (Throwable ignored) { }
        }

        @JavascriptInterface
        public String journal() { return prefs().getString("velo_journal", "[]"); }

        @JavascriptInterface
        public void viderJournal() { prefs().edit().putString("velo_journal", "[]").apply(); }

        @JavascriptInterface
        public String paquet() { return prefs().getString("velo_package", "com.jcdecaux.vls.toulouse"); }

        @JavascriptInterface
        public void definirPaquet(String p) {
            prefs().edit().putString("velo_package", p == null ? "" : p).apply();
        }

        @JavascriptInterface
        public String evenements() { return prefs().getString("velo_events", "[]"); }

        @JavascriptInterface
        public void viderEvenements() { prefs().edit().putString("velo_events", "[]").apply(); }

        @JavascriptInterface
        public void simuler(String type) {
            try {
                org.json.JSONArray a = new org.json.JSONArray(evenements());
                JSONObject o = new JSONObject();
                o.put("type", "FIN".equals(type) ? "FIN" : "DEBUT");
                o.put("titre", "Test Mon Bureau");
                o.put("texte", "Evenement simule");
                o.put("t", System.currentTimeMillis());
                a.put(o);
                prefs().edit().putString("velo_events", a.toString()).apply();
            } catch (Throwable ignored) { }
        }

        // ------------------------------------------------ BULLE FLOTTANTE

        /** L'autorisation "afficher par-dessus les autres applications" est-elle donnee ? */
        @JavascriptInterface
        public boolean bulleAutorisee() {
            try { return Settings.canDrawOverlays(activite); } catch (Throwable t) { return false; }
        }

        /** Ouvre l'ecran Android ou cette autorisation se donne. */
        @JavascriptInterface
        public void demanderBulle() {
            try {
                Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:" + activite.getPackageName()));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                activite.startActivity(i);
            } catch (Throwable t) {
                try {
                    Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    activite.startActivity(i);
                } catch (Throwable ignored) { }
            }
        }

        /**
         * Affiche la bulle. Le service recalcule le temps tout seul a partir
         * de l'heure de depart : Mon Bureau peut etre ferme, le chronometre
         * de la bulle reste juste.
         */
        @JavascriptInterface
        public void demarrerBulle(String debutMs, int gratuitMin, String tarif, int dureeTranche) {
            try {
                long debut;
                try { debut = Long.parseLong(debutMs); }
                catch (Throwable t) { debut = System.currentTimeMillis(); }

                prefs().edit()
                        .putLong("bulle_debut", debut)
                        .putInt("bulle_gratuit", gratuitMin > 0 ? gratuitMin : 30)
                        .putInt("bulle_tranche", dureeTranche > 0 ? dureeTranche : 30)
                        .putString("bulle_tarif", (tarif == null || tarif.isEmpty()) ? "1" : tarif)
                        .apply();

                Intent i = new Intent(activite, VeloOverlayService.class);
                activite.startForegroundService(i);
            } catch (Throwable t) {
                android.util.Log.w("MonBureau", "bulle: " + t.getMessage());
            }
        }

        /** Retire la bulle et sa notification. */
        @JavascriptInterface
        public void arreterBulle() {
            try {
                activite.stopService(new Intent(activite, VeloOverlayService.class));
            } catch (Throwable ignored) { }
        }
    }

    // =====================================================================
    // BULLE FLOTTANTE
    // ---------------------------------------------------------------------
    // Une petite fenetre qui reste visible PAR-DESSUS toutes les autres
    // applications, y compris veloToulouse. Elle affiche le temps ecoule et
    // le prix en cours, et permet d'arreter le trajet sans quitter ce qu'on
    // est en train de faire.
    //
    // Elle se deplace au doigt. Un appui simple ouvre Mon Bureau. Le bouton
    // rouge arrete le trajet.
    //
    // Android exige deux choses pour ce genre de fenetre :
    //   - la permission "Afficher par-dessus les autres applications",
    //     accordee a la main une seule fois ;
    //   - un service dit "au premier plan", avec une notification permanente,
    //     sinon le systeme coupe le service au bout de quelques minutes.
    // =====================================================================
    public static class VeloOverlayService extends Service {

        public static final String PREFS = "mon_bureau_velo";
        public static final String CANAL = "velo_trajet";
        public static final int NOTIF_ID = 4201;

        private WindowManager fenetres;
        private View bulle;
        private TextView vChrono, vPrix;
        private Handler horloge;
        private Runnable battement;

        public static final String ACTION_FIN = "com.monbureau.launcher.VELO_FIN";

        private long debut;
        private int gratuitMin;
        private double tarif;
        private int dureeTranche;
        private int trancheMin;      // meme valeur que dureeTranche, nom court
        private int derniereMinute = -1;

        @Override
        public IBinder onBind(Intent i) { return null; }

        @Override
        public int onStartCommand(Intent intent, int flags, int startId) {
            // Bouton "Repose" de la notification : on note la fin du trajet
            // pour que Mon Bureau la prenne en compte a sa prochaine ouverture,
            // puis on s'arrete. Aucun deverrouillage necessaire.
            if (intent != null && ACTION_FIN.equals(intent.getAction())) {
                try {
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                            .putLong("velo_fin_demandee", System.currentTimeMillis()).apply();
                } catch (Throwable ignored) { }
                stopSelf();
                return START_NOT_STICKY;
            }

            SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
            debut        = p.getLong("bulle_debut", System.currentTimeMillis());
            gratuitMin   = p.getInt("bulle_gratuit", 30);
            dureeTranche = p.getInt("bulle_tranche", 30);
            trancheMin   = dureeTranche;
            try { tarif = Double.parseDouble(p.getString("bulle_tarif", "1")); }
            catch (Throwable t) { tarif = 1.0; }

            demarrerAuPremierPlan();
            if (bulle == null) construireBulle();
            lancerHorloge();
            return START_STICKY;
        }

        // ---------------------------------------------------- notification

        private void demarrerAuPremierPlan() {
            construireNotification(true);
        }

        /**
         * La notification du trajet.
         *
         * A velo, on ne veut pas avoir a deverrouiller le telephone pour
         * savoir ou on en est. Trois reglages rendent la notification
         * lisible directement sur l'ecran de verrouillage :
         *
         *   IMPORTANCE_DEFAULT   la notification apparait sur l'ecran
         *                        verrouille (IMPORTANCE_LOW l'y masque)
         *   VISIBILITY_PUBLIC    son contenu s'affiche en entier, meme
         *                        quand le telephone est verrouille
         *   setUsesChronometer   le compteur est mis a jour par Android
         *                        lui-meme, chaque seconde, sans que notre
         *                        application ait besoin de tourner
         *
         * Le bouton "Repose" arrete le trajet sans rien deverrouiller.
         */
        private void construireNotification(boolean premierAppel) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null && nm.getNotificationChannel(CANAL) == null) {
                NotificationChannel c = new NotificationChannel(
                        CANAL, "Trajet velo", NotificationManager.IMPORTANCE_DEFAULT);
                c.setDescription("Chronometre visible pendant un trajet");
                c.setShowBadge(false);
                c.setSound(null, null);          // silencieux : pas de bip a chaque mise a jour
                c.enableVibration(false);
                c.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
                nm.createNotificationChannel(c);
            }

            Intent ouvrir = new Intent(this, MainActivity.class);
            ouvrir.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent piOuvrir = PendingIntent.getActivity(this, 0, ouvrir,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            Intent fin = new Intent(this, VeloOverlayService.class).setAction(ACTION_FIN);
            PendingIntent piFin = PendingIntent.getService(this, 2, fin,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            long sec = (System.currentTimeMillis() - debut) / 1000;
            double depassement = (sec / 60.0) - gratuitMin;
            double cout = depassement <= 0 ? 0 : Math.ceil(depassement / trancheMin) * tarif;
            long resteMin = (gratuitMin * 60L - sec) / 60;

            String texte = cout > 0
                    ? String.format(java.util.Locale.FRANCE, "Depassement - %.2f EUR", cout).replace('.', ',')
                    : "Encore " + Math.max(0, resteMin) + " min incluses";

            Notification n = new Notification.Builder(this, CANAL)
                    .setContentTitle("Trajet velo en cours")
                    .setContentText(texte)
                    .setSmallIcon(android.R.drawable.ic_menu_compass)
                    .setContentIntent(piOuvrir)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setUsesChronometer(true)
                    .setWhen(debut)
                    .setShowWhen(true)
                    .setVisibility(Notification.VISIBILITY_PUBLIC)
                    .setCategory(Notification.CATEGORY_STOPWATCH)
                    .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Repose", piFin)
                    .build();

            if (premierAppel) {
                try {
                    startForeground(NOTIF_ID, n);
                } catch (Throwable t) {
                    try { startForeground(NOTIF_ID, n, 1073741824); } catch (Throwable ignored) { }
                }
            } else if (nm != null) {
                try { nm.notify(NOTIF_ID, n); } catch (Throwable ignored) { }
            }
        }

        // ------------------------------------------------------- apparence

        private int dp(float v) {
            return Math.round(TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
        }

        private void construireBulle() {
            fenetres = (WindowManager) getSystemService(WINDOW_SERVICE);
            if (fenetres == null) return;

            LinearLayout boite = new LinearLayout(this);
            boite.setOrientation(LinearLayout.HORIZONTAL);
            boite.setGravity(Gravity.CENTER_VERTICAL);
            boite.setPadding(dp(12), dp(9), dp(9), dp(9));

            GradientDrawable fond = new GradientDrawable();
            fond.setColor(Color.parseColor("#F2FFFFFF"));
            fond.setCornerRadius(dp(22));
            fond.setStroke(dp(2), Color.parseColor("#2D9C6A"));
            boite.setBackground(fond);
            boite.setElevation(dp(8));

            TextView icone = new TextView(this);
            icone.setText("\ud83d\udeb2");
            icone.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
            icone.setPadding(0, 0, dp(9), 0);
            boite.addView(icone);

            LinearLayout colonne = new LinearLayout(this);
            colonne.setOrientation(LinearLayout.VERTICAL);

            vChrono = new TextView(this);
            vChrono.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19);
            vChrono.setTextColor(Color.parseColor("#1A1A1A"));
            vChrono.setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD);
            vChrono.setText("00:00");
            colonne.addView(vChrono);

            vPrix = new TextView(this);
            vPrix.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            vPrix.setTextColor(Color.parseColor("#6B6B6B"));
            vPrix.setText("gratuit");
            colonne.addView(vPrix);

            boite.addView(colonne);

            TextView stop = new TextView(this);
            stop.setText("\u25a0");
            stop.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            stop.setTextColor(Color.WHITE);
            stop.setGravity(Gravity.CENTER);
            stop.setPadding(dp(11), dp(6), dp(11), dp(8));
            GradientDrawable rond = new GradientDrawable();
            rond.setColor(Color.parseColor("#E63946"));
            rond.setCornerRadius(dp(16));
            stop.setBackground(rond);
            LinearLayout.LayoutParams lpStop = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lpStop.leftMargin = dp(10);
            boite.addView(stop, lpStop);

            stop.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) { arreterDepuisBulle(); }
            });

            final WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    android.graphics.PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.START;
            SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
            lp.x = p.getInt("bulle_x", dp(14));
            lp.y = p.getInt("bulle_y", dp(120));

            // Deplacement au doigt ; un appui bref ouvre Mon Bureau.
            boite.setOnTouchListener(new View.OnTouchListener() {
                float xDepart, yDepart;
                int lpx, lpy;
                long quand;

                @Override
                public boolean onTouch(View v, MotionEvent e) {
                    switch (e.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            xDepart = e.getRawX(); yDepart = e.getRawY();
                            lpx = lp.x; lpy = lp.y;
                            quand = System.currentTimeMillis();
                            return true;
                        case MotionEvent.ACTION_MOVE:
                            lp.x = lpx + (int) (e.getRawX() - xDepart);
                            lp.y = lpy + (int) (e.getRawY() - yDepart);
                            try { fenetres.updateViewLayout(bulle, lp); } catch (Throwable ignored) { }
                            return true;
                        case MotionEvent.ACTION_UP:
                            boolean bref = System.currentTimeMillis() - quand < 220;
                            boolean bouge = Math.abs(e.getRawX() - xDepart) > dp(8)
                                         || Math.abs(e.getRawY() - yDepart) > dp(8);
                            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                                    .putInt("bulle_x", lp.x).putInt("bulle_y", lp.y).apply();
                            if (bref && !bouge) {
                                Intent i = new Intent(VeloOverlayService.this, MainActivity.class);
                                i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                                startActivity(i);
                            }
                            return true;
                    }
                    return false;
                }
            });

            bulle = boite;
            try {
                fenetres.addView(bulle, lp);
            } catch (Throwable t) {
                bulle = null;   // permission absente : on renonce sans planter
            }
        }

        // --------------------------------------------------------- horloge

        private void lancerHorloge() {
            if (horloge != null) return;
            horloge = new Handler(Looper.getMainLooper());
            battement = new Runnable() {
                @Override
                public void run() {
                    majAffichage();
                    horloge.postDelayed(this, 1000);
                }
            };
            horloge.post(battement);
        }

        private void majAffichage() {
            long sec = (System.currentTimeMillis() - debut) / 1000;
            if (sec < 0) sec = 0;

            // Texte de la notification : une fois par minute suffit.
            int minute = (int) (sec / 60);
            if (minute != derniereMinute) {
                derniereMinute = minute;
                construireNotification(false);
            }

            if (vChrono == null) return;

            long h = sec / 3600, m = (sec % 3600) / 60, r = sec % 60;
            String t = h > 0
                    ? String.format("%d:%02d:%02d", h, m, r)
                    : String.format("%02d:%02d", m, r);
            vChrono.setText(t);

            double minutes = sec / 60.0;
            double sup = minutes - gratuitMin;
            int tranches = sup <= 0 ? 0 : (int) Math.ceil(sup / dureeTranche);
            double prix = tranches * tarif;

            if (tranches == 0) {
                long reste = (long) (gratuitMin * 60 - sec);
                vPrix.setText("gratuit " + (reste / 60) + " min encore");
                vPrix.setTextColor(Color.parseColor("#2D9C6A"));
                vChrono.setTextColor(Color.parseColor("#1A1A1A"));
            } else {
                vPrix.setText(String.format("%.2f", prix).replace('.', ',') + " EUR");
                vPrix.setTextColor(Color.parseColor("#E63946"));
                vChrono.setTextColor(Color.parseColor("#E63946"));
            }
        }

        // ----------------------------------------------------------- arret

        /** Arret demande depuis la bulle : on note l'heure, la page web la lira. */
        private void arreterDepuisBulle() {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putLong("bulle_arret", System.currentTimeMillis()).apply();
            stopSelf();
        }

        @Override
        public void onDestroy() {
            if (horloge != null && battement != null) horloge.removeCallbacks(battement);
            horloge = null;
            if (bulle != null && fenetres != null) {
                try { fenetres.removeView(bulle); } catch (Throwable ignored) { }
            }
            bulle = null;
            try { stopForeground(true); } catch (Throwable ignored) { }
            super.onDestroy();
        }
    }
}
