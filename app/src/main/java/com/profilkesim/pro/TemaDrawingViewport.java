package com.profilkesim.pro;

import android.content.Context;
import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewConfiguration;

abstract class TemaDrawingViewport extends View {
 private final ScaleGestureDetector pinch;
 private final int touchSlop;
 private float zoom = 1, panX, panY, downX, downY, lastX, lastY;
 private boolean moved, multiTouch;

 TemaDrawingViewport(Context context) {
  super(context);
  touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
  pinch = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
   @Override public boolean onScale(ScaleGestureDetector detector) {
    float next = Math.max(1, Math.min(6, zoom * detector.getScaleFactor()));
    float ratio = next / zoom;
    float focusX = detector.getFocusX() - getWidth() / 2f;
    float focusY = detector.getFocusY() - getHeight() / 2f;
    panX = focusX - (focusX - panX) * ratio;
    panY = focusY - (focusY - panY) * ratio;
    zoom = next;
    clampPan();
    invalidate();
    return true;
   }
  });
  pinch.setQuickScaleEnabled(false);
  setClickable(true);
  setContentDescription("Doğrama çizimi. İki parmakla yakınlaştırın. Seçili aracı uygulamak için dokunun.");
 }

 void fit() {
  zoom = 1;
  panX = panY = 0;
  invalidate();
 }

 @Override protected final void onDraw(Canvas canvas) {
  super.onDraw(canvas);
  canvas.drawColor(0xff061426);
  canvas.save();
  canvas.translate(panX, panY);
  canvas.scale(zoom, zoom, getWidth() / 2f, getHeight() / 2f);
  drawBoard(canvas);
  canvas.restore();
 }

 protected abstract void drawBoard(Canvas canvas);
 protected abstract void tap(float x, float y);

 private void clampPan() {
  float xLimit = getWidth() * (zoom - 1) / 2;
  float yLimit = getHeight() * (zoom - 1) / 2;
  panX = Math.max(-xLimit, Math.min(xLimit, panX));
  panY = Math.max(-yLimit, Math.min(yLimit, panY));
 }

 @Override public boolean onTouchEvent(MotionEvent event) {
  pinch.onTouchEvent(event);
  float x = event.getX(), y = event.getY();
  switch (event.getActionMasked()) {
   case MotionEvent.ACTION_DOWN:
    downX = lastX = x;
    downY = lastY = y;
    moved = multiTouch = false;
    getParent().requestDisallowInterceptTouchEvent(true);
    break;
   case MotionEvent.ACTION_POINTER_DOWN:
    multiTouch = true;
    break;
   case MotionEvent.ACTION_POINTER_UP:
    int remaining = event.getActionIndex() == 0 ? 1 : 0;
    lastX = event.getX(remaining);
    lastY = event.getY(remaining);
    break;
   case MotionEvent.ACTION_MOVE:
    if (event.getPointerCount() == 1) {
     if (Math.hypot(x - downX, y - downY) > touchSlop) moved = true;
     if (moved || multiTouch) {
      panX += x - lastX;
      panY += y - lastY;
      clampPan();
      invalidate();
     }
     lastX = x;
     lastY = y;
    }
    break;
   case MotionEvent.ACTION_UP:
    getParent().requestDisallowInterceptTouchEvent(false);
    if (!moved && !multiTouch) {
     performClick();
     float cx = getWidth() / 2f, cy = getHeight() / 2f;
     tap(cx + (x - cx - panX) / zoom, cy + (y - cy - panY) / zoom);
    }
    break;
   case MotionEvent.ACTION_CANCEL:
    getParent().requestDisallowInterceptTouchEvent(false);
    break;
  }
  return true;
 }

 @Override public boolean performClick() {
  super.performClick();
  return true;
 }
}
