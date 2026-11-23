package com.profilkesim.pro;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

final class DrawingMeasureView extends View {
 private static final int[] COLORS = {0xffffce83, 0xff54f4e3, 0xff91b8ff};
 private final Bitmap bitmap;
 private final Runnable onChange;
 private final boolean selectArea;
 private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
 private final RectF picture = new RectF(), displayed = new RectF();
 private final Matrix matrix = new Matrix(), inverse = new Matrix();
 private final List<PointF> points = new ArrayList<>();
 private final float density;
 private final ScaleGestureDetector zoom;
 private final GestureDetector tapAndDrag;
 private boolean pinch;
 private float scale = 1f, shiftX, shiftY;

 DrawingMeasureView(Context context, Bitmap bitmap, Runnable onChange) {
  this(context, bitmap, onChange, false);
 }

 DrawingMeasureView(Context context, Bitmap bitmap, Runnable onChange, boolean selectArea) {
  super(context);
  this.bitmap = bitmap;
  this.onChange = onChange;
  this.selectArea = selectArea;
  density = getResources().getDisplayMetrics().density;
  picture.set(0, 0, bitmap.getWidth(), bitmap.getHeight());
  setContentDescription(selectArea ? "Kesim tablosu alanı seçilen fotoğraf" : "Ölçek ve dış kasa ölçüleri için nokta seçilen çizim");
  zoom = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
   @Override public boolean onScale(ScaleGestureDetector detector) {
    float next = Math.max(1f, Math.min(6f, scale * detector.getScaleFactor()));
    float factor = next / scale;
    shiftX += (1 - factor) * (detector.getFocusX() - getWidth() / 2f - shiftX);
    shiftY += (1 - factor) * (detector.getFocusY() - getHeight() / 2f - shiftY);
    scale = next;
    updateMatrix();
    return true;
   }
  });
  tapAndDrag = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
   @Override public boolean onDown(MotionEvent event) { return true; }

   @Override public boolean onSingleTapUp(MotionEvent event) {
    if (!pinch && points.size() < (selectArea ? 2 : 6) && displayed.contains(event.getX(), event.getY())) {
     float[] position = {event.getX(), event.getY()};
     inverse.mapPoints(position);
     points.add(new PointF(position[0], position[1]));
     onChange.run();
     invalidate();
     performClick();
    }
    return true;
   }

   @Override public boolean onScroll(MotionEvent start, MotionEvent now, float x, float y) {
    if (!zoom.isInProgress() && scale > 1f) {
     shiftX -= x;
     shiftY -= y;
     updateMatrix();
    }
    return true;
   }
  });
 }

 int pointCount() { return points.size(); }

 RectF selectedArea() {
  if (points.size() != 2) throw new IllegalArgumentException("Tablonun karşılıklı iki köşesini seçin.");
  PointF first = points.get(0), second = points.get(1);
  RectF area = new RectF(Math.min(first.x, second.x) / picture.width(), Math.min(first.y, second.y) / picture.height(),
   Math.max(first.x, second.x) / picture.width(), Math.max(first.y, second.y) / picture.height());
  if (area.width() < .04f || area.height() < .04f) throw new IllegalArgumentException("Kesim tablosu alanı çok küçük.");
  return area;
 }

 void undo() {
  if (!points.isEmpty()) points.remove(points.size() - 1);
  onChange.run();
  invalidate();
 }

 DrawingDimensions measurements(int reference) {
  if (points.size() != 6) throw new IllegalArgumentException("Referans, en ve yükseklik için ikişer nokta seçin.");
  PointF w0 = points.get(2), w1 = points.get(3), h0 = points.get(4), h1 = points.get(5);
  double width = distance(2), height = distance(4);
  double dot = (w1.x - w0.x) * (h1.x - h0.x) + (w1.y - w0.y) * (h1.y - h0.y);
  if (Math.abs(dot) > width * height * .25) {
   throw new IllegalArgumentException("Kasa eni ile yüksekliği yaklaşık dik olmalı. Noktaları kontrol edin.");
  }
  return DrawingDimensions.scaled(reference, distance(0), width, height);
 }

 private double distance(int i) {
  PointF a = points.get(i), b = points.get(i + 1);
  return Math.hypot(a.x - b.x, a.y - b.y);
 }

 @Override public boolean onTouchEvent(MotionEvent event) {
  if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
   pinch = false;
   getParent().requestDisallowInterceptTouchEvent(true);
  } else if (event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) {
   pinch = true;
  }
  zoom.onTouchEvent(event);
  tapAndDrag.onTouchEvent(event);
  return true;
 }

 @Override public boolean performClick() {
  super.performClick();
  return true;
 }

 @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
  updateMatrix();
 }

 private void updateMatrix() {
  float factor = Math.min(getWidth() / picture.width(), getHeight() / picture.height()) * scale;
  float travelX = Math.max(0, (factor * picture.width() - getWidth()) / 2f);
  float travelY = Math.max(0, (factor * picture.height() - getHeight()) / 2f);
  shiftX = Math.max(-travelX, Math.min(travelX, shiftX));
  shiftY = Math.max(-travelY, Math.min(travelY, shiftY));
  matrix.setScale(factor, factor);
  matrix.postTranslate((getWidth() - picture.width() * factor) / 2f + shiftX,
   (getHeight() - picture.height() * factor) / 2f + shiftY);
  matrix.invert(inverse);
  matrix.mapRect(displayed, picture);
  invalidate();
 }

 @Override protected void onDraw(Canvas canvas) {
  canvas.drawColor(0xff101d28);
  paint.setColor(0xffffffff);
  canvas.drawBitmap(bitmap, matrix, paint);
  float[] mapped = new float[points.size() * 2];
  for (int i = 0; i < points.size(); i++) {
   mapped[2 * i] = points.get(i).x;
   mapped[2 * i + 1] = points.get(i).y;
  }
  matrix.mapPoints(mapped);
  if (selectArea && points.size() == 2) {
   float left = Math.min(mapped[0], mapped[2]), top = Math.min(mapped[1], mapped[3]);
   float right = Math.max(mapped[0], mapped[2]), bottom = Math.max(mapped[1], mapped[3]);
   paint.setColor(0x4049e5d8);
   canvas.drawRect(left, top, right, bottom, paint);
   paint.setStyle(Paint.Style.STROKE);
   paint.setColor(0xff67f8d8);
   paint.setStrokeWidth(2 * density);
   canvas.drawRect(left, top, right, bottom, paint);
   paint.setStyle(Paint.Style.FILL);
  }
  for (int i = 0; i < points.size(); i++) {
   float x = mapped[2 * i], y = mapped[2 * i + 1];
   paint.setColor(COLORS[i / 2]);
   paint.setStrokeWidth(2f * density);
   if (!selectArea && i % 2 == 1) canvas.drawLine(mapped[2 * i - 2], mapped[2 * i - 1], x, y, paint);
   canvas.drawCircle(x, y, 5f * density, paint);
   paint.setColor(0xff122330);
   canvas.drawCircle(x, y, 2f * density, paint);
  }
 }
}
