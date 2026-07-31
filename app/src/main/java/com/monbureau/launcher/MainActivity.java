package com.monbureau.launcher;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;

public class MainActivity extends Activity {

    private static final String TAG = "MonBureauLauncher";

    // URL de Mon Bureau — change ici si ton adresse évolue
    private static final String MON_BUREAU_URL = "https://franfran120374-design.github.io/mon-bureau/";
    private static final int SWIPE_MIN_DISTANCE = 60;
    private static final int SWIPE_MAX_OFF_PATH = 200;
    private static final int SWIPE_THRESHOLD_VELOCITY = 100;

    // ---- Rechargement automatique ----
    // Si tu reviens sur l'accueil apres plus de 10 minutes d'absence, la
    // page est rechargee silencieusement, cache navigateur ignore.
    //
    // Toute cette logique est entouree de try/catch : une erreur ici est
    // journalisee et ignoree, elle ne doit jamais faire planter l'ecran
    // d'accueil pour une simple fonctionnalite de confort.
    private static final long INTERVALLE_RECHARGEMENT_MS = 10 * 60 * 1000; // 10 minutes
    private static final String PREFS = "mon_bureau_launcher_prefs";
    private static final String CLE_DERNIER_CHARGEMENT = "dernier_chargement";

    private WebView webView;
    private GestureDetector gestureDetector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webview);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setAllowFileAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);

        webView.setWebViewClient(new WebViewClient());

        if (savedInstanceState == null) {
            webView.loadUrl(MON_BUREAU_URL);
            marquerChargement();
        }

        ImageButton btnAppDrawer = findViewById(R.id.btn_app_drawer);
        btnAppDrawer.setOnClickListener(v -> openAppDrawer());

        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null) return false;
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

    @Override
    protected void onResume() {
        super.onResume();
        try {
            if (webView != null && dernierChargementDepasse()) {
                webView.clearCache(false);
                webView.loadUrl(MON_BUREAU_URL);
                marquerChargement();
            }
        } catch (Exception e) {
            Log.e(TAG, "Rechargement automatique ignore suite a une erreur", e);
        }
    }

    private boolean dernierChargementDepasse() {
        try {
            SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
            long dernier = prefs.getLong(CLE_DERNIER_CHARGEMENT, 0);
            return System.currentTimeMillis() - dernier > INTERVALLE_RECHARGEMENT_MS;
        } catch (Exception e) {
            Log.e(TAG, "Lecture des preferences impossible", e);
            return false;
        }
    }

    private void marquerChargement() {
        try {
            getSharedPreferences(PREFS, MODE_PRIVATE)
                    .edit()
                    .putLong(CLE_DERNIER_CHARGEMENT, System.currentTimeMillis())
                    .apply();
        } catch (Exception e) {
            Log.e(TAG, "Ecriture des preferences impossible", e);
        }
    }

    private void openAppDrawer() {
        startActivity(new Intent(this, AppDrawerActivity.class));
    }

    @Override
    public void onBackPressed() {
        // Comportement launcher : le bouton retour navigue dans la page,
        // jamais ne quitte l'appli (sinon le téléphone se retrouve sans accueil)
        if (webView.canGoBack()) {
            webView.goBack();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        webView.saveState(outState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        webView.restoreState(savedInstanceState);
    }
}
