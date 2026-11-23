package de.uni.getraenke;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Mensa-Ausgaben (getrennt von der Getränkekasse). */
public final class MensaStore {

    private static final String PREFS = "mensa";

    public static final class Meal {
        public final long time;
        public final String name;
        public final String place;
        public final int cents;

        Meal(long time, String name, String place, int cents) {
            this.time = time;
            this.name = name;
            this.place = place;
            this.cents = cents;
        }
    }

    private MensaStore() {}

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static synchronized List<Meal> entries(Context c) {
        List<Meal> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs(c).getString("entries", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                list.add(new Meal(o.getLong("t"), o.optString("n"), o.optString("m"), o.getInt("c")));
            }
        } catch (JSONException ignored) {
        }
        return list;
    }

    private static synchronized void save(Context c, List<Meal> list) {
        JSONArray arr = new JSONArray();
        try {
            for (Meal m : list) {
                arr.put(new JSONObject().put("t", m.time).put("n", m.name).put("m", m.place).put("c", m.cents));
            }
        } catch (JSONException ignored) {
        }
        prefs(c).edit().putString("entries", arr.toString()).commit();
        MensaWidget.updateAll(c);
    }

    public static synchronized void add(Context c, String name, String place, int cents) {
        List<Meal> list = entries(c);
        list.add(new Meal(System.currentTimeMillis(), name, place, cents));
        save(c, list);
    }

    public static synchronized Meal removeLast(Context c) {
        List<Meal> list = entries(c);
        if (list.isEmpty()) return null;
        Meal m = list.remove(list.size() - 1);
        save(c, list);
        return m;
    }

    public static synchronized void remove(Context c, long time) {
        List<Meal> list = entries(c);
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).time == time) {
                list.remove(i);
                break;
            }
        }
        save(c, list);
    }

    /** Summe der Ausgaben im Zeitraum [from, to). */
    public static int spent(List<Meal> list, long from, long to) {
        int s = 0;
        for (Meal m : list) if (m.time >= from && m.time < to) s += m.cents;
        return s;
    }

    /** Als Store.Entry-Liste, damit Stats/Diagramm wiederverwendet werden können. */
    public static List<Store.Entry> asEntries(List<Meal> list) {
        List<Store.Entry> out = new ArrayList<>();
        for (Meal m : list) out.add(new Store.Entry(m.time, 0, m.cents));
        return out;
    }

    // ------------------------------------------------------------ Einstellungen

    public static int priceGroup(Context c) {
        return prefs(c).getInt("group", 0);
    }

    public static void setPriceGroup(Context c, int g) {
        prefs(c).edit().putInt("group", g).apply();
        MensaWidget.updateAll(c);
    }

    public static int widgetSlot(Context c) {
        return prefs(c).getInt("widget_slot", MensaApi.TU);
    }

    public static void setWidgetSlot(Context c, int slot) {
        prefs(c).edit().putInt("widget_slot", slot).apply();
    }

    public static int appSlot(Context c) {
        return prefs(c).getInt("app_slot", MensaApi.TU);
    }

    public static void setAppSlot(Context c, int slot) {
        prefs(c).edit().putInt("app_slot", slot).apply();
    }
}
