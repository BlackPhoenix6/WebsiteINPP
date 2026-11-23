package de.uni.getraenke;

import android.app.Activity;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class StatsActivity extends Activity {

    private static final int WEEKS = 12;
    private static final int MONTHS = 12;
    private static final long DAY = 24L * 60 * 60 * 1000;

    private int mode = Stats.WEEK;
    private List<Store.Entry> entries = new ArrayList<>();
    private Stats.Buckets buckets;
    private int[] drinkColors;

    private BarChartView chart;
    private TextView chartTitle, chartDetail;
    private Button weekBtn, monthBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stats);
        if (savedInstanceState != null) mode = savedInstanceState.getInt("mode", Stats.WEEK);

        drinkColors = new int[]{getColor(R.color.spezi), getColor(R.color.sterni), getColor(R.color.mate)};
        chart = findViewById(R.id.chart);
        chartTitle = findViewById(R.id.chart_title);
        chartDetail = findViewById(R.id.chart_detail);
        weekBtn = findViewById(R.id.mode_week);
        monthBtn = findViewById(R.id.mode_month);

        findViewById(R.id.back).setOnClickListener(v -> finish());
        weekBtn.setOnClickListener(v -> setMode(Stats.WEEK));
        monthBtn.setOnClickListener(v -> setMode(Stats.MONTH));
        chart.setOnSelectListener(this::showDetail);

        buildLegend();
    }

    @Override
    protected void onResume() {
        super.onResume();
        entries = Store.entries(this);
        boolean empty = Stats.firstDrink(entries) < 0;
        findViewById(R.id.empty).setVisibility(empty ? View.VISIBLE : View.GONE);
        findViewById(R.id.content).setVisibility(empty ? View.GONE : View.VISIBLE);
        if (empty) return;
        buildFigures();
        buildPerDrink();
        buildWeekdays();
        setMode(mode);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("mode", mode);
    }

    // ---------------------------------------------------------------- Verlauf

    private void setMode(int m) {
        mode = m;
        boolean week = m == Stats.WEEK;
        styleToggle(weekBtn, week);
        styleToggle(monthBtn, !week);
        chartTitle.setText(week ? "Pro Woche" : "Pro Monat");
        buckets = Stats.buckets(entries, m, week ? WEEKS : MONTHS, System.currentTimeMillis());
        chart.setData(buckets.labels, buckets.cents, drinkColors, -1, average(buckets));
        showDetail(-1);
    }

    private void styleToggle(Button b, boolean active) {
        b.setBackgroundResource(active ? R.drawable.btn_spezi : R.drawable.btn_neutral);
        b.setTextColor(active ? 0xFFFFFFFF : getColor(R.color.text));
    }

    /** Durchschnitt ab dem ersten Zeitraum, in dem getrunken wurde. */
    private float average(Stats.Buckets b) {
        long first = Stats.firstDrink(entries);
        int from = 0;
        while (from < b.size() - 1 && b.starts[from + 1] <= first) from++;
        int sum = 0;
        for (int i = from; i < b.size(); i++) sum += b.total(i);
        int n = b.size() - from;
        return n > 0 ? (float) sum / n : -1;
    }

    private void showDetail(int i) {
        if (i < 0) {
            float avg = average(buckets);
            chartDetail.setText("- - -  Ø " + Store.euro(Math.round(avg))
                    + (mode == Stats.WEEK ? " pro Woche" : " pro Monat")
                    + "\nBalken antippen für Details");
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(buckets.longLabels[i]).append(": ").append(Store.euro(buckets.total(i)));
        List<String> parts = new ArrayList<>();
        for (int d = 0; d < Store.NAMES.length; d++) {
            if (buckets.counts[d][i] > 0) parts.add(buckets.counts[d][i] + "× " + Store.NAMES[d]);
        }
        sb.append('\n').append(parts.isEmpty() ? "Nichts getrunken" : String.join(", ", parts));
        if (buckets.paid[i] > 0) sb.append("\nEingezahlt: ").append(Store.euro(buckets.paid[i]));
        chartDetail.setText(sb);
    }

    private void buildLegend() {
        LinearLayout legend = findViewById(R.id.legend);
        legend.removeAllViews();
        for (int d = 0; d < Store.NAMES.length; d++) {
            TextView t = new TextView(this);
            t.setText(Store.NAMES[d]);
            t.setTextColor(getColor(R.color.text_secondary));
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            Drawable dot = getDrawable(R.drawable.dot).mutate();
            dot.setTint(drinkColors[d]);
            dot.setBounds(0, 0, dp(9), dp(9));
            t.setCompoundDrawables(dot, null, null, null);
            t.setCompoundDrawablePadding(dp(5));
            t.setPadding(dp(8), 0, dp(8), 0);
            legend.addView(t);
        }
    }

    // ------------------------------------------------------------- Kennzahlen

    private void buildFigures() {
        long now = System.currentTimeMillis();
        long weekStart = Stats.periodStart(now, Stats.WEEK).getTimeInMillis();
        Calendar c = Stats.periodStart(now, Stats.WEEK);
        c.add(Calendar.WEEK_OF_YEAR, -1);
        long lastWeekStart = c.getTimeInMillis();
        long monthStart = Stats.periodStart(now, Stats.MONTH).getTimeInMillis();

        int thisWeek = Stats.spent(entries, weekStart, Long.MAX_VALUE);
        // fairer Vergleich: letzte Woche bis zum gleichen Wochentag/Uhrzeit
        int lastWeekSoFar = Stats.spent(entries, lastWeekStart, lastWeekStart + (now - weekStart));
        int lastWeek = Stats.spent(entries, lastWeekStart, weekStart);
        int thisMonth = Stats.spent(entries, monthStart, Long.MAX_VALUE);

        int last4 = Stats.spent(entries, weekStart - 28 * DAY, weekStart);
        int prev4 = Stats.spent(entries, weekStart - 56 * DAY, weekStart - 28 * DAY);

        Store.Summary s = Store.summary(this);
        long first = Stats.firstDrink(entries);
        long firstWeek = Stats.periodStart(first, Stats.WEEK).getTimeInMillis();
        int weeks = (int) Math.max(1, Math.ceil((now - firstWeek) / (7.0 * DAY)));
        int drinks = 0, fav = 0;
        for (int d = 0; d < s.counts.length; d++) {
            drinks += s.counts[d];
            if (s.counts[d] > s.counts[fav]) fav = d;
        }

        LinearLayout box = findViewById(R.id.figures);
        box.removeAllViews();

        Integer wk = Stats.percentChange(lastWeekSoFar, thisWeek);
        String weekSub = "Letzte Woche: " + Store.euro(lastWeek);
        if (wk != null) weekSub += "\n" + trendText(wk) + " ggü. gleichem Stand";
        View a = figure("Diese Woche", Store.euro(thisWeek), weekSub, wk);

        View b = figure("Dieser Monat", Store.euro(thisMonth),
                "Hochrechnung: ≈ " + Store.euro(Stats.monthProjection(thisMonth, now)), null);
        addRow(box, a, b);

        Integer tr = Stats.percentChange(prev4, last4);
        String trendValue = tr == null ? (last4 > 0 ? "neu" : "–") : trendText(tr);
        View t = figure("Trend 4 Wochen", trendValue,
                Store.euro(last4) + " vs. " + Store.euro(prev4) + " davor", tr);
        View avg = figure("Ø pro Woche", Store.euro(Math.round(s.consumed / (float) weeks)),
                "seit " + new SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY).format(new Date(first)), null);
        addRow(box, t, avg);

        View total = figure("Insgesamt", Store.euro(s.consumed), drinks + " Getränke", null);
        View favV = figure("Lieblingsgetränk", Store.NAMES[fav], s.counts[fav] + "× getrunken", null);
        addRow(box, total, favV);
    }

    private static String trendText(int pct) {
        if (pct > 0) return "▲ +" + pct + " %";
        if (pct < 0) return "▼ " + pct + " %";
        return "→ ±0 %";
    }

    /** Kachel; {@code trend} färbt den Wert (mehr ausgeben = rot, weniger = grün). */
    private View figure(String title, String value, String sub, Integer trend) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.card_bg);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));

        TextView tt = text(title, 13, R.color.text_secondary);
        TextView vv = text(value, 22, R.color.text);
        vv.setTypeface(vv.getTypeface(), android.graphics.Typeface.BOLD);
        if (trend != null && title.startsWith("Trend")) {
            vv.setTextColor(getColor(trend > 0 ? R.color.debt : trend < 0 ? R.color.credit : R.color.text));
        }
        TextView ss = text(sub, 12, R.color.text_secondary);
        card.addView(tt);
        card.addView(vv);
        card.addView(ss);
        return card;
    }

    private void addRow(LinearLayout box, View a, View b) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lpA = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1);
        lpA.setMarginEnd(dp(12));
        row.addView(a, lpA);
        row.addView(b, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        if (box.getChildCount() > 0) lp.topMargin = dp(12);
        box.addView(row, lp);
    }

    // ------------------------------------------------------------ Nach Getränk

    private void buildPerDrink() {
        LinearLayout box = findViewById(R.id.per_drink);
        box.removeAllViews();
        TextView title = text("Nach Getränk", 16, R.color.text);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        box.addView(title);

        Store.Summary s = Store.summary(this);
        int[] cents = new int[Store.NAMES.length];
        for (Store.Entry e : entries) if (!e.isPayment() && e.drink < cents.length) cents[e.drink] += e.cents;
        int total = Math.max(1, s.consumed);

        for (int d = 0; d < Store.NAMES.length; d++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, dp(12), 0, dp(4));
            TextView name = text(Store.NAMES[d], 15, R.color.text);
            row.addView(name, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            int pct = Math.round(cents[d] * 100f / total);
            TextView val = text(s.counts[d] + "× · " + Store.euro(cents[d]) + " · " + pct + " %", 14, R.color.text_secondary);
            val.setGravity(Gravity.END);
            row.addView(val);
            box.addView(row);

            LinearLayout bar = new LinearLayout(this);
            bar.setOrientation(LinearLayout.HORIZONTAL);
            bar.setBackgroundResource(R.drawable.bar_track);
            bar.setClipToOutline(true);
            View fill = new View(this);
            fill.setBackgroundColor(drinkColors[d]);
            bar.addView(fill, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, cents[d]));
            bar.addView(new View(this), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT,
                    total - cents[d]));
            box.addView(bar, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(8)));
        }
    }

    // -------------------------------------------------------------- Wochentage

    private void buildWeekdays() {
        int[] w = Stats.byWeekday(entries);
        BarChartView wc = findViewById(R.id.weekday_chart);
        TextView detail = findViewById(R.id.weekday_detail);
        int best = 0, sum = 0;
        for (int i = 0; i < 7; i++) {
            sum += w[i];
            if (w[i] > w[best]) best = i;
        }
        wc.setData(Stats.WEEKDAYS, new int[][]{w}, new int[]{getColor(R.color.mate)}, -1, -1);
        final int bestDay = best, total = Math.max(1, sum);
        String base = "Am meisten am " + Stats.WEEKDAYS_LONG[bestDay] + " ("
                + Math.round(w[bestDay] * 100f / total) + " % deiner Ausgaben)";
        detail.setText(base);
        wc.setOnSelectListener(i -> detail.setText(i < 0 ? base
                : Stats.WEEKDAYS_LONG[i] + ": " + Store.euro(w[i]) + " insgesamt ("
                + Math.round(w[i] * 100f / total) + " %)"));
    }

    // ----------------------------------------------------------------- Helfer

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(getColor(color));
        return t;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
