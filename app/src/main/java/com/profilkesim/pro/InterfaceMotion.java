package com.profilkesim.pro;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.StateListAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.view.animation.PathInterpolator;

final class InterfaceMotion {
 static final PathInterpolator ARRIVE = new PathInterpolator(.20f, .75f, .22f, 1f);
 private static final PathInterpolator RELEASE = new PathInterpolator(.20f, .72f, .28f, 1f);

 private InterfaceMotion() {}

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
}
