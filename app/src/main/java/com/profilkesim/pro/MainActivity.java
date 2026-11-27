package com.profilkesim.pro;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.net.Uri;
import android.provider.MediaStore;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
 static final int DEFAULT_PROFILE_STOCK = 6000;
 static final int DEFAULT_KERF = 4;
 LinearLayout root,body;
 private FrameLayout shell;
 private BalconyBackdrop backdrop;
 private LedTrace mastheadTrace;
 private boolean rgbRunning;
 private TemaWorkshop temaWorkshop;
 private final Map<TemaCatalog.Series, TemaWorkshop> temaWorkshops = new java.util.EnumMap<>(TemaCatalog.Series.class);
 private final PhotoReview generalReview = new PhotoReview("Fotoğraflardan kesim listesi", null, null);
 private final PhotoReview albertReview = new PhotoReview("Albert Genau İmalat Kesim Listesi", null, null);
 private PhotoReview photoReview = generalReview;
 private boolean photoBatchRunning;
 private LinearLayout scanPanel;
 private Runnable savePhotoReview;
 Runnable pageBack;

 private static final class PhotoReview {
  final String title;
  final PhotoResult<Map<Integer, List<CutRow>>> accept;
  final Runnable back;
  final Map<Integer, List<CutRow>> detected = new LinkedHashMap<>();
  final Map<String, String> stockDrafts = new HashMap<>();
  final Map<Integer, String> failedPhotos = new LinkedHashMap<>(), sourceNames = new LinkedHashMap<>();
  final Map<Integer, Uri> photoSources = new LinkedHashMap<>();
  final Map<Integer, CutPhotoReader.Result> scans = new LinkedHashMap<>();
  String[] extraDraft = {"", "", "", String.valueOf(DEFAULT_PROFILE_STOCK), String.valueOf(DEFAULT_KERF)};
  Uri cameraUri;
  int photoNumber;

  PhotoReview(String title, PhotoResult<Map<Integer, List<CutRow>>> accept, Runnable back) {
   this.title = title;
   this.accept = accept;
   this.back = back;
  }

  String sourceName(int number) {
   String name = sourceNames.get(number);
   return name == null ? "Fotoğraf " + number : name;
  }
 }
 static class CutRow {
  String code, name;
  final int length, qty;
  final Bitmap icon;
  String[] draft;
  CutRow(String code, String name, int length, int qty, Bitmap icon) {
   this.code = code;
   this.name = name;
   this.length = length;
   this.qty = qty;
   this.icon = icon;
  }
 }
 static final int PICK_IMAGE=41,TAKE_PHOTO=42;
 int navy=0xff173447,teal=0xff087e89;
 int dp(int n){return (int)(n*getResources().getDisplayMetrics().density);}
 TextView text(String s,int size){
  TextView t=new TextView(this);t.setText(s);t.setTextSize(size);
  t.setTextColor(navy);t.setPadding(dp(12),dp(12),dp(12),dp(12));return t;
 }
 private static final String MUSIC_MUTED = "home_music_muted";
 private static final String MUSIC_SETTINGS = "MainActivity";
 private static boolean musicScreenResumed;
 private Button musicButton;
 private final android.content.SharedPreferences.OnSharedPreferenceChangeListener musicSettingsChanged =
  (preferences, key) -> {
   if (MUSIC_MUTED.equals(key)) updateMusicButton();
  };

 private void updateMusicButton() {
  if (musicButton == null) return;
  boolean muted = getPreferences(MODE_PRIVATE).getBoolean(MUSIC_MUTED, false);
  musicButton.setText(muted ? "Sesi aç" : "Sustur");
  musicButton.setContentDescription(muted ? "Müziği aç" : "Müziği sustur");
 }

 private void updateMusicService() {
  Intent command = new Intent(this, MusicService.class);
  if (!musicScreenResumed || getPreferences(MODE_PRIVATE).getBoolean(MUSIC_MUTED, false)) {
   stopService(command);
  } else if (hasWindowFocus()) {
   try {
    startService(command);
   } catch (IllegalStateException ignored) {
    // A concurrent screen transition can still make Android deny the start.
    // Resume or the next focus gain will retry if music is enabled.
   }
  }
 }

 public static final class MusicService extends android.app.Service {
  private static final String CHANNEL = "profile_music";
  private static final String MUTE_ACTION = "com.profilkesim.pro.MUTE_MUSIC";
  private static final int NOTIFICATION = 31;
  private android.media.AudioManager audio;
  private android.media.MediaPlayer player;
  private boolean destroyed, prepared, focused, focusRequested;

  private final android.media.AudioManager.OnAudioFocusChangeListener focusListener = change -> {
   if (change == android.media.AudioManager.AUDIOFOCUS_LOSS) {
    stopSelf();
    return;
   }
   focused = change == android.media.AudioManager.AUDIOFOCUS_GAIN;
   applyPlayback();
  };

  @Override public android.os.IBinder onBind(Intent intent) { return null; }

  @Override public int onStartCommand(Intent intent, int flags, int startId) {
   android.content.SharedPreferences settings = getSharedPreferences(MUSIC_SETTINGS, MODE_PRIVATE);
   if (intent != null && MUTE_ACTION.equals(intent.getAction())) {
    settings.edit().putBoolean(MUSIC_MUTED, true).apply();
    stopSelf();
    return START_NOT_STICKY;
   }
   if (!mayPlay()) {
    stopSelf();
    return START_NOT_STICKY;
   }
   startForeground(NOTIFICATION, playbackNotification());
   if (player == null) {
    Uri track = new Uri.Builder().scheme(android.content.ContentResolver.SCHEME_ANDROID_RESOURCE)
     .authority(getPackageName()).appendPath(String.valueOf(R.raw.home_music)).build();
    prepareTrack(track);
   }
   return START_NOT_STICKY;
  }

  private boolean mayPlay() {
   return musicScreenResumed && !getSharedPreferences(MUSIC_SETTINGS, MODE_PRIVATE).getBoolean(MUSIC_MUTED, false);
  }

  private android.app.Notification playbackNotification() {
   android.app.NotificationManager manager = (android.app.NotificationManager) getSystemService(NOTIFICATION_SERVICE);
   android.app.Notification.Builder notification;
   if (android.os.Build.VERSION.SDK_INT >= 26) {
    manager.createNotificationChannel(new android.app.NotificationChannel(CHANNEL,
     "ProfilKesimPro müzik", android.app.NotificationManager.IMPORTANCE_LOW));
    notification = new android.app.Notification.Builder(this, CHANNEL);
   } else {
    notification = new android.app.Notification.Builder(this);
   }
   Intent open = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
   int pendingFlags = android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE;
   android.app.PendingIntent openApp = android.app.PendingIntent.getActivity(this, 0, open, pendingFlags);
   Intent mute = new Intent(this, MusicService.class).setAction(MUTE_ACTION);
   android.app.PendingIntent muteAction = android.app.PendingIntent.getService(this, 1, mute, pendingFlags);
   return notification.setSmallIcon(android.R.drawable.ic_media_play)
    .setContentTitle("ProfilKesimPro").setContentText("Uygulama müziği")
    .setContentIntent(openApp).setOngoing(true).setOnlyAlertOnce(true)
    .addAction(new android.app.Notification.Action.Builder(android.R.drawable.ic_media_pause,
     "Sustur", muteAction).build()).build();
  }

  private void prepareTrack(Uri source) {
   try {
    player = new android.media.MediaPlayer();
    player.setAudioAttributes(new android.media.AudioAttributes.Builder()
     .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
     .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC).build());
    player.setWakeMode(this, android.os.PowerManager.PARTIAL_WAKE_LOCK);
    player.setDataSource(this, source);
    player.setOnErrorListener((failed, what, extra) -> {
     if (failed == player && !destroyed) playbackError();
     return true;
    });
    player.setOnPreparedListener(ready -> {
     if (ready != player || destroyed) return;
    if (!mayPlay()) {
     stopSelf();
     return;
    }
     prepared = true;
     ready.setLooping(true);
     audio = (android.media.AudioManager) getSystemService(AUDIO_SERVICE);
     if (!focusRequested && audio != null) {
      focusRequested = audio.requestAudioFocus(focusListener, android.media.AudioManager.STREAM_MUSIC,
       android.media.AudioManager.AUDIOFOCUS_GAIN) == android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
      focused = focusRequested;
     }
     if (focusRequested) applyPlayback();
     else stopSelf();
    });
    player.prepareAsync();
   } catch (java.io.IOException | RuntimeException e) {
    playbackError();
   }
  }

  private void applyPlayback() {
   if (player == null || !prepared) return;
   if (focused && mayPlay()) player.start();
   else if (player.isPlaying()) player.pause();
  }

  private void playbackError() {
   if (mayPlay()) Toast.makeText(this, "Uygulama müziği açılamadı.", Toast.LENGTH_LONG).show();
   stopSelf();
  }

  private void releaseTrack() {
   prepared = false;
   if (player != null) {
    player.release();
    player = null;
   }
  }

  @Override public void onDestroy() {
   destroyed = true;
   releaseTrack();
   if (focusRequested && audio != null) audio.abandonAudioFocus(focusListener);
   stopForeground(true);
   super.onDestroy();
  }
 }

 void page(String title) {
  page(title, false);
 }

 void page(String title, boolean home) {
  savePhotoReview = null;
  pageBack = null;
  scanPanel = null;
  musicButton = null;
  if (shell == null) {
   shell = new FrameLayout(this);
   backdrop = new BalconyBackdrop(this);
   shell.addView(backdrop, new FrameLayout.LayoutParams(-1, -1));
   setContentView(shell);
  } else {
   if (mastheadTrace != null) mastheadTrace.setActive(false);
   shell.removeView(root);
  }
  backdrop.setHome(home);
  root=new LinearLayout(this);root.setOrientation(1);
  shell.addView(root, new FrameLayout.LayoutParams(-1, -1));
  FrameLayout header = new FrameLayout(this);
  header.setBackground(rgbPanel(0xf9142332, 0xf9193440, 0xff58838c));
  LinearLayout masthead = new LinearLayout(this);
  masthead.setGravity(Gravity.CENTER_VERTICAL);
  masthead.setMinimumHeight(dp(73));
  masthead.setPadding(dp(12), dp(8), dp(12), dp(8));

  ImageView appIcon = new ImageView(this);
  appIcon.setImageResource(R.drawable.cannuryapi_icon);
  appIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
  appIcon.setPadding(dp(4), dp(4), dp(4), dp(4));
  appIcon.setBackground(rgbPanel(0xff172a36, 0xff194451, 0xff62929b));
  appIcon.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO);
  masthead.addView(appIcon, new LinearLayout.LayoutParams(dp(43), dp(43)));

  LinearLayout identity = new LinearLayout(this);
  identity.setOrientation(LinearLayout.VERTICAL);
  LinearLayout.LayoutParams identitySize = new LinearLayout.LayoutParams(0, -2, 1f);
  identitySize.setMarginStart(dp(9));
  identitySize.setMarginEnd(dp(7));
  masthead.addView(identity, identitySize);

  TextView brand = text("MUHAMMET CAN . K", 16);
  brand.setTextColor(0xffffdfa6);
  brand.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
  brand.setLetterSpacing(.055f);
  brand.setPadding(0, 0, 0, 0);
  identity.addView(brand);

  TextView tagline = new InterfaceMotion.Tagline(this, this::rgbRunning);
  tagline.setText("Yeni gelmedik, geri geldik");
  tagline.setTextSize(13);
  tagline.setTypeface(null, Typeface.BOLD);
  tagline.setTextColor(0xffc7f9ef);
  tagline.setShadowLayer(dp(3), 0, 0, 0x8a995dff);
  tagline.setPadding(0, dp(2), 0, 0);
  identity.addView(tagline);

  View light = new View(this);
  GradientDrawable lightColor = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
   new int[]{0xfff8cc8c, 0xff8cdedb, 0x008cdedb});
  lightColor.setCornerRadius(dp(2));
  light.setBackground(lightColor);
  LinearLayout.LayoutParams lightSize = new LinearLayout.LayoutParams(dp(60), dp(2));
  lightSize.topMargin = dp(5);
  identity.addView(light, lightSize);

  musicButton = new Button(this);
  musicButton.setAllCaps(false);
  musicButton.setTextSize(13);
  musicButton.setTextColor(Color.WHITE);
  musicButton.setPadding(dp(9), 0, dp(9), 0);
  musicButton.setBackground(new android.graphics.drawable.RippleDrawable(
   android.content.res.ColorStateList.valueOf(0x66a1fff3),
   rgbPanel(0xff172e3b, 0xff1b4651, 0xff5e98a1), null));
  InterfaceMotion.buttonFeedback(musicButton);
  masthead.addView(musicButton, new LinearLayout.LayoutParams(-2, dp(48)));
  header.addView(masthead, new FrameLayout.LayoutParams(-1, -2));
  LedTrace trace = new LedTrace(this);
  mastheadTrace = trace;
  header.getOverlay().add(trace);
  header.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
   trace.layout(0, 0, right - left, bottom - top));
  LinearLayout.LayoutParams headerSize = new LinearLayout.LayoutParams(-1, -2);
  headerSize.setMargins(dp(10), dp(7), dp(10), dp(4));
  root.addView(header, headerSize);
  mastheadTrace.setActive(musicScreenResumed && InterfaceMotion.enabled(this));
  updateMusicButton();
  musicButton.setOnClickListener(view -> {
   android.content.SharedPreferences settings = getPreferences(MODE_PRIVATE);
   boolean muted = !settings.getBoolean(MUSIC_MUTED, false);
   settings.edit().putBoolean(MUSIC_MUTED, muted).apply();
   updateMusicButton();
   updateMusicService();
  });

  ScrollView scroll=new ScrollView(this);
  scroll.setFillViewport(true);
  root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
  body=new LinearLayout(this);body.setOrientation(1);
  body.setPadding(dp(14),dp(8),dp(14),dp(22));scroll.addView(body);
  TextView h=text(title,home ? 31 : 24);
  h.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
  h.setTextColor(Color.WHITE);
  if (home) {
   h.setPadding(dp(8), dp(20), dp(8), dp(3));
   h.setShadowLayer(dp(9), 0, dp(1), 0xff07131d);
   body.addView(h);
  } else {
   addTextPanel(h, 0xff132432, 0xff173b47, 0xff548b95);
  }
  if (!home) reveal(body, 0);
 }
 void note(String s){
  TextView t = text(s, 14);
  t.setTextColor(0xffdff5ff);
  addTextPanel(t, 0xff14223a, 0xff243f5f, 0xff4f7b9b);
 }
 Button button(String s,Runnable r){
  Button b=new Button(this);b.setText(s);b.setAllCaps(false);
  b.setTextColor(Color.WHITE);
  b.setBackground(new android.graphics.drawable.RippleDrawable(
   android.content.res.ColorStateList.valueOf(0x66a1fff3),
   rgbPanel(0xff152a3b, 0xff1a5360, 0xff64969f), null));
  b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
  b.setPadding(dp(12), 0, dp(12), 0);
  b.setElevation(dp(4));
  InterfaceMotion.buttonFeedback(b);
  LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(54));
  p.setMargins(0,dp(6),0,dp(6));body.addView(b,p);
  if (body.getChildCount() < 10) reveal(b, body.getChildCount() * 24);
  b.setOnClickListener(v -> r.run());
  return b;
 }

 void homeButton(String label, int iconResource, Runnable action) {
  homeButton(label, iconResource, 0xff9ae9e4, action);
 }

 void homeButton(String label, int iconResource, int accent, Runnable action) {
  LinearLayout tile = new LinearLayout(this);
  tile.setGravity(Gravity.CENTER_VERTICAL);
  tile.setMinimumHeight(dp(59));
  tile.setPaddingRelative(dp(15), dp(9), dp(15), dp(9));
  tile.setBackground(new android.graphics.drawable.RippleDrawable(
   android.content.res.ColorStateList.valueOf(0x5894f6ef),
   rgbPanel(0xff142836, 0xff164550, 0xff6ba6aa), null));
  ImageView icon = new ImageView(this);
  icon.setImageResource(iconResource);
  icon.setColorFilter(accent);
  icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
  tile.addView(icon, new LinearLayout.LayoutParams(dp(24), dp(24)));
  TextView caption = text(label, 16);
  caption.setTextColor(0xfff5fbff);
  caption.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
  caption.setPaddingRelative(dp(13), 0, 0, 0);
  caption.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
  tile.addView(caption, new LinearLayout.LayoutParams(0, -2, 1f));
  tile.setContentDescription(label);
  tile.setFocusable(true);
  tile.setAccessibilityDelegate(new View.AccessibilityDelegate() {
   @Override public void onInitializeAccessibilityNodeInfo(View host, android.view.accessibility.AccessibilityNodeInfo info) {
    super.onInitializeAccessibilityNodeInfo(host, info);
    info.setClassName(Button.class.getName());
   }
  });
  tile.setOnClickListener(view -> action.run());
  InterfaceMotion.buttonFeedback(tile);
  LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(-1, -2);
  size.setMargins(0, dp(5), 0, dp(5));
  body.addView(tile, size);
  reveal(tile, body.getChildCount() * 26);
 }
 EditText input(String hint,String val){
  return input(body, hint, val);
 }
 EditText input(LinearLayout parent, String hint, String val){
  TextView label=text(hint,15);label.setTypeface(null,Typeface.BOLD);
  label.setTextColor(0xfff0f9ff);
  addTextPanel(parent, label, 0xff19253f, 0xff254965, 0xff627ea7);
  EditText e=new EditText(this);e.setText(val);e.setHint(hint);
  e.setTextSize(17);e.setPadding(dp(12),dp(12),dp(12),dp(12));
  e.setTextColor(0xffeffaff);
  e.setHintTextColor(0xffb8cde1);
  e.setBackground(rgbPanel(0xff162440, 0xff223c59, 0xff5684a4));
  e.setOnFocusChangeListener((view, focused) -> {
   e.setBackground(focused
    ? rgbPanel(0xff123649, 0xff175865, 0xff56ffe1)
    : rgbPanel(0xff162440, 0xff223c59, 0xff5684a4));
   e.setShadowLayer(focused ? dp(4) : 0, 0, 0, 0xff4eeacc);
   if (InterfaceMotion.enabled(this)) {
    e.animate().translationZ(dp(focused ? 5 : 0)).setDuration(220).setInterpolator(InterfaceMotion.ARRIVE).start();
   } else {
    e.setTranslationZ(dp(focused ? 5 : 0));
   }
  });
  LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(55));
  p.setMargins(0,dp(6),0,dp(6));parent.addView(e,p);return e;
 }
 int val(EditText e){return Integer.parseInt(e.getText().toString().trim());}
 void result(String s){
  TextView t=text(s,16);t.setTextIsSelectable(true);
  t.setTextColor(0xffeffaff);
  addTextPanel(t, 0xff14233e, 0xff285575, 0xff4ecade);
  reveal(t, 0);
 }
 void bad(){Toast.makeText(this,"Ölçü veya adet hatalı",Toast.LENGTH_LONG).show();}
 @Override public void onCreate(Bundle state) {
  super.onCreate(state);
  getWindow().setStatusBarColor(0xff0b1925);
  getWindow().setNavigationBarColor(0xff081620);
  setVolumeControlStream(android.media.AudioManager.STREAM_MUSIC);
  getPreferences(MODE_PRIVATE).registerOnSharedPreferenceChangeListener(musicSettingsChanged);
  for (TemaCatalog.Series series : TemaCatalog.Series.values()) {
   TemaWorkshop workshop = new TemaWorkshop(this, series);
   temaWorkshops.put(series, workshop);
   Bundle saved = state == null ? null : state.getBundle("workshop." + series.name());
   if (saved == null && state != null) {
    int[] legacy = state.getIntArray("temaSelection");
    if (legacy != null && legacy.length > 0 && legacy[0] == series.ordinal()) saved = state;
   }
   if (workshop.restoreState(saved)) temaWorkshop = workshop;
  }
  if (temaWorkshop == null) home();
 }
 @Override protected void onSaveInstanceState(Bundle out) {
  super.onSaveInstanceState(out);
  for (Map.Entry<TemaCatalog.Series, TemaWorkshop> entry : temaWorkshops.entrySet()) {
   Bundle workshop = new Bundle();
   entry.getValue().saveState(workshop);
   out.putBundle("workshop." + entry.getKey().name(), workshop);
  }
 }
 @Override protected void onResume() {
  super.onResume();
  musicScreenResumed = true;
  updateMusicButton();
  updateMusicService();
  boolean motion = InterfaceMotion.enabled(this);
  rgbRunning = motion;
  InterfaceMotion.redraw(root);
  if (backdrop != null) backdrop.setActive(motion);
  if (mastheadTrace != null) mastheadTrace.setActive(motion);
 }
 @Override public void onWindowFocusChanged(boolean hasFocus) {
  super.onWindowFocusChanged(hasFocus);
  if (hasFocus && musicScreenResumed) updateMusicService();
 }
 @Override protected void onPause() {
  musicScreenResumed = false;
  rgbRunning = false;
  if (backdrop != null) backdrop.setActive(false);
  if (mastheadTrace != null) mastheadTrace.setActive(false);
  updateMusicService();
  if (savePhotoReview != null) savePhotoReview.run();
  super.onPause();
 }
 @Override protected void onDestroy() {
  if (backdrop != null) backdrop.setActive(false);
  getPreferences(MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(musicSettingsChanged);
  super.onDestroy();
 }
 @Override public void onBackPressed(){
  if (photoBatchRunning) {
   Toast.makeText(this, "Fotoğraflar okunuyor, lütfen bekleyin.", Toast.LENGTH_SHORT).show();
   return;
  }
  if (savePhotoReview != null) savePhotoReview.run();
  if (pageBack != null) pageBack.run();
  else home();
 }
 private String historyId;
 private android.os.Bundle historyRestore;
 void saveHistory(String kind, android.os.Bundle data) {
  if(historyId==null)historyId=java.util.UUID.randomUUID().toString();
  android.os.Parcel parcel=android.os.Parcel.obtain();
  try {parcel.writeBundle(data);getSharedPreferences("operation_history",MODE_PRIVATE).edit().putString(historyId,kind+"|"+System.currentTimeMillis()+"|"+android.util.Base64.encodeToString(parcel.marshall(),android.util.Base64.NO_WRAP)).apply();}finally{parcel.recycle();}
 }
 private void saveFields(String kind, EditText... fields){android.os.Bundle b=new android.os.Bundle();for(int i=0;i<fields.length;i++)b.putString("f"+i,fields[i].getText().toString());saveHistory(kind,b);}
 private void restoreFields(EditText... fields){if(historyRestore==null)return;for(int i=0;i<fields.length;i++)if(historyRestore.containsKey("f"+i))fields[i].setText(historyRestore.getString("f"+i));historyRestore=null;}
 private void history(){
  page("İşlem geçmişi");
  android.content.SharedPreferences prefs=getSharedPreferences("operation_history",MODE_PRIVATE);
  java.util.List<java.util.Map.Entry<String,?>> entries=new java.util.ArrayList<>(prefs.getAll().entrySet());
  entries.sort((a,b)->Long.compare(Long.parseLong(b.getValue().toString().split("\\|",3)[1]),Long.parseLong(a.getValue().toString().split("\\|",3)[1])));
  if(entries.isEmpty())note("Henüz kayıt yok.");
  for(java.util.Map.Entry<String,?> entry:entries){
   String[] parts=entry.getValue().toString().split("\\|",3);
   String label=parts[0]+" · "+new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm",java.util.Locale.getDefault()).format(new java.util.Date(Long.parseLong(parts[1])));
   button(label+" — Düzenle",()->{android.os.Parcel parcel=android.os.Parcel.obtain();try{byte[] bytes=android.util.Base64.decode(parts[2],android.util.Base64.DEFAULT);parcel.unmarshall(bytes,0,bytes.length);parcel.setDataPosition(0);historyRestore=parcel.readBundle(getClassLoader());historyId=entry.getKey();switch(parts[0]){case "Optimizasyon":opt();break;case "Kanat bölme":panels();break;case "Cam m²":glass();break;default:for(TemaCatalog.Series series:TemaCatalog.Series.values())if(parts[0].equals(series.label)){temaWorkshop=temaWorkshops.get(series);android.os.Bundle state=historyRestore;historyRestore=null;temaWorkshop.restoreHistory(state);break;}}}finally{parcel.recycle();}});
   button("Sil — "+label,()->new android.app.AlertDialog.Builder(this).setMessage("Bu işlem silinsin mi?").setNegativeButton("Vazgeç",null).setPositiveButton("Sil",(d,w)->{prefs.edit().remove(entry.getKey()).apply();history();}).show());
  }
  button("Ana menü",()->home());
 }
 void home(){
  historyId=null;historyRestore=null;
  page(getApplicationInfo().loadLabel(getPackageManager()).toString(), true);
  String version = "";
  try {
   version = " · Sürüm " + getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
  } catch (android.content.pm.PackageManager.NameNotFoundException ignored) { }
  TextView description = text("Cam balkon ve doğrama" + version, 14);
  description.setTextColor(0xffd2ebee);
  description.setShadowLayer(dp(7), 0, dp(1), 0xff07131d);
  description.setPadding(dp(9), 0, dp(8), dp(18));
  body.addView(description);
  homeButton("İşlem geçmişi", android.R.drawable.ic_menu_recent_history, () -> history());
  temaMenu(TemaCatalog.Series.ALDOKS);
  temaMenu(TemaCatalog.Series.C60);
  homeButton("Fotoğraftan kesim listesi oku", android.R.drawable.ic_menu_camera, () -> openPhotoReview(generalReview));
  homeButton("Profil kesim optimizasyonu", android.R.drawable.ic_menu_sort_by_size, () -> opt());
  homeButton("Cam m² hesaplama", android.R.drawable.ic_menu_crop, () -> glass());
  homeButton("Albert Genau İmalat Kesim Listesi", android.R.drawable.ic_menu_agenda, () -> openPhotoReview(albertReview));
 }

 private void temaMenu(TemaCatalog.Series series) {
  homeButton("Tema " + series.label + " imalat", android.R.drawable.ic_menu_manage, series.accent, () -> {
   temaWorkshop = temaWorkshops.get(series);
   temaWorkshop.open();
  });
 }
 private void openPhotoReview(PhotoReview review) {
  photoReview = review;
  photo();
 }

 void openTemaCutReview(String series, PhotoResult<Map<Integer, List<CutRow>>> accept, Runnable back) {
  openPhotoReview(new PhotoReview("Tema " + series + " · Kesim listesi tara", accept, back));
 }

 private void photo() {
  if (photoReview.photoNumber > 0) {
   showDetected();
   return;
  }
  page(photoReview.title);
  pageBack = photoReview.back == null ? null : this::closeTemaCutReview;
  note("Kesim föyü, el yazılı liste veya PDF ekleyin. Her kaynağın ölçülerini ve adetlerini kontrol edin.");
  addPhotoButtons(() -> {});
  if (photoReview.accept == null) button("Elle satır ekle", this::showDetected);
  addPhotoBackButton();
 }

 private void closeTemaCutReview() {
  Runnable back = photoReview.back;
  releaseTemaCutReview();
  if (back != null) back.run();
 }

 private void releaseTemaCutReview() {
  if (photoReview.back == null) return;
  for (Uri source : photoReview.photoSources.values()) CutDocumentPages.deleteRendered(this, source);
  photoReview = generalReview;
 }

 private void addPhotoBackButton() {
  Runnable back = photoReview.back;
  button(back == null ? "Ana menü" : "Pozlara dön", () -> {
   if (savePhotoReview != null) savePhotoReview.run();
   if (back != null) closeTemaCutReview();
   else home();
  });
 }

 void addPhotoButtons(Runnable saveDraft) {
  button("Fotoğraf veya PDF ekle", () -> {
   saveDraft.run();
   Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT);
   picker.setType("*/*");
   picker.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "application/pdf"});
   picker.addCategory(Intent.CATEGORY_OPENABLE);
   picker.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
   picker.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
   startActivityForResult(picker, PICK_IMAGE);
  });
  button("Kameradan fotoğraf ekle", () -> {
   saveDraft.run();
   try {
    photoReview.cameraUri = cameraFile();
    capturePhoto(photoReview.cameraUri, TAKE_PHOTO);
   } catch (Exception e) {
    Toast.makeText(this, "Kamera açılamadı: " + e.getMessage(), Toast.LENGTH_LONG).show();
   }
  });
 }

 Uri cameraFile() throws java.io.IOException {
  java.io.File folder = new java.io.File(getCacheDir(), "camera");
  if (!folder.isDirectory() && !folder.mkdirs()) throw new java.io.IOException("Kamera klasörü açılamadı.");
  java.io.File photo = java.io.File.createTempFile("cizim_", ".jpg", folder);
  return androidx.core.content.FileProvider.getUriForFile(this, getPackageName() + ".photos", photo);
 }

 void capturePhoto(Uri uri, int request) {
  Intent camera = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
  camera.putExtra(MediaStore.EXTRA_OUTPUT, uri);
  camera.setClipData(android.content.ClipData.newRawUri("Çizim fotoğrafı", uri));
  camera.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
  startActivityForResult(camera, request);
 }

 @Override protected void onActivityResult(int request, int result, Intent data) {
  super.onActivityResult(request, result, data);
  if (temaWorkshop != null && temaWorkshop.handlePhotoResult(request, result, data)) return;
  if (result != RESULT_OK || photoBatchRunning) return;
  LinkedHashSet<Uri> selected = new LinkedHashSet<>();
  if (request == TAKE_PHOTO && photoReview.cameraUri != null) {
   selected.add(photoReview.cameraUri);
  } else if (request == PICK_IMAGE && data != null) {
   android.content.ClipData clips = data.getClipData();
   if (clips != null) {
    for (int i = 0; i < clips.getItemCount(); i++) {
     Uri uri = clips.getItemAt(i).getUri();
     if (uri != null) selected.add(uri);
    }
   }
   if (data.getData() != null) selected.add(data.getData());
  }
  if (selected.isEmpty()) return;
  if (request == PICK_IMAGE) for (Uri uri : selected) {
   try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); }
   catch (SecurityException ignored) { }
  }
  photoBatchRunning = true;
  TextView progress = scanningPage("Dosyalar hazırlanıyor…");
  PhotoReview review = photoReview;
  new Thread(() -> {
   List<CutDocumentPages.Page> pages = new ArrayList<>();
   int invalid = 0;
   for (Uri uri : selected) {
    try {
     pages.addAll(CutDocumentPages.read(this, uri));
    } catch (java.io.IOException | RuntimeException error) {
     invalid++;
    }
   }
   int unreadable = invalid;
   runOnUiThread(() -> {
    if (isDestroyed() || isFinishing()) {
     for (CutDocumentPages.Page page : pages) CutDocumentPages.deleteRendered(this, page.uri);
     return;
    }
    for (int i = 0; i < unreadable; i++) {
     int number = ++review.photoNumber;
     review.sourceNames.put(number, "Dosya " + number);
     review.failedPhotos.put(number, "Dosya açılamadı. Görüntüyü veya PDF dosyasını yeniden ekleyin.");
    }
    readPhotoBatch(pages, 0, progress, TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS));
   });
  }, "cut-document-pages").start();
 }

 private void readPhotoBatch(List<CutDocumentPages.Page> pages, int index, TextView progress,
                             com.google.mlkit.vision.text.TextRecognizer recognizer) {
  if (isDestroyed() || isFinishing()) {
   recognizer.close();
   return;
  }
  if (index == pages.size()) {
   recognizer.close();
   photoBatchRunning = false;
   showDetected();
   return;
  }
  CutDocumentPages.Page page = pages.get(index);
  int number = ++photoReview.photoNumber;
  if (page.label != null) photoReview.sourceNames.put(number, page.label);
  progress.setText(photoReview.sourceName(number) + " · " + (index + 1) + " / " + pages.size());
  Runnable next = () -> readPhotoBatch(pages, index + 1, progress, recognizer);
  photoReview.photoSources.put(number, page.uri);
  readPhoto(page.uri, number, null, recognizer, reason -> {
   photoReview.failedPhotos.put(number, reason);
   next.run();
  }, result -> {
   acceptScan(number, result);
   next.run();
  });
 }

 interface PhotoResult<T> { void accept(T result); }

 void readPhoto(Uri uri, int number, CutPhotoReader.Selection selection, com.google.mlkit.vision.text.TextRecognizer recognizer,
                PhotoResult<String> failed, PhotoResult<CutPhotoReader.Result> finished) {
  new Thread(() -> {
   Bitmap decoded = null;
   InputImage original = null;
   if (android.os.Build.VERSION.SDK_INT >= 28) {
    try {
     decoded = android.graphics.ImageDecoder.decodeBitmap(
      android.graphics.ImageDecoder.createSource(getContentResolver(), uri),
      (decoder, info, source) -> {
       decoder.setAllocator(android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE);
       int width = info.getSize().getWidth(), height = info.getSize().getHeight();
       float scale = 3200f / Math.max(width, height);
       if (scale < 1) decoder.setTargetSize(Math.max(1, Math.round(width * scale)),
        Math.max(1, Math.round(height * scale)));
      });
    } catch (java.io.IOException | RuntimeException e) {
     android.util.Log.w("ProfilKesimOCR", "ImageDecoder could not open photo " + number, e);
    }
   }
   if (decoded == null) {
    try {
     decoded = decodeSampledPhoto(uri);
    } catch (java.io.IOException | RuntimeException e) {
     android.util.Log.w("ProfilKesimOCR", "Legacy bitmap decode failed for photo " + number, e);
    }
   }
   if (decoded == null) {
    try {
     original = InputImage.fromFilePath(this, uri);
    } catch (java.io.IOException | RuntimeException e) {
     android.util.Log.w("ProfilKesimOCR", "URI OCR input unavailable for photo " + number, e);
    }
   }
   Bitmap sourcePreview = CutPhotoReader.thumbnail(decoded, 840);
   if (sourcePreview != null) runOnUiThread(() -> {
    if (!isDestroyed() && photoBatchRunning) showScanImage(sourcePreview);
   });
   CutPhotoReader.Result parsed = null;
   String fault = "Dosya açılamadı.";
   boolean mapped = selection != null && selection.usesDividers();
   if (decoded != null || (original != null && !mapped)) {
    try {
     parsed = mapped ? CutPhotoReader.scanColumns(decoded, selection, recognizer)
      : CutPhotoReader.scan(decoded, original, selection == null ? null : selection.area, recognizer);
    } catch (InterruptedException e) {
     Thread.currentThread().interrupt();
     fault = "Fotoğraf taraması kesildi.";
    } catch (RuntimeException e) {
     android.util.Log.w("ProfilKesimOCR", "Cut table scan failed for photo " + number, e);
     parsed = new CutPhotoReader.Result(Collections.emptyList(), sourcePreview, sourcePreview, Collections.emptyList());
    }
   }
   CutPhotoReader.Result result = parsed;
   String reason = fault;
   runOnUiThread(() -> {
    if (isDestroyed() || isFinishing()) recognizer.close();
    else if (result == null) failed.accept(reason);
    else finished.accept(result);
   });
  }, "photo-decoder").start();
 }

 private Bitmap decodeSampledPhoto(Uri uri) throws java.io.IOException {
  android.graphics.BitmapFactory.Options options = new android.graphics.BitmapFactory.Options();
  options.inJustDecodeBounds = true;
  try (java.io.InputStream input = getContentResolver().openInputStream(uri)) {
   android.graphics.BitmapFactory.decodeStream(input, null, options);
  }
  if (options.outWidth <= 0 || options.outHeight <= 0) return null;
  options.inJustDecodeBounds = false;
  options.inSampleSize = 1;
  int longest = Math.max(options.outWidth, options.outHeight);
  while (longest / options.inSampleSize > 3200) options.inSampleSize *= 2;
  try (java.io.InputStream input = getContentResolver().openInputStream(uri)) {
   return android.graphics.BitmapFactory.decodeStream(input, null, options);
  }
 }

 private void acceptScan(int number, CutPhotoReader.Result result) {
  photoReview.scans.put(number, result);
  if (result.rows.isEmpty()) {
   photoReview.failedPhotos.put(number, "Kesim satırı bulunamadı. Liste alanını seçip yeniden tarayabilirsiniz.");
  } else {
   photoReview.detected.put(number, result.rows);
   photoReview.failedPhotos.remove(number);
  }
 }

 private TextView scanningPage(String message) {
  page("Kesim tablosu taranıyor");
  scanPanel = planCard(body, 0xff122435, 0xff1d4c4d, 0xff62f4d1);
  lightPanel(scanPanel);
  TextView progress = planLabel(scanPanel, message, 16, Color.WHITE);
  showScanImage(null);
  return progress;
 }

 private void showScanImage(Bitmap image) {
  if (scanPanel == null) return;
  if (scanPanel.getChildCount() > 1) scanPanel.removeViewAt(1);
  scanPanel.addView(new PhotoScanView(this, image, null, true), new LinearLayout.LayoutParams(-1, dp(image == null ? 70 : 225)));
 }

 private void retryPhoto(int number, CutPhotoReader.Selection selection) {
  Uri source = photoReview.photoSources.get(number);
  if (source == null || photoBatchRunning) return;
  savePhotoReview.run();
  photoBatchRunning = true;
  scanningPage("Fotoğraf " + number + " · Seçilen tablo okunuyor…");
  com.google.mlkit.vision.text.TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
  readPhoto(source, number, selection, recognizer, reason -> {
   recognizer.close();
   photoBatchRunning = false;
   if (!photoReview.detected.containsKey(number)) photoReview.failedPhotos.put(number, reason);
   Toast.makeText(this, reason, Toast.LENGTH_LONG).show();
   showDetected();
  }, result -> {
   recognizer.close();
   photoBatchRunning = false;
   if (result.rows.isEmpty() && photoReview.detected.containsKey(number)) {
    Toast.makeText(this, "Seçilen alanda tablo bulunamadı. Mevcut satırlar saklandı.", Toast.LENGTH_LONG).show();
   } else acceptScan(number, result);
   showDetected();
  });
 }

 private void selectCutArea(int number, Bitmap image, DrawingMeasureView.Mode mode) {
  if (image == null) return;
  LinearLayout content = new LinearLayout(this);
  content.setOrientation(LinearLayout.VERTICAL);
  content.setPadding(dp(15), dp(3), dp(15), dp(8));
  boolean columns = mode == DrawingMeasureView.Mode.COLUMNS;
  String corners = columns ? "Tablonun iki karşı köşesini seçin. Sütunlar soldan sağa profil, ölçü ve adet olmalı."
   : "Listenin iki karşı köşesini seçin. Varsa başlığı dahil edin. İki parmakla yakınlaştırabilirsiniz.";
  TextView directions = planLabel(content, corners, 14, Color.DKGRAY);
  DrawingMeasureView[] view = new DrawingMeasureView[1];
  DrawingMeasureView picker = new DrawingMeasureView(this, image, () -> {
   int count = view[0].pointCount();
   if (count < 2) directions.setText(corners);
   else if (columns && count == 2) directions.setText("Profil ve ölçü sütunlarının arasına dokunun.");
   else if (columns && count == 3) directions.setText("Ölçü ve adet sütunlarının arasına dokunun.");
   else directions.setText("Seçimi kontrol edip taramayı başlatın.");
  }, mode);
  view[0] = picker;
  content.addView(picker, new LinearLayout.LayoutParams(-1, dp(274)));
  Button undo = new Button(this);
  undo.setText("Son noktayı geri al");
  undo.setOnClickListener(v -> picker.undo());
  content.addView(undo);
  android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this).setTitle(columns ? "Profil, ölçü ve adet sütunları" : "Kesim tablosunun alanı")
   .setView(content).setNegativeButton("Vazgeç", null).setPositiveButton("Alanı tara", null).create();
  dialog.setOnShowListener(ignored -> dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
   try {
    CutPhotoReader.Selection selection = picker.tableSelection();
    dialog.dismiss();
    retryPhoto(number, selection);
   } catch (IllegalArgumentException error) {
    Toast.makeText(this, error.getMessage(), Toast.LENGTH_SHORT).show();
   }
  }));
  dialog.show();
 }

 private void showPhotoSource(int number, String status) {
  LinearLayout card = planCard(body, 0xff13283a, 0xff20414d, 0xff55ddd5);
  planLabel(card, photoReview.sourceName(number), 19, Color.WHITE).setTypeface(null, Typeface.BOLD);
  if (status != null) planLabel(card, status, 14, 0xffffcc8b);
  CutPhotoReader.Result scan = photoReview.scans.get(number);
  if (scan != null && scan.preview != null) {
   card.addView(new PhotoScanView(this, scan.preview, scan.highlights, false), new LinearLayout.LayoutParams(-1, dp(167)));
   photoSelectionButton(card, "Liste alanını seç ve yeniden tara", () -> selectCutArea(number, scan.original, DrawingMeasureView.Mode.AREA));
   if (status != null) photoSelectionButton(card, "Profil, ölçü ve adet sütunlarını seç", () ->
    selectCutArea(number, scan.original, DrawingMeasureView.Mode.COLUMNS));
  }
  Button remove = new Button(this);
  remove.setAllCaps(false);
  remove.setText("Kaynağı plandan çıkar");
  remove.setTextColor(0xffffddae);
  remove.setBackground(rgbPanel(0xff1b273b, 0xff413447, 0xffe4a26d));
  remove.setOnClickListener(v -> new android.app.AlertDialog.Builder(this)
   .setMessage(photoReview.sourceName(number) + " bu kesim planından çıkarılsın mı?")
   .setNegativeButton("Vazgeç", null).setPositiveButton("Plandan çıkar", (d, w) -> {
    savePhotoReview.run();
    photoReview.detected.remove(number);
    photoReview.failedPhotos.remove(number);
    CutDocumentPages.deleteRendered(this, photoReview.photoSources.remove(number));
    photoReview.scans.remove(number);
    photoReview.sourceNames.remove(number);
    showDetected();
   }).show());
  card.addView(remove, new LinearLayout.LayoutParams(-1, dp(48)));
 }

 String cutSourceName(int number) { return photoReview.sourceName(number); }

 private void photoSelectionButton(LinearLayout card, String label, Runnable select) {
  Button button = new Button(this);
  button.setAllCaps(false);
  button.setText(label);
  button.setTextColor(Color.WHITE);
  button.setBackground(rgbPanel(0xff172a3b, 0xff24505b, 0xff59e8ce));
  button.setOnClickListener(v -> select.run());
  InterfaceMotion.buttonFeedback(button);
  LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(-1, -2);
  size.topMargin = dp(5);
  button.setMinHeight(dp(48));
  card.addView(button, size);
 }

 LinearLayout profileHeading(LinearLayout card, String name, Bitmap icon) {
  LinearLayout heading = new LinearLayout(this);
  heading.setGravity(Gravity.CENTER_VERTICAL);
  card.addView(heading, new LinearLayout.LayoutParams(-1, -2));
  if (icon != null) {
   ImageView preview = new ImageView(this);
   preview.setImageBitmap(icon);
   preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
   preview.setPadding(dp(5), dp(5), dp(5), dp(5));
   preview.setBackground(rgbPanel(0xffe5fbff, 0xffd4eafa, 0xff398ea0));
   preview.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO);
   LinearLayout.LayoutParams iconSize = new LinearLayout.LayoutParams(dp(104), dp(72));
   iconSize.setMarginEnd(dp(12));
   heading.addView(preview, iconSize);
  }
  TextView label = text(name, 16);
  label.setTextColor(Color.WHITE);
  label.setTypeface(null, Typeface.BOLD);
  label.setPadding(0, dp(5), 0, dp(5));
  heading.addView(label, new LinearLayout.LayoutParams(0, -2, 1f));
  return heading;
 }

 private void editPhotoProfile(CutRow row) {
  LinearLayout form = new LinearLayout(this);
  form.setOrientation(LinearLayout.VERTICAL);
  form.setPadding(dp(20), dp(8), dp(20), 0);
  EditText name = new EditText(this), code = new EditText(this);
  name.setHint("Profil adı");
  name.setText(row.name);
  code.setHint("Profil kodu (varsa)");
  code.setText(row.code);
  name.setSingleLine(true);
  code.setSingleLine(true);
  form.addView(name, new LinearLayout.LayoutParams(-1, -2));
  form.addView(code, new LinearLayout.LayoutParams(-1, -2));
  android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this)
   .setTitle("Profili düzenle").setView(form).setNegativeButton("Vazgeç", null).setPositiveButton("Kaydet", null).create();
  dialog.setOnShowListener(d -> dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
   String newName = name.getText().toString().trim();
   String newCode = code.getText().toString().trim().toUpperCase(Locale.ROOT);
   if (newName.isEmpty() && newCode.isEmpty()) {
    name.setError("Profil adını veya kodunu yazın.");
    return;
   }
   savePhotoReview.run();
   row.name = newName;
   row.code = newCode;
   dialog.dismiss();
   showDetected();
  }));
  dialog.show();
 }

 static final class ProfileCutPlan {
  final String name;
  final int stock;
  final ArrayList<Integer> pieces = new ArrayList<>();
  Bitmap icon;

  ProfileCutPlan(String name, int stock, Bitmap icon) {
   this.name = name;
   this.stock = stock;
   this.icon = icon;
  }

  List<CutPacking.Bar> pack(int kerf) {
   return CutPacking.pack(pieces, Collections.singletonList(stock), kerf);
  }
 }

 void showDetected() {
  page(photoReview.title + " · Kontrol");
  pageBack = photoReview.back == null ? null : this::closeTemaCutReview;
  int rowCount = 0;
  for (List<CutRow> rows : photoReview.detected.values()) rowCount += rows.size();
  note(photoReview.detected.size() + " kaynak okundu · " + rowCount + " profil satırı");
  for (Map.Entry<Integer, String> failed : photoReview.failedPhotos.entrySet()) showPhotoSource(failed.getKey(), failed.getValue());

  Map<CutRow, EditText[]> fields = new LinkedHashMap<>();
  Map<String, List<EditText>> stocks = new HashMap<>();
  Map<Integer, CheckBox> confirmations = new LinkedHashMap<>();
  addPhotoButtons(() -> { if (savePhotoReview != null) savePhotoReview.run(); });
  boolean tema = photoReview.accept != null;
  if (!photoReview.detected.isEmpty()) note("Her kaynağın satırlarını kontrol edip onaylayın.");
  if (tema) note("Onaylanan her kaynak bir poz olur. Stok boyu ve testere payı Tema ekranından alınır.");

  for (Map.Entry<Integer, List<CutRow>> photo : photoReview.detected.entrySet()) {
   int number = photo.getKey();
   showPhotoSource(number, null);
   CheckBox confirmed = new CheckBox(this);
   for (CutRow row : photo.getValue()) {
    LinearLayout profile = planCard(body, 0xff121f38, 0xff244361, 0xff5685a4);
    String heading = row.code.isEmpty() ? row.name : row.name.isEmpty() ? row.code : row.code + "\n" + row.name;
    LinearLayout profileHeader = profileHeading(profile, heading, row.icon);
    TextView edit = text("Düzelt", 13);
    edit.setTextColor(0xff9deef1);
    edit.setMinHeight(dp(48));
    edit.setGravity(Gravity.CENTER);
    edit.setContentDescription("Profil adını ve kodunu düzelt");
    edit.setOnClickListener(v -> editPhotoProfile(row));
    profileHeader.addView(edit, new LinearLayout.LayoutParams(-2, -2));
    if (!tema) addProfileStockInput(profile, profileStockKey(row), stocks);
    String[] values = row.draft != null ? row.draft : new String[]{
     row.length < 0 ? "" : ShopDimensions.mm(row.length), row.qty < 0 ? "" : String.valueOf(row.qty)};
    EditText measure = input(profile, "KESİM ÖLÇÜSÜ (mm)", values[0]);
    EditText quantity = input(profile, "KESİM ADEDİ", values[1]);
    measure.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
    quantity.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
    if (values[0].trim().isEmpty()) measure.setError("Kesim ölçüsü okunamadı");
    if (values[1].trim().isEmpty()) quantity.setError("Kesim adedi okunamadı");
    if (row.qty == 0) planLabel(profile, "Adedi 0 olan satırlar kesime katılmaz.", 14, REMAIN_GREEN);
    android.text.TextWatcher invalidateConfirmation = new android.text.TextWatcher() {
     @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
     @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
      confirmed.setChecked(false);
     }
     @Override public void afterTextChanged(android.text.Editable text) {}
    };
    measure.addTextChangedListener(invalidateConfirmation);
    quantity.addTextChangedListener(invalidateConfirmation);
    fields.put(row, new EditText[]{measure, quantity});
   }
   confirmed.setText(photoReview.sourceName(number) + " için ölçüleri ve adetleri kontrol ettim.");
   confirmed.setTextColor(0xffeffaff);
   confirmed.setButtonTintList(android.content.res.ColorStateList.valueOf(0xff5af4ca));
   addTextPanel(confirmed, 0xff15283b, 0xff244d5a, 0xff51cfc0);
   confirmations.put(number, confirmed);
  }

  if (!photoReview.detected.isEmpty()) button(tema ? "Onayla ve pozlara ekle" : "Tüm kaynakları birlikte optimize et", () -> {
   if (!photoReview.failedPhotos.isEmpty()) {
    Toast.makeText(this, "Okunamayan listeyi yeniden tarayın veya kaynağı plandan çıkarın.", Toast.LENGTH_LONG).show();
    return;
   }
   for (Map.Entry<Integer, CheckBox> entry : confirmations.entrySet()) {
    if (!entry.getValue().isChecked()) {
     Toast.makeText(this, "Önce " + photoReview.sourceName(entry.getKey()) + " için ölçü ve adetleri onaylayın.", Toast.LENGTH_LONG).show();
     return;
    }
   }
   savePhotoReview.run();
   try {
    if (tema) {
     photoReview.accept.accept(photoReview.detected);
     releaseTemaCutReview();
    } else {
     int kerf = readKerf();
     Collection<ProfileCutPlan> plans = combinePhotoCuts(photoReview.detected, photoReview.stockDrafts);
     showPhotoOptimization(plans, kerf, photoReview.detected.size() + " kaynaktan birleşik kesim planı");
    }
   } catch (IllegalArgumentException e) {
    Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
   }
  });

  if (tema) {
   savePhotoReview = () -> saveCutReviewFields(fields);
   addPhotoBackButton();
   return;
  }
  EditText saw = input("TESTERE PAYI (mm / parça arası)", photoReview.extraDraft[4]);
  saw.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
  note("Elle kesim ekle");
  EditText extraName = input("Profil kodu / adı", photoReview.extraDraft[0]);
  EditText extraMeasure = input("Kesim ölçüsü (mm)", photoReview.extraDraft[1]);
  EditText extraQuantity = input("Kesim adedi", photoReview.extraDraft[2]);
  EditText extraStock = input("Profil stok boyu (mm)", photoReview.extraDraft[3]);
  extraMeasure.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
  extraQuantity.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
  extraStock.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
  EditText[] extraFields = {extraName, extraMeasure, extraQuantity, extraStock, saw};
  savePhotoReview = () -> {
   saveCutReviewFields(fields);
   for (Map.Entry<String, List<EditText>> entry : stocks.entrySet()) {
    String value = entry.getValue().get(0).getText().toString().trim();
    photoReview.stockDrafts.put(entry.getKey(), value);
    try {
     int length = Integer.parseInt(value);
     if (length > 0) getPreferences(MODE_PRIVATE).edit().putInt("stock_" + entry.getKey(), length).apply();
    } catch (NumberFormatException ignored) {}
   }
   photoReview.extraDraft = fieldValues(extraFields);
  };

  button("Elle girilen kesim için plan oluştur", () -> {
   savePhotoReview.run();
   try {
    String name = photoReview.extraDraft[0].trim();
    if (name.isEmpty()) throw new IllegalArgumentException("Profilin kodunu veya adını yazın.");
    int size = ShopDimensions.tenths(photoReview.extraDraft[1], "Kesim ölçüsü");
    int quantity = cutNumber(photoReview.extraDraft[2], "Kesim adedi");
    int stock = ShopDimensions.tenths(photoReview.extraDraft[3], "Stok boyu");
    int kerf = readKerf();
    if (quantity == 0) throw new IllegalArgumentException("Kesim adedi en az 1 olmalı.");
    validateCut(size, quantity, stock, name);
    ProfileCutPlan plan = new ProfileCutPlan(name, stock, null);
    for (int i = 0; i < quantity; i++) plan.pieces.add(size);
    showPhotoOptimization(Collections.singletonList(plan), kerf, "Elle girilen kesim");
   } catch (IllegalArgumentException e) {
    Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
   }
  });

  if (photoReview.photoNumber > 0) button("Yeni fotoğraf planı başlat", () -> new android.app.AlertDialog.Builder(this)
   .setMessage("Bu plandaki fotoğraflar ve düzenlenen kesim satırları temizlensin mi? Profil stok boyları saklanır.")
   .setNegativeButton("Vazgeç", null)
   .setPositiveButton("Yeni plan", (dialog, which) -> {
    savePhotoReview.run();
    photoReview.detected.clear();
    photoReview.stockDrafts.clear();
    photoReview.photoNumber = 0;
    photoReview.failedPhotos.clear();
    for (Uri source : photoReview.photoSources.values()) CutDocumentPages.deleteRendered(this, source);
    photoReview.photoSources.clear();
    photoReview.scans.clear();
    photoReview.sourceNames.clear();
    photoReview.extraDraft = new String[]{"", "", "", String.valueOf(DEFAULT_PROFILE_STOCK), String.valueOf(DEFAULT_KERF)};
    photo();
   }).show());
  addPhotoBackButton();
 }

 private void saveCutReviewFields(Map<CutRow, EditText[]> fields) {
  for (Map.Entry<CutRow, EditText[]> entry : fields.entrySet()) {
   entry.getKey().draft = fieldValues(entry.getValue());
  }
 }

 int readKerf() {
  int kerf = ShopDimensions.tenths(photoReview.extraDraft[4], "Testere payı");
  if (kerf < 0) throw new IllegalArgumentException("Testere payı negatif olamaz.");
  return kerf;
 }

 static Collection<ProfileCutPlan> combinePhotoCuts(Map<Integer, List<CutRow>> photos,
                                                    Map<String, String> stocks) {
  Map<String, ProfileCutPlan> plans = new LinkedHashMap<>();
  for (Map.Entry<Integer, List<CutRow>> photo : photos.entrySet()) {
   for (CutRow row : photo.getValue()) {
    String label = (row.code + " " + row.name).trim();
    String context = label + " · Fotoğraf " + photo.getKey();
    int quantity = cutNumber(row.draft[1], context + " kesim adedi");
    if (quantity == 0) continue;
    int size = ShopDimensions.tenths(row.draft[0], context + " kesim ölçüsü");
    String key = profileStockKey(row);
    int stock = ShopDimensions.tenths(stocks.get(key), label + " stok boyu");
    validateCut(size, quantity, stock, context);
    ProfileCutPlan plan = plans.get(key);
    if (plan == null) {
     plan = new ProfileCutPlan(label, stock, row.icon);
     plans.put(key, plan);
    } else if (plan.icon == null) {
     plan.icon = row.icon;
    }
    for (int i = 0; i < quantity; i++) plan.pieces.add(size);
   }
  }
  if (plans.isEmpty()) throw new IllegalArgumentException("En az bir geçerli kesim girin.");
  return plans.values();
 }

 static String profileStockKey(CutRow row) {
  return row.code.isEmpty() ? "NAME:" + CutSheet.identity(row.name) : row.code;
 }

 void addProfileStockInput(LinearLayout card, String code, Map<String, List<EditText>> stocks) {
  int saved = getPreferences(MODE_PRIVATE).getInt("stock_" + code, DEFAULT_PROFILE_STOCK);
  String length = photoReview.stockDrafts.get(code);
  if (length == null) length = String.valueOf(saved);
  EditText stock = input(card, "PROFİL STOK BOYU (mm)", length);
  stock.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
  List<EditText> peers = stocks.get(code);
  if (peers == null) {
   peers = new ArrayList<>();
   stocks.put(code, peers);
  }
  List<EditText> matchingFields = peers;
  matchingFields.add(stock);
  stock.addTextChangedListener(new android.text.TextWatcher() {
   @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
   @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
    String value = text.toString();
    if (value.equals(photoReview.stockDrafts.put(code, value))) return;
    for (EditText field : matchingFields) {
     if (field != stock && !value.contentEquals(field.getText())) field.setText(value);
    }
   }
   @Override public void afterTextChanged(android.text.Editable text) {}
  });
 }

 String[] fieldValues(EditText[] fields) {
  String[] values = new String[fields.length];
  for (int i = 0; i < fields.length; i++) values[i] = fields[i].getText().toString();
  return values;
 }

 static int cutNumber(String value, String label) {
  try {
   return Integer.parseInt(value == null ? "" : value.trim());
  } catch (NumberFormatException e) {
   throw new IllegalArgumentException(label + ": geçerli bir sayı girin.");
  }
 }

 static void validateCut(int size, int quantity, int stock, String label) {
  if (size <= 0 || quantity < 0 || quantity > 1000 || stock <= 0) {
   throw new IllegalArgumentException(label + ": ölçüyü, adedi ve stok boyunu kontrol edin.");
  }
  if (size > stock) {
   throw new IllegalArgumentException(label + ": kesim " + ShopDimensions.mm(stock) + " mm stoğa sığmıyor.");
  }
 }

 void showPhotoOptimization(Collection<ProfileCutPlan> profiles, int kerf, String source) {
  LinearLayout cards = new LinearLayout(this);
  cards.setOrientation(LinearLayout.VERTICAL);
  int allBars = 0, allPieces = 0;
  long remaining = 0, net = 0;
  int commonLength = profiles.iterator().next().stock;
  for (ProfileCutPlan plan : profiles) {
   if (commonLength != plan.stock) commonLength = 0;
   List<CutPacking.Bar> bars = plan.pack(kerf);
   LinearLayout group = planCard(cards, 0xff172b44, 0xff254662, 0xff597d9b);
   profileHeading(group, plan.name, plan.icon);
   planLabel(group, "Stok " + ShopDimensions.mm(plan.stock) + " mm  ·  " + plan.pieces.size() + " parça", 14, 0xffd2e8f4);
   allPieces += plan.pieces.size();
   for (int i = 0; i < bars.size(); i++) {
    CutPacking.Bar bar = bars.get(i);
    int waste = plan.stock - bar.used;
    remaining += waste;
    net += bar.used - (long) kerf * (bar.pieces.size() - 1);
    planBar(group, i + 1, bar.pieces, plan.stock, kerf, waste, allBars < 12, ShopDimensions.PER_MM);
    allBars++;
   }
  }
  page(photoReview == albertReview ? "Albert Genau · Kesim optimizasyonu" : "Kesim optimizasyon sonucu");
  note(source);
  planSummary(commonLength, kerf, allBars, allPieces, net, remaining, ShopDimensions.PER_MM);
  body.addView(cards);
  button("Fotoğrafları ve stok boylarını düzenle", () -> showDetected());
  button("Ana menü", () -> home());
 }

 static final int CUT_RED = 0xffef4963;
 static final int REMAIN_GREEN = 0xff2ddd9e;
 static final int KERF_GOLD = 0xfff3bf60;

 GradientDrawable gradient(int start, int end, int border) {
  GradientDrawable background = new GradientDrawable(
   GradientDrawable.Orientation.TL_BR, new int[]{start, end});
  background.setCornerRadius(dp(16));
  background.setStroke(dp(1), border);
  return background;
 }

 android.graphics.drawable.Drawable rgbPanel(int start, int end, int edge) {
  GradientDrawable frame = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
   new int[]{0xff7899a6, 0xff27424e, 0xff425c63, 0xff96785a});
  frame.setCornerRadius(dp(18));
  GradientDrawable surface = gradient(start, end, (edge & 0x00ffffff) | 0x66000000);
  InterfaceMotion.PanelLights lights = new InterfaceMotion.PanelLights(
   getResources().getDisplayMetrics().density, this::rgbRunning);
  android.graphics.drawable.LayerDrawable layers = new android.graphics.drawable.LayerDrawable(
   new android.graphics.drawable.Drawable[]{frame, surface, lights});
  layers.setPaddingMode(android.graphics.drawable.LayerDrawable.PADDING_MODE_STACK);
  layers.setLayerInset(1, dp(1), dp(1), dp(1), dp(1));
  return layers;
 }

 private boolean rgbRunning() { return rgbRunning; }

 void addTextPanel(TextView view, int start, int end, int edge) {
  addTextPanel(body, view, start, end, edge);
 }

 void addTextPanel(LinearLayout parent, TextView view, int start, int end, int edge) {
  view.setBackground(rgbPanel(start, end, edge));
  view.setPadding(dp(14), dp(12), dp(14), dp(12));
  LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
  params.setMargins(0, dp(7), 0, dp(3));
  parent.addView(view, params);
 }

 void reveal(android.view.View view, int delay) {
  if (!InterfaceMotion.enabled(this)) return;
  view.setAlpha(.14f);
  view.setTranslationY(dp(14));
  view.animate().alpha(1f).translationY(0f).setStartDelay(delay)
   .setDuration(430).setInterpolator(InterfaceMotion.ARRIVE).start();
 }

 LinearLayout planCard(LinearLayout parent, int start, int end, int border) {
  LinearLayout card = new LinearLayout(this);
  card.setOrientation(LinearLayout.VERTICAL);
  card.setPadding(dp(14), dp(12), dp(14), dp(12));
  card.setBackground(rgbPanel(start, end, border));
  card.setElevation(dp(3));
  LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
  params.setMargins(0, dp(10), 0, 0);
  parent.addView(card, params);
  return card;
 }

 void lightPanel(LinearLayout panel) {
  LedTrace trace = new LedTrace(this);
  panel.getOverlay().add(trace);
  panel.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
   trace.layout(0, 0, right - left, bottom - top);
   if (InterfaceMotion.enabled(this)) trace.lightUp();
  });
  panel.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
   @Override public void onViewAttachedToWindow(View view) { }
   @Override public void onViewDetachedFromWindow(View view) { trace.setActive(false); }
  });
 }

 TextView planLabel(LinearLayout card, String value, int size, int color) {
  TextView label = text(value, size);
  label.setPadding(0, dp(3), 0, dp(3));
  label.setTextColor(color);
  card.addView(label);
  return label;
 }

 void planSummary(int length, int kerf, int bars, int count, long net, long remaining) {
  planSummary(length, kerf, bars, count, net, remaining, 1);
 }

 void planSummary(int length, int kerf, int bars, int count, long net, long remaining, int unitsPerMm) {
  LinearLayout card = planCard(body, 0xff10233e, 0xff17485a, 0xff46dfdb);
  planLabel(card, bars + " stok profili  ·  " + count + " parça", 23, Color.WHITE)
   .setTypeface(null, Typeface.BOLD);
  String stockLabel = length > 0 ? "Stok " + formatMm(length, unitsPerMm) + " mm" : "Karma stok boyları";
  planLabel(card, stockLabel + "  ·  Testere payı " + formatMm(kerf, unitsPerMm) + " mm/ara", 14, 0xffd2e8f4);
  planLabel(card, "Kesim " + formatMm(net, unitsPerMm) + " mm", 16, 0xffff8c9d);
  planLabel(card, "Kalan " + formatMm(remaining, unitsPerMm) + " mm", 16, 0xff6af5c0);
  long sawUsed = (long) kerf * (count - bars);
  if (kerf > 0) planLabel(card, "Toplam testere payı " + formatMm(sawUsed, unitsPerMm) + " mm", 14, KERF_GOLD);
  long stockTotal = net + remaining + sawUsed;
  if (stockTotal > 0) planLabel(card, String.format(new Locale("tr", "TR"), "Kalan stok %% %.1f", 100d * remaining / stockTotal), 14, 0xffb4e3e3);
  reveal(card, 0);
 }

 void planBar(LinearLayout parent, int number, List<Integer> cuts, int length, int kerf,
              int remaining, boolean animated) {
  planBar(parent, number, cuts, length, kerf, remaining, animated, 1);
 }

 void planBar(LinearLayout parent, int number, List<Integer> cuts, int length, int kerf,
              int remaining, boolean animated, int unitsPerMm) {
  planBar(parent, number, cuts, length, kerf, remaining, animated, unitsPerMm, null);
 }

 void planBar(LinearLayout parent, int number, List<Integer> cuts, int length, int kerf,
              int remaining, boolean animated, int unitsPerMm, List<TemaCutBatch.Piece> positions) {
  LinearLayout card = planCard(parent, 0xff121c33, 0xff203453, 0xff557b9b);
  planLabel(card, "Stok profil " + number + "  ·  " + formatMm(length, unitsPerMm) + " mm", 17, Color.WHITE)
   .setTypeface(null, Typeface.BOLD);
  StockBar bar = new StockBar(cuts, length, kerf);
  LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(27));
  params.setMargins(0, dp(9), 0, dp(9));
  card.addView(bar, params);
  StringBuilder sizes = new StringBuilder();
  for (int i = 0; i < cuts.size(); i++) {
   if (i > 0) sizes.append("  ·  ");
   sizes.append(formatMm(cuts.get(i), unitsPerMm));
   if (positions != null) {
    TemaCutBatch.Piece piece = positions.get(i);
    sizes.append(" [").append(piece.position);
    if (!piece.ends.isEmpty()) sizes.append(" / ").append(piece.ends);
    sizes.append(']');
   }
  }
  planLabel(card, "Kesilen (mm)  " + sizes, 14, 0xffff8c9d);
  planLabel(card, "Kalan  " + formatMm(remaining, unitsPerMm) + " mm", 15, 0xff6af5c0);
  if (animated) {
   reveal(card, Math.min(160, number * 35));
   bar.animateFill();
  }
 }

 static String formatMm(long value, int unitsPerMm) {
  if (unitsPerMm == 1) return Long.toString(value);
  return java.math.BigDecimal.valueOf(value, 1).stripTrailingZeros().toPlainString().replace('.', ',');
 }

 final class StockBar extends android.view.View {
  private final List<Integer> cuts;
  private final int length;
  private final int kerf;
  private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
  private final android.graphics.RectF bounds = new android.graphics.RectF();
  private final android.graphics.Path outline = new android.graphics.Path();
  private android.graphics.LinearGradient bevel;
  private float fill = 1f;
  private ValueAnimator animator;

  StockBar(List<Integer> cuts, int length, int kerf) {
   super(MainActivity.this);
   this.cuts = cuts;
   this.length = length;
   this.kerf = kerf;
   setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
  }

  void animateFill() {
   if (!InterfaceMotion.enabled(MainActivity.this)) return;
   fill = 0f;
   animator = ValueAnimator.ofFloat(0f, 1f);
   animator.setDuration(740);
   animator.setInterpolator(InterfaceMotion.ARRIVE);
   animator.addUpdateListener(value -> {
    fill = (float) value.getAnimatedValue();
    invalidate();
   });
   animator.start();
  }

  @Override protected void onDetachedFromWindow() {
   if (animator != null) animator.cancel();
   animator = null;
   fill = 1f;
   super.onDetachedFromWindow();
  }

  @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
   super.onSizeChanged(width, height, oldWidth, oldHeight);
   bounds.set(0, 0, width, height);
   outline.reset();
   outline.addRoundRect(bounds, dp(9), dp(9), android.graphics.Path.Direction.CW);
   bevel = new android.graphics.LinearGradient(0f, 0f, 0f, Math.max(1, height),
    new int[]{0x75ffffff, 0x16ffffff, 0x38031119},
    new float[]{0f, .40f, 1f}, android.graphics.Shader.TileMode.CLAMP);
  }

  @Override protected void onDraw(android.graphics.Canvas canvas) {
   super.onDraw(canvas);
   canvas.save();
   canvas.clipPath(outline);
   canvas.drawColor(0xff263d4b);
   canvas.clipRect(0, 0, getWidth() * fill, getHeight());
   float scale = getWidth() / (float) length;
   long offset = 0;
   for (int i = 0; i < cuts.size(); i++) {
    int cut = cuts.get(i);
    paint.setColor(CUT_RED);
    canvas.drawRect(offset * scale, 0, (offset + cut) * scale, getHeight(), paint);
    offset += cut;
    if (i == cuts.size() - 1) break;
    paint.setColor(KERF_GOLD);
    canvas.drawRect(offset * scale, 0, (offset + kerf) * scale, getHeight(), paint);
    offset += kerf;
    if (offset < length) {
     paint.setColor(0xff66263b);
     canvas.drawLine(offset * scale, 0, offset * scale, getHeight(), paint);
    }
   }
   paint.setColor(REMAIN_GREEN);
   canvas.drawRect(offset * scale, 0, getWidth(), getHeight(), paint);
   paint.setShader(bevel);
   canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
   paint.setShader(null);
   if (fill > 0f && fill < 1f) {
    float endpoint = getWidth() * fill;
    paint.setColor(0x64fbfff6);
    canvas.drawRect(Math.max(0, endpoint - dp(2)), 0, endpoint, getHeight(), paint);
   }
   canvas.restore();
   paint.setColor(0x8ab0d6d6);
   paint.setStrokeWidth(dp(1));
   paint.setStyle(android.graphics.Paint.Style.STROKE);
   canvas.drawRoundRect(.5f, .5f, getWidth() - .5f, getHeight() - .5f, dp(9), dp(9), paint);
   paint.setStyle(android.graphics.Paint.Style.FILL);
  }
 }

 void opt() {
  page("Profil kesim optimizasyonu");
  button("Fotoğraflardan kesim planı oluştur", () -> photo());
  EditText profileName = input("PROFİL İSMİ", "");
  profileName.setSingleLine(true);
  note("Her satıra ölçü × adet girin. Stok boylarını noktalı virgülle ayırabilirsiniz. Testere payı parçaların arasına eklenir.");
  EditText stock = input("HAM PROFİL BOYU (mm)", String.valueOf(DEFAULT_PROFILE_STOCK));
  EditText kerf = input("TESTERE PAYI (mm / parça arası)", String.valueOf(DEFAULT_KERF));
  kerf.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
  EditText cuts = input("KESİM ÖLÇÜSÜ × ADET · örnek 1250×3", "");
  cuts.setSingleLine(false);
  cuts.setMinLines(4);
  cuts.getLayoutParams().height = dp(140);
  restoreFields(profileName,stock,kerf,cuts);
  button("Kesim planı oluştur", () -> {
   try {
    List<Integer> stocks = ShopDimensions.stocks(stock.getText().toString());
    int saw = ShopDimensions.tenths(kerf.getText().toString(), "Testere payı");
    List<Integer> pieces = new ArrayList<>();
    for (String row : cuts.getText().toString().split("[\\n;]+")) {
     if (row.trim().isEmpty()) continue;
     String[] fields = row.split("(?i)\\s*[x×*]\\s*", -1);
     if (fields.length > 2) throw new IllegalArgumentException("Her satıra ölçü × adet girin.");
     int size = ShopDimensions.tenths(fields[0], "Kesim ölçüsü");
     int quantity = fields.length == 1 ? 1 : cutNumber(fields[1], "Kesim adedi");
     if (quantity < 1 || quantity > 1000 || pieces.size() + quantity > 3000) {
      throw new IllegalArgumentException("Kesim adetlerini kontrol edin. Bir plana en fazla 3000 parça ekleyebilirsiniz.");
     }
     for (int i = 0; i < quantity; i++) pieces.add(size);
    }
    if (pieces.isEmpty()) throw new IllegalArgumentException("En az bir kesim ölçüsü girin.");
    List<CutPacking.Bar> bars = CutPacking.pack(pieces, stocks, saw);
    long net = 0, remaining = 0;
    for (int piece : pieces) net += piece;
    int common = bars.get(0).stock;
    for (CutPacking.Bar bar : bars) {
     remaining += bar.remaining();
     if (common != bar.stock) common = 0;
    }
    saveFields("Optimizasyon",profileName,stock,kerf,cuts);
    page(photoReview == albertReview ? "Albert Genau · Kesim optimizasyonu" : "Kesim optimizasyon sonucu");
    planSummary(common, saw, bars.size(), pieces.size(), net, remaining, ShopDimensions.PER_MM);
    String name = profileName.getText().toString().trim();
    LinearLayout profile = planCard(body, 0xff172b44, 0xff254662, 0xff597d9b);
    profileHeading(profile, name.isEmpty() ? "Profil" : name, null);
    for (int i = 0; i < bars.size(); i++) {
     CutPacking.Bar bar = bars.get(i);
     planBar(profile, i + 1, bar.pieces, bar.stock, saw, bar.remaining(), i < 12, ShopDimensions.PER_MM);
    }
    button("Yeni kesim planı", () -> {historyId=null;opt();});
    button("Ana menü", () -> home());
   } catch (IllegalArgumentException error) {
    Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
   }
  });
  button("Ana menü", () -> home());
 }
 static final class GlassRow {
  final TextView heading, result;
  final EditText width, height, quantity;

  GlassRow(TextView heading, TextView result, EditText width, EditText height, EditText quantity) {
   this.heading = heading;
   this.result = result;
   this.width = width;
   this.height = height;
   this.quantity = quantity;
  }
 }

 void glass() {
  page("Cam m² Hesaplama");
  LinearLayout cards = new LinearLayout(this);
  cards.setOrientation(LinearLayout.VERTICAL);
  body.addView(cards, new LinearLayout.LayoutParams(-1, -2));
  ArrayList<GlassRow> rows = new ArrayList<>();
  TextView total = text("", 21);
  Runnable clearResults = () -> {
   if (total.getVisibility() != android.view.View.VISIBLE) return;
   total.setVisibility(android.view.View.GONE);
   for (GlassRow row : rows) row.result.setVisibility(android.view.View.GONE);
  };
  addGlassRow(cards, rows, clearResults);
  if(historyRestore!=null){int count=historyRestore.getInt("count",1);while(rows.size()<count)addGlassRow(cards,rows,clearResults);for(int i=0;i<rows.size();i++){GlassRow r=rows.get(i);r.width.setText(historyRestore.getString("w"+i,""));r.height.setText(historyRestore.getString("h"+i,""));r.quantity.setText(historyRestore.getString("q"+i,"1"));}historyRestore=null;}
  button("Cam ekle", () -> {
   clearResults.run();
   addGlassRow(cards, rows, clearResults);
  });
  button("Toplam m² hesapla", () -> {calculateGlass(rows,total);if(total.getVisibility()==View.VISIBLE){android.os.Bundle b=new android.os.Bundle();b.putInt("count",rows.size());for(int i=0;i<rows.size();i++){GlassRow r=rows.get(i);b.putString("w"+i,r.width.getText().toString());b.putString("h"+i,r.height.getText().toString());b.putString("q"+i,r.quantity.getText().toString());}saveHistory("Cam m²",b);}});
  total.setTypeface(null, Typeface.BOLD);
  total.setTextColor(0xffa7ffdd);
  addTextPanel(total, 0xff122a42, 0xff17525a, 0xff4cf0c2);
  total.setVisibility(android.view.View.GONE);
  total.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);
  button("Ana menü", () -> home());
 }

 void addGlassRow(LinearLayout cards, List<GlassRow> rows, Runnable clearResults) {
  boolean first = rows.isEmpty();
  LinearLayout card = planCard(cards, 0xff13233f, 0xff263f62, 0xff58aec6);
  TextView heading = planLabel(card, "Cam " + (rows.size() + 1), 18, Color.WHITE);
  heading.setTypeface(null, Typeface.BOLD);
  EditText width = input(card, "En (mm)", "");
  EditText height = input(card, "Yükseklik (mm)", "");
  EditText quantity = input(card, "Cam adedi", "1");
  TextView result = planLabel(card, "", 15, 0xff88f9d0);
  result.setVisibility(android.view.View.GONE);
  GlassRow row = new GlassRow(heading, result, width, height, quantity);
  rows.add(row);
  android.text.TextWatcher watcher = new android.text.TextWatcher() {
   @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
   @Override public void onTextChanged(CharSequence text, int start, int before, int count) { clearResults.run(); }
   @Override public void afterTextChanged(android.text.Editable text) {}
  };
  for (EditText field : new EditText[]{width, height, quantity}) {
   field.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
    | (field == quantity ? 0 : android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL));
   field.addTextChangedListener(watcher);
  }
  if (!first) {
   Button remove = new Button(this);
   remove.setAllCaps(false);
   remove.setText("Camı kaldır");
   remove.setTextColor(0xffffd5df);
   remove.setBackground(rgbPanel(0xff34243e, 0xff51354f, 0xffaa719b));
   card.addView(remove, new LinearLayout.LayoutParams(-1, dp(48)));
   remove.setOnClickListener(view -> {
    clearResults.run();
    rows.remove(row);
    cards.removeView(card);
    for (int i = 0; i < rows.size(); i++) rows.get(i).heading.setText("Cam " + (i + 1));
   });
   reveal(card, 0);
  }
 }

 void calculateGlass(List<GlassRow> rows, TextView total) {
  ArrayList<String> results = new ArrayList<>();
  java.math.BigDecimal area = java.math.BigDecimal.ZERO;
  long pieces = 0;
  for (GlassRow row : rows) {
   int width = positiveGlassLength(row.width, "Cam eni");
   int height = positiveGlassLength(row.height, "Cam yüksekliği");
   int quantity;
   try {
    quantity = val(row.quantity);
    if (quantity <= 0) throw new NumberFormatException();
   } catch (NumberFormatException e) {
    row.quantity.setError("Sıfırdan büyük bir adet girin.");
    quantity = 0;
   }
   if (width == 0 || height == 0 || quantity == 0) {
    (width == 0 ? row.width : height == 0 ? row.height : row.quantity).requestFocus();
    return;
   }
   java.math.BigDecimal single = ShopDimensions.area(width, height, 1);
   java.math.BigDecimal subtotal = ShopDimensions.area(width, height, quantity);
   pieces += quantity;
   area = area.add(subtotal);
   results.add(ShopDimensions.mm(width) + " × " + ShopDimensions.mm(height) + " mm  ·  " + quantity
    + " adet\nTek cam " + ShopDimensions.squareMeters(single) + " m²  ·  Toplam "
    + ShopDimensions.squareMeters(subtotal) + " m²");
  }
  for (int i = 0; i < rows.size(); i++) {
   TextView result = rows.get(i).result;
   result.setText(results.get(i));
   result.setVisibility(android.view.View.VISIBLE);
  }
  total.setText("Genel toplam " + ShopDimensions.squareMeters(area) + " m²\n" + pieces + " adet cam");
  total.setVisibility(android.view.View.VISIBLE);
  reveal(total, 0);
 }

 int positiveGlassLength(EditText field, String label) {
  try {
   int value = ShopDimensions.tenths(field.getText().toString(), label);
   if (value > 0) return value;
   field.setError("Sıfırdan büyük bir ölçü girin.");
  } catch (IllegalArgumentException e) {
   field.setError(e.getMessage());
  }
  return 0;
 }

 void panels(){
  page("Kanat bölme hesabı");
  note("Net açıklığı seçilen kanat adedine böler. Profil ve montaj paylarını toplam düşüm alanına girin.");
  EditText w=input("AÇIKLIK ENİ (mm)","");
  EditText n=input("KANAT ADEDİ","");
  EditText ded=input("TOPLAM DÜŞÜM (mm)","0");
  int decimal = android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL;
  w.setInputType(decimal);
  ded.setInputType(decimal);
  n.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
  restoreFields(w,n,ded);
  TextView answer = text("", 16);
  answer.setTextColor(0xffeffaff);
  answer.setVisibility(View.GONE);
  addTextPanel(answer, 0xff14233e, 0xff285575, 0xff4ecade);
  button("Hesapla",()->{
   try{
    int width = ShopDimensions.tenths(w.getText().toString(), "Açıklık eni");
    int count = cutNumber(n.getText().toString(), "Kanat adedi");
    int deduction = ShopDimensions.tenths(ded.getText().toString(), "Toplam düşüm");
    int net = width - deduction;
    if (width <= 0 || count <= 0 || deduction < 0 || net < count) {
     throw new IllegalArgumentException("Net açıklık, düşüm ve kanat adedini kontrol edin.");
    }
    int base = net / count, extra = net % count;
    String widths = extra == 0 ? count + " × " + ShopDimensions.mm(base) + " mm"
     : extra + " × " + ShopDimensions.mm(base + 1) + " mm  ·  " + (count - extra) + " × " + ShopDimensions.mm(base) + " mm";
    saveFields("Kanat bölme",w,n,ded);
    answer.setText("Net açıklık " + ShopDimensions.mm(net) + " mm\n" + widths);
    answer.setVisibility(View.VISIBLE);
   } catch (IllegalArgumentException e) {
    answer.setVisibility(View.GONE);
    Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
   }
  });
  button("Ana menü",()->home());
 }
}
