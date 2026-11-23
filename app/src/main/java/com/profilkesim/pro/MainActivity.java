package com.profilkesim.pro;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.graphics.BlurMaskFilter;
import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.content.ContentValues;
import com.google.mlkit.vision.text.Text;
import java.util.regex.*;
import android.net.Uri;
import android.provider.MediaStore;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
 LinearLayout root,body;
 Uri cameraUri;
 final ArrayList<CutRow> detected = new ArrayList<>();
 final Map<String, String> stockDrafts = new HashMap<>();
 String[] extraDraft = {"", "", "", "6000", "3"};
 int photoNumber, photosRead;
 boolean photoBatchRunning;
 Runnable savePhotoReview;
 String photoBatchWarning = "";
 static class CutRow {
  String code,name;int mm,qty,y;Bitmap icon;
  int photo;
  String[] draft;
  CutRow(String c,String n,int m,int q,Bitmap b,int cy){
   code=c;name=n;mm=m;qty=q;icon=b;y=cy;
  }
 }
 static final int PICK_IMAGE=41,TAKE_PHOTO=42;
 int navy=0xff173447,teal=0xff087e89;
 int dp(int n){return (int)(n*getResources().getDisplayMetrics().density);}
 GradientDrawable box(int color){
  GradientDrawable d=new GradientDrawable();
  d.setColor(color);d.setCornerRadius(dp(12));return d;
 }
 TextView text(String s,int size){
  TextView t=new TextView(this);t.setText(s);t.setTextSize(size);
  t.setTextColor(navy);t.setPadding(dp(12),dp(12),dp(12),dp(12));return t;
 }
 private static final int PICK_MUSIC = 43;
 private static final String MUSIC_MUTED = "home_music_muted";
 private static final String MUSIC_URI = "home_music_uri";
 private static final String MUSIC_SETTINGS = "MainActivity";
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
  if (getPreferences(MODE_PRIVATE).getBoolean(MUSIC_MUTED, false)) {
   stopService(command);
  } else if (android.os.Build.VERSION.SDK_INT >= 26) {
   startForegroundService(command);
  } else {
   startService(command);
  }
 }

 public static final class MusicService extends android.app.Service {
  private static final String CHANNEL = "profile_music";
  private static final String MUTE_ACTION = "com.profilkesim.pro.MUTE_MUSIC";
  private static final int NOTIFICATION = 31;
  private android.media.AudioManager audio;
  private android.media.MediaPlayer player;
  private String currentSource;
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
   startForeground(NOTIFICATION, playbackNotification());
   if (settings.getBoolean(MUSIC_MUTED, false)) {
    stopSelf();
    return START_NOT_STICKY;
   }
   String source = settings.getString(MUSIC_URI, "");
   if (source.equals(currentSource)) return START_NOT_STICKY;
   currentSource = source;
   releaseTrack();
   Uri track = source.isEmpty()
    ? new Uri.Builder().scheme(android.content.ContentResolver.SCHEME_ANDROID_RESOURCE)
      .authority(getPackageName()).appendPath(String.valueOf(R.raw.home_music)).build()
    : Uri.parse(source);
   prepareTrack(track);
   return START_NOT_STICKY;
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
    .setContentTitle("ProfilKesimPro").setContentText("Arka plan müziği")
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
   if (focused) player.start();
   else if (player.isPlaying()) player.pause();
  }

  private void playbackError() {
   Toast.makeText(this, "Müzik açılamadı. Ana ekrandan ses dosyasını yeniden seçin.", Toast.LENGTH_LONG).show();
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

 void page(String title){
  savePhotoReview = null;
  musicButton = null;
  root=new LinearLayout(this);root.setOrientation(1);
  root.setBackgroundResource(R.drawable.cambalkon);
  setContentView(root);
  TextView brand=text("muhammet can . K",12);
  brand.setTextColor(Color.WHITE);brand.setGravity(Gravity.CENTER);
  brand.setBackground(rgbPanel(0xff0e1933, 0xff233953, 0xff3ab6c9));
  {
   LinearLayout masthead = new LinearLayout(this);
   masthead.setGravity(Gravity.CENTER_VERTICAL);
   masthead.setBackground(rgbPanel(0xff0e1933, 0xff233953, 0xff3ab6c9));
   brand.setBackgroundColor(Color.TRANSPARENT);
   masthead.addView(brand, new LinearLayout.LayoutParams(0, dp(50), 1f));
   musicButton = new Button(this);
   musicButton.setAllCaps(false);
   musicButton.setTextSize(13);
   musicButton.setTextColor(Color.WHITE);
   musicButton.setPadding(dp(12), 0, dp(12), 0);
   musicButton.setBackground(new android.graphics.drawable.RippleDrawable(
    android.content.res.ColorStateList.valueOf(0x66a1fff3),
    rgbPanel(0xff193154, 0xff185467, 0xff54d7e1), null));
   LinearLayout.LayoutParams buttonSize = new LinearLayout.LayoutParams(-2, dp(48));
   buttonSize.setMarginEnd(dp(7));
   masthead.addView(musicButton, buttonSize);
   root.addView(masthead, new LinearLayout.LayoutParams(-1, dp(56)));
   updateMusicButton();
   musicButton.setOnClickListener(view -> {
    android.content.SharedPreferences settings = getPreferences(MODE_PRIVATE);
    boolean muted = !settings.getBoolean(MUSIC_MUTED, false);
    settings.edit().putBoolean(MUSIC_MUTED, muted).apply();
    updateMusicButton();
    updateMusicService();
   });
  }
  
brand.setText("✦ MUHAMMET CAN . K ✦");
brand.setTextSize(17);
brand.setTypeface(null, Typeface.BOLD);
brand.setTextColor(0xffffd76a);
brand.setLayerType(
    android.view.View.LAYER_TYPE_SOFTWARE, null
);

brand.setShadowLayer(
    dp(12), 0, 0, 0xffffc400
);

ValueAnimator neon = ValueAnimator.ofArgb(
    0xffffd76a,
    0xff00f5ff,
    0xffffd76a
);

neon.setDuration(2400);
neon.setRepeatCount(ValueAnimator.INFINITE);
neon.setInterpolator(
    new AccelerateDecelerateInterpolator()
);

neon.addUpdateListener(animation -> {
    int color = (int) animation.getAnimatedValue();
    brand.setTextColor(color);
    brand.setShadowLayer(dp(12), 0, 0, color);
});

neon.start();

ObjectAnimator pulse = ObjectAnimator.ofFloat(
    brand, "scaleX", 1.0f, 1.04f, 1.0f
);

pulse.setDuration(2400);
pulse.setRepeatCount(ValueAnimator.INFINITE);
pulse.start();
  brand.addOnAttachStateChangeListener(new android.view.View.OnAttachStateChangeListener() {
   @Override public void onViewAttachedToWindow(android.view.View view) {}
   @Override public void onViewDetachedFromWindow(android.view.View view) {
    neon.cancel();
    pulse.cancel();
   }
  });

  ScrollView scroll=new ScrollView(this);root.addView(scroll);
  body=new LinearLayout(this);body.setOrientation(1);
  body.setPadding(dp(14),dp(12),dp(14),dp(20));scroll.addView(body);
  TextView h=text(title,23);h.setTypeface(null,Typeface.BOLD);
  h.setTextColor(Color.WHITE);
  h.setShadowLayer(dp(5), 0, 0, 0xff20a4dc);
  h.setElevation(dp(4));
  addTextPanel(h, 0xff121c37, 0xff164e61, 0xff4ce5de);
  reveal(body, 0);
 }
 void note(String s){
  TextView t = text(s, 14);
  t.setTextColor(0xffdff5ff);
  addTextPanel(t, 0xff14223a, 0xff243f5f, 0xff4f7b9b);
 }
 void button(String s,Runnable r){
  Button b=new Button(this);b.setText(s);b.setAllCaps(false);
  b.setTextColor(Color.WHITE);
  b.setBackground(new android.graphics.drawable.RippleDrawable(
   android.content.res.ColorStateList.valueOf(0x66a1fff3),
   rgbPanel(0xff1d2853, 0xff086a76, 0xff5bc9cf), null));
  b.setShadowLayer(dp(4), 0, 0, 0xff389dbd);
  b.setPadding(dp(12), 0, dp(12), 0);
  b.setElevation(dp(4));
  LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(54));
  p.setMargins(0,dp(6),0,dp(6));body.addView(b,p);
  if (body.getChildCount() < 10) reveal(b, body.getChildCount() * 24);
  b.setOnClickListener(v -> r.run());
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
   e.animate().translationZ(dp(focused ? 5 : 0)).setDuration(180).start();
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
  setVolumeControlStream(android.media.AudioManager.STREAM_MUSIC);
  getPreferences(MODE_PRIVATE).registerOnSharedPreferenceChangeListener(musicSettingsChanged);
  home();
 }
 @Override protected void onResume() {
  super.onResume();
  updateMusicButton();
  updateMusicService();
 }
 @Override protected void onPause() {
  if (savePhotoReview != null) savePhotoReview.run();
  super.onPause();
 }
 @Override protected void onDestroy() {
  getPreferences(MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(musicSettingsChanged);
  super.onDestroy();
 }
 @Override public void onBackPressed(){
  if (photoBatchRunning) {
   Toast.makeText(this, "Fotoğraflar okunuyor, lütfen bekleyin.", Toast.LENGTH_SHORT).show();
   return;
  }
  if (savePhotoReview != null) savePhotoReview.run();
  home();
 }
 void home(){
  page("Profil Kesim Pro");
  note("Cam balkon ve doğrama hesaplama • Ölçüler mm");
  button("Müzik dosyası seç", () -> {
   Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT);
   picker.addCategory(Intent.CATEGORY_OPENABLE);
   picker.setType("audio/*");
   picker.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
   startActivityForResult(picker, PICK_MUSIC);
  });
  if (getPreferences(MODE_PRIVATE).contains(MUSIC_URI)) {
   button("Varsayılan müziğe dön", () -> {
    getPreferences(MODE_PRIVATE).edit().remove(MUSIC_URI).apply();
    updateMusicService();
    home();
   });
  }
  button("Fotoğraftan kesim listesi oku",()->photo());
  button("Profil kesim optimizasyonu",()->opt());
  button("Cam alanı ve adet hesabı",()->glass());
  button("Kanat eşit bölme hesabı",()->panels());
  for(String s:new String[]{
   "Tiara Twin","Statü","Sürme","Giyotin","Cam tavan",
   "PVC","Sineklik","Duşakabin","ASAŞ RST116 Hebeschiebe"
  })button(s,()->system(s));
 }
 void photo() {
  if (!detected.isEmpty()) {
   showDetected();
   return;
  }
  page("Fotoğraflardan kesim listesi");
  note("Bir veya daha fazla Albert Genau föyü ekleyin. Uygulama aynı profil koduna ait parçaları birlikte optimize eder.");
  if (!photoBatchWarning.isEmpty()) note(photoBatchWarning);
  addPhotoButtons(() -> {});
  button("Elle satır ekle", () -> showDetected());
  button("Ana menü", () -> home());
 }

 void addPhotoButtons(Runnable saveDraft) {
  button("Galeriden fotoğraf ekle", () -> {
   saveDraft.run();
   Intent picker = new Intent(Intent.ACTION_GET_CONTENT);
   picker.setType("image/*");
   picker.addCategory(Intent.CATEGORY_OPENABLE);
   picker.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
   startActivityForResult(picker, PICK_IMAGE);
  });
  button("Kameradan fotoğraf ekle", () -> {
   saveDraft.run();
   try {
    ContentValues values = new ContentValues();
    values.put(MediaStore.Images.Media.DISPLAY_NAME, "profil_foy_" + System.currentTimeMillis() + ".jpg");
    values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
    cameraUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
    if (cameraUri == null) throw new IllegalStateException("Fotoğraf dosyası oluşturulamadı");
    Intent camera = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
    camera.putExtra(MediaStore.EXTRA_OUTPUT, cameraUri);
    camera.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
    startActivityForResult(camera, TAKE_PHOTO);
   } catch (Exception e) {
    Toast.makeText(this, "Kamera açılamadı: " + e.getMessage(), Toast.LENGTH_LONG).show();
   }
  });
 }

 @Override protected void onActivityResult(int request, int result, Intent data) {
  super.onActivityResult(request, result, data);
  if (request == PICK_MUSIC) {
   if (result == RESULT_OK && data != null && data.getData() != null) {
    Uri uri = data.getData();
    try {
     getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
     getPreferences(MODE_PRIVATE).edit().putString(MUSIC_URI, uri.toString()).apply();
     updateMusicService();
    } catch (SecurityException e) {
     Toast.makeText(this, "Bu ses dosyası kaydedilemedi. Başka bir dosya seçin.", Toast.LENGTH_LONG).show();
    }
   }
   return;
  }
  if (result != RESULT_OK || photoBatchRunning) return;
  LinkedHashSet<Uri> selected = new LinkedHashSet<>();
  if (request == TAKE_PHOTO && cameraUri != null) {
   selected.add(cameraUri);
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
  photoBatchRunning = true;
  page("Fotoğraflar okunuyor");
  TextView progress = text("", 16);
  progress.setTextColor(Color.WHITE);
  addTextPanel(progress, 0xff14233e, 0xff285575, 0xff4ecade);
  readPhotoBatch(new ArrayList<>(selected), 0, new ArrayList<>(), progress,
   TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS));
 }

 void readPhotoBatch(List<Uri> photos, int index, List<Integer> failures, TextView progress,
                     com.google.mlkit.vision.text.TextRecognizer recognizer) {
  if (isDestroyed() || isFinishing()) {
   recognizer.close();
   return;
  }
  if (index == photos.size()) {
   recognizer.close();
   photoBatchRunning = false;
   photoBatchWarning = failures.isEmpty() ? "" : "Profil okunamayan fotoğraflar: " + failures
    + ". Bu fotoğrafları yeniden ekleyin veya eksik parçaları elle girin.";
   showDetected();
   return;
  }
  int number = ++photoNumber;
  progress.setText("Fotoğraf " + (index + 1) + " / " + photos.size() + " okunuyor…");
  Runnable next = () -> readPhotoBatch(photos, index + 1, failures, progress, recognizer);
  Runnable failed = () -> {
   failures.add(number);
   next.run();
  };
  new Thread(() -> {
   Bitmap bitmap;
   try {
    if (android.os.Build.VERSION.SDK_INT >= 28) {
     bitmap = android.graphics.ImageDecoder.decodeBitmap(
      android.graphics.ImageDecoder.createSource(getContentResolver(), photos.get(index)),
      (decoder, info, source) -> decoder.setAllocator(android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE));
    } else {
     bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), photos.get(index));
    }
   } catch (Exception e) {
    runOnUiThread(failed);
    return;
   }
   runOnUiThread(() -> {
    if (isDestroyed() || isFinishing()) {
     recognizer.close();
     return;
    }
    try {
     recognizer.process(InputImage.fromBitmap(bitmap, 0)).addOnSuccessListener(doc -> {
      ArrayList<CutRow> rows;
      try {
       rows = parseSheet(doc, bitmap);
      } catch (RuntimeException e) {
       failed.run();
       return;
      }
      if (rows.isEmpty()) {
       failed.run();
       return;
      }
      readColumns(bitmap, doc, rows, recognizer, () -> {
       for (CutRow row : rows) row.photo = number;
       detected.addAll(rows);
       photosRead++;
       next.run();
      });
     }).addOnFailureListener(e -> failed.run());
    } catch (RuntimeException e) {
     failed.run();
    }
   });
  }, "photo-decoder").start();
 }

 String profileName(String code){
  switch(code){
   case "B15082":return "42×123 Kasa üst profili";
   case "B15083":return "11×123 Yan dikme profili";
   case "B15085":return "96×26 Kanat profili";
   case "B15086":return "31×34 Kanat çektirme profili";
   case "B15087":return "33×19 Yan birleşim kilit karşılık profili";
   case "B15081":return "33×22 Yatay ayar profili";
   case "B15099":return "70×44 Çektirmeli kilit ara dikme profili";
   case "B15088":return "59×27 Orta birleşim kilit karşılık profili";
   case "B15092":return "42×36 Tek ray profili";
   case "B15091":return "11 Tek ray kapak profili";
   default:return "Profil (adını kontrol edin)";
  }
 }
 ArrayList<CutRow> parseSheet(Text doc,Bitmap bmp){
  ArrayList<CutRow> rows=new ArrayList<>();
  HashSet<String> seen=new HashSet<>();
  HashMap<CutRow, Rect> anchors = new HashMap<>();
  Pattern codes=Pattern.compile(
   "[B8]\\s*1\\s*5\\s*0\\s*[0-9]{2}",Pattern.CASE_INSENSITIVE);
  for(Text.TextBlock block:doc.getTextBlocks())
   for(Text.Line line:block.getLines()){
    Matcher match=codes.matcher(
     line.getText().toUpperCase(Locale.ROOT));
    if(!match.find())continue;
    String code=match.group().replaceAll("\\s+","")
     .replaceFirst("^8","B");
    Rect rect = line.getBoundingBox();
    if (rect == null || !seen.add(code)) continue;
    CutRow row = new CutRow(code, profileName(code), -1, -1, null, rect.centerY());
    rows.add(row);
    anchors.put(row, rect);
   }
  Collections.sort(rows, (a, b) -> Integer.compare(a.y, b.y));
  cropProfileIcons(bmp, rows, anchors);
  return rows;
 }
 
 void cropProfileIcons(Bitmap bitmap, List<CutRow> rows, Map<CutRow, Rect> anchors) {
  if (bitmap == null || rows.isEmpty()) return;
  int width = bitmap.getWidth(), height = bitmap.getHeight();
  ArrayList<Integer> gaps = new ArrayList<>();
  for (int i = 1; i < rows.size(); i++) {
   int gap = rows.get(i).y - rows.get(i - 1).y;
   if (gap > 0) gaps.add(gap);
  }
  Collections.sort(gaps);
  int typicalGap = gaps.isEmpty() ? 0 : gaps.get((gaps.size() - 1) / 2);
  for (int i = 0; i < rows.size(); i++) {
   CutRow row = rows.get(i);
   Rect anchor = anchors.get(row);
   int half = Math.max(16, Math.max(anchor.height(), (int) (typicalGap * .60f)));
   int top = Math.max(0, row.y - half);
   int bottom = Math.min(height, row.y + half);
   if (i > 0) top = Math.max(top, row.y - (row.y - rows.get(i - 1).y) / 2);
   if (i + 1 < rows.size()) bottom = Math.min(bottom, row.y + (rows.get(i + 1).y - row.y) / 2);
   int inset = Math.min(Math.max(0, (bottom - top) / 24), 6);
   top += inset;
   bottom -= inset;
   int left = (int) (width * .035f);
   int right = (int) (width * .23f);
   if (anchor.left >= width * .14f && anchor.left <= width * .36f) {
    right = anchor.left - Math.max(1, width / 100);
   }
   if (right > left && bottom > top) {
    row.icon = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top);
   }
  }
 }

 void profileHeading(LinearLayout card, String name, Bitmap icon) {
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
 }

