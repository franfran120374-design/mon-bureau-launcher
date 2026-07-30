package com.monbureau.launcher;

import android.content.Intent;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    // URL de Mon Bureau — change ici si ton adresse évolue
    private static final String MON_BUREAU_URL = "https://franfran120374-design.github.io/mon-bureau/";
    private static final int SWIPE_MIN_DISTANCE = 60;
    private static final int SWIPE_MAX_OFF_PATH = 200;
    private static final int SWIPE_THRESHOLD_VELOCITY = 100;

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
