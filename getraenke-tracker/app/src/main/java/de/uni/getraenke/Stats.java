package de.uni.getraenke;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** Reine Rechenlogik für die Statistik-Seite (ohne Android-Abhängigkeiten). */
public final class Stats {

    public static final int WEEK = 0;
    public static final int MONTH = 1;

    private static final String[] MONTHS =
            {"Jan", "Feb", "Mär", "Apr", "Mai", "Jun", "Jul", "Aug", "Sep", "Okt", "Nov", "Dez"};
    public static final String[] WEEKDAYS = {"Mo", "Di", "Mi", "Do", "Fr", "Sa", "So"};
    public static final String[] WEEKDAYS_LONG =
            {"Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag", "Sonntag"};

    private Stats() {}

    /** Ausgaben pro Zeitraum, aufgeteilt nach Getränk. */
    public static final class Buckets {
        public String[] labels;
        public String[] longLabels;
        public long[] starts;          // n + 1 Grenzen
        public int[][] cents;          // [getränk][zeitraum]
        public int[][] counts;         // [getränk][zeitraum]
        public int[] paid;             // eingezahlt pro Zeitraum

        public int total(int i) {
            int t = 0;
            for (int[] s : cents) t += s[i];
            return t;
        }

        public int size() {
            return labels.length;
        }
    }

    static Calendar cal(long time) {
        Calendar c = Calendar.getInstance(Locale.GERMANY);
        c.setFirstDayOfWeek(Calendar.MONDAY);
        c.setMinimalDaysInFirstWeek(4); // ISO-Kalenderwochen
        c.setTimeInMillis(time);
        return c;
    }

    /** Beginn der Woche (Montag 0:00) bzw. des Monats, in dem {@code time} liegt. */
    public static Calendar periodStart(long time, int mode) {
        Calendar c = cal(time);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        if (mode == WEEK) {
            int dow = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7; // Mo=0 … So=6
            c.add(Calendar.DAY_OF_MONTH, -dow);
        } else {
            c.set(Calendar.DAY_OF_MONTH, 1);
        }
        return c;
    }

    private static void step(Calendar c, int mode, int amount) {
        if (mode == WEEK) c.add(Calendar.WEEK_OF_YEAR, amount);
        else c.add(Calendar.MONTH, amount);
    }

    /** Die letzten {@code n} Wochen/Monate bis einschließlich dem aktuellen. */
    public static Buckets buckets(List<Store.Entry> entries, int mode, int n, long now) {
        Buckets b = new Buckets();
        b.labels = new String[n];
        b.longLabels = new String[n];
        b.starts = new long[n + 1];
        b.cents = new int[Store.NAMES.length][n];
        b.counts = new int[Store.NAMES.length][n];
        b.paid = new int[n];

        Calendar c = periodStart(now, mode);
        step(c, mode, -(n - 1));
        for (int i = 0; i <= n; i++) {
            b.starts[i] = c.getTimeInMillis();
            if (i < n) {
                if (mode == WEEK) {
                    int kw = c.get(Calendar.WEEK_OF_YEAR);
                    b.labels[i] = String.valueOf(kw);
                    Calendar end = (Calendar) c.clone();
                    end.add(Calendar.DAY_OF_MONTH, 6);
                    b.longLabels[i] = String.format(Locale.GERMANY, "KW %d (%02d.%02d.–%02d.%02d.)", kw,
                            c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.MONTH) + 1,
                            end.get(Calendar.DAY_OF_MONTH), end.get(Calendar.MONTH) + 1);
                } else {
                    b.labels[i] = MONTHS[c.get(Calendar.MONTH)];
                    b.longLabels[i] = MONTHS[c.get(Calendar.MONTH)] + " " + c.get(Calendar.YEAR);
                }
            }
            step(c, mode, 1);
        }

        for (Store.Entry e : entries) {
            int i = index(b.starts, e.time);
            if (i < 0) continue;
            if (e.isPayment()) {
                b.paid[i] += e.cents;
            } else if (e.drink < Store.NAMES.length) {
                b.cents[e.drink][i] += e.cents;
                b.counts[e.drink][i]++;
            }
        }
        return b;
    }

    private static int index(long[] starts, long t) {
        if (t < starts[0] || t >= starts[starts.length - 1]) return -1;
        for (int i = 0; i < starts.length - 1; i++) {
            if (t < starts[i + 1]) return i;
        }
        return -1;
    }

    /** Ausgaben (Getränke) im Zeitraum [from, to). */
    public static int spent(List<Store.Entry> entries, long from, long to) {
        int sum = 0;
        for (Store.Entry e : entries) {
            if (!e.isPayment() && e.time >= from && e.time < to) sum += e.cents;
        }
        return sum;
    }

    /** Ausgaben nach Wochentag, Mo=0 … So=6. */
    public static int[] byWeekday(List<Store.Entry> entries) {
        int[] w = new int[7];
        for (Store.Entry e : entries) {
            if (e.isPayment()) continue;
            Calendar c = cal(e.time);
            w[(c.get(Calendar.DAY_OF_WEEK) + 5) % 7] += e.cents;
        }
        return w;
    }

    /** Erstes Getränk (Zeitpunkt) oder -1. */
    public static long firstDrink(List<Store.Entry> entries) {
        long first = -1;
        for (Store.Entry e : entries) {
            if (!e.isPayment() && (first < 0 || e.time < first)) first = e.time;
        }
        return first;
    }

    /** Hochrechnung für den laufenden Monat anhand der bisherigen Tage. */
    public static int monthProjection(int spentSoFar, long now) {
        Calendar c = cal(now);
        int day = c.get(Calendar.DAY_OF_MONTH);
        int days = c.getActualMaximum(Calendar.DAY_OF_MONTH);
        return Math.round(spentSoFar * (float) days / day);
    }

    /** Prozentuale Änderung von {@code before} zu {@code now}; null wenn nicht sinnvoll. */
    public static Integer percentChange(int before, int now) {
        if (before <= 0) return null;
        return Math.round((now - before) * 100f / before);
    }
}
