package de.uni.getraenke;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Steuert den Mensa-Tab in der MainActivity. */
final class MensaTab {

    private static final int WEEKS = 8;
    private static final int RECENT = 3;

    private final Activity a;
    private final ExecutorService bg = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private final TextView month, stats, date, status, chartDetail;
    private final LinearLayout menu, history;
    private final Button slot0, slot1;
    private final BarChartView chart;

    private int dayOffset = 0;
    private boolean showAll = false;
    private int loadToken = 0;
    private Stats.Buckets buckets;

    MensaTab(Activity activity) {
        a = activity;
        month = a.findViewById(R.id.m_month);
        stats = a.findViewById(R.id.m_stats);
        date = a.findViewById(R.id.m_date);
        status = a.findViewById(R.id.m_status);
        menu = a.findViewById(R.id.m_menu);
        history = a.findViewById(R.id.m_history);
        slot0 = a.findViewById(R.id.m_slot0);
        slot1 = a.findViewById(R.id.m_slot1);
        chart = a.findViewById(R.id.m_chart);
        chartDetail = a.findViewById(R.id.m_chart_detail);

        slot0.setOnClickListener(v -> selectSlot(MensaApi.TU));
        slot1.setOnClickListener(v -> selectSlot(MensaApi.VEGGIE));
        a.findViewById(R.id.m_prev).setOnClickListener(v -> changeDay(-1));
        a.findViewById(R.id.m_next).setOnClickListener(v -> changeDay(1));
        a.findViewById(R.id.m_reload).setOnClickListener(v -> loadMenu(true));
        a.findViewById(R.id.m_settings).setOnClickListener(v -> settings());
        a.findViewById(R.id.m_custom).setOnClickListener(v -> askMeal("", -1));
        a.findViewById(R.id.m_undo).setOnClickListener(v -> {
            MensaStore.Meal m = MensaStore.removeLast(a);
            toast(m == null ? "Nichts zum Rückgängigmachen" : MensaWidget.shorten(m.name) + " entfernt");
            refreshSpending();
        });
        chart.setOnSelectListener(this::showWeek);
        a.findViewById(R.id.m_more).setOnClickListener(v -> {
            showAll = !showAll;
            refreshSpending();
        });
    }

    void onResume() {
        updateSlotButtons();
        refreshSpending();
        loadMenu(false);
    }

    void onDestroy() {
        bg.shutdownNow();
    }

    // ------------------------------------------------------------ Speiseplan

    private void selectSlot(int slot) {
        MensaStore.setAppSlot(a, slot);
        updateSlotButtons();
        loadMenu(false);
    }

    private void updateSlotButtons() {
        int slot = MensaStore.appSlot(a);
        slot0.setText(MensaApi.canteenName(a, MensaApi.TU));
        slot1.setText(MensaApi.canteenName(a, MensaApi.VEGGIE));
        style(slot0, slot == MensaApi.TU);
        style(slot1, slot == MensaApi.VEGGIE);
    }

    private void style(Button b, boolean active) {
        b.setBackgroundResource(active ? R.drawable.btn_pay : R.drawable.btn_neutral);
        b.setTextColor(active ? 0xFFFFFFFF : a.getColor(R.color.text));
    }

    private void changeDay(int d) {
        dayOffset = Math.max(-7, Math.min(14, dayOffset + d));
        loadMenu(false);
    }

    private void loadMenu(boolean force) {
        final int token = ++loadToken;
        final int slot = MensaStore.appSlot(a);
        final long day = MensaApi.dayOffset(dayOffset);
        String d = new SimpleDateFormat("EE dd.MM.", Locale.GERMANY).format(new Date(day));
        date.setText(dayOffset == 0 ? "Heute, " + d : dayOffset == 1 ? "Morgen, " + d
                : dayOffset == -1 ? "Gestern, " + d : d);

        MensaApi.Menu cached = force ? null : MensaApi.cachedMenu(a, slot, day);
        if (cached != null && !cached.meals.isEmpty()) render(cached, true);
        else {
            menu.removeAllViews();
        }
        showStatus("Speiseplan wird geladen …");
        bg.submit(() -> {
            MensaApi.Menu m = MensaApi.menu(a, slot, day, force);
            main.post(() -> {
                if (token != loadToken || a.isFinishing()) return;
                updateSlotButtons(); // Namen können sich nach der Suche geändert haben
                render(m, false);
            });
        });
    }

