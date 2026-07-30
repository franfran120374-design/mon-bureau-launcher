package com.monbureau.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.ConsoleMessage;
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

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    // URL de Mon Bureau - change ici si ton adresse evolue
    private static final String MON_BUREAU_URL = "https://franfran120374-design.github.io/mon-bureau/";
    private static final long TIMEOUT_MS = 20000;
    private static final int DEMANDE_CHOIX_APP = 101;

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
        });
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
}
