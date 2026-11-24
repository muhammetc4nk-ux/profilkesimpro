package com.profilkesim.pro;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.LinearInterpolator;

final class LedTrace extends View {
 private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
 private final Path rail = new Path();
 private final Path highlight = new Path();
 private final PathMeasure circuit = new PathMeasure();
 private final float density;
 private float phase;
 private ValueAnimator animator;

 LedTrace(Context context) {
  super(context);
  setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
  density = getResources().getDisplayMetrics().density;
  paint.setStrokeCap(Paint.Cap.ROUND);
  paint.setStyle(Paint.Style.STROKE);
 }

 void setActive(boolean active) {
  if (active) animate(ValueAnimator.INFINITE, 4400);
  else if (animator != null) {
   animator.cancel();
   animator = null;
  }
 }

 void lightUp() { animate(0, 2200); }

 private void animate(int repeats, int duration) {
  if (animator == null) {
   animator = ValueAnimator.ofFloat(0f, 1f);
   animator.setDuration(duration);
   animator.setInterpolator(new LinearInterpolator());
   animator.setRepeatCount(repeats);
   animator.addUpdateListener(animation -> {
    phase = (float) animation.getAnimatedValue();
    invalidate();
   });
   animator.start();
  }
 }

 @Override protected void onDetachedFromWindow() {
  setActive(false);
  super.onDetachedFromWindow();
 }

 @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
  float margin = 2.6f * density;
  rail.reset();
  if (width > 2 * margin && height > 2 * margin) {
   rail.addRoundRect(new RectF(margin, margin, width - margin, height - margin),
    15 * density, 15 * density, Path.Direction.CW);
  }
  circuit.setPath(rail, false);
 }

 @Override protected void onDraw(Canvas canvas) {
  super.onDraw(canvas);
  float length = circuit.getLength();
  if (length == 0) return;
  paint.setColor(0x68518b9a);
  paint.setStrokeWidth(density);
  canvas.drawPath(rail, paint);
  // Small etched facets sit inside the metal rail, outside the text area.
  paint.setColor(0x6674aab0);
  for (int i = 0; i < 5; i++) {
   float x = getWidth() - (20 + 5 * i) * density;
   float y = getHeight() - 5.6f * density;
   canvas.drawLine(x, y, x + 3.0f * density, y - 3.0f * density, paint);
  }
  trace(canvas, length, phase, .16f, 0xff52ffe0);
  trace(canvas, length, (phase + .41f) % 1f, .09f, 0xffffa663);
  trace(canvas, length, (phase + .68f) % 1f, .10f, 0xffa685ff);
  trace(canvas, length, (phase + .86f) % 1f, .05f, 0xffff7cc9);
 }

 private void trace(Canvas canvas, float length, float position, float span, int color) {
  float start = position * length;
  float end = start + span * length;
  highlight.reset();
  circuit.getSegment(start, Math.min(length, end), highlight, true);
  if (end > length) circuit.getSegment(0, end - length, highlight, true);
  paint.setColor(color);
  paint.setStrokeWidth(5f * density);
  paint.setAlpha(34);
  canvas.drawPath(highlight, paint);
  paint.setStrokeWidth(2.5f * density);
  paint.setAlpha(86);
  canvas.drawPath(highlight, paint);
  paint.setStrokeWidth(1.1f * density);
  paint.setAlpha(244);
  canvas.drawPath(highlight, paint);
 }
}
