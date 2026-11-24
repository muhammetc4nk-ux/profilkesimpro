package com.profilkesim.pro;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.StateListAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.TextView;

final class InterfaceMotion {
 static final PathInterpolator ARRIVE = new PathInterpolator(.20f, .75f, .22f, 1f);
 private static final PathInterpolator RELEASE = new PathInterpolator(.20f, .72f, .28f, 1f);

 private InterfaceMotion() {}

 interface AnimationState { boolean isRunning(); }

 static boolean enabled(Context context) {
  PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
  if (power != null && power.isPowerSaveMode()) return false;
  if (Build.VERSION.SDK_INT >= 26) return ValueAnimator.areAnimatorsEnabled();
  return Settings.Global.getFloat(context.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0;
 }

 static void buttonFeedback(View view) {
  if (!enabled(view.getContext())) {
   view.setStateListAnimator(null);
   return;
  }
  StateListAnimator states = new StateListAnimator();
  states.addState(new int[]{android.R.attr.state_enabled, android.R.attr.state_pressed}, scaleTo(view, .972f, 95));
  states.addState(new int[]{}, scaleTo(view, 1f, 240));
  view.setStateListAnimator(states);
 }

 private static ObjectAnimator scaleTo(View view, float scale, int duration) {
  ObjectAnimator animator = ObjectAnimator.ofPropertyValuesHolder(view,
   PropertyValuesHolder.ofFloat(View.SCALE_X, scale), PropertyValuesHolder.ofFloat(View.SCALE_Y, scale));
  animator.setDuration(duration);
  animator.setInterpolator(RELEASE);
  return animator;
 }

 static void redraw(View view) {
  if (view == null) return;
  view.invalidate();
  if (view instanceof ViewGroup) {
   ViewGroup group = (ViewGroup) view;
   for (int i = 0; i < group.getChildCount(); i++) redraw(group.getChildAt(i));
  }
 }

 static final class PanelLights extends Drawable {
  private static final int[] TOP = {0xfff258c9, 0xff9878ff, 0xff42efdf, 0xff92f785, 0xffffcb71, 0xfff258c9};
  private static final int[] BOTTOM = {0xff36eacc, 0xff5087fb, 0xffd180ff, 0xffff9b82, 0xff36eacc};
  private final AnimationState motion;
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Matrix shift = new Matrix();
  private final float density;
  private final Runnable frame = () -> invalidateSelf();
  private LinearGradient top, bottom;
  private float span;

  PanelLights(float density, AnimationState motion) {
   this.density = density;
   this.motion = motion;
  }

  @Override protected void onBoundsChange(Rect bounds) {
   span = Math.max(1, bounds.width() * .85f);
   top = new LinearGradient(0, 0, span, 0, TOP, null, Shader.TileMode.REPEAT);
   bottom = new LinearGradient(0, 0, span, 0, BOTTOM, null, Shader.TileMode.REPEAT);
  }

  @Override public void draw(Canvas canvas) {
   Rect bounds = getBounds();
   float inset = 17 * density;
   float left = bounds.left + inset;
   float right = bounds.right - inset;
   if (top == null || right <= left || bounds.height() < 14 * density) return;
   long time = SystemClock.uptimeMillis();
   boolean running = motion.isRunning();
   float phase = running ? (time % 3600L) / 3600f : .26f;
   rail(canvas, top, left, right, bounds.top + 5.2f * density, phase, .68f, .9f);
   rail(canvas, bottom, left, right, bounds.bottom - 5.2f * density, 1 - phase, .34f, .62f);
   if (running && isVisible()) {
    unscheduleSelf(frame);
    scheduleSelf(frame, time + 32);
   }
  }

  private void rail(Canvas canvas, LinearGradient color, float left, float right, float y,
                    float phase, float reach, float opacity) {
   shift.setTranslate(left + span * phase, 0);
   color.setLocalMatrix(shift);
   paint.setShader(color);
   paint.setStrokeCap(Paint.Cap.ROUND);
   paint.setAlpha((int) (65 * opacity));
   paint.setStrokeWidth(4.4f * density);
   canvas.drawLine(left, y, right, y, paint);
   paint.setAlpha((int) (120 * opacity));
   paint.setStrokeWidth(.8f * density);
   canvas.drawLine(left, y, right, y, paint);
   float anchor = left + (right - left) * phase * (1 - reach);
   paint.setAlpha((int) (255 * opacity));
   paint.setStrokeWidth(1.8f * density);
   canvas.drawLine(anchor, y, anchor + (right - left) * reach, y, paint);
  }

  @Override public void setAlpha(int alpha) { }
  @Override public void setColorFilter(ColorFilter filter) { }
  @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
 }

 static final class Tagline extends TextView {
  private static final int[] COLORS = {0xffffa8db, 0xffc8aaff, 0xff80f3ff, 0xffa2ffb1, 0xffffdb8f, 0xffffa8db};
  private final AnimationState motion;
  private final Matrix shift = new Matrix();
  private LinearGradient colors;
  private float span;

  Tagline(Context context, AnimationState motion) {
   super(context);
   this.motion = motion;
  }

  @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
   super.onSizeChanged(width, height, oldWidth, oldHeight);
   span = Math.max(48, width * 1.25f);
   colors = new LinearGradient(0, 0, span, 0, COLORS, null, Shader.TileMode.REPEAT);
  }

  @Override protected void onDraw(Canvas canvas) {
   boolean running = motion.isRunning();
   if (colors != null) {
    float phase = running ? (SystemClock.uptimeMillis() % 1180L) / 1180f : 0f;
    shift.setTranslate(-span * phase, 0);
    colors.setLocalMatrix(shift);
    getPaint().setShader(colors);
   }
   super.onDraw(canvas);
   if (running) postInvalidateDelayed(25);
  }
 }
}