    private void render(MensaApi.Menu m, boolean fromCache) {
        menu.removeAllViews();
        if (m.error != null && m.meals.isEmpty()) {
            showStatus(m.error + "\nDu kannst Essen trotzdem unten selbst eintragen.");
            return;
        }
        if (m.meals.isEmpty()) {
            showStatus("Keine Gerichte für diesen Tag – geschlossen oder noch nicht veröffentlicht.");
            return;
        }
        if (m.stale) showStatus("Offline – zeige zuletzt geladenen Stand.");
        else if (!fromCache) showStatus(null);

        int group = MensaStore.priceGroup(a);
        String place = MensaApi.canteenName(a, MensaStore.appSlot(a));
        Map<String, List<MensaApi.Meal>> byCat = new LinkedHashMap<>();
        for (MensaApi.Meal meal : sortForDisplay(m.meals)) {
            String cat = meal.category.isEmpty() ? "Gerichte" : meal.category;
            if (!byCat.containsKey(cat)) byCat.put(cat, new ArrayList<>());
            byCat.get(cat).add(meal);
        }
        for (Map.Entry<String, List<MensaApi.Meal>> e : byCat.entrySet()) {
            TextView h = text(e.getKey(), 14, R.color.text_secondary);
            h.setTypeface(h.getTypeface(), android.graphics.Typeface.BOLD);
            h.setPadding(0, dp(16), 0, dp(6));
            menu.addView(h);
            for (MensaApi.Meal meal : e.getValue()) menu.addView(mealRow(meal, group, place));
        }
    }

    private View mealRow(MensaApi.Meal meal, int group, String place) {
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.card_bg);
        row.setPadding(dp(14), dp(10), dp(10), dp(10));

