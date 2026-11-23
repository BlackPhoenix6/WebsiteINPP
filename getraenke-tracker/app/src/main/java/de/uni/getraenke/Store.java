package de.uni.getraenke;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Speichert alle Getränke und Einzahlungen lokal auf dem Handy. */
public final class Store {

    public static final int SPEZI = 0;
    public static final int STERNI = 1;
    public static final int MATE = 2;
    public static final String[] NAMES = {"Spezi", "Sterni", "Mate"};
    private static final int[] DEFAULT_PRICES = {100, 100, 150};

    private static final String PREFS = "getraenke";
    private static final String KEY_ENTRIES = "entries";

    /** Ein Eintrag: entweder ein Getränk (drink >= 0) oder eine Einzahlung (drink == -1). */
    public static final class Entry {
        public final long time;
        public final int drink;
        public final int cents;

        Entry(long time, int drink, int cents) {
            this.time = time;
            this.drink = drink;
            this.cents = cents;
        }

        public boolean isPayment() {
            return drink < 0;
        }

        public String label() {
            return isPayment() ? "Eingezahlt" : NAMES[drink];
        }
    }

    private Store() {}

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static int price(Context c, int drink) {
        return prefs(c).getInt("price_" + drink, DEFAULT_PRICES[drink]);
    }

    public static void setPrice(Context c, int drink, int cents) {
        prefs(c).edit().putInt("price_" + drink, cents).apply();
        DrinkWidget.updateAll(c);
    }

    public static synchronized List<Entry> entries(Context c) {
        List<Entry> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs(c).getString(KEY_ENTRIES, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                list.add(new Entry(o.getLong("t"), o.getInt("d"), o.getInt("c")));
            }
        } catch (JSONException ignored) {
            // kaputte Daten -> leere Liste
        }
        return list;
    }

    private static synchronized void save(Context c, List<Entry> list) {
        JSONArray arr = new JSONArray();
        try {
            for (Entry e : list) {
                arr.put(new JSONObject().put("t", e.time).put("d", e.drink).put("c", e.cents));
            }
        } catch (JSONException ignored) {
        }
        prefs(c).edit().putString(KEY_ENTRIES, arr.toString()).commit();
        DrinkWidget.updateAll(c);
    }

    private static synchronized void add(Context c, Entry e) {
        List<Entry> list = entries(c);
        list.add(e);
        save(c, list);
    }

    /** Ein Getränk zum aktuellen Preis buchen. Gibt den Preis in Cent zurück. */
    public static int addDrink(Context c, int drink) {
        int p = price(c, drink);
        add(c, new Entry(System.currentTimeMillis(), drink, p));
        return p;
    }

    public static void addPayment(Context c, int cents) {
        add(c, new Entry(System.currentTimeMillis(), -1, cents));
    }

    public static synchronized Entry removeLast(Context c) {
        List<Entry> list = entries(c);
        if (list.isEmpty()) return null;
        Entry e = list.remove(list.size() - 1);
        save(c, list);
        return e;
    }

    public static synchronized void remove(Context c, long time) {
        List<Entry> list = entries(c);
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).time == time) {
                list.remove(i);
                break;
            }
        }
        save(c, list);
    }

    public static void clear(Context c) {
        save(c, new ArrayList<>());
    }

    /** Zusammenfassung aller Einträge. */
    public static final class Summary {
        public int paid;       // eingezahlt (Cent)
        public int consumed;   // getrunken (Cent)
        public final int[] counts = new int[NAMES.length];

        /** positiv = Guthaben, negativ = Schulden */
        public int balance() {
            return paid - consumed;
        }
    }

    public static Summary summary(Context c) {
        Summary s = new Summary();
        for (Entry e : entries(c)) {
            if (e.isPayment()) {
                s.paid += e.cents;
            } else {
                s.consumed += e.cents;
                if (e.drink < s.counts.length) s.counts[e.drink]++;
            }
        }
        return s;
    }

    public static String euro(int cents) {
        return NumberFormat.getCurrencyInstance(Locale.GERMANY).format(cents / 100.0);
    }

    /** "3,50" oder "3.5" -> 350; ungültig -> -1 */
    public static int parseCents(String s) {
        if (s == null) return -1;
        s = s.trim().replace("€", "").replace(" ", "").replace(',', '.');
        if (s.isEmpty()) return -1;
        try {
            double v = Double.parseDouble(s);
            if (v < 0 || v > 100000) return -1;
            return (int) Math.round(v * 100);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
