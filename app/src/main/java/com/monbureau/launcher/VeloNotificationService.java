package com.monbureau.launcher;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * VeloNotificationService
 * =======================
 * Ecoute les notifications affichees par les AUTRES applications du telephone.
 *
 * Objectif : l'application officielle veloToulouse previent a chaque debut et
 * chaque fin de trajet. En lisant ces notifications, Mon Bureau demarre et
 * arrete son chronometre tout seul, sans jamais connaitre ton mot de passe.
 *
 * Deux modes :
 *
 *  1. APPRENTISSAGE (par defaut, tant qu'aucune application n'est designee)
 *     Le service note les 60 dernieres notifications recues, toutes
 *     applications confondues. Mon Bureau te les affiche pour que tu
 *     designes celle de veloToulouse d'un simple clic.
 *
 *  2. SURVEILLANCE (une fois l'application designee)
 *     Seules les notifications de cette application sont analysees. Selon
 *     les mots reperes dans le texte, un evenement DEBUT ou FIN est depose
 *     dans une file d'attente que la page web vient relever.
 *
 * Rien n'est envoye sur Internet : tout reste dans les preferences locales
 * de l'application, sur ton telephone.
 *
 * IMPORTANT : Android n'active JAMAIS ce service tout seul. Il faut
 * l'autoriser une fois a la main dans
 * Parametres > Applications > Acces speciaux > Acces aux notifications.
 * Mon Bureau propose un bouton qui ouvre directement cet ecran.
 */
public class VeloNotificationService extends NotificationListenerService {

    public static final String PREFS = "mon_bureau_velo";

    // Cle : nom technique de l'application veloToulouse, une fois designee
    public static final String CLE_PACKAGE = "velo_package";
    // Cle : journal des notifications observees (mode apprentissage)
    public static final String CLE_JOURNAL = "velo_journal";
    // Cle : file des evenements DEBUT / FIN en attente de lecture par le site
    public static final String CLE_EVENTS = "velo_events";
    // Cles : mots declencheurs, modifiables depuis Mon Bureau
    public static final String CLE_MOTS_DEBUT = "velo_mots_debut";
    public static final String CLE_MOTS_FIN = "velo_mots_fin";

    // Valeurs de depart. Les textes exacts de l'application officielle ne sont
    // pas connus a l'avance : ces listes sont larges, et ajustables depuis
    // l'ecran de reglages de la tuile velo.
    public static final String MOTS_DEBUT_DEFAUT =
            "debut de trajet|trajet en cours|location en cours|velo deverrouille|"
          + "bonne route|velo emprunte|deverrouillage|trajet demarre";

    public static final String MOTS_FIN_DEFAUT =
            "fin de trajet|trajet termine|bien restitue|restitution|velo restitue|"
          + "trajet fini|merci d avoir|velo rendu|bonne restitution";

    private static final int JOURNAL_MAX = 60;
    private static final int EVENTS_MAX = 30;
    // Deux notifications identiques a moins de 20 s d'ecart : on ignore la seconde
    private static final long ANTI_DOUBLON_MS = 20000;

    /**
     * Nom technique de l'application officielle veloToulouse sur Android,
     * releve sur sa fiche Google Play. Il sert de valeur de depart : le
     * service surveille donc la bonne application des la premiere ouverture,
     * sans passer par la phase d'apprentissage. Si JCDecaux renommait un jour
     * son application, l'ecran de reglages permet toujours d'en designer
     * une autre a la main.
     */
    public static final String PACKAGE_DEFAUT = "com.jcdecaux.vls.toulouse";

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        try {
            if (sbn == null || sbn.getNotification() == null) return;

            final String paquet = sbn.getPackageName();
            if (paquet == null) return;
            // On ignore nos propres notifications
            if (paquet.equals(getPackageName())) return;

            Bundle extras = sbn.getNotification().extras;
            String titre = texte(extras, "android.title");
            String corps = texte(extras, "android.text");
            if (corps == null || corps.isEmpty()) corps = texte(extras, "android.bigText");
            if (titre == null) titre = "";
            if (corps == null) corps = "";

            long quand = System.currentTimeMillis();

            noterDansJournal(paquet, titre, corps, quand);

            String cible = prefs().getString(CLE_PACKAGE, PACKAGE_DEFAUT);
            if (cible == null || cible.isEmpty()) return;      // surveillance desactivee a la main
            if (!paquet.equals(cible)) return;                  // autre application

            String phrase = normaliser(titre + " " + corps);

            String motsFin = prefs().getString(CLE_MOTS_FIN, MOTS_FIN_DEFAUT);
            String motsDebut = prefs().getString(CLE_MOTS_DEBUT, MOTS_DEBUT_DEFAUT);

            // La fin est testee en premier : "fin de trajet" contient le mot
            // "trajet", qui pourrait aussi declencher un faux depart.
            if (contientUnDe(phrase, motsFin)) {
                deposerEvenement("FIN", titre, corps, quand);
            } else if (contientUnDe(phrase, motsDebut)) {
                deposerEvenement("DEBUT", titre, corps, quand);
            }
        } catch (Throwable t) {
            // Un service de notification qui plante est desactive par Android.
            // On avale donc toute erreur plutot que de risquer la coupure.
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        // Sans objet : le retrait d'une notification ne signifie rien pour nous.
    }

    // ------------------------------------------------------------ Journal

    private void noterDansJournal(String paquet, String titre, String corps, long quand) {
        try {
            JSONArray journal = new JSONArray(prefs().getString(CLE_JOURNAL, "[]"));

            // Anti-doublon : meme application, meme texte, il y a moins de 20 s
            for (int i = journal.length() - 1; i >= 0 && i >= journal.length() - 5; i--) {
                JSONObject o = journal.getJSONObject(i);
                if (o.optString("pkg").equals(paquet)
                        && o.optString("texte").equals(corps)
                        && quand - o.optLong("t") < ANTI_DOUBLON_MS) {
                    return;
                }
            }

            JSONObject o = new JSONObject();
            o.put("pkg", paquet);
            o.put("app", nomLisible(paquet));
            o.put("titre", titre);
            o.put("texte", corps);
            o.put("t", quand);
            journal.put(o);

            while (journal.length() > JOURNAL_MAX) journal.remove(0);
            prefs().edit().putString(CLE_JOURNAL, journal.toString()).apply();
        } catch (Throwable ignored) {
        }
    }

    // ---------------------------------------------------------- Evenements

    private void deposerEvenement(String type, String titre, String corps, long quand) {
        try {
            JSONArray events = new JSONArray(prefs().getString(CLE_EVENTS, "[]"));

            // Deux evenements du meme type coup sur coup : on garde le premier
            if (events.length() > 0) {
                JSONObject dernier = events.getJSONObject(events.length() - 1);
                if (dernier.optString("type").equals(type)
                        && quand - dernier.optLong("t") < ANTI_DOUBLON_MS) {
                    return;
                }
            }

            JSONObject o = new JSONObject();
            o.put("type", type);
            o.put("titre", titre);
            o.put("texte", corps);
            o.put("t", quand);
            events.put(o);

            while (events.length() > EVENTS_MAX) events.remove(0);
            prefs().edit().putString(CLE_EVENTS, events.toString()).apply();
        } catch (Throwable ignored) {
        }
    }

    // -------------------------------------------------------------- Outils

    private static String texte(Bundle extras, String cle) {
        if (extras == null) return "";
        CharSequence cs = extras.getCharSequence(cle);
        return cs == null ? "" : cs.toString();
    }

    /**
     * Minuscules, sans accents, sans ponctuation : la comparaison devient
     * insensible a "Restitue" / "restitué" / "RESTITUE".
     */
    private static String normaliser(String s) {
        if (s == null) return "";
        String r = s.toLowerCase();
        r = r.replace('à', 'a').replace('â', 'a').replace('ä', 'a')
             .replace('é', 'e').replace('è', 'e').replace('ê', 'e').replace('ë', 'e')
             .replace('î', 'i').replace('ï', 'i')
             .replace('ô', 'o').replace('ö', 'o')
             .replace('ù', 'u').replace('û', 'u').replace('ü', 'u')
             .replace('ç', 'c');
        r = r.replaceAll("[^a-z0-9 ]", " ");
        return r.replaceAll("\\s+", " ").trim();
    }

    /** motsSepares : liste de phrases separees par des barres verticales. */
    private static boolean contientUnDe(String phrase, String motsSepares) {
        if (motsSepares == null) return false;
        for (String mot : motsSepares.split("\\|")) {
            String m = normaliser(mot);
            if (!m.isEmpty() && phrase.contains(m)) return true;
        }
        return false;
    }

    private String nomLisible(String paquet) {
        try {
            return getPackageManager()
                    .getApplicationLabel(getPackageManager().getApplicationInfo(paquet, 0))
                    .toString();
        } catch (Throwable t) {
            return paquet;
        }
    }
}
