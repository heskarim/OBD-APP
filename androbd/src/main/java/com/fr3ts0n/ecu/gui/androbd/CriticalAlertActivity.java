package com.fr3ts0n.ecu.gui.androbd;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

public class CriticalAlertActivity extends Activity
{
    public static final String EXTRA_TEMPERATURE = "temperature";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private ToneGenerator toneGenerator;
    private boolean highTone;
    private final Runnable sirenRunnable = new Runnable()
    {
        @Override
        public void run()
        {
            if (toneGenerator == null)
            {
                return;
            }
            toneGenerator.startTone(
                    highTone ? ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD : ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK,
                    750);
            highTone = !highTone;
            handler.postDelayed(this, 850);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        showAboveLockScreen();
        buildContent();
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        startSiren();
    }

    @Override
    protected void onPause()
    {
        stopSiren();
        super.onPause();
    }

    private void showAboveLockScreen()
    {
        Window window = getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1)
        {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }
        window.addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                        | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                        | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
    }

    private void buildContent()
    {
        double temperature = getIntent().getDoubleExtra(EXTRA_TEMPERATURE, Double.NaN);
        String temperatureText = Double.isNaN(temperature)
                ? "--.- C"
                : String.format(Locale.US, "%.1f C", temperature);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setPadding(dp(28), dp(36), dp(28), dp(36));
        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF1A0507, 0xFF5C080D, 0xFF120305});
        layout.setBackground(background);

        TextView eyebrow = new TextView(this);
        eyebrow.setText("CRITICAL ENGINE TEMPERATURE");
        eyebrow.setTextColor(0xFFFFD8D8);
        eyebrow.setTextSize(14);
        eyebrow.setGravity(Gravity.CENTER);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
        {
            eyebrow.setLetterSpacing(0.08f);
        }
        eyebrow.setPadding(0, 0, 0, dp(18));

        OverheatCarView icon = new OverheatCarView(this);
        layout.addView(eyebrow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        layout.addView(icon, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(260)));

        TextView title = new TextView(this);
        title.setText("ENGINE OVERHEATING");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(30);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(18), 0, dp(10));

        TextView temperatureView = new TextView(this);
        temperatureView.setText(temperatureText);
        temperatureView.setTextColor(0xFFFFF3B0);
        temperatureView.setTextSize(44);
        temperatureView.setGravity(Gravity.CENTER);
        temperatureView.setPadding(0, 0, 0, dp(14));

        TextView body = new TextView(this);
        body.setText(R.string.coolant_critical_message_short);
        body.setTextColor(0xFFFFECEC);
        body.setTextSize(20);
        body.setGravity(Gravity.CENTER);
        body.setPadding(0, 0, 0, dp(34));

        Button acknowledge = new Button(this);
        acknowledge.setText(R.string.acknowledge);
        acknowledge.setTextSize(18);
        acknowledge.setTextColor(0xFF330000);
        acknowledge.setAllCaps(false);
        GradientDrawable buttonBackground = new GradientDrawable();
        buttonBackground.setColor(0xFFFFF0B8);
        buttonBackground.setCornerRadius(dp(10));
        acknowledge.setBackground(buttonBackground);
        acknowledge.setPadding(dp(28), dp(12), dp(28), dp(12));
        acknowledge.setOnClickListener(view ->
        {
            new VehicleAlertNotifier(this).acknowledgeCriticalAlert();
            finish();
        });

        layout.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        layout.addView(temperatureView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        layout.addView(body, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        buttonParams.setMargins(0, 0, 0, 0);
        layout.addView(acknowledge, buttonParams);
        setContentView(layout);
    }

    private void startSiren()
    {
        if (toneGenerator == null)
        {
            toneGenerator = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
        }
        handler.removeCallbacks(sirenRunnable);
        handler.post(sirenRunnable);
    }

    private void stopSiren()
    {
        handler.removeCallbacks(sirenRunnable);
        if (toneGenerator != null)
        {
            toneGenerator.release();
            toneGenerator = null;
        }
    }

    private int dp(int value)
    {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static final class OverheatCarView extends View
    {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        OverheatCarView(Activity activity)
        {
            super(activity);
        }

        @Override
        protected void onDraw(Canvas canvas)
        {
            super.onDraw(canvas);
            float width = getWidth();
            float height = getHeight();
            float cx = width / 2f;

            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new LinearGradient(
                    0,
                    0,
                    0,
                    height,
                    0x44FFB000,
                    0x00FFB000,
                    Shader.TileMode.CLAMP));
            canvas.drawOval(new RectF(cx - width * 0.38f, height * 0.05f, cx + width * 0.38f, height * 0.92f), paint);
            paint.setShader(null);

            drawHeatWaves(canvas, cx, height);
            drawCar(canvas, cx, height);
            drawWarningTriangle(canvas, cx, height);
        }

        private void drawHeatWaves(Canvas canvas, float cx, float height)
        {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(10f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(0xFFFFD15C);

            for (int i = -1; i <= 1; i++)
            {
                float x = cx + i * 58f;
                path.reset();
                path.moveTo(x, height * 0.08f);
                path.cubicTo(x - 28f, height * 0.18f, x + 28f, height * 0.25f, x, height * 0.36f);
                path.cubicTo(x - 24f, height * 0.45f, x + 20f, height * 0.52f, x, height * 0.60f);
                canvas.drawPath(path, paint);
            }
        }

        private void drawCar(Canvas canvas, float cx, float height)
        {
            float carTop = height * 0.45f;
            float carBottom = height * 0.78f;
            float carWidth = Math.min(getWidth() * 0.78f, 620f);
            float left = cx - carWidth / 2f;
            float right = cx + carWidth / 2f;

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFFFFFFFF);
            path.reset();
            path.moveTo(left + carWidth * 0.16f, carBottom);
            path.lineTo(left + carWidth * 0.22f, carTop + 38f);
            path.quadTo(left + carWidth * 0.32f, carTop, left + carWidth * 0.43f, carTop);
            path.lineTo(left + carWidth * 0.62f, carTop);
            path.quadTo(left + carWidth * 0.75f, carTop + 6f, left + carWidth * 0.83f, carTop + 44f);
            path.lineTo(right - carWidth * 0.06f, carBottom);
            path.close();
            canvas.drawPath(path, paint);

            paint.setColor(0xFF5C080D);
            canvas.drawRoundRect(new RectF(left + carWidth * 0.33f, carTop + 26f, left + carWidth * 0.50f, carTop + 78f), 12f, 12f, paint);
            canvas.drawRoundRect(new RectF(left + carWidth * 0.55f, carTop + 26f, left + carWidth * 0.73f, carTop + 78f), 12f, 12f, paint);

            paint.setColor(0xFF150305);
            canvas.drawCircle(left + carWidth * 0.28f, carBottom, 34f, paint);
            canvas.drawCircle(right - carWidth * 0.28f, carBottom, 34f, paint);

            paint.setColor(0xFFFFD15C);
            canvas.drawCircle(left + carWidth * 0.28f, carBottom, 15f, paint);
            canvas.drawCircle(right - carWidth * 0.28f, carBottom, 15f, paint);
        }

        private void drawWarningTriangle(Canvas canvas, float cx, float height)
        {
            float top = height * 0.33f;
            float size = 86f;
            path.reset();
            path.moveTo(cx, top);
            path.lineTo(cx - size, top + size * 1.55f);
            path.lineTo(cx + size, top + size * 1.55f);
            path.close();

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFFFFD15C);
            canvas.drawPath(path, paint);

            paint.setColor(0xFF5C080D);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(76f);
            paint.setFakeBoldText(true);
            canvas.drawText("!", cx, top + size * 1.18f, paint);
            paint.setFakeBoldText(false);
        }
    }
}
