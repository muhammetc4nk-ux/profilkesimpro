package com.profilkesim.pro;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.FrameLayout;
import java.io.IOException;

final class BalconyBackdrop extends FrameLayout implements TextureView.SurfaceTextureListener {
 private final TextureView video;
 private final View tint;
 private MediaPlayer player;
 private Surface surface;
 private int videoWidth, videoHeight, resumePosition;
 private boolean active, prepared, unavailable;

 BalconyBackdrop(Context context) {
  super(context);
  setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
  setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
   new int[]{0xff142435, 0xff103743, 0xff101924}));
  video = new TextureView(context);
  video.setSurfaceTextureListener(this);
  video.setAlpha(0f);
  addView(video, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
  tint = new View(context);
  addView(tint, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
  setHome(false);
 }

 void setHome(boolean home) {
  int[] colors = home
   ? new int[]{0x8c091522, 0x470a1620, 0x180a141d, 0x6508111a}
   : new int[]{0xaa091522, 0x880a1620, 0x5c0a141d, 0xa608111a};
  tint.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, colors));
 }

 void setActive(boolean active) {
  this.active = active;
  if (active) startVideo();
  else releaseVideo();
 }

 private void startVideo() {
  SurfaceTexture texture = video.getSurfaceTexture();
  if (!active || unavailable || player != null || !video.isAvailable() || texture == null) return;
  try (AssetFileDescriptor movie = getResources().openRawResourceFd(R.raw.balcony_seascape)) {
   if (movie == null) throw new IOException("Video resource is missing");
   surface = new Surface(texture);
   player = new MediaPlayer();
   player.setSurface(surface);
   player.setDataSource(movie.getFileDescriptor(), movie.getStartOffset(), movie.getLength());
   player.setVolume(0f, 0f);
   player.setOnVideoSizeChangedListener((source, width, height) -> {
    if (source != player) return;
    videoWidth = width;
    videoHeight = height;
    fillView();
   });
   player.setOnPreparedListener(source -> {
    if (source != player || !active) return;
    try {
     prepared = true;
     source.setLooping(true);
     if (resumePosition > 0) source.seekTo(resumePosition);
     source.start();
    } catch (RuntimeException e) {
     videoUnavailable("Could not start balcony background", e);
    }
   });
   player.setOnInfoListener((source, what, extra) -> {
    if (source != player || what != MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) return false;
    video.animate().alpha(1f).setDuration(440).start();
    return true;
   });
   player.setOnErrorListener((source, what, extra) -> {
    if (source == player) videoUnavailable("Decoder error " + what + "/" + extra, null);
    return true;
   });
   player.prepareAsync();
  } catch (IOException | RuntimeException e) {
   videoUnavailable("Could not load balcony background", e);
  }
 }

 private void videoUnavailable(String message, Throwable cause) {
  Log.w("BalconyBackdrop", message, cause);
  unavailable = true;
  releaseVideo();
 }

 private void fillView() {
  int width = video.getWidth(), height = video.getHeight();
  if (width == 0 || height == 0 || videoWidth == 0 || videoHeight == 0) return;
  float scale = Math.max((float) width / videoWidth, (float) height / videoHeight);
  Matrix transform = new Matrix();
  transform.setScale(videoWidth * scale / width, videoHeight * scale / height, width / 2f, height / 2f);
  // Keep the warm sunset in frame on taller phones that crop the sides.
  transform.postTranslate(Math.max(0f, videoWidth * scale - width) * .30f, 0f);
  video.setTransform(transform);
 }

 private void releaseVideo() {
  video.animate().cancel();
  video.setAlpha(0f);
  if (player != null) {
   if (prepared && !unavailable) {
    try {
     resumePosition = player.getCurrentPosition();
    } catch (IllegalStateException ignored) {
     resumePosition = 0;
    }
   }
   prepared = false;
   player.release();
   player = null;
  }
  if (surface != null) {
   surface.release();
   surface = null;
  }
 }

 @Override public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
  startVideo();
 }

 @Override public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
  fillView();
 }

 @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
  releaseVideo();
  return true;
 }

 @Override public void onSurfaceTextureUpdated(SurfaceTexture texture) {}
}
