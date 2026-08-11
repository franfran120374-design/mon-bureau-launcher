package com.monbureau.launcher;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.DialogInterface;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import android.widget.LinearLayout;
import android.widget.TextView;
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
    }
}
