package org.telegram.ui.Components.Paint.Views;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

import org.telegram.messenger.AndroidUtilities;
import com.maxgeram.amir.R;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Paint.Swatch;

public class ColorPicker extends FrameLayout {

    public interface ColorPickerDelegate {
        void onBeganColorPicking();
        void onColorValueChanged();
        void onFinishedColorPicking();
        void onSettingsPressed();
    }

    private ColorPickerDelegate delegate;
    private boolean interacting;
    private boolean changingWeight;
    private boolean wasChangingWeight;
    private OvershootInterpolator interpolator = new OvershootInterpolator(1.02f);

    private static final int[] COLORS = new int[]{
            0xffea2739,
            0xffdb3ad2,
            0xff3051e3,
            0xff49c5ed,
            0xff80c864,
            0xfffcde65,
            0xfffc964d,
            0xff000000,
            0xffffffff
    };

    private static final float[] LOCATIONS = new float[]{
            0.0f,
            0.14f,
            0.24f,
            0.39f,
            0.49f,
            0.62f,
            0.73f,
            0.85f,
            1.0f
    };

    private ImageView settingsButton;
    private Drawable shadowDrawable;

    private Paint gradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint swatchPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint swatchStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private RectF rectF = new RectF();

    private float location = 1.0f;
    private float weight = 0.27f;
    private float draggingFactor;
    private boolean dragging;