void readColumns(Bitmap bmp, Text doc, List<CutRow> rows,
                 com.google.mlkit.vision.text.TextRecognizer recognizer, Runnable done) {
    float measureX = bmp.getWidth() * 0.60f;
    float quantityX = bmp.getWidth() * 0.72f;
    for (Text.TextBlock block : doc.getTextBlocks()) {
        for (Text.Line line : block.getLines()) {
            String word = line.getText().toUpperCase(Locale.ROOT)
                .replace("Ü", "U").replace("İ", "I").replace("Ö", "O").trim();
            Rect rect = line.getBoundingBox();
            if (rect == null) continue;
            if (word.equals("OLCU")) measureX = rect.centerX();
            if (word.equals("MIKTAR")) quantityX = rect.centerX();
        }
    }
    scanCells(bmp, measureX, quantityX, 0, rows, recognizer, done);
}

void scanCells(
    Bitmap bmp,
    float measureX,
    float quantityX,
    int index,
    List<CutRow> rows,
    com.google.mlkit.vision.text.TextRecognizer recognizer,
    Runnable done
) {
    if (index >= rows.size() * 2) {
        for (CutRow row : rows) {
            if (row.mm == 0 && row.qty < 0) {
                row.qty = 0;
            }
        }
        done.run();
        return;
    }

    int rowIndex = index / 2;
    boolean quantity = index % 2 == 1;

    CutRow row = rows.get(rowIndex);

    // Satırın üst ve alt sınırlarını hesapla
    int top;
    int bottom;

    if (rowIndex == 0) {
        top = row.y - 35;
    } else {
        top = (rows.get(rowIndex - 1).y + row.y) / 2;
    }

    if (rowIndex == rows.size() - 1) {
        bottom = row.y + 35;
    } else {
        bottom = (row.y + rows.get(rowIndex + 1).y) / 2;
    }

    top = Math.max(0, top);
    bottom = Math.min(bmp.getHeight(), bottom);

    float centerX = quantity ? quantityX : measureX;
    int cellWidth = Math.max(
        20,
        (int)(bmp.getWidth() * 0.085f)
    );

    int left = Math.max(
        0,
        (int)centerX - cellWidth / 2
    );

    int width = Math.min(
        cellWidth,
        bmp.getWidth() - left
    );

    int height = bottom - top;

    if (width < 5 || height < 5) {
        scanCells(bmp, measureX, quantityX, index + 1, rows, recognizer, done);
        return;
    }

    try {
        Bitmap cell = Bitmap.createBitmap(
            bmp, left, top, width, height
        );

        Bitmap enlarged = Bitmap.createScaledBitmap(
            cell,
            width * 4,
            height * 4,
            true
        );

        recognizer.process(InputImage.fromBitmap(enlarged, 0))
        .addOnSuccessListener(result -> {

            String raw = result.getText()
                .trim()
                .toUpperCase(Locale.ROOT)
                .replace("O", "0")
                .replace("〇", "0");

            java.util.regex.Matcher matcher =
                Pattern.compile("\\b[0-9]{1,4}\\b")
                .matcher(raw);

            int number = -1;

            while (matcher.find()) {
                try {
                    int candidate = Integer.parseInt(
                        matcher.group()
                    );

                    if (quantity) {
                        if (candidate >= 0 &&
                            candidate <= 100) {
                            number = candidate;
                            break;
                        }
                    } else {
                        if (candidate >= 0 &&
                            candidate <= 6500) {
                            number = candidate;
                            break;
                        }
                    }
                } catch (Exception ignored) {}
            }

            if (number >= 0) {
                if (quantity) {
                    row.qty = number;
                } else {
                    row.mm = number;
                }
            }

            scanCells(
                bmp, measureX, quantityX, index + 1, rows, recognizer, done
            );

        }).addOnFailureListener(e -> {
            scanCells(
                bmp, measureX, quantityX, index + 1, rows, recognizer, done
            );
        });

    } catch (Exception e) {
        scanCells(
            bmp, measureX, quantityX, index + 1, rows, recognizer, done
        );
    }
}

  void scanColumn(Bitmap bmp, double left, double right,
    boolean quantity, Runnable finished) {
    try {
        int x = (int)(bmp.getWidth() * left);
        int w = (int)(bmp.getWidth() * (right - left));

        if (w < 8) {
            finished.run();
            return;
        }

        Bitmap strip = Bitmap.createBitmap(
            bmp, x, 0, w, bmp.getHeight());

        Bitmap enlarged = Bitmap.createScaledBitmap(
            strip, w * 4, bmp.getHeight() * 4, true);

        TextRecognition.getClient(
            TextRecognizerOptions.DEFAULT_OPTIONS
        ).process(InputImage.fromBitmap(enlarged, 0))
        .addOnSuccessListener(doc -> {

            HashMap<CutRow, Integer> bestValues =
                new HashMap<>();
            HashMap<CutRow, Integer> bestDistances =
                new HashMap<>();

            for (Text.TextBlock block : doc.getTextBlocks()) {
                for (Text.Line line : block.getLines()) {
                    Rect rect = line.getBoundingBox();
                    if (rect == null) continue;

                    String raw = line.getText()
                        .trim()
                        .toUpperCase(Locale.ROOT)
                        .replace("O", "0")
                        .replace("〇", "0")
                        .replace(" ", "");

                    String digits = raw.replaceAll(
                        "[^0-9]", "");

                    if (digits.isEmpty()) continue;

                    if (digits.length() >
                        (quantity ? 3 : 4)) continue;

                    int number;
                    try {
                        number = Integer.parseInt(digits);
                    } catch (Exception e) {
                        continue;
                    }

                    if (quantity && number > 100)
                        continue;

                    if (!quantity && number > 6500)
                        continue;

                    int cy = rect.centerY() / 4;

                    CutRow closest = null;
                    int distance = Integer.MAX_VALUE;

                    for (CutRow row : detected) {
                        int d = Math.abs(row.y - cy);
                        if (d < distance) {
                            distance = d;
                            closest = row;
                        }
                    }

                    if (closest == null) continue;

                    int tolerance = Math.max(
                        14, bmp.getHeight() / 65);

                    if (distance > tolerance) continue;

                    Integer oldDistance =
                        bestDistances.get(closest);

                    if (oldDistance == null ||
                        distance < oldDistance) {
                        bestDistances.put(
                            closest, distance);
                        bestValues.put(
                            closest, number);
                    }
                }
            }

            for (CutRow row : detected) {
                Integer number = bestValues.get(row);

                if (number == null) continue;

                if (quantity) {
                    row.qty = number;
                } else {
                    row.mm = number;
                }
            }

            finished.run();
        })
        .addOnFailureListener(e -> finished.run());

    } catch (Exception e) {
        finished.run();
    }
}
 static final int DEFAULT_PROFILE_STOCK = 6000;

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
 }

 void showDetected() {
  page("Profil kesim föyleri – kontrol");
  note(photosRead + " fotoğraf okundu  ·  " + detected.size() + " profil satırı");
  if (!photoBatchWarning.isEmpty()) note(photoBatchWarning);

  ArrayList<CutRow> rows = new ArrayList<>(detected);
  ArrayList<EditText[]> fields = new ArrayList<>();
  Map<String, List<EditText>> stocks = new HashMap<>();
  Runnable saveDraft = () -> {
   for (int i = 0; i < fields.size(); i++) rows.get(i).draft = fieldValues(fields.get(i));
   for (Map.Entry<String, List<EditText>> entry : stocks.entrySet()) {
    String value = entry.getValue().get(0).getText().toString().trim();
    stockDrafts.put(entry.getKey(), value);
    try {
     int length = Integer.parseInt(value);
     if (length > 0) getPreferences(MODE_PRIVATE).edit().putInt("stock_" + entry.getKey(), length).apply();
    } catch (NumberFormatException ignored) {}
   }
  };
  addPhotoButtons(() -> {
   if (savePhotoReview != null) savePhotoReview.run();
  });
  if (rows.isEmpty()) note("Henüz profil okunmadı. Fotoğraf ekleyebilir veya aşağıdan elle satır girebilirsiniz.");
  else note("Kesim ölçüsü ve adedi fotoğrafla karşılaştırın. Stok boyunu her profilin adının hemen altından değiştirebilirsiniz.");

  for (CutRow row : rows) {
   LinearLayout profile = planCard(body, 0xff121f38, 0xff244361, 0xff5685a4);
   profileHeading(profile, row.code + "\n" + row.name, row.icon);
   addProfileStockInput(profile, row.code, stocks);
   planLabel(profile, "Fotoğraf " + row.photo, 13, 0xffbdd7ee);
   String[] values = row.draft != null ? row.draft : new String[]{
    row.mm < 0 ? "" : String.valueOf(row.mm), row.qty < 0 ? "" : String.valueOf(row.qty)};
   EditText measure = input(profile, "KESİM ÖLÇÜSÜ (mm)", values[0]);
   EditText quantity = input(profile, "KESİM ADEDİ", values[1]);
   if (values[0].trim().isEmpty()) measure.setError("Kesim ölçüsü okunamadı");
   if (values[1].trim().isEmpty()) quantity.setError("Kesim adedi okunamadı");
   if (row.mm > 0 && row.mm < 300) planLabel(profile, row.mm + " mm okundu. Ölçüyü föyden doğrulayın.", 14, KERF_GOLD);
   if (row.mm == 0 && row.qty == 0) planLabel(profile, "0 adet, kesime katılmaz.", 14, REMAIN_GREEN);
   fields.add(new EditText[]{measure, quantity});
  }

  note("Eksik parçaları elle ekle");
  EditText extraName = input("Ek profil kodu / adı", extraDraft[0]);
  EditText extraMeasure = input("Ek kesim ölçüsü (mm)", extraDraft[1]);
  EditText extraQuantity = input("Ek kesim adedi", extraDraft[2]);
  EditText extraStock = input("Ek profil stok boyu (mm)", extraDraft[3]);
  EditText saw = input("TESTERE PAYI (mm / parça)", extraDraft[4]);
  EditText[] extraFields = {extraName, extraMeasure, extraQuantity, extraStock, saw};
  savePhotoReview = () -> {
   saveDraft.run();
   extraDraft = fieldValues(extraFields);
  };

  CheckBox confirmed = new CheckBox(this);
  confirmed.setText("Tüm fotoğraflardaki kesim ölçülerini ve adetleri kontrol ettim.");
  confirmed.setTextColor(0xffeffaff);
  confirmed.setButtonTintList(android.content.res.ColorStateList.valueOf(0xff5af4ca));
  addTextPanel(confirmed, 0xff15283b, 0xff244d5a, 0xff51cfc0);
  button("Tüm fotoğrafları birlikte optimize et", () -> {
   if (!confirmed.isChecked()) {
    Toast.makeText(this, "Önce ölçüleri ve adetleri kontrol edip onaylayın.", Toast.LENGTH_LONG).show();
    return;
   }
   savePhotoReview.run();
   try {
    int kerf = val(saw);
    if (kerf < 0) throw new IllegalArgumentException("Testere payı negatif olamaz.");
    TreeMap<String, ProfileCutPlan> plans = new TreeMap<>();
    for (CutRow row : rows) {
     String[] values = row.draft;
     String context = row.code + " · Fotoğraf " + row.photo;
     int quantity = cutNumber(values[1], context + " kesim adedi");
     if (quantity == 0) continue;
     int size = cutNumber(values[0], context + " kesim ölçüsü");
     int stock = cutNumber(stockDrafts.get(row.code), row.code + " stok boyu");
     validateCut(size, quantity, stock, kerf, context);
     ProfileCutPlan plan = plans.get(row.code);
     if (plan == null) {
      plan = new ProfileCutPlan(row.code + " " + row.name, stock, row.icon);
      plans.put(row.code, plan);
     }
     if (plan.icon == null) plan.icon = row.icon;
     for (int i = 0; i < quantity; i++) plan.pieces.add(size);
    }

    String manualName = extraDraft[0].trim();
    if (!manualName.isEmpty() || !extraDraft[1].trim().isEmpty() || !extraDraft[2].trim().isEmpty()) {
     if (manualName.isEmpty()) throw new IllegalArgumentException("Ek profilin kodunu veya adını yazın.");
     int quantity = cutNumber(extraDraft[2], "Ek kesim adedi");
     if (quantity != 0) {
      int size = cutNumber(extraDraft[1], "Ek kesim ölçüsü");
      int stock = cutNumber(extraDraft[3], "Ek profil stok boyu");
      validateCut(size, quantity, stock, kerf, manualName);
      ProfileCutPlan plan = plans.get(manualName.toUpperCase(Locale.ROOT));
      if (plan == null || plan.stock != stock) {
       plan = null;
       for (ProfileCutPlan candidate : plans.values()) {
        if (candidate.name.equalsIgnoreCase(manualName) && candidate.stock == stock) {
         plan = candidate;
         break;
        }
       }
      }
      if (plan == null) {
       plan = new ProfileCutPlan(manualName, stock, null);
       plans.put("~" + manualName, plan);
      }
      for (int i = 0; i < quantity; i++) plan.pieces.add(size);
     }
    }
    if (plans.isEmpty()) throw new IllegalArgumentException("En az bir geçerli kesim girin.");
    showPhotoOptimization(plans.values(), kerf);
   } catch (IllegalArgumentException e) {
    String message = e instanceof NumberFormatException ? "Testere payını kontrol edin." : e.getMessage();
    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
   }
  });
  if (!rows.isEmpty()) button("Yeni fotoğraf planı başlat", () -> new android.app.AlertDialog.Builder(this)
   .setMessage("Bu plandaki fotoğraflar ve düzenlenen kesim satırları temizlensin mi? Profil stok boyları saklanır.")
   .setNegativeButton("Vazgeç", null)
   .setPositiveButton("Yeni plan", (dialog, which) -> {
    savePhotoReview.run();
    detected.clear();
    stockDrafts.clear();
    photoNumber = 0;
    photosRead = 0;
    photoBatchWarning = "";
    extraDraft = new String[]{"", "", "", "6000", "3"};
    photo();
   }).show());
  button("Ana menü", () -> {
   savePhotoReview.run();
   home();
  });
 }

 void addProfileStockInput(LinearLayout card, String code, Map<String, List<EditText>> stocks) {
  int saved = getPreferences(MODE_PRIVATE).getInt("stock_" + code, DEFAULT_PROFILE_STOCK);
  String length = stockDrafts.get(code);
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
    if (value.equals(stockDrafts.put(code, value))) return;
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

 int cutNumber(String value, String label) {
  try {
   return Integer.parseInt(value.trim());
  } catch (NumberFormatException e) {
   throw new IllegalArgumentException(label + ": geçerli bir sayı girin.");
  }
 }

 void validateCut(int size, int quantity, int stock, int kerf, String label) {
  if (size <= 0 || quantity < 0 || quantity > 1000 || stock <= 0) {
   throw new IllegalArgumentException(label + ": ölçüyü, adedi ve stok boyunu kontrol edin.");
  }
  if ((long) size + kerf > stock) {
   throw new IllegalArgumentException(label + ": kesim ve testere payı " + stock + " mm stoğa sığmıyor.");
  }
 }

 void showPhotoOptimization(Collection<ProfileCutPlan> profiles, int kerf) {
  LinearLayout cards = new LinearLayout(this);
  cards.setOrientation(LinearLayout.VERTICAL);
  int allBars = 0, allPieces = 0;
  long remaining = 0, net = 0;
  int commonLength = profiles.iterator().next().stock;
  for (ProfileCutPlan plan : profiles) {
   if (commonLength != plan.stock) commonLength = 0;
   Collections.sort(plan.pieces, Collections.reverseOrder());
   ArrayList<ArrayList<Integer>> bins = new ArrayList<>();
   ArrayList<Integer> used = new ArrayList<>();
   for (int size : plan.pieces) {
    int bin = 0;
    while (bin < used.size() && (long) used.get(bin) + size + kerf > plan.stock) bin++;
    if (bin == bins.size()) {
     bins.add(new ArrayList<>());
     used.add(0);
    }
    bins.get(bin).add(size);
    used.set(bin, used.get(bin) + size + kerf);
   }
   LinearLayout group = planCard(cards, 0xff172b44, 0xff254662, 0xff597d9b);
   profileHeading(group, plan.name, plan.icon);
   planLabel(group, "Stok " + plan.stock + " mm  ·  " + plan.pieces.size() + " parça", 14, 0xffd2e8f4);
   allPieces += plan.pieces.size();
   for (int i = 0; i < bins.size(); i++) {
    int waste = plan.stock - used.get(i);
    remaining += waste;
    net += used.get(i) - (long) kerf * bins.get(i).size();
    planBar(group, i + 1, bins.get(i), plan.stock, kerf, waste, allBars < 12);
    allBars++;
   }
  }
  page("Kesim optimizasyon sonucu");
  planSummary(commonLength, kerf, allBars, allPieces, net, remaining);
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
  int[] spectrum = {0xfffa5f8b, 0xffa975ff, 0xff54cdfd, 0xff43f0ab, 0xffffca6d};
  GradientDrawable frame = new GradientDrawable(GradientDrawable.Orientation.TL_BR, spectrum);
  frame.setCornerRadius(dp(18));
  GradientDrawable surface = gradient(start, end, edge);
  GradientDrawable strip = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, spectrum);
  strip.setCornerRadius(dp(3));
  android.graphics.drawable.LayerDrawable layers = new android.graphics.drawable.LayerDrawable(
   new android.graphics.drawable.Drawable[]{frame, surface, strip});
  layers.setPaddingMode(android.graphics.drawable.LayerDrawable.PADDING_MODE_STACK);
  layers.setLayerInset(1, dp(2), dp(2), dp(2), dp(2));
  layers.setLayerInset(2, dp(14), dp(3), dp(14), 0);
  layers.setLayerHeight(2, dp(2));
  layers.setLayerGravity(2, Gravity.TOP | Gravity.FILL_HORIZONTAL);
  return layers;
 }

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
  view.setAlpha(0f);
  view.setTranslationY(dp(10));
  view.animate().alpha(1f).translationY(0f).setStartDelay(delay)
   .setDuration(320).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
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

 TextView planLabel(LinearLayout card, String value, int size, int color) {
  TextView label = text(value, size);
  label.setPadding(0, dp(3), 0, dp(3));
  label.setTextColor(color);
  card.addView(label);
  return label;
 }

 void planSummary(int length, int kerf, int bars, int count, long net, long remaining) {
  LinearLayout card = planCard(body, 0xff10233e, 0xff17485a, 0xff46dfdb);
  planLabel(card, bars + " stok profili  ·  " + count + " parça", 23, Color.WHITE)
   .setTypeface(null, Typeface.BOLD);
  String stockLabel = length > 0 ? "Stok " + length + " mm" : "Profil türüne göre farklı stok boyları";
  planLabel(card, stockLabel + "  ·  Testere payı " + kerf + " mm/parça", 14, 0xffd2e8f4);
  planLabel(card, "Kesim " + net + " mm", 16, 0xffff8c9d);
  planLabel(card, "Kalan " + remaining + " mm", 16, 0xff6af5c0);
  if (kerf > 0) planLabel(card, "Testere payı " + ((long) kerf * count) + " mm", 14, KERF_GOLD);
  reveal(card, 0);
 }

 void planBar(LinearLayout parent, int number, List<Integer> cuts, int length, int kerf,
              int remaining, boolean animated) {
  LinearLayout card = planCard(parent, 0xff121c33, 0xff203453, 0xff557b9b);
  planLabel(card, "Stok profil " + number + "  ·  " + length + " mm", 17, Color.WHITE)
   .setTypeface(null, Typeface.BOLD);
  StockBar bar = new StockBar(cuts, length, kerf);
  LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(27));
  params.setMargins(0, dp(9), 0, dp(9));
  card.addView(bar, params);
  StringBuilder sizes = new StringBuilder();
  for (int size : cuts) {
   if (sizes.length() > 0) sizes.append("  ·  ");
   sizes.append(size);
  }
  planLabel(card, "Kesilen (mm)  " + sizes, 14, 0xffff8c9d);
  planLabel(card, "Kalan  " + remaining + " mm", 15, 0xff6af5c0);
  if (animated) {
   reveal(card, Math.min(160, number * 35));
   bar.animateFill();
  }
 }

 final class StockBar extends android.view.View {
  private final List<Integer> cuts;
  private final int length;
  private final int kerf;
  private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
  private final android.graphics.RectF bounds = new android.graphics.RectF();
  private final android.graphics.Path outline = new android.graphics.Path();
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
   fill = 0f;
   animator = ValueAnimator.ofFloat(0f, 1f);
   animator.setDuration(780);
   animator.setInterpolator(new android.view.animation.DecelerateInterpolator());
   animator.addUpdateListener(value -> {
    fill = (float) value.getAnimatedValue();
    invalidate();
   });
   animator.start();
  }

  @Override protected void onDetachedFromWindow() {
   if (animator != null) animator.cancel();
   super.onDetachedFromWindow();
  }

  @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
   super.onSizeChanged(width, height, oldWidth, oldHeight);
   bounds.set(0, 0, width, height);
   outline.reset();
   outline.addRoundRect(bounds, dp(9), dp(9), android.graphics.Path.Direction.CW);
  }

  @Override protected void onDraw(android.graphics.Canvas canvas) {
   super.onDraw(canvas);
   canvas.save();
   canvas.clipPath(outline);
   canvas.drawColor(0xff354765);
   canvas.clipRect(0, 0, getWidth() * fill, getHeight());
   float scale = getWidth() / (float) length;
   long offset = 0;
   for (int cut : cuts) {
    paint.setColor(CUT_RED);
    canvas.drawRect(offset * scale, 0, (offset + cut) * scale, getHeight(), paint);
    offset += cut;
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
   paint.setColor(0x40ffffff);
   canvas.drawRect(0, 0, getWidth(), dp(3), paint);
   canvas.restore();
  }
 }

 void opt(){
  page("Profil kesim optimizasyonu");
  button("Fotoğraflardan kesim planı oluştur", () -> photo());
  note("Kesimler: Her satır 1250x3 biçiminde. Testere payı parça başına eklenir. Azalan ilk uygun yerleştirme yöntemi kullanılır.");
  EditText stock=input(
   "HAM PROFİL BOYU (mm) – örnek 6000","6000");
  EditText kerf=input(
   "TESTERE PAYI (mm / parça) – örnek 3","3");
  EditText cuts=input(
   "KESİM ÖLÇÜSÜ x ADET – örnek 1250x3",
   "1250x3\n850x4");
  cuts.setSingleLine(false);
  cuts.setMinLines(4);
  cuts.getLayoutParams().height=dp(140);
  button("Kesim planı oluştur",()->{
   try{
    int length=val(stock),saw=val(kerf);
    if(length<=0||saw<0)throw new Exception();
    ArrayList<Integer> pieces=new ArrayList<>();
    for(String line:cuts.getText().toString()
     .split("[\\n,;]+")){
     String[] a=line.trim()
      .toLowerCase(Locale.ROOT)
      .replace(" ","").split("[x×*]");
     if(a[0].isEmpty())continue;
     int size=Integer.parseInt(a[0]);
     int qty=a.length>1?Integer.parseInt(a[1]):1;
     if(size<=0||qty<1||qty>1000||
      (long)size+saw>length||
      pieces.size()+qty>3000)
      throw new Exception();
     for(int i=0;i<qty;i++)pieces.add(size);
    }
    if(pieces.isEmpty())throw new Exception();
    Collections.sort(pieces,Collections.reverseOrder());
    ArrayList<ArrayList<Integer>> bins=new ArrayList<>();
    ArrayList<Integer> used=new ArrayList<>();
    long total=0;
    for(int size:pieces){
     total+=size;int target=-1;
     for(int i=0;i<used.size();i++)
      if((long)used.get(i)+size+saw<=length){
       target=i;break;
      }
     if(target<0){
      target=bins.size();
      bins.add(new ArrayList<Integer>());
      used.add(0);
     }
     bins.get(target).add(size);
     used.set(target,used.get(target)+size+saw);
    }
    page("Kesim optimizasyon sonucu");
    long remaining = (long) bins.size() * length - total - (long) pieces.size() * saw;
    planSummary(length, saw, bins.size(), pieces.size(), total, remaining);
    for (int i = 0; i < bins.size(); i++) {
     planBar(body, i + 1, bins.get(i), length, saw, length - used.get(i), i < 12);
    }
    button("Yeni kesim planı", () -> opt());
    button("Ana menü", () -> home());
   }catch(Exception ex){bad();}
  });
  button("Ana menü",()->home());
 }
 void glass(){
  page("Cam alan hesabı");
  EditText w=input("CAM ENİ (mm)","870");
  EditText h=input("CAM YÜKSEKLİĞİ (mm)","1265");
  EditText n=input("CAM ADEDİ","1");
  button("Hesapla",()->{
   try{
    int a=val(w),b=val(h),c=val(n);
    if(a<=0||b<=0||c<=0)throw new Exception();
    result(String.format(Locale.US,
     "Tek cam: %.3f m²\nToplam: %.3f m²",
     a*b/1000000.0,a*b*c/1000000.0));
   }catch(Exception e){bad();}
  });
  button("Ana menü",()->home());
 }
 void panels(){
  page("Kanat bölme hesabı");
  note("Sistem payları hariç teorik eşit bölme.");
  EditText w=input("AÇIKLIK ENİ (mm)","4100");
  EditText n=input("KANAT ADEDİ","6");
  EditText ded=input("TOPLAM DÜŞÜM (mm)","0");
  button("Hesapla",()->{
   try{
    int a=val(w),b=val(n),c=val(ded);
    if(a<=0||b<=0||c<0||c>=a)
     throw new Exception();
    result(String.format(Locale.US,
     "Teorik kanat eni: %.1f mm",
     (a-c)/(double)b));
   }catch(Exception e){bad();}
  });
  button("Ana menü",()->home());
 }
 void system(String s){
  page(s);
  note("Ön ölçülendirme. Gerçek profil/cam kesim payları için üretici kataloğu gereklidir.");
  EditText w=input("AÇIKLIK ENİ (mm)","3000");
  EditText h=input("AÇIKLIK YÜKSEKLİĞİ (mm)","2200");
  EditText n=input("KANAT / BÖLÜM ADEDİ","4");
  button("Ön hesapla",()->{
   try{
    int a=val(w),b=val(h),c=val(n);
    if(a<=0||b<=0||c<=0)
     throw new Exception();
    result(String.format(Locale.US,
     "Sistem: %s\nAlan: %.2f m²\n"+
     "Teorik bölüm eni: %.1f mm\n\n"+
     "Bunlar nihai imalat kesim ölçüsü değildir.",
     s,a*b/1000000.0,a/(double)c));
   }catch(Exception e){bad();}
  });
  button("Ana menü",()->home());
 }
}
