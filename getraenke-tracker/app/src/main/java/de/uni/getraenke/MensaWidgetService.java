package de.uni.getraenke;

import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import java.util.ArrayList;
import java.util.List;

/** Liefert die Zeilen (Gerichte) für das Mensa-Widget. */
public class MensaWidgetService extends RemoteViewsService {

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new Factory(getApplicationContext());
    }

    static final class Factory implements RemoteViewsFactory {
        private final Context context;
        private final List<MensaApi.Meal> meals = new ArrayList<>();
        private int group;
        private String place;

        Factory(Context context) {
            this.context = context;
        }

        @Override
        public void onCreate() {}

        @Override
        public void onDataSetChanged() {
            // läuft im Hintergrund-Thread: Netzwerk ist hier erlaubt
            int slot = MensaStore.widgetSlot(context);
            group = MensaStore.priceGroup(context);
            MensaApi.Menu m = MensaApi.menu(context, slot, System.currentTimeMillis(), false);
            place = MensaApi.canteenName(context, slot);
            meals.clear();
            meals.addAll(MensaTab.sortForDisplay(m.meals));
        }

        @Override
        public void onDestroy() {
            meals.clear();
        }

        @Override
        public int getCount() {
            return meals.size();
        }

        @Override
        public RemoteViews getViewAt(int position) {
            RemoteViews v = new RemoteViews(context.getPackageName(), R.layout.widget_mensa_item);
            if (position >= meals.size()) return v;
            MensaApi.Meal meal = meals.get(position);
            int cents = meal.price(group);
            v.setTextViewText(R.id.wmi_name, meal.name);
            v.setTextViewText(R.id.wmi_cat, meal.category);
            v.setTextViewText(R.id.wmi_price, cents >= 0 ? Store.euro(cents) : "–");
            Intent fill = new Intent()
                    .putExtra(MensaWidget.EXTRA_NAME, meal.name)
                    .putExtra(MensaWidget.EXTRA_PLACE, place)
                    .putExtra(MensaWidget.EXTRA_CENTS, cents);
            v.setOnClickFillInIntent(R.id.wmi_root, fill);
            return v;
        }

        @Override
        public RemoteViews getLoadingView() {
            return null;
        }

        @Override
        public int getViewTypeCount() {
            return 1;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public boolean hasStableIds() {
            return false;
        }
    }
}
