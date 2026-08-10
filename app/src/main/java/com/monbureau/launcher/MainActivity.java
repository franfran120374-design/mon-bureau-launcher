package com.monbureau.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.widget.Toast;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final String TAG = "MonBureauLauncher";

    // URL de Mon Bureau — change ici si ton adresse evolue
    private static final String MON_BUREAU_URL = "https://franfran120374-design.github.io/mon-bureau/";

    private static final int SWIPE_MIN_DISTANCE = 60;
    private static final int SWIPE_MAX_OFF_PATH = 200;
    private static final int SWIPE_THRESHOLD_VELOCITY = 100;

    // ---- Rechargement automatique ----
    // Si tu reviens sur l'accueil apres plus de 10 minutes d'absence, la
    // page est rechargee silencieusement. Toute cette logique est protegee :
    // une erreur ici est journalisee et ignoree, elle ne doit jamais faire
    // planter l'ecran d'accueil.
    private static final long INTERVALLE_RECHARGEMENT_MS = 10 * 60 * 1000; // 10 minutes
    private static final String PREFS = "mon_bureau_launcher_prefs";
    private static final String CLE_DERNIER_CHARGEMENT = "dernier_chargement";
    private static final String CLE_DERNIER_CRASH = "dernier_crash";

    private WebView webView;
    private GestureDetector gestureDetector;
    private DockManager dockManager;

    // =====================================================================
    //  DEMARRAGE
    // =====================================================================
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Le filet de securite doit etre pose EN PREMIER : a partir d'ici,
        // toute erreur non rattrapee est enregistree avant que le systeme
        // ne ferme l'application. On pourra la relire au demarrage suivant.
        installerCaptureDesCrashs();

        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webview);
        configurerWebView(savedInstanceState);

        // Le dock, le tiroir d'applis et les gestes sont des elements de
        // confort. Si l'un d'eux echoue, l'ecran d'accueil doit rester
        // utilisable : on ne laisse jamais le telephone sans accueil.
        try {
            configurerDockEtGestes();
        } catch (Throwable t) {
            Log.e(TAG, "Dock/gestes indisponibles", t);
            enregistrerCrash(t);
        }

        afficherRapportDeCrashSiPresent();
    }

    // =====================================================================
    //  WEBVIEW
    // =====================================================================
    private void configurerWebView(Bundle savedInstanceState) {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setAllowFileAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);

        // Permet d'inspecter la page depuis le PC (chrome://inspect) quand
        // le telephone est branche en USB avec le debogage active.
        try {
            WebView.setWebContentsDebuggingEnabled(true);
        } catch (Throwable t) {
            Log.w(TAG, "debogage distant indisponible: " + t.getMessage());
        }

        // --- Connexion Google dans une WebView -----------------------------
        // Google refuse l'authentification OAuth quand il detecte une WebView.
        // Il la reconnait au marqueur "; wv" present dans le user-agent.
        // En le retirant, la page de connexion Google s'affiche normalement.
        try {
            String ua = settings.getUserAgentString();
            if (ua != null) {
                ua = ua.replace("; wv", "").replace("Version/4.0 ", "");
                settings.setUserAgentString(ua);
            }
        } catch (Throwable t) {
            Log.w(TAG, "user-agent non modifie: " + t.getMessage());
        }

        // --- Cookies persistants -------------------------------------------
        // Sans ceci, la session Google est perdue des qu'Android recycle le
        // processus du launcher.
        try {
            CookieManager cookies = CookieManager.getInstance();
            cookies.setAcceptCookie(true);
            cookies.setAcceptThirdPartyCookies(webView, true);
        } catch (Throwable t) {
            Log.w(TAG, "cookies: " + t.getMessage());
        }

        // Sans ce client personnalise, les liens tel:/sms:/mailto: du site
        // restent muets a l'interieur de la WebView : elle essaie de les
        // "afficher" comme une page au lieu de les transmettre au telephone.
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String schema = uri.getScheme();
                if (schema != null && !schema.equals("http") && !schema.equals("https")) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, uri));
                    } catch (ActivityNotFoundException e) {
                        Log.w(TAG, "Aucune appli pour ouvrir : " + uri);
                    }
                    return true;
                }
                return false;
            }
        });

        if (savedInstanceState == null) {
            webView.loadUrl(MON_BUREAU_URL);
            marquerChargement();
        }
    }

    // =====================================================================
    //  DOCK + TIROIR D'APPLIS + GESTES
    // =====================================================================
    private void configurerDockEtGestes() {
        ImageButton btnAppDrawer = findViewById(R.id.btn_app_drawer);
        btnAppDrawer.setOnClickListener(v -> openAppDrawer());

        // Appui long sur le bouton du tiroir = menu de depannage.
        // C'est le seul moyen d'acceder aux diagnostics sans PC.
        btnAppDrawer.setOnLongClickListener(v -> {
            afficherMenuDepannage();
            return true;
        });

        ImageButton[] slotsDock = new ImageButton[] {
                findViewById(R.id.dock_slot_0),
                findViewById(R.id.dock_slot_1),
                findViewById(R.id.dock_slot_2),
                findViewById(R.id.dock_slot_3)
        };
        dockManager = new DockManager(this, slotsDock);

        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) return false;
                float deltaY = e1.getY() - e2.getY();
                float deltaX = Math.abs(e1.getX() - e2.getX());
                boolean isSwipeUp = deltaY > SWIPE_MIN_DISTANCE
                        && deltaX < SWIPE_MAX_OFF_PATH
                        && Math.abs(velocityY) > SWIPE_THRESHOLD_VELOCITY;
                if (isSwipeUp) {
                    openAppDrawer();
                    return true;
                }
                return false;
            }
        });

        View edgeSwipeZone = findViewById(R.id.edge_swipe_zone);
        edgeSwipeZone.setOnTouchListener((v, event) -> gestureDetector.onTouchEvent(event));
    }

    private void openAppDrawer() {
        try {
            startActivity(new Intent(this, AppDrawerActivity.class));
        } catch (Throwable t) {
            Log.e(TAG, "Ouverture du tiroir impossible", t);
            enregistrerCrash(t);
            Toast.makeText(this, "Tiroir d'applis indisponible", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        try {
            if (dockManager != null) {
                dockManager.onActivityResult(requestCode, resultCode, data);
            }
        } catch (Throwable t) {
            Log.e(TAG, "onActivityResult", t);
        }
    }

    // =====================================================================
    //  CYCLE DE VIE
    // =====================================================================
    //
    //  CORRECTIF PRINCIPAL DE CETTE VERSION
    //  ------------------------------------
    //  La version precedente appelait ici :
    //      WebStorage.getInstance().getOrigins(null);
    //  Android execute cet appel EN DIFFERE puis fait
    //  callback.onReceiveValue(...) sur l'objet fourni. Comme l'objet
    //  fourni etait null, une NullPointerException etait levee APRES la
    //  sortie du bloc try/catch : impossible a rattraper, l'application
    //  se fermait. Et comme onPause() se declenche chaque fois qu'on
    //  quitte l'accueil, le launcher plantait a repetition.
    //
    //  Cet appel ne servait a rien : il listait des quotas de stockage,
    //  il ne "vidait" rien du tout. Il est purement et simplement retire.
    //  La persistance reelle est assuree par CookieManager.flush() et par
    //  la WebView elle-meme, qui ecrit le localStorage sur le disque.
    //
    @Override
    protected void onPause() {
        super.onPause();
        try {
            if (webView != null) {
                webView.evaluateJavascript(
                        "try{window.MBSync&&window.MBSync.flushOnExit&&window.MBSync.flushOnExit();}catch(e){}",
                        null);
            }
        } catch (Throwable t) {
            Log.w(TAG, "flush JS onPause: " + t.getMessage());
        }
        try {
            CookieManager.getInstance().flush();
        } catch (Throwable t) {
            Log.w(TAG, "flush cookies onPause: " + t.getMessage());
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        try {
            CookieManager.getInstance().flush();
        } catch (Throwable t) {
            Log.w(TAG, "flush cookies onStop: " + t.getMessage());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            if (webView != null && dernierChargementDepasse()) {
                webView.clearCache(false);
                webView.loadUrl(MON_BUREAU_URL);
                marquerChargement();
            }
        } catch (Throwable t) {
            Log.e(TAG, "Rechargement automatique ignore suite a une erreur", t);
        }
    }

    @Override
    public void onBackPressed() {
        // Comportement launcher : le bouton retour navigue dans la page,
        // il ne quitte jamais l'appli (sinon le telephone n'a plus d'accueil).
        try {
            if (webView != null && webView.canGoBack()) {
                webView.goBack();
            }
        } catch (Throwable t) {
            Log.w(TAG, "onBackPressed: " + t.getMessage());
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        try {
            if (webView != null) webView.saveState(outState);
        } catch (Throwable t) {
            Log.w(TAG, "saveState: " + t.getMessage());
        }
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        try {
            if (webView != null) webView.restoreState(savedInstanceState);
        } catch (Throwable t) {
            Log.w(TAG, "restoreState: " + t.getMessage());
        }
    }

    // =====================================================================
    //  MEMOIRE DU DERNIER CHARGEMENT
    // =====================================================================
    private boolean dernierChargementDepasse() {
        try {
            SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
            long dernier = prefs.getLong(CLE_DERNIER_CHARGEMENT, 0);
            return System.currentTimeMillis() - dernier > INTERVALLE_RECHARGEMENT_MS;
        } catch (Throwable t) {
            Log.e(TAG, "Lecture des preferences impossible", t);
            return false;
        }
    }

    private void marquerChargement() {
        try {
            getSharedPreferences(PREFS, MODE_PRIVATE)
                    .edit()
                    .putLong(CLE_DERNIER_CHARGEMENT, System.currentTimeMillis())
                    .apply();
        } catch (Throwable t) {
            Log.e(TAG, "Ecriture des preferences impossible", t);
        }
    }

    // =====================================================================
    //  BOITE NOIRE : capture et relecture des plantages
    // =====================================================================
    private void installerCaptureDesCrashs() {
        try {
            final Thread.UncaughtExceptionHandler precedent =
                    Thread.getDefaultUncaughtExceptionHandler();
            Thread.setDefaultUncaughtExceptionHandler((thread, erreur) -> {
                try {
                    enregistrerCrash(erreur);
                } catch (Throwable ignore) {
                    // On ne fait jamais echouer l'enregistrement du crash.
                }
                if (precedent != null) {
                    precedent.uncaughtException(thread, erreur);
                }
            });
        } catch (Throwable t) {
            Log.w(TAG, "capture des crashs indisponible: " + t.getMessage());
        }
    }

    private void enregistrerCrash(Throwable erreur) {
        try {
            StringWriter sw = new StringWriter();
            erreur.printStackTrace(new PrintWriter(sw));
            String horodatage = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.FRANCE)
                    .format(new Date());
            String rapport = "Date : " + horodatage
                    + "\nAndroid : " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")"
                    + "\nAppareil : " + Build.MANUFACTURER + " " + Build.MODEL
                    + "\n\n" + sw;
            // commit() et non apply() : le processus peut mourir dans la
            // milliseconde qui suit, l'ecriture doit etre immediate.
            getSharedPreferences(PREFS, MODE_PRIVATE)
                    .edit()
                    .putString(CLE_DERNIER_CRASH, rapport)
                    .commit();
        } catch (Throwable ignore) {
        }
    }

    private void afficherRapportDeCrashSiPresent() {
        try {
            SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
            final String rapport = prefs.getString(CLE_DERNIER_CRASH, null);
            if (rapport == null || rapport.isEmpty()) return;

            new AlertDialog.Builder(this)
                    .setTitle("Mon Bureau a rencontre une erreur")
                    .setMessage(rapport)
                    .setPositiveButton("Copier", (d, w) -> copierDansPressePapier(rapport))
                    .setNegativeButton("Effacer", (d, w) -> effacerRapportDeCrash())
                    .setNeutralButton("Plus tard", null)
                    .show();
        } catch (Throwable t) {
            Log.w(TAG, "affichage du rapport impossible: " + t.getMessage());
        }
    }

    private void effacerRapportDeCrash() {
        try {
            getSharedPreferences(PREFS, MODE_PRIVATE)
                    .edit()
                    .remove(CLE_DERNIER_CRASH)
                    .apply();
        } catch (Throwable ignore) {
        }
    }

    private void copierDansPressePapier(String texte) {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("Mon Bureau - erreur", texte));
                Toast.makeText(this, "Copie. Colle-le dans la conversation.", Toast.LENGTH_LONG).show();
            }
        } catch (Throwable ignore) {
        }
    }

    // =====================================================================
    //  MENU DE DEPANNAGE (appui long sur le bouton du tiroir d'applis)
    // =====================================================================
    private void afficherMenuDepannage() {
        try {
            final String[] choix = {
                    "Recharger la page",
                    "Vider le cache et recharger",
                    "Voir la derniere erreur",
                    "Effacer le rapport d'erreur"
            };
            new AlertDialog.Builder(this)
                    .setTitle("Depannage — Mon Bureau")
                    .setItems(choix, (d, index) -> {
                        switch (index) {
                            case 0:
                                if (webView != null) webView.reload();
                                break;
                            case 1:
                                if (webView != null) {
                                    webView.clearCache(true);
                                    webView.loadUrl(MON_BUREAU_URL);
                                    marquerChargement();
                                    Toast.makeText(this, "Cache vide", Toast.LENGTH_SHORT).show();
                                }
                                break;
                            case 2:
                                String r = getSharedPreferences(PREFS, MODE_PRIVATE)
                                        .getString(CLE_DERNIER_CRASH, null);
                                if (r == null || r.isEmpty()) {
                                    Toast.makeText(this, "Aucune erreur enregistree", Toast.LENGTH_SHORT).show();
                                } else {
                                    afficherRapportDeCrashSiPresent();
                                }
                                break;
                            case 3:
                                effacerRapportDeCrash();
                                Toast.makeText(this, "Rapport efface", Toast.LENGTH_SHORT).show();
                                break;
                        }
                    })
                    .setNegativeButton("Fermer", null)
                    .show();
        } catch (Throwable t) {
            Log.w(TAG, "menu depannage: " + t.getMessage());
        }
    }
}
