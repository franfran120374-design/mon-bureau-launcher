package com.monbureau.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.provider.MediaStore;
import android.provider.Settings;
import android.widget.ImageButton;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Gere la barre de raccourcis en bas de l'ecran d'accueil.
 *
 * Chaque emplacement (0 a 3) est associe a une action : appuyer dessus
 * l'execute, rester appuye 0,6 s ouvre un choix pour le remplacer par
 * une autre action predefinie ou par n'importe quelle application
 * installee. Le choix est sauvegarde sur l'appareil (SharedPreferences)
 * et survit aux redemarrages — jamais besoin de recompiler l'appli
 * pour changer un raccourci.
 */
public class DockManager {

    private static final String PREFS = "mon_bureau_dock_prefs";
    private static final String CLE_SLOT = "dock_slot_";
    public static final int REQUEST_CODE_BASE = 4200; // 4200..4203 pour les 4 emplacements

    private final Activity activity;
    private final ImageButton[] boutons;
    private final SharedPreferences prefs;

    // Actions predefinies proposees dans le menu de choix.
    private enum Action {
        TELEPHONE("telephone", "Téléphone", R.drawable.ic_phone) {
            void executer(Activity a) {
                a.startActivity(new Intent(Intent.ACTION_DIAL));
            }
        },
        SMS("sms", "Messages", R.drawable.ic_sms) {
            void executer(Activity a) {
                Intent i = new Intent(Intent.ACTION_MAIN);
                i.addCategory(Intent.CATEGORY_APP_MESSAGING);
                if (i.resolveActivity(a.getPackageManager()) != null) {
                    a.startActivity(i);
                } else {
                    a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("sms:")));
                }
            }
        },
        CAMERA("camera", "Appareil photo", R.drawable.ic_camera) {
            void executer(Activity a) {
                a.startActivity(new Intent(MediaStore.ACTION_IMAGE_CAPTURE));
            }
        },
        CONTACTS("contacts", "Contacts", R.drawable.ic_contacts) {
            void executer(Activity a) {
                Intent i = new Intent(Intent.ACTION_MAIN);
                i.addCategory(Intent.CATEGORY_APP_CONTACTS);
                if (i.resolveActivity(a.getPackageManager()) != null) {
                    a.startActivity(i);
                } else {
                    Toast.makeText(a, "Aucune appli de contacts trouvée", Toast.LENGTH_SHORT).show();
                }
            }
        },
        NAVIGATEUR("navigateur", "Navigateur", R.drawable.ic_browser) {
            void executer(Activity a) {
                a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://")));
            }
        },
        PARAMETRES("parametres", "Paramètres", R.drawable.ic_settings) {
            void executer(Activity a) {
                a.startActivity(new Intent(Settings.ACTION_SETTINGS));
            }
        };

        final String id;
        final String label;
        final int icone;

        Action(String id, String label, int icone) {
            this.id = id;
            this.label = label;
            this.icone = icone;
        }

        abstract void executer(Activity a);

        static Action parId(String id) {
            for (Action a : values()) if (a.id.equals(id)) return a;
            return TELEPHONE;
        }
    }

    // Attribution par defaut des 4 emplacements, dans l'ordre.
    private static final Action[] DEFAUTS = { Action.TELEPHONE, Action.SMS, Action.CAMERA, Action.PARAMETRES };

    public DockManager(Activity activity, ImageButton[] boutons) {
        this.activity = activity;
        this.boutons = boutons;
        this.prefs = activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE);
        for (int i = 0; i < boutons.length; i++) {
            rafraichirBouton(i);
            final int slot = i;
            boutons[i].setOnClickListener(v -> executerSlot(slot));
            boutons[i].setOnLongClickListener(v -> { proposerChoix(slot); return true; });
        }
    }

    private void rafraichirBouton(int slot) {
        String valeur = prefs.getString(cleSlot(slot), null);
        if (valeur != null && valeur.startsWith("app:")) {
            // Une application installee a ete choisie : on ne peut pas
            // toujours retrouver son icone facilement ici sans re-scanner
            // la liste, donc on affiche l'icone generique du tiroir.
            boutons[slot].setImageResource(R.drawable.ic_apps);
        } else {
            Action a = (valeur != null) ? Action.parId(valeur) : DEFAUTS[slot % DEFAUTS.length];
            boutons[slot].setImageResource(a.icone);
        }
    }

    private String cleSlot(int slot) {
        return CLE_SLOT + slot;
    }

    private void executerSlot(int slot) {
        String valeur = prefs.getString(cleSlot(slot), null);
        try {
            if (valeur != null && valeur.startsWith("app:")) {
                String paquet = valeur.substring(4);
                Intent i = activity.getPackageManager().getLaunchIntentForPackage(paquet);
                if (i != null) {
                    activity.startActivity(i);
                } else {
                    Toast.makeText(activity, "Application introuvable — choisis-en une autre (appui long)", Toast.LENGTH_LONG).show();
                }
            } else {
                Action a = (valeur != null) ? Action.parId(valeur) : DEFAUTS[slot % DEFAUTS.length];
                a.executer(activity);
            }
        } catch (Exception e) {
            Toast.makeText(activity, "Impossible d'ouvrir ce raccourci", Toast.LENGTH_SHORT).show();
        }
    }

    private void proposerChoix(int slot) {
        List<String> libelles = new ArrayList<>();
        for (Action a : Action.values()) libelles.add(a.label);
        libelles.add("Choisir une application installée…");

        new AlertDialog.Builder(activity)
                .setTitle("Ce raccourci ouvre :")
                .setItems(libelles.toArray(new String[0]), (dialog, which) -> {
                    if (which < Action.values().length) {
                        Action choisi = Action.values()[which];
                        prefs.edit().putString(cleSlot(slot), choisi.id).apply();
                        rafraichirBouton(slot);
                        Toast.makeText(activity, choisi.label + " assigné", Toast.LENGTH_SHORT).show();
                    } else {
                        Intent i = new Intent(activity, AppDrawerActivity.class);
                        i.putExtra(AppDrawerActivity.EXTRA_MODE_CHOIX, true);
                        activity.startActivityForResult(i, REQUEST_CODE_BASE + slot);
                    }
                })
                .show();
    }

    /** A appeler depuis onActivityResult() de l'activite hote. */
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK || data == null) return;
        int slot = requestCode - REQUEST_CODE_BASE;
        if (slot < 0 || slot >= boutons.length) return;
        String paquet = data.getStringExtra(AppDrawerActivity.EXTRA_PACKAGE_CHOISI);
        if (paquet == null) return;
        prefs.edit().putString(cleSlot(slot), "app:" + paquet).apply();
        rafraichirBouton(slot);
        Toast.makeText(activity, "Raccourci mis à jour", Toast.LENGTH_SHORT).show();
    }
}
