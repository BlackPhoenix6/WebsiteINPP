package de.uni.getraenke;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/** Gestapeltes Balkendiagramm mit Ø-Linie; Balken antippen wählt ihn aus. */
public class BarChartView extends View {

    public interface OnSelectListener {
        void onSelect(int index);
    }

    private String[] labels = new String[0];
    private int[][] series = new int[0][0];
    private int[] colors = new int[0];
    private int selected = -1;
    private float average = -1;
    private OnSelectListener listener;

    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint avgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path clip = new Path();

    public BarChartView(Context context) {
        this(context, null);
    }

    public BarChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        float sp = getResources().getDisplayMetrics().scaledDensity;
        labelPaint.setColor(context.getColor(R.color.text_secondary));
        labelPaint.setTextSize(11 * sp);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        valuePaint.setColor(context.getColor(R.color.text));
        valuePaint.setTextSize(12 * sp);
        valuePaint.setFakeBoldText(true);
        valuePaint.setTextAlign(Paint.Align.CENTER);
        avgPaint.setColor(context.getColor(R.color.text_secondary));
        avgPaint.setStyle(Paint.Style.STROKE);
        avgPaint.setStrokeWidth(dp(1.2f));
        avgPaint.setPathEffect(new DashPathEffect(new float[]{dp(4), dp(4)}, 0));
        basePaint.setColor(context.getColor(R.color.divider));
        basePaint.setStrokeWidth(dp(1));
    }

    public void setData(String[] labels, int[][] series, int[] colors, int selected, float average) {
        this.labels = labels;
        this.series = series;
        this.colors = colors;
        this.selected = selected;
        this.average = average;
        invalidate();
    }

    public void setOnSelectListener(OnSelectListener l) {
        listener = l;
    }

    public int getSelected() {
        return selected;
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    private int total(int i) {
        int t = 0;
        for (int[] s : series) t += s[i];
        return t;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int n = labels.length;
        if (n == 0) return;
        float w = getWidth() - getPaddingLeft() - getPaddingRight();
        float left = getPaddingLeft();
        float top = getPaddingTop() + dp(20);              // Platz für Wert-Label
        float bottom = getHeight() - getPaddingBottom() - dp(20); // Platz für Achsenbeschriftung
        float chartH = bottom - top;

        int max = 0;
        for (int i = 0; i < n; i++) {
            int t = total(i);
            max = Math.max(max, t);
        }
        max = Math.max(max, Math.round(average));
        if (max == 0) max = 100;

        float slot = w / n;
        float barW = Math.min(slot * 0.64f, dp(36));
        float radius = Math.min(barW / 2, dp(6));

        canvas.drawLine(left, bottom, left + w, bottom, basePaint);

        for (int i = 0; i < n; i++) {
            float x = left + i * slot + (slot - barW) / 2;
            int t = total(i);
            boolean dim = selected >= 0 && selected != i;
            if (t > 0) {
                float barTop = bottom - chartH * t / max;
                clip.reset();
                rect.set(x, barTop, x + barW, bottom + radius);
                clip.addRoundRect(rect, radius, radius, Path.Direction.CW);
                canvas.save();
                canvas.clipRect(x, barTop, x + barW, bottom);
                canvas.clipPath(clip);
                float y = bottom;
                for (int s = 0; s < series.length; s++) {
                    int v = series[s][i];
                    if (v <= 0) continue;
                    float h = chartH * v / max;
                    barPaint.setColor(colors[s]);
                    barPaint.setAlpha(dim ? 90 : 255);
                    canvas.drawRect(x, y - h, x + barW, y, barPaint);
                    y -= h;
                }
                canvas.restore();
                if (selected == i || (selected < 0 && t == max)) {
                    canvas.drawText(Store.euro(t), x + barW / 2, barTop - dp(5), valuePaint);
                }
            }
            labelPaint.setFakeBoldText(selected == i);
            canvas.drawText(labels[i], x + barW / 2, bottom + dp(15), labelPaint);
        }

        if (average > 0) {
            float y = bottom - chartH * average / max;
            canvas.drawLine(left, y, left + w, y, avgPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int n = labels.length;
        if (n == 0) return false;
        if (e.getAction() == MotionEvent.ACTION_DOWN) return true;
        if (e.getAction() == MotionEvent.ACTION_UP) {
            float w = getWidth() - getPaddingLeft() - getPaddingRight();
            int i = (int) ((e.getX() - getPaddingLeft()) / (w / n));
            i = Math.max(0, Math.min(n - 1, i));
            selected = selected == i ? -1 : i;
            invalidate();
            performClick();
            if (listener != null) listener.onSelect(selected);
            return true;
        }
        return super.onTouchEvent(e);
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}
