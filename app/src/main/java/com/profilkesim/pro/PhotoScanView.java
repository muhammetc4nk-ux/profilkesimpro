package com.profilkesim.pro;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.LinearInterpolator;
import java.util.Collections;
import java.util.List;

/** Preview with a short scanning animation and the source rectangles used by the parser. */
final class PhotoScanView extends View {
 private static final int[] CELL_COLORS = {0x6029e6d2, 0x60936cf3, 0x60ffc85a};
 private final Bitmap image;
 private final List<RectF> highlights;
 private final boolean scanning;
 private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
 private final RectF picture = new RectF(), cell = new RectF();
 private final float density;
 private ValueAnimator animator;
 private float progress;

 PhotoScanView(Context context, Bitmap image, List<RectF> highlights, boolean scanning) {
  super(context);
  this.image = image;
  this.highlights = highlights == null ? Collections.emptyList() : highlights;
  this.scanning = scanning;
  density = getResources().getDisplayMetrics().density;
  setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
 }

 @Override protected void onAttachedToWindow() {
  super.onAttachedToWindow();
  if (InterfaceMotion.enabled(getContext())) {
   animator = ValueAnimator.ofFloat(0, 1);
   animator.setDuration(scanning ? 1600 : 1550);
   animator.setRepeatCount(scanning ? ValueAnimator.INFINITE : 0);
   animator.setInterpolator(new LinearInterpolator());
   animator.addUpdateListener(value -> {
    progress = (float) value.getAnimatedValue();
    invalidate();
   });
   animator.start();
  } else {
   progress = scanning ? .5f : 1;
  }
 }

 @Override protected void onDetachedFromWindow() {
  if (animator != null) animator.cancel();
  animator = null;
  super.onDetachedFromWindow();
 }

 @Override protected void onWindowVisibilityChanged(int visibility) {
  super.onWindowVisibilityChanged(visibility);
  if (animator == null || !animator.isStarted()) return;
  if (visibility == VISIBLE) animator.resume();
  else animator.pause();
 }

 @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
  float margin = 4 * density;
  if (image == null) picture.set(margin, margin, width - margin, height - margin);
  else {
   float scale = Math.min((width - 2 * margin) / image.getWidth(), (height - 2 * margin) / image.getHeight());
   float halfWidth = image.getWidth() * scale / 2, halfHeight = image.getHeight() * scale / 2;
   picture.set(width / 2f - halfWidth, height / 2f - halfHeight, width / 2f + halfWidth, height / 2f + halfHeight);
  }
 }

 @Override protected void onDraw(Canvas canvas) {
  canvas.drawColor(0xff132838);
  paint.setStyle(Paint.Style.FILL);
  paint.setColor(0xffffffff);
  if (image != null) canvas.drawBitmap(image, null, picture, paint);
  paint.setStrokeWidth(density);
  int index = 0;
  for (RectF bound : highlights) {
   cell.set(picture.left + bound.left * picture.width(), picture.top + bound.top * picture.height(),
    picture.left + bound.right * picture.width(), picture.top + bound.bottom * picture.height());
   paint.setColor(CELL_COLORS[index++ % CELL_COLORS.length]);
   paint.setStyle(Paint.Style.FILL);
   canvas.drawRoundRect(cell, 2 * density, 2 * density, paint);
   paint.setAlpha(215);
   paint.setStyle(Paint.Style.STROKE);
   canvas.drawRoundRect(cell, 2 * density, 2 * density, paint);
  }
  if (scanning || progress < 1) {
   float y = picture.top + progress * picture.height();
   float range = Math.min(22 * density, picture.height() / 2);
   canvas.save();
   canvas.clipRect(picture);
   paint.setStyle(Paint.Style.FILL);
   paint.setAlpha(255);
   paint.setShader(new LinearGradient(0, y - range, 0, y + range, new int[]{0x0041e0cd, 0x605ba4ff,
    0xc065fbde, 0x00ae73ef}, null, Shader.TileMode.CLAMP));
   canvas.drawRect(picture.left, y - range, picture.right, y + range, paint);
   paint.setShader(null);
   paint.setColor(0xffbbffed);
   canvas.drawRect(picture.left, y, picture.right, y + 1.6f * density, paint);
   canvas.restore();
  }
  paint.setStyle(Paint.Style.STROKE);
  paint.setColor(0xff65e8d0);
  paint.setStrokeWidth(density);
  canvas.drawRoundRect(picture, 4 * density, 4 * density, paint);
 }
}