    public ColorPicker(Context context) {
        super(context);
        setWillNotDraw(false);
        shadowDrawable = getResources().getDrawable(R.drawable.knob_shadow);
        backgroundPaint.setColor(0xffffffff);
        swatchStrokePaint.setStyle(Paint.Style.STROKE);
        swatchStrokePaint.setStrokeWidth(AndroidUtilities.dp(1));

        settingsButton = new ImageView(context);
        settingsButton.setScaleType(ImageView.ScaleType.CENTER);
        settingsButton.setImageResource(R.drawable.photo_paint_brush);
        addView(settingsButton, LayoutHelper.createFrame(60, 52));
        settingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (delegate != null) {
                    delegate.onSettingsPressed();
                }
            }
        });

        location = context.getSharedPreferences("paint", Activity.MODE_PRIVATE).getFloat("last_color_location", 1.0f);
        setLocation(location);
    }

    public void setDelegate(ColorPickerDelegate colorPickerDelegate) {
        delegate = colorPickerDelegate;
    }

    public View getSettingsButton() {
        return settingsButton;
    }

    public void setSettingsButtonImage(int resId) {
        settingsButton.setImageResource(resId);
    }

    public Swatch getSwatch() {
        return new Swatch(colorForLocation(location), location, weight);
    }

    public void setSwatch(Swatch swatch) {
        setLocation(swatch.colorLocation);
        setWeight(swatch.brushWeight);
    }

    public int colorForLocation(float location) {
        if (location <= 0) {
            return COLORS[0];
        } else if (location >= 1) {
            return COLORS[COLORS.length - 1];
        }

        int leftIndex = -1;
        int rightIndex = -1;

        for (int i = 1; i < LOCATIONS.length; i++) {
            float value = LOCATIONS[i];
            if (value > location) {
                leftIndex = i - 1;
                rightIndex = i;
                break;
            }
        }

        float leftLocation = LOCATIONS[leftIndex];
        int leftColor = COLORS[leftIndex];

        float rightLocation = LOCATIONS[rightIndex];
        int rightColor = COLORS[rightIndex];

        float factor = (location - leftLocation) / (rightLocation - leftLocation);
        return interpolateColors(leftColor, rightColor, factor);
    }

    private int interpolateColors(int leftColor, int rightColor, float factor) {
        factor = Math.min(Math.max(factor, 0.0f), 1.0f);

        int r1 = Color.red(leftColor);
        int r2 = Color.red(rightColor);

        int g1 = Color.green(leftColor);
        int g2 = Color.green(rightColor);

        int b1 = Color.blue(leftColor);
        int b2 = Color.blue(rightColor);

        int r = Math.min(255, (int) (r1 + (r2 - r1) * factor));
        int g = Math.min(255, (int) (g1 + (g2 - g1) * factor));
        int b = Math.min(255, (int) (b1 + (b2 - b1) * factor));

        return Color.argb(255, r, g, b);
    }

    public void setLocation(float value) {
        int color = colorForLocation(location = value);
        swatchPaint.setColor(color);

        float hsv[] = new float[3];
        Color.colorToHSV(color, hsv);

        if (hsv[0] < 0.001 && hsv[1] < 0.001 && hsv[2] > 0.92f) {
            int c = (int) ((1.0f - (hsv[2] - 0.92f) / 0.08f * 0.22f) * 255);
            swatchStrokePaint.setColor(Color.rgb(c, c, c));
        } else {
            swatchStrokePaint.setColor(color);
        }


        invalidate();
    }

    public void setWeight(float value) {
        weight = value;
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getPointerCount() > 1) {
            return false;
        }

        float x = event.getX() - rectF.left;
        float y = event.getY() - rectF.top;

        if (!interacting && x < -AndroidUtilities.dp(10)) {
            return false;
        }

        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
            if (interacting && delegate != null) {
                delegate.onFinishedColorPicking();

                getContext().getSharedPreferences("paint", Activity.MODE_PRIVATE).edit().putFloat("last_color_location", location).commit();
            }
            interacting = false;
            wasChangingWeight = changingWeight;
            changingWeight = false;
            setDragging(false, true);
        } else if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
            if (!interacting) {
                interacting = true;
                if (delegate != null) {
                    delegate.onBeganColorPicking();
                }
            }

            float colorLocation = Math.max(0.0f, Math.min(1.0f, y / rectF.height()));
            setLocation(colorLocation);

            setDragging(true, true);

            if (x < -AndroidUtilities.dp(10)) {
                changingWeight = true;
                float weightLocation = (-x - AndroidUtilities.dp(10)) / AndroidUtilities.dp(190);
                weightLocation = Math.max(0.0f, Math.min(1.0f, weightLocation));
                setWeight(weightLocation);
            }

            if (delegate != null) {
                delegate.onColorValueChanged();
            }
            return true;
        }
        return false;
    }

    @SuppressLint("DrawAllocation")
    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int width = right - left;
        int height = bottom - top;

        int backHeight = getMeasuredHeight() - AndroidUtilities.dp(26) - AndroidUtilities.dp(64);
        gradientPaint.setShader(new LinearGradient(0, AndroidUtilities.dp(26), 0, backHeight + AndroidUtilities.dp(26), COLORS, LOCATIONS, Shader.TileMode.REPEAT));
        int x = width - AndroidUtilities.dp(26) - AndroidUtilities.dp(8);
        int y = AndroidUtilities.dp(26);
        rectF.set(x, y, x + AndroidUtilities.dp(8), y + backHeight);

        settingsButton.layout(width - settingsButton.getMeasuredWidth(), height - AndroidUtilities.dp(52), width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawRoundRect(rectF, AndroidUtilities.dp(6), AndroidUtilities.dp(6), gradientPaint);

        int cx = (int) (rectF.centerX() + draggingFactor * -AndroidUtilities.dp(70) - (changingWeight ? weight * AndroidUtilities.dp(190) : 0.0f));
        int cy = (int) (rectF.top - AndroidUtilities.dp(22) + rectF.height() * location) + AndroidUtilities.dp(22);

        int side = (int) (AndroidUtilities.dp(24) * (0.5f * (1 + draggingFactor)));
        shadowDrawable.setBounds(cx - side, cy - side, cx + side, cy + side);
        shadowDrawable.draw(canvas);

        float swatchRadius = (int) Math.floor(AndroidUtilities.dp(4) + (AndroidUtilities.dp(19) - AndroidUtilities.dp(4)) * weight) * (1 + draggingFactor) / 2;

        canvas.drawCircle(cx, cy, AndroidUtilities.dp(22) / 2 * (draggingFactor + 1), backgroundPaint);
        canvas.drawCircle(cx, cy, swatchRadius, swatchPaint);
        canvas.drawCircle(cx, cy, swatchRadius - AndroidUtilities.dp(0.5f), swatchStrokePaint);
    }

    private void setDraggingFactor(float factor) {
        draggingFactor = factor;
        invalidate();
    }

    public float getDraggingFactor() {
        return draggingFactor;
    }

    private void setDragging(boolean value, boolean animated) {
        if (dragging == value) {
            return;
        }
        dragging = value;
        float target = dragging ? 1.0f : 0.0f;
        if (animated) {
            Animator a = ObjectAnimator.ofFloat(this, "draggingFactor", draggingFactor, target);
            a.setInterpolator(interpolator);
            int duration = 300;
            if (wasChangingWeight) {
                duration += weight * 75;
            }
            a.setDuration(duration);
            a.start();
        } else {
            setDraggingFactor(target);
        }
    }
}
