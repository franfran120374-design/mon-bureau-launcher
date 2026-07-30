package com.monbureau.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.ConsoleMessage;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    // URL de Mon Bureau - change ici si ton adresse evolue
    private static final String MON_BUREAU_URL = "https://franfran120374-design.github.io/mon-bureau/";

    private static final int SWIPE_MIN_DISTANCE = 60;
    private static final int SWIPE_MAX_OFF_PATH = 200;
    private static final int SWIPE_THRESHOLD_VELOCITY = 100;
    private static final long TIMEOUT_MS = 20000;

    private WebView webView;
    private TextView statusBar;
    private GestureDetector gestureDetector;

    private final List<String> journal = new ArrayList<>();
    private boolean pageChargee = false;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webview);
        statusBar = findViewById(R.id.status_bar);

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
        s.setSupportMultipleWindows(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);

        note("WebView UA: " + s.getUserAgentString());

        webView.setWebViewClient(new WebViewClient() {

            @Override
            public void onPageStarted(WebView v, String url, android.graphics.Bitmap favicon) {
                note("Chargement demarre: " + url);
                statut("Chargement...");
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                pageChargee = true;
                note("Page terminee: " + url);
                // Verifie que la page contient reellement quelque chose
                v.evaluateJavascript(
                        "(function(){return document.body?document.body.innerText.length:-1;})();",
                        new android.webkit.ValueCallback<String>() {
                            @Override
                            public void onReceiveValue(String value) {
                                note("Taille du contenu: " + value);
                                if ("-1".equals(value) || "0".equals(value)) {
                                    statut("Page chargee mais VIDE - appui long sur le bouton pour le detail");
                                } else {
                                    masquerStatut();
                                }
                            }
                        });
            }

            @Override
            public void onReceivedError(WebView v, WebResourceRequest req, WebResourceError err) {
                String msg = "Erreur " + err.getErrorCode() + " : " + err.getDescription()
                        + " sur " + req.getUrl();
                note(msg);
                if (req.isForMainFrame()) statut(msg);
            }

            @Override
            public void onReceivedHttpError(WebView v, WebResourceRequest req, WebResourceResponse resp) {
                String msg = "HTTP " + resp.getStatusCode() + " sur " + req.getUrl();
                note(msg);
                if (req.isForMainFrame()) statut(msg);
            }

            @Override
            public void onReceivedSslError(WebView v, SslErrorHandler handler, android.net.http.SslError error) {
                note("Erreur SSL: " + error.toString());
                statut("Erreur SSL - certificat refuse");
                handler.cancel();
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView v, int progress) {
                if (!pageChargee) statut("Chargement " + progress + "%");
            }

            @Override
            public boolean onConsoleMessage(ConsoleMessage cm) {
                note("JS[" + cm.messageLevel() + "] " + cm.message()
                        + " (ligne " + cm.lineNumber() + ")");
                return true;
            }
        });

        if (savedInstanceState == null) {
            webView.loadUrl(MON_BUREAU_URL);
        }

        // Chien de garde : si rien n'a charge au bout de 20s, on le dit
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!pageChargee) {
                    statut("Aucune reponse apres 20s - appui long sur le bouton pour le detail");
                }
            }
        }, TIMEOUT_MS);

        ImageButton btn = findViewById(R.id.btn_app_drawer);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openAppDrawer();
            }
        });
        btn.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                afficherDiagnostic();
                return true;
            }
        });

        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if (e1 == null || e2 == null) return false;
                float dy = e1.getY() - e2.getY();
                float dx = Math.abs(e1.getX() - e2.getX());
                if (dy > SWIPE_MIN_DISTANCE && dx < SWIPE_MAX_OFF_PATH
                        && Math.abs(vy) > SWIPE_THRESHOLD_VELOCITY) {
                    openAppDrawer();
                    return true;
                }
                return false;
            }
        });

        findViewById(R.id.edge_swipe_zone).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                gestureDetector.onTouchEvent(event);
                return true;
            }
        });
    }

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
        sb.append("URL cible :\n").append(MON_BUREAU_URL).append("\n\n");
        sb.append("Page chargee : ").append(pageChargee).append("\n\n");
        sb.append("--- Journal (").append(journal.size()).append(" entrees) ---\n");
        for (String l : journal) {
            sb.append("\n").append(l).append("\n");
        }
        final String texte = sb.toString();

        new AlertDialog.Builder(this)
                .setTitle("Diagnostic Mon Bureau")
                .setMessage(texte)
                .setPositiveButton("Copier", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        android.content.ClipboardManager cm =
                                (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("diagnostic", texte));
                        Toast.makeText(MainActivity.this, "Copie dans le presse-papier", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNeutralButton("Recharger", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        pageChargee = false;
                        journal.clear();
                        webView.loadUrl(MON_BUREAU_URL);
                    }
                })
                .setNegativeButton("Fermer", null)
                .show();
    }

    private void openAppDrawer() {
        try {
            startActivity(new Intent(this, AppDrawerActivity.class));
        } catch (Exception e) {
            Toast.makeText(this, "Impossible d'ouvrir le tiroir : " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
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
}
