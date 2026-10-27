package com.freeyourtv.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/**
 * A translucent glass slab: soft sheen fill, bright top specular, crisp rim and a faint outer glow.
 * One instance floats over the grid and is moved with spring animations between tiles.
 * Caches drawing to a hardware layer to avoid replaying on CPU every spring frame.
 */
public class GlassView extends View {

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint specular = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float density;
    private float radius;
    private int rimColor = 0xCCFFFFFF;
    private int glowColor = 0x22FFFFFF;
    private int specularColor = 0x66FFFFFF;
    private int fillColor = 0x3DFFFFFF;

    public GlassView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        radius = 20f * density;
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(1.5f * density);
        rim.setColor(rimColor);
        glow.setStyle(Paint.Style.STROKE);
        glow.setStrokeWidth(2f * density);
        glow.setColor(glowColor);
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    void setRadius(float px) {
        radius = px;
        invalidate();
    }

    public void setThemeColors(int rimColor, int glowColor, int specularColor, int fillColor) {
        this.rimColor = rimColor;
        this.glowColor = glowColor;
        this.specularColor = specularColor;
        this.fillColor = fillColor;
        rim.setColor(rimColor);
        glow.setColor(glowColor);
        updateShaders(getWidth(), getHeight());
        invalidate();
    }

    private void updateShaders(int w, int h) {
        if (w <= 0 || h <= 0) return;
        int fillTop = fillColor;
        int fillBottom = (fillColor & 0x00FFFFFF) | 0x12000000;
        fill.setShader(new LinearGradient(0, 0, 0, h, fillTop, fillBottom, Shader.TileMode.CLAMP));
        int specTop = specularColor;
        int specBottom = (specularColor & 0x00FFFFFF);
        specular.setShader(new LinearGradient(0, 0, 0, h * 0.45f, specTop, specBottom, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateShaders(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float inset = 6f * density;   // room for the glow
        rect.set(inset, inset, getWidth() - inset, getHeight() - inset);

        float o = density;
        canvas.drawRoundRect(rect.left - o, rect.top - o, rect.right + o, rect.bottom + o,
                radius + o, radius + o, glow);

        canvas.drawRoundRect(rect, radius, radius, fill);

        float sheenBottom = rect.top + rect.height() * 0.42f;
        canvas.drawRoundRect(rect.left, rect.top, rect.right, sheenBottom, radius, radius, specular);

        canvas.drawRoundRect(rect, radius, radius, rim);
    }
}