        LinearLayout left = new LinearLayout(a);
        left.setOrientation(LinearLayout.VERTICAL);
        left.addView(text(meal.name, 15, R.color.text));
        if (!meal.notes.isEmpty()) left.addView(text(meal.notes, 12, R.color.text_secondary));
        row.addView(left, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        int cents = meal.price(group);
        TextView price = text(cents >= 0 ? "+ " + Store.euro(cents) : "+ Preis?", 14, R.color.text);
        price.setTextColor(0xFFFFFFFF);
        price.setTypeface(price.getTypeface(), android.graphics.Typeface.BOLD);
        price.setBackgroundResource(R.drawable.btn_pay);
        price.setPadding(dp(12), dp(8), dp(12), dp(8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMarginStart(dp(10));
        row.addView(price, lp);

        View.OnClickListener add = v -> {
            if (cents < 0) {
                askMeal(meal.name, -1);
                return;
            }
            MensaStore.add(a, meal.name, place, cents);
            toast(MensaWidget.shorten(meal.name) + " eingetragen (" + Store.euro(cents) + ")");
            refreshSpending();
        };
        row.setOnClickListener(add);
        price.setOnClickListener(add);

        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rp.bottomMargin = dp(8);
        row.setLayoutParams(rp);
        return row;
    }

    /** Hauptgerichte zuerst, Beilagen/Desserts ans Ende. */
    static List<MensaApi.Meal> sortForDisplay(List<MensaApi.Meal> meals) {
        List<MensaApi.Meal> out = new ArrayList<>(meals);
        out.sort((x, y) -> Integer.compare(rank(x.category), rank(y.category)));
        return out;
    }

    private static int rank(String category) {
        String c = category == null ? "" : category.toLowerCase(Locale.GERMANY);
        if (c.contains("aktion") || c.contains("essen") || c.contains("haupt") || c.contains("gericht")) return 0;
        if (c.contains("suppe")) return 1;
        if (c.contains("salat")) return 2;
        if (c.contains("vorspeise")) return 3;
        if (c.contains("beilage")) return 5;
        if (c.contains("dessert") || c.contains("nachspeise")) return 6;
        return 4;
    }

    private void showStatus(String s) {
        status.setVisibility(s == null ? View.GONE : View.VISIBLE);
        if (s != null) status.setText(s);
    }

    // ------------------------------------------------------------ Ausgaben

    void refreshSpending() {
        List<MensaStore.Meal> list = MensaStore.entries(a);
        long now = System.currentTimeMillis();
        long weekStart = Stats.periodStart(now, Stats.WEEK).getTimeInMillis();
        long monthStart = Stats.periodStart(now, Stats.MONTH).getTimeInMillis();
        java.util.Calendar t = java.util.Calendar.getInstance();
        t.set(java.util.Calendar.HOUR_OF_DAY, 0);
        t.set(java.util.Calendar.MINUTE, 0);
        t.set(java.util.Calendar.SECOND, 0);
        t.set(java.util.Calendar.MILLISECOND, 0);
        long todayStart = t.getTimeInMillis();

        int m = MensaStore.spent(list, monthStart, Long.MAX_VALUE);
        int w = MensaStore.spent(list, weekStart, Long.MAX_VALUE);
        int d = MensaStore.spent(list, todayStart, Long.MAX_VALUE);
        int total = MensaStore.spent(list, 0, Long.MAX_VALUE);
        month.setText(Store.euro(m));
        String s = "Heute: " + Store.euro(d) + " · Diese Woche: " + Store.euro(w);
        if (!list.isEmpty()) {
            s += "\nØ pro Essen: " + Store.euro(Math.round(total / (float) list.size()))
                    + " · " + list.size() + " Einträge insgesamt";
            s += "\nPreise: " + MensaApi.GROUP_NAMES[MensaStore.priceGroup(a)];
        }
        stats.setText(s);

        List<Store.Entry> entries = MensaStore.asEntries(list);
        buckets = Stats.buckets(entries, Stats.WEEK, WEEKS, now);
        long first = Stats.firstDrink(entries);
        float avg = -1;
        if (first >= 0) {
            int from = 0;
            while (from < WEEKS - 1 && buckets.starts[from + 1] <= first) from++;
            int sum = 0;
            for (int i = from; i < WEEKS; i++) sum += buckets.total(i);
            avg = (float) sum / (WEEKS - from);
        }
        chart.setData(buckets.labels, new int[][]{buckets.cents[0]}, new int[]{a.getColor(R.color.pay)}, -1, avg);
        chartDetail.setTag(avg);
        showWeek(-1);

        history.removeAllViews();
        if (list.isEmpty()) {
            TextView empty = text("Noch nichts eingetragen", 14, R.color.text_secondary);
            empty.setPadding(0, dp(14), 0, dp(14));
            history.addView(empty);
            a.findViewById(R.id.m_more).setVisibility(View.GONE);
            return;
        }
        SimpleDateFormat fmt = new SimpleDateFormat("EE dd.MM. HH:mm", Locale.GERMANY);
        int limit = showAll ? 200 : RECENT;
        int shown = 0;
        for (int i = list.size() - 1; i >= 0 && shown < limit; i--, shown++) {
            history.addView(historyRow(list.get(i), fmt, shown > 0));
        }
        Button more = a.findViewById(R.id.m_more);
        more.setVisibility(list.size() > RECENT ? View.VISIBLE : View.GONE);
        more.setText(showAll ? "Weniger anzeigen" : "Alle anzeigen (" + list.size() + ")");
    }

    private void showWeek(int i) {
        if (buckets == null) return;
        if (i < 0) {
            Object avg = chartDetail.getTag();
            float v = avg instanceof Float ? (Float) avg : -1;
            chartDetail.setText(v > 0 ? "- - -  Ø " + Store.euro(Math.round(v)) + " pro Woche · Balken antippen"
                    : "Noch keine Daten");
            return;
        }
        chartDetail.setText(buckets.longLabels[i] + ": " + Store.euro(buckets.total(i))
                + " · " + buckets.counts[0][i] + " Essen");
    }

    private View historyRow(MensaStore.Meal m, SimpleDateFormat fmt, boolean divider) {
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(12), 0, dp(12));
        LinearLayout left = new LinearLayout(a);
        left.setOrientation(LinearLayout.VERTICAL);
        TextView name = text(m.name, 15, R.color.text);
        name.setMaxLines(2);
        left.addView(name);
        String where = m.place == null || m.place.isEmpty() ? "" : " · " + m.place;
        left.addView(text(fmt.format(new Date(m.time)) + where, 12, R.color.text_secondary));
        row.addView(left, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        TextView amount = text(Store.euro(m.cents), 15, R.color.text);
        amount.setPadding(dp(10), 0, 0, 0);
        row.addView(amount);
        row.setOnClickListener(v -> new AlertDialog.Builder(a)
                .setTitle("Eintrag löschen?")
                .setMessage(m.name + "\n" + Store.euro(m.cents) + " · " + fmt.format(new Date(m.time)))
                .setPositiveButton("Löschen", (dlg, w) -> {
                    MensaStore.remove(a, m.time);
                    refreshSpending();
                })
                .setNegativeButton("Abbrechen", null)
                .show());
        if (!divider) return row;
        LinearLayout wrap = new LinearLayout(a);
        wrap.setOrientation(LinearLayout.VERTICAL);
        View line = new View(a);
        line.setBackgroundColor(a.getColor(R.color.divider));
        wrap.addView(line, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
        wrap.addView(row);
        return wrap;
    }

    // ------------------------------------------------------------ Dialoge

    private void askMeal(String presetName, int presetCents) {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(24), dp(8), dp(24), 0);
        EditText name = new EditText(a);
        name.setHint("Was gab's? (z. B. Currywurst)");
        name.setText(presetName);
        EditText price = new EditText(a);
        price.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        price.setHint("Preis, z. B. 2,85");
        if (presetCents >= 0) price.setText(String.format(Locale.GERMANY, "%.2f", presetCents / 100.0));
        box.addView(name);
        box.addView(price);
        new AlertDialog.Builder(a)
                .setTitle("Essen eintragen")
                .setView(box)
                .setPositiveButton("Eintragen", (d, w) -> {
                    int c = Store.parseCents(price.getText().toString());
                    String n = name.getText().toString().trim();
                    if (c <= 0) {
                        toast("Ungültiger Preis");
                        return;
                    }
                    if (n.isEmpty()) n = "Mensa-Essen";
                    MensaStore.add(a, n, MensaApi.canteenName(a, MensaStore.appSlot(a)), c);
                    toast(MensaWidget.shorten(n) + " eingetragen (" + Store.euro(c) + ")");
                    refreshSpending();
                })
                .setNegativeButton("Abbrechen", null)
                .show();
        (presetName.isEmpty() ? name : price).requestFocus();
    }

    private void settings() {
        String[] items = {
                "Preisgruppe: " + MensaApi.GROUP_NAMES[MensaStore.priceGroup(a)],
                "Linke Mensa wählen (" + MensaApi.canteenName(a, MensaApi.TU) + ")",
                "Rechte Mensa wählen (" + MensaApi.canteenName(a, MensaApi.VEGGIE) + ")"
        };
        new AlertDialog.Builder(a)
                .setTitle("Mensa-Einstellungen")
                .setItems(items, (d, which) -> {
                    if (which == 0) pickGroup();
                    else pickCanteen(which - 1);
                })
                .show();
    }

    private void pickGroup() {
        new AlertDialog.Builder(a)
                .setTitle("Welche Preise gelten für dich?")
                .setSingleChoiceItems(MensaApi.GROUP_NAMES, MensaStore.priceGroup(a), (d, which) -> {
                    MensaStore.setPriceGroup(a, which);
                    d.dismiss();
                    refreshSpending();
                    loadMenu(false);
                })
                .show();
    }

    private void pickCanteen(int slot) {
        toast("Suche Mensen in der Nähe …");
        bg.submit(() -> {
            List<MensaApi.Canteen> list;
            String error = null;
            try {
                list = MensaApi.nearbyCanteens();
            } catch (Exception e) {
                list = new ArrayList<>();
                error = e.getMessage();
            }
            final List<MensaApi.Canteen> found = list;
            final String err = error;
            main.post(() -> {
                if (a.isFinishing()) return;
                if (found.isEmpty()) {
                    toast("Keine Mensen gefunden" + (err != null ? " (" + err + ")" : ""));
                    return;
                }
                String[] names = new String[found.size()];
                for (int i = 0; i < names.length; i++) names[i] = found.get(i).shortName();
                new AlertDialog.Builder(a)
                        .setTitle("Mensa wählen")
                        .setItems(names, (d, which) -> {
                            MensaApi.setCanteen(a, slot, found.get(which));
                            MensaStore.setAppSlot(a, slot);
                            updateSlotButtons();
                            loadMenu(true);
                            MensaWidget.updateAll(a);
                        })
                        .show();
            });
        });
    }

    // ------------------------------------------------------------ Helfer

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(a);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(a.getColor(color));
        return t;
    }

    private int dp(int v) {
        return Math.round(v * a.getResources().getDisplayMetrics().density);
    }

    private void toast(String s) {
        Toast.makeText(a, s, Toast.LENGTH_SHORT).show();
    }
}
