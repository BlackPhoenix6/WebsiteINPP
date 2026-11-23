package de.uni.getraenke;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    public static final String ACTION_PAY = "de.uni.getraenke.PAY";
    private static final int MAX_HISTORY = 200;

    private TextView label, balance, stats;
    private LinearLayout history;
    private final Button[] drinkButtons = new Button[3];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        label = findViewById(R.id.label);
        balance = findViewById(R.id.balance);
        stats = findViewById(R.id.stats);
        history = findViewById(R.id.history);
        drinkButtons[Store.SPEZI] = findViewById(R.id.b_spezi);
        drinkButtons[Store.STERNI] = findViewById(R.id.b_sterni);
        drinkButtons[Store.MATE] = findViewById(R.id.b_mate);

        for (int i = 0; i < drinkButtons.length; i++) {
            final int drink = i;
            drinkButtons[i].setOnClickListener(v -> {
                int p = Store.addDrink(this, drink);
                toast(Store.NAMES[drink] + " +1 (" + Store.euro(p) + ")");
                refresh();
            });
        }

        payButton(R.id.p1, 100);
        payButton(R.id.p2, 200);
        payButton(R.id.p5, 500);
        payButton(R.id.p10, 1000);
        findViewById(R.id.p_other).setOnClickListener(v -> askCustomPayment());
        findViewById(R.id.settle).setOnClickListener(v -> settle());
        findViewById(R.id.undo).setOnClickListener(v -> {
            Store.Entry e = Store.removeLast(this);
            toast(e == null ? "Nichts zum Rückgängigmachen" : e.label() + " (" + Store.euro(e.cents) + ") entfernt");
            refresh();
        });
        findViewById(R.id.stats).setOnClickListener(v ->
                startActivity(new Intent(this, StatsActivity.class)));
        findViewById(R.id.prices).setOnClickListener(v -> editPrices());
        findViewById(R.id.reset).setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Alles zurücksetzen?")
                .setMessage("Alle Getränke und Einzahlungen werden gelöscht.")
                .setPositiveButton("Löschen", (d, w) -> {
                    Store.clear(this);
                    refresh();
                })
                .setNegativeButton("Abbrechen", null)
                .show());

        if (savedInstanceState == null) handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void handleIntent(Intent intent) {
        if (intent != null && ACTION_PAY.equals(intent.getAction())) {
            intent.setAction(null);
            askCustomPayment();
        }
    }

    private void payButton(int id, int cents) {
        findViewById(id).setOnClickListener(v -> {
            Store.addPayment(this, cents);
            toast(Store.euro(cents) + " eingezahlt");
            refresh();
        });
    }

    private void settle() {
        int bal = Store.summary(this).balance();
        if (bal >= 0) {
            toast("Du hast keine Schulden 🎉");
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Schulden bezahlt?")
                .setMessage(Store.euro(-bal) + " als eingezahlt eintragen?")
                .setPositiveButton("Ja", (d, w) -> {
                    Store.addPayment(this, -bal);
                    refresh();
                })
                .setNegativeButton("Abbrechen", null)
                .show();
    }

    private void askCustomPayment() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setHint("z. B. 3,50");
        int bal = Store.summary(this).balance();
        if (bal < 0) input.setHint("offen: " + Store.euro(-bal));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Wie viel hast du eingeworfen?")
                .setView(wrap(input))
                .setPositiveButton("Eintragen", (d, w) -> {
                    int cents = Store.parseCents(input.getText().toString());
                    if (cents <= 0) {
                        toast("Ungültiger Betrag");
                        return;
                    }
                    Store.addPayment(this, cents);
                    toast(Store.euro(cents) + " eingezahlt");
                    refresh();
                })
                .setNegativeButton("Abbrechen", null)
                .create();
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        dialog.show();
        input.requestFocus();
    }

    private void editPrices() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        EditText[] fields = new EditText[Store.NAMES.length];
        for (int i = 0; i < fields.length; i++) {
            TextView t = new TextView(this);
            t.setText(Store.NAMES[i] + " (€)");
            t.setPadding(0, dp(8), 0, 0);
            fields[i] = new EditText(this);
            fields[i].setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            fields[i].setText(String.format(Locale.GERMANY, "%.2f", Store.price(this, i) / 100.0));
            box.addView(t);
            box.addView(fields[i]);
        }
        new AlertDialog.Builder(this)
                .setTitle("Preise")
                .setMessage("Gilt nur für neue Einträge.")
                .setView(wrap(box))
                .setPositiveButton("Speichern", (d, w) -> {
                    for (int i = 0; i < fields.length; i++) {
                        int c = Store.parseCents(fields[i].getText().toString());
                        if (c > 0) Store.setPrice(this, i, c);
                    }
                    refresh();
                })
                .setNegativeButton("Abbrechen", null)
                .show();
    }

    private void refresh() {
        Store.Summary s = Store.summary(this);
        int bal = s.balance();
        if (bal < 0) {
            label.setText("Noch zu zahlen");
            balance.setText(Store.euro(-bal));
            balance.setTextColor(getColor(R.color.debt));
        } else {
            label.setText(bal == 0 ? "Alles beglichen" : "Guthaben");
            balance.setText(Store.euro(bal));
            balance.setTextColor(getColor(R.color.credit));
        }
        stats.setText("Getrunken: " + Store.euro(s.consumed) + "\nEingezahlt: " + Store.euro(s.paid)
                + "\n" + s.counts[0] + "× Spezi · " + s.counts[1] + "× Sterni · " + s.counts[2] + "× Mate");

        for (int i = 0; i < drinkButtons.length; i++) {
            drinkButtons[i].setText(Store.NAMES[i] + "\n" + Store.euro(Store.price(this, i)));
        }

        history.removeAllViews();
        List<Store.Entry> list = Store.entries(this);
        if (list.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Noch keine Einträge");
            empty.setTextColor(getColor(R.color.text_secondary));
            empty.setPadding(0, dp(14), 0, dp(14));
            history.addView(empty);
            return;
        }
        SimpleDateFormat fmt = new SimpleDateFormat("EE dd.MM. HH:mm", Locale.GERMANY);
        int shown = 0;
        for (int i = list.size() - 1; i >= 0 && shown < MAX_HISTORY; i--, shown++) {
            history.addView(historyRow(list.get(i), fmt, shown > 0));
        }
    }

    private View historyRow(Store.Entry e, SimpleDateFormat fmt, boolean divider) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(12), 0, dp(12));

        TextView name = new TextView(this);
        name.setText(e.label());
        name.setTextColor(getColor(R.color.text));
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);

        TextView date = new TextView(this);
        date.setText(fmt.format(new Date(e.time)));
        date.setTextColor(getColor(R.color.text_secondary));
        date.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.addView(name);
        left.addView(date);
        row.addView(left, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView amount = new TextView(this);
        amount.setText((e.isPayment() ? "+ " : "− ") + Store.euro(e.cents));
        amount.setTextColor(getColor(e.isPayment() ? R.color.credit : R.color.text));
        amount.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        row.addView(amount);

        row.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Eintrag löschen?")
                .setMessage(e.label() + " · " + Store.euro(e.cents) + "\n" + fmt.format(new Date(e.time)))
                .setPositiveButton("Löschen", (d, w) -> {
                    Store.remove(this, e.time);
                    refresh();
                })
                .setNegativeButton("Abbrechen", null)
                .show());

        if (!divider) return row;
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        View line = new View(this);
        line.setBackgroundColor(getColor(R.color.divider));
        wrap.addView(line, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
        wrap.addView(row);
        return wrap;
    }

    private View wrap(View v) {
        LinearLayout l = new LinearLayout(this);
        l.setPadding(dp(24), dp(8), dp(24), 0);
        l.addView(v, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        return l;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
