package de.uni.getraenke;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import android.widget.Toast;

/** Homescreen-Widget: Kontostand + Knöpfe für Spezi, Sterni, Mate und Einzahlen. */
public class DrinkWidget extends AppWidgetProvider {

    private static final String ACTION_DRINK = "de.uni.getraenke.ACTION_DRINK";
    private static final String EXTRA_DRINK = "drink";

    private static final int[] BUTTON_IDS = {R.id.w_spezi, R.id.w_sterni, R.id.w_mate};

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) {
            manager.updateAppWidget(id, buildViews(context));
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ACTION_DRINK.equals(intent.getAction())) {
            int drink = intent.getIntExtra(EXTRA_DRINK, -1);
            if (drink >= 0 && drink < Store.NAMES.length) {
                int price = Store.addDrink(context, drink);
                int bal = Store.summary(context).balance();
                Toast.makeText(context,
                        Store.NAMES[drink] + " +1 (" + Store.euro(price) + ") · "
                                + (bal < 0 ? "offen: " + Store.euro(-bal) : "Guthaben: " + Store.euro(bal)),
                        Toast.LENGTH_SHORT).show();
            }
            return;
        }
        super.onReceive(context, intent);
    }

    public static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, DrinkWidget.class));
        if (ids == null || ids.length == 0) return;
        RemoteViews views = buildViews(context);
        for (int id : ids) {
            manager.updateAppWidget(id, views);
        }
    }

    private static RemoteViews buildViews(Context context) {
        RemoteViews v = new RemoteViews(context.getPackageName(), R.layout.widget);
        Store.Summary s = Store.summary(context);
        int bal = s.balance();

        if (bal < 0) {
            v.setTextViewText(R.id.w_label, "Noch zu zahlen");
            v.setTextViewText(R.id.w_balance, Store.euro(-bal));
            v.setTextColor(R.id.w_balance, context.getColor(R.color.debt));
        } else {
            v.setTextViewText(R.id.w_label, "Guthaben");
            v.setTextViewText(R.id.w_balance, Store.euro(bal));
            v.setTextColor(R.id.w_balance, context.getColor(R.color.credit));
        }
        v.setTextViewText(R.id.w_stats,
                "Getrunken " + Store.euro(s.consumed) + " · Eingezahlt " + Store.euro(s.paid));

        for (int i = 0; i < BUTTON_IDS.length; i++) {
            v.setTextViewText(BUTTON_IDS[i], Store.NAMES[i] + "\n" + s.counts[i] + "×");
            Intent intent = new Intent(context, DrinkWidget.class)
                    .setAction(ACTION_DRINK)
                    .putExtra(EXTRA_DRINK, i);
            PendingIntent pi = PendingIntent.getBroadcast(context, 100 + i, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            v.setOnClickPendingIntent(BUTTON_IDS[i], pi);
        }

        Intent pay = new Intent(context, MainActivity.class)
                .setAction(MainActivity.ACTION_PAY)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        v.setOnClickPendingIntent(R.id.w_pay, PendingIntent.getActivity(context, 200, pay,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

        Intent open = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        v.setOnClickPendingIntent(R.id.w_header, PendingIntent.getActivity(context, 201, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        return v;
    }
}
