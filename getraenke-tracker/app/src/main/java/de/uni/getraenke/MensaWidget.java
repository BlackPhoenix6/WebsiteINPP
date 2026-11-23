package de.uni.getraenke;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.RemoteViews;
import android.widget.Toast;

import java.util.List;

/** Mensa-Widget: heutige Gerichte, Tipp = Essen eintragen. */
public class MensaWidget extends AppWidgetProvider {

    static final String ACTION_ADD = "de.uni.getraenke.MENSA_ADD";
    static final String ACTION_TOGGLE = "de.uni.getraenke.MENSA_TOGGLE";
    static final String ACTION_REFRESH = "de.uni.getraenke.MENSA_REFRESH";
    static final String EXTRA_NAME = "name";
    static final String EXTRA_PLACE = "place";
    static final String EXTRA_CENTS = "cents";

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        updateAll(context);
    }

    @Override
    public void onEnabled(Context context) {
        MensaRefreshJob.schedule(context);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String a = intent.getAction();
        if (ACTION_ADD.equals(a)) {
            String name = intent.getStringExtra(EXTRA_NAME);
            int cents = intent.getIntExtra(EXTRA_CENTS, -1);
            if (name == null) return;
            if (cents < 0) {
                Toast.makeText(context, "Kein Preis bekannt – bitte in der App eintragen", Toast.LENGTH_SHORT).show();
                return;
            }
            MensaStore.add(context, name, intent.getStringExtra(EXTRA_PLACE), cents);
            Toast.makeText(context, shorten(name) + " eingetragen (" + Store.euro(cents) + ")",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (ACTION_TOGGLE.equals(a)) {
            MensaStore.setWidgetSlot(context, 1 - MensaStore.widgetSlot(context));
            updateAll(context);
            return;
        }
        if (ACTION_REFRESH.equals(a)) {
            PendingResult pr = goAsync();
            new Thread(() -> {
                try {
                    MensaApi.menu(context, MensaStore.widgetSlot(context), System.currentTimeMillis(), true);
                    updateAll(context);
                } finally {
                    pr.finish();
                }
            }).start();
            return;
        }
        super.onReceive(context, intent);
    }

    static String shorten(String s) {
        return s.length() > 40 ? s.substring(0, 38) + "…" : s;
    }

    public static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, MensaWidget.class));
        if (ids == null || ids.length == 0) return;

        int slot = MensaStore.widgetSlot(context);
        List<MensaStore.Meal> meals = MensaStore.entries(context);
        long now = System.currentTimeMillis();
        int week = MensaStore.spent(meals, Stats.periodStart(now, Stats.WEEK).getTimeInMillis(), Long.MAX_VALUE);
        int month = MensaStore.spent(meals, Stats.periodStart(now, Stats.MONTH).getTimeInMillis(), Long.MAX_VALUE);

        for (int id : ids) {
            RemoteViews v = new RemoteViews(context.getPackageName(), R.layout.widget_mensa);
            v.setTextViewText(R.id.wm_title, "🍽 " + MensaApi.canteenName(context, slot) + "  ⇄");
            v.setTextViewText(R.id.wm_sum, "Woche " + Store.euro(week) + " · Monat " + Store.euro(month));

            Intent svc = new Intent(context, MensaWidgetService.class)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
            svc.setData(Uri.parse("mensa://widget/" + id + "/" + slot));
            v.setRemoteAdapter(R.id.wm_list, svc);
            v.setEmptyView(R.id.wm_list, R.id.wm_empty);

            Intent add = new Intent(context, MensaWidget.class).setAction(ACTION_ADD);
            v.setPendingIntentTemplate(R.id.wm_list, PendingIntent.getBroadcast(context, 300, add,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE));

            Intent toggle = new Intent(context, MensaWidget.class).setAction(ACTION_TOGGLE);
            v.setOnClickPendingIntent(R.id.wm_title, PendingIntent.getBroadcast(context, 301, toggle,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

            Intent refresh = new Intent(context, MensaWidget.class).setAction(ACTION_REFRESH);
            v.setOnClickPendingIntent(R.id.wm_empty, PendingIntent.getBroadcast(context, 302, refresh,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

            Intent open = new Intent(context, MainActivity.class)
                    .setAction(MainActivity.ACTION_MENSA)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            v.setOnClickPendingIntent(R.id.wm_sum, PendingIntent.getActivity(context, 303, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

            manager.updateAppWidget(id, v);
        }
        manager.notifyAppWidgetViewDataChanged(ids, R.id.wm_list);
    }
}
