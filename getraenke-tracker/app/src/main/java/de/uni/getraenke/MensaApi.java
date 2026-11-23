package de.uni.getraenke;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Speisepläne über die offene OpenMensa-API (https://openmensa.org).
 * Netzwerk-Methoden blockieren – nur im Hintergrund aufrufen.
 */
public final class MensaApi {

    private static final String BASE = "https://openmensa.org/api/v2/";
    // TU Berlin, Hauptgebäude / Hardenbergstraße
    private static final String NEAR = "canteens?near%5Blat%5D=52.5125&near%5Blng%5D=13.3266&near%5Bdist%5D=3&limit=100";
    private static final long MAX_AGE = 6L * 60 * 60 * 1000;

    public static final int TU = 0;
    public static final int VEGGIE = 1;
    public static final String[] SLOT_NAMES = {"TU Hardenberg", "Veggie 2.0"};

    public static final String[] GROUP_KEYS = {"students", "employees", "others"};
    public static final String[] GROUP_NAMES = {"Studierende", "Beschäftigte", "Gäste"};

    private MensaApi() {}

    public static final class Canteen {
        public final int id;
        public final String name;

        Canteen(int id, String name) {
            this.id = id;
            this.name = name;
        }

        public String shortName() {
            return name.replaceFirst("^Berlin,\\s*", "");
        }
    }

    public static final class Meal {
        public String name;
        public String category;
        public String notes;
        public final int[] prices = {-1, -1, -1}; // Cent je Preisgruppe, -1 = unbekannt

        public int price(int group) {
            return prices[group];
        }
    }

    /** Ergebnis eines Abrufs: Gerichte (evtl. leer) + ggf. Fehlermeldung. */
    public static final class Menu {
        public final List<Meal> meals = new ArrayList<>();
        public String error;      // gesetzt, wenn nicht geladen werden konnte
        public boolean stale;     // aus altem Cache
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("mensa_cache", Context.MODE_PRIVATE);
    }

    public static String dateKey(long time) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY).format(new Date(time));
    }

    public static long dayOffset(int days) {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_MONTH, days);
        return c.getTimeInMillis();
    }

    // ------------------------------------------------------------ HTTP

    static String get(String path) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(BASE + path).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(15000);
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("User-Agent", "GetraenkeTracker-Android/1.3");
        try {
            int code = c.getResponseCode();
            if (code == 404) return null;
            if (code != 200) throw new IOException("Server antwortet mit " + code);
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                return out.toString(StandardCharsets.UTF_8.name());
            }
        } finally {
            c.disconnect();
        }
    }

    // ------------------------------------------------------------ Mensen

    public static List<Canteen> nearbyCanteens() throws IOException, JSONException {
        String body = get(NEAR);
        List<Canteen> list = new ArrayList<>();
        if (body == null) return list;
        JSONArray arr = new JSONArray(body);
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            list.add(new Canteen(o.getInt("id"), o.optString("name", "Mensa " + o.getInt("id"))));
        }
        return list;
    }

    public static int canteenId(Context c, int slot) {
        return prefs(c).getInt("id_" + slot, -1);
    }

    public static String canteenName(Context c, int slot) {
        return prefs(c).getString("name_" + slot, SLOT_NAMES[slot]);
    }

    public static void setCanteen(Context c, int slot, Canteen canteen) {
        prefs(c).edit().putInt("id_" + slot, canteen.id).putString("name_" + slot, canteen.shortName()).apply();
    }

    /** Findet TU Hardenbergstraße und die vegane Mensa automatisch, falls noch nicht bekannt. */
    public static void resolveCanteens(Context c) throws IOException, JSONException {
        if (canteenId(c, TU) > 0 && canteenId(c, VEGGIE) > 0) return;
        Canteen[] found = pick(nearbyCanteens());
        for (int slot = 0; slot < found.length; slot++) {
            if (found[slot] != null && canteenId(c, slot) <= 0) setCanteen(c, slot, found[slot]);
        }
    }

    /** [TU Hardenbergstraße, vegane Mensa] aus einer Liste heraussuchen (null = nicht gefunden). */
    static Canteen[] pick(List<Canteen> list) {
        Canteen[] out = new Canteen[2];
        for (Canteen k : list) {
            String n = " " + k.name.toLowerCase(Locale.GERMANY).replaceAll("[^a-zäöüß0-9.]+", " ") + " ";
            boolean vegan = n.contains("veggie") || n.contains("vegan");
            boolean cafe = n.contains("cafe") || n.contains("café") || n.contains("coffee");
            if (vegan && !cafe) {
                if (out[VEGGIE] == null) out[VEGGIE] = k;
            } else if (!cafe && n.contains("hardenberg") && (n.contains(" tu ") || n.contains("technische"))) {
                if (out[TU] == null) out[TU] = k;
            }
        }
        return out;
    }

    // ------------------------------------------------------------ Gerichte

    /** Speiseplan für Mensa-Slot und Tag; nutzt einen Cache (max. 6 h alt). */
    public static Menu menu(Context c, int slot, long day, boolean force) {
        Menu m = new Menu();
        String date = dateKey(day);
        try {
            resolveCanteens(c);
        } catch (Exception e) {
            // weiter unten mit Cache versuchen
        }
        int id = canteenId(c, slot);
        String key = "menu_" + id + "_" + date;
        SharedPreferences p = prefs(c);
        String cached = p.getString(key, null);
        long fetched = p.getLong(key + "_t", 0);

        if (id <= 0) {
            m.error = "Mensa nicht gefunden – bitte in den Mensa-Einstellungen auswählen.";
            return m;
        }
        if (cached != null && !force && System.currentTimeMillis() - fetched < MAX_AGE) {
            parse(cached, m);
            return m;
        }
        try {
            String body = get("canteens/" + id + "/days/" + date + "/meals");
            if (body == null) body = "[]";
            p.edit().putString(key, body).putLong(key + "_t", System.currentTimeMillis()).apply();
            cleanup(p);
            parse(body, m);
        } catch (Exception e) {
            if (cached != null) {
                parse(cached, m);
                m.stale = true;
            } else {
                m.error = "Speiseplan konnte nicht geladen werden (" + e.getMessage() + ").";
            }
        }
        return m;
    }

    /** Nur aus dem Cache (für schnelle Anzeige ohne Netz). */
    public static Menu cachedMenu(Context c, int slot, long day) {
        Menu m = new Menu();
        String cached = prefs(c).getString("menu_" + canteenId(c, slot) + "_" + dateKey(day), null);
        if (cached == null) return null;
        parse(cached, m);
        return m;
    }

    static void parse(String body, Menu m) {
        try {
            JSONArray arr = new JSONArray(body);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Meal meal = new Meal();
                meal.name = o.optString("name", "?");
                meal.category = o.optString("category", "");
                JSONArray notes = o.optJSONArray("notes");
                StringBuilder sb = new StringBuilder();
                if (notes != null) {
                    for (int j = 0; j < notes.length() && j < 4; j++) {
                        if (sb.length() > 0) sb.append(" · ");
                        sb.append(notes.optString(j));
                    }
                }
                meal.notes = sb.toString();
                JSONObject prices = o.optJSONObject("prices");
                if (prices != null) {
                    for (int g = 0; g < GROUP_KEYS.length; g++) {
                        if (!prices.isNull(GROUP_KEYS[g])) {
                            double v = prices.optDouble(GROUP_KEYS[g], -1);
                            if (v >= 0) meal.prices[g] = (int) Math.round(v * 100);
                        }
                    }
                }
                m.meals.add(meal);
            }
        } catch (JSONException e) {
            m.error = "Unerwartetes Datenformat vom Server.";
        }
    }

    /** Alte Tage aus dem Cache werfen. */
    private static void cleanup(SharedPreferences p) {
        String limit = dateKey(dayOffset(-3));
        SharedPreferences.Editor ed = p.edit();
        for (String k : p.getAll().keySet()) {
            if (k.startsWith("menu_")) {
                String d = k.replaceFirst("^menu_-?\\d+_", "").replace("_t", "");
                if (d.compareTo(limit) < 0) ed.remove(k);
            }
        }
        ed.apply();
    }
}
