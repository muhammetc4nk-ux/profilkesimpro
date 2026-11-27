package com.profilkesim.pro;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

final class TemaWorkshop {
 private static final int DRAWING_FILE = 46, DRAWING_CAMERA = 47;
 private static final int DECIMAL = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL;
 private static final int SIGNED = DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED;
 private final MainActivity app;
 private final Map<String, String> draft = new LinkedHashMap<>();
 private final Map<String, EditText> fields = new LinkedHashMap<>();
 private final int[] profileIndices = new int[5];
 private final TemaCatalog.Series series;
 private final Set<Integer> chosenSashes = new LinkedHashSet<>();
 private LinearLayout recordProfile, sashProfile, fixedBeadProfile, sashBeadProfile, advancedPanel;
 private Button bayButton, fixedCountButton;
 private TextView photoBayCount;
 private CheckBox adapter;
 private int beadCorner, expectedFixed = -1;
 private boolean drawingReading;
 private int corner, sashCorner, continuous, photoRequest;
 private Uri cameraUri;
 private Uri drawingUri;
 private Bitmap drawing;
 private DrawingDimensions recognized = new DrawingDimensions(-1, -1);
 private CheckBox confirmed;
 private LinearLayout photoCard, form;

 TemaWorkshop(MainActivity app, TemaCatalog.Series series) {
  this.app = app;
  this.series = series;
  TemaCatalog.Profile[][] groups = {series.frames, series.mullions, series.sashes, series.beads, series.beads};
  for (int i = 0; i < groups.length; i++) {
   String saved = app.getPreferences(Activity.MODE_PRIVATE).getString(preference(i), "");
   for (int j = 0; j < groups[i].length; j++) if (groups[i][j].code.equals(saved)) profileIndices[i] = j;
  }
 }

 private int workingFace(TemaCatalog.Profile profile) {
  if (series == TemaCatalog.Series.ALDOKS) {
   if ("T.3004".equals(profile.code)) return 420;
   if ("T.3002".equals(profile.code)) return 600;
  }
  return profile.face;
 }

 private String preference(int role) { return "tema.profile." + series.name() + "." + role; }

 void open() {
  app.page("Tema " + series.label + " · İmalat hesabı");
  form = app.body;
  fields.clear();
  confirmed = null;
  LinearLayout dimensions = section("Dış kasa ölçüleri");
  LinearLayout sizeRow = horizontal(dimensions);
  shortField(column(sizeRow), "width", "Genişlik (mm)", "", DECIMAL);
  shortField(column(sizeRow), "height", "Yükseklik (mm)", "", DECIMAL);
  chooseButton(dimensions, "KENDİN ÇİZ", this::drawEditor);
  shortField(dimensions, "qty", "Doğrama adedi", "1", InputType.TYPE_CLASS_NUMBER);
  photoCard = section("Çizimden ölçü al");
  showPhotoCard();

  LinearLayout divisions = section("Bölmeler ve orta kayıt");
  LinearLayout counts = horizontal(divisions);
  EditText vertical = shortField(column(counts), "vertical", "Dikey kayıt", "0", InputType.TYPE_CLASS_NUMBER);
  EditText horizontal = shortField(column(counts), "horizontal", "Yatay kayıt", "0", InputType.TYPE_CLASS_NUMBER);
  LinearLayout bayControls = horizontal(divisions);
  fixedCountButton = chooseButton(column(bayControls), "Sabit adedi", this::chooseFixedCount);
  bayButton = chooseButton(column(bayControls), "Çizim / bölme düzeni", this::chooseBays);
  photoBayCount = app.planLabel(divisions, "", 13, 0xffffce83);
  for (EditText input : new EditText[]{vertical, horizontal}) input.addTextChangedListener(watcher(() -> {
   chosenSashes.clear();
   expectedFixed = -1;
   updateLayout();
  }));

  LinearLayout profiles = section("Katalogdan profil seç");
  profile(profiles, 0, "Kasa", series.frames);
  recordProfile = profile(profiles, 1, "Orta kayıt", series.mullions);
  sashProfile = profile(profiles, 2, "Kanat", series.sashes);
  fixedBeadProfile = profile(profiles, 3, "Sabit cam çıtası", series.beads);
  sashBeadProfile = profile(profiles, 4, "Kanat cam çıtası", series.beads);
  chooseButton(profiles, "Tema katalog PDF’ini aç", () -> openUrl(series.url));
  chooseButton(profiles, "Ölçülendirme ve fiş payları", this::dimensionDialog);

  LinearLayout material = section("Hesapla");
  app.planLabel(material, "Gönye boyları uzun uçtan ölçülür.", 13, 0xffc9dbe3);
  Button details = chooseButton(material, "Montaj ve stok ayarları", null);
  advancedPanel = new LinearLayout(app);
  advancedPanel.setOrientation(LinearLayout.VERTICAL);
  material.addView(advancedPanel);
  advancedPanel.setVisibility(View.GONE);
  details.setOnClickListener(v -> toggleAdvanced());
  buildAdvanced();
  confirmed = new CheckBox(app);
  confirmed.setText("Ölçüleri ve seçilen montajı kontrol ettim.");
  confirmed.setTextColor(0xffeefbff);
  confirmed.setButtonTintList(android.content.res.ColorStateList.valueOf(series.accent));
  material.addView(confirmed, new LinearLayout.LayoutParams(-1, -2));
  app.savePhotoReview = this::remember;
  app.button("İmalat kesimlerini hesapla", this::calculate);
  app.button("Ana menü", () -> {
   remember();
   app.home();
  });
  updateLayout();
 }

 private void buildAdvanced() {
  chooseButton(advancedPanel, "Montaj kesiti kataloğunu aç", () -> openUrl(series == TemaCatalog.Series.ALDOKS ? TemaCatalog.DETAILS : series.url));
  app.planLabel(advancedPanel, "Standart kesim: kasa, kanat ve çıta 45° uzun uç. Orta kayıt 90° düz temas.", 13, 0xffc9dbe3);
  app.planLabel(advancedPanel, "Çıta boyu oturma hattından alınır. Farklı montajda ölçülen kenar farkını girin.", 13, 0xffc9dbe3);
  LinearLayout profileOverrides = new LinearLayout(app);
  profileOverrides.setOrientation(LinearLayout.VERTICAL);
  profileOverrides.setVisibility(View.GONE);
  chooseButton(advancedPanel, "Özel profil kodu ve yüz ölçüsü", () -> profileOverrides.setVisibility(
   profileOverrides.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
  advancedPanel.addView(profileOverrides);
  TemaCatalog.Profile[][] groups = {series.frames, series.mullions, series.sashes};
  String[] roleNames = {"Kasa", "Orta kayıt", "Kanat"};
  for (int i = 0; i < groups.length; i++) {
   TemaCatalog.Profile selected = groups[i][profileIndices[i]];
   field(profileOverrides, "code" + i, roleNames[i] + " profil kodu", selected.code, InputType.TYPE_CLASS_TEXT);
   field(profileOverrides, "face" + i, roleNames[i] + " oturma yüzü (mm)", mm(workingFace(selected)), DECIMAL);
  }
  String[] corners = {"45° gönye · uzun uç", "90° düz · dikeyler tam boy", "90° düz · yataylar tam boy"};
  choiceControl(advancedPanel, "Kasa köşesi", corners, corner, i -> corner = i);
  choiceControl(advancedPanel, "Kanat köşesi", corners, sashCorner, i -> sashCorner = i);
  app.planLabel(advancedPanel, "Cam çıtası: 90° düz kesim (dikey tam boy)", 14, Color.WHITE);
  field(advancedPanel, "beadEdge", "Çıta oturma farkı (mm/kenar)", "0", SIGNED);
  String[] directions = {"Dikey kayıtlar sürekli", "Yatay kayıtlar sürekli"};
  choiceControl(advancedPanel, "Kayıt birleşimi", directions, continuous, i -> continuous = i);
  field(advancedPanel, "joint", "Kasa bağlantı farkı (mm/uç; eksi düşer)", "0", SIGNED);
   field(advancedPanel, "mullionJoint", "Orta kayda bağlantı farkı (mm/uç; eksi düşer)", "", SIGNED);
   field(advancedPanel, "leftCells", "Sol menteşeli kanat bölmeleri (; ile ayır)", "", InputType.TYPE_CLASS_TEXT);
   field(advancedPanel, "emptyCells", "Boş kasa bölmeleri (; ile ayır)", "", InputType.TYPE_CLASS_TEXT);
  field(advancedPanel, "columns", "Özel net sütun enleri (mm; mm)", "", InputType.TYPE_CLASS_TEXT);
  field(advancedPanel, "rows", "Özel net sıra yükseklikleri (mm; mm)", "", InputType.TYPE_CLASS_TEXT);
  if (series == TemaCatalog.Series.C60) {
   adapter = new CheckBox(app);
   adapter.setText("T.3101 ve T.3105 ile T.3107 katalog montajı");
   adapter.setTextColor(0xffeefbff);
   adapter.setButtonTintList(android.content.res.ColorStateList.valueOf(series.accent));
   adapter.setChecked(!"0".equals(draft.get("adapter")));
   adapter.setOnCheckedChangeListener((box, checked) -> invalidate());
   advancedPanel.addView(adapter);
  }
  String sashHint = series == TemaCatalog.Series.C60 ? "Kanat boşluğu (mm/kenar; T.3107 detayında 5)" : "Kanat boşluğu (mm/kenar)";
  field(advancedPanel, "sashGap", sashHint, "", SIGNED);
  app.planLabel(advancedPanel, "Cam siparişi için kullanılan tarafların net cam paylarını girin. Boş bırakılan payda föy cam açıklığını gösterir.", 13, 0xffc9dbe3);
  field(advancedPanel, "frameGlass", "Sabit cam kasa payı (mm/kenar)", "", SIGNED);
  field(advancedPanel, "mullionGlass", "Sabit cam kayıt payı (mm/kenar)", "", SIGNED);
  field(advancedPanel, "sashGlass", "Kanat camı payı (mm/kenar)", "", SIGNED);
  EditText extra = field(advancedPanel, "extraCuts", "Ek katalog kesimleri · kod;ölçü;adet;açı", "", InputType.TYPE_CLASS_TEXT);
  extra.setSingleLine(false);
  extra.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
  extra.getLayoutParams().height = app.dp(82);
  field(advancedPanel, "stock", "Stok boyları (mm; mm)", "6000;7000", InputType.TYPE_CLASS_TEXT);
  field(advancedPanel, "kerf", "Testere payı (mm/ara)", String.valueOf(MainActivity.DEFAULT_KERF), DECIMAL);
 }

 private void toggleAdvanced() {
  advancedPanel.setVisibility(advancedPanel.isShown() ? View.GONE : View.VISIBLE);
  if (advancedPanel.isShown()) app.reveal(advancedPanel, 0);
 }

 private void choiceControl(LinearLayout parent, String name, String[] choices, int initial, Option selected) {
  Button button = chooseButton(parent, name + " · " + choices[initial], null);
  int[] current = {initial};
  button.setOnClickListener(v -> choose(name, choices, current[0], index -> {
   current[0] = index;
   selected.selected(index);
   button.setText(name + " · " + choices[index]);
   invalidate();
  }));
 }

 private LinearLayout horizontal(LinearLayout parent) {
  LinearLayout row = new LinearLayout(app);
  parent.addView(row, new LinearLayout.LayoutParams(-1, -2));
  return row;
 }

 private LinearLayout column(LinearLayout row) {
  LinearLayout column = new LinearLayout(app);
  column.setOrientation(LinearLayout.VERTICAL);
  LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(0, -2, 1);
  size.setMargins(app.dp(3), 0, app.dp(3), 0);
  row.addView(column, size);
  return column;
 }

 private EditText shortField(LinearLayout parent, String key, String title, String fallback, int inputType) {
  app.planLabel(parent, title, 13, 0xffcfe5ec);
  EditText input = new EditText(app);
  input.setSingleLine(true);
  input.setInputType(inputType);
  input.setText(draft.containsKey(key) ? draft.get(key) : fallback);
  input.setContentDescription(title);
  input.setTextColor(Color.WHITE);
  input.setTextSize(19);
  input.setPadding(app.dp(10), app.dp(9), app.dp(10), app.dp(9));
  input.setBackground(app.rgbPanel(0xff152638, 0xff224449, series.accent));
  parent.addView(input, new LinearLayout.LayoutParams(-1, app.dp(48)));
  input.addTextChangedListener(watcher(this::invalidate));
  fields.put(key, input);
  return input;
 }

 private static TextWatcher watcher(Runnable action) {
  return new TextWatcher() {
   @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
   @Override public void onTextChanged(CharSequence s, int start, int before, int count) { action.run(); }
   @Override public void afterTextChanged(Editable s) {}
  };
 }

 private int recordCount(String key) {
  try {
   int count = Integer.parseInt(fields.get(key).getText().toString().trim());
   return count >= 0 && count <= 12 ? count : -1;
  } catch (NumberFormatException error) {
   return -1;
  }
 }

 private void updateLayout() {
  if (bayButton == null || fixedBeadProfile == null) return;
  int columns = recordCount("vertical") + 1, rows = recordCount("horizontal") + 1;
  int cells = columns * rows;
  int open = chosenSashes.size();
  fixedCountButton.setText(cells == 0 ? "Sabit adedi" : (cells - open) + " sabit · " + open + " açılır");
  fixedCountButton.setContentDescription("Sabit adedini değiştir · " + fixedCountButton.getText());
  fixedCountButton.setEnabled(cells > 0);
  photoBayCount.setText(expectedFixed < 0 ? "" : "Çizimde " + expectedFixed + " sabit bölme yazıyor.");
  photoBayCount.setVisibility(expectedFixed < 0 ? View.GONE : View.VISIBLE);
  bayButton.setEnabled(cells > 0);
  recordProfile.setVisibility(cells > 1 ? View.VISIBLE : View.GONE);
  sashProfile.setVisibility(View.VISIBLE);
  sashBeadProfile.setVisibility(open > 0 ? View.VISIBLE : View.GONE);
  fixedBeadProfile.setVisibility(cells > open ? View.VISIBLE : View.GONE);
 }

 private void chooseFixedCount() {
  int total = (recordCount("vertical") + 1) * (recordCount("horizontal") + 1);
  if (total <= 0) return;
  LinearLayout content = new LinearLayout(app);
  content.setOrientation(LinearLayout.VERTICAL);
  content.setPadding(app.dp(22), app.dp(4), app.dp(22), app.dp(8));
  app.planLabel(content, "0–" + total + " arası. Açılır bölmeler sağdan başlar. Konumları Bölme düzeni’nden değiştirebilirsiniz.", 14, Color.DKGRAY);
  EditText count = new EditText(app);
  count.setSingleLine(true);
  count.setInputType(InputType.TYPE_CLASS_NUMBER);
  count.setContentDescription("Sabit bölme sayısı");
  count.setText(String.valueOf(total - chosenSashes.size()));
  count.setSelectAllOnFocus(true);
  content.addView(count);
  AlertDialog dialog = new AlertDialog.Builder(app).setTitle("Kaç sabit bölme?").setView(content)
   .setNegativeButton("Vazgeç", null).setPositiveButton("Uygula", null).create();
  dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
   try {
    int fixed = MainActivity.cutNumber(count.getText().toString(), "Sabit adedi");
    if (fixed < 0 || fixed > total) throw new IllegalArgumentException("Sabit adedi 0–" + total + " arasında olmalı.");
    if (fixed != total - chosenSashes.size()) {
     chosenSashes.clear();
     for (int cell = fixed + 1; cell <= total; cell++) chosenSashes.add(cell);
    }
    expectedFixed = -1;
    updateLayout();
    invalidate();
    dialog.dismiss();
   } catch (IllegalArgumentException error) {
    count.setError(error.getMessage());
   }
  }));
  dialog.show();
 }

 private void drawEditor() { drawEditor(null); }
 private void drawEditor(DrawingDimensions imported) {
  ScrollView scroll = new ScrollView(app);
  LinearLayout panel = new LinearLayout(app);
  panel.setOrientation(LinearLayout.VERTICAL);
  panel.setPadding(app.dp(12), app.dp(8), app.dp(12), app.dp(8));
  scroll.addView(panel);
  EditText[] sizes = new EditText[2];
  String[] sizeKeys = {"width", "height"};
  for (int i=0;i<2;i++) {
   app.planLabel(panel, i==0?"Dış genişlik (mm)":"Dış yükseklik (mm)",14,Color.DKGRAY);
   sizes[i]=new EditText(app); sizes[i].setInputType(DECIMAL);
   sizes[i].setText(fields.get(sizeKeys[i]).getText()); panel.addView(sizes[i]);
  }
  TemaCatalog.Profile[][] groups = {series.frames,series.mullions,series.sashes};
  int[] picks = profileIndices.clone();
  Runnable[] refreshDrawing={()->{}};
  Runnable[] beforeProfileChange={()->{}};
  Button[] profileButtons=new Button[3];
  String[] names = {"Kasa","Orta kayıt","Kanat"};
  for(int i=0;i<3;i++){
   final int role=i;
   Button b=new Button(app); b.setAllCaps(false); b.setText(names[i]+" · "+groups[i][picks[i]].label()); panel.addView(b);profileButtons[i]=b;
   b.setOnClickListener(v->chooseProfile(names[role],groups[role],picks[role],index->{beforeProfileChange[0].run();picks[role]=index;b.setText(names[role]+" · "+groups[role][index].label());refreshDrawing[0].run();}));
  }
  java.util.TreeSet<Integer> xs=new java.util.TreeSet<>(), ys=new java.util.TreeSet<>();
  java.util.Set<Integer> openings=new java.util.HashSet<>();
  java.util.Set<Integer> leftCells=new java.util.HashSet<>(), emptyCells=new java.util.HashSet<>();
  java.util.Map<String,Integer> lineProfiles=new java.util.HashMap<>();
  java.util.ArrayDeque<Runnable> undo=new java.util.ArrayDeque<>();
  java.util.Map<Integer,Integer> gaps=new java.util.HashMap<>();
  for(String entry:value("individualSashGaps").split(";")){try{String[] pair=entry.split(":");gaps.put(Integer.parseInt(pair[0]),Integer.parseInt(pair[1]));}catch(RuntimeException ignored){}}
  java.util.ArrayList<int[]> localLines=new java.util.ArrayList<>();
  java.util.function.Supplier<java.util.ArrayList<int[]>> bays=()->{
   java.util.ArrayList<int[]> cells=new java.util.ArrayList<>();
   java.util.ArrayList<Integer> ax=new java.util.ArrayList<>(xs),ay=new java.util.ArrayList<>(ys);
   ax.add(0,0);ax.add(1000000);ay.add(0,0);ay.add(1000000);
   for(int j=0;j<ay.size()-1;j++)for(int i=0;i<ax.size()-1;i++)cells.add(new int[]{ax.get(i),ay.get(j),ax.get(i+1),ay.get(j+1)});
   for(int[] q:localLines)for(int i=0;i<cells.size();i++){int[] z=cells.get(i);
    if(q[0]==q[2]&&q[0]>z[0]&&q[0]<z[2]&&q[1]<=z[1]&&q[3]>=z[3]){cells.set(i,new int[]{z[0],z[1],q[0],z[3]});cells.add(new int[]{q[0],z[1],z[2],z[3]});}
    else if(q[1]==q[3]&&q[1]>z[1]&&q[1]<z[3]&&q[0]<=z[0]&&q[2]>=z[2]){cells.set(i,new int[]{z[0],z[1],z[2],q[1]});cells.add(new int[]{z[0],q[1],z[2],z[3]});}
   }
   return cells;
  };
  java.util.function.BiFunction<Integer,Integer,Integer> hit=(x,y)->{java.util.ArrayList<int[]> cells=bays.get();for(int i=0;i<cells.size();i++){int[] z=cells.get(i);if(x>=z[0]&&x<=z[2]&&y>=z[1]&&y<=z[3])return i+1;}return 0;};
  int[] mode={9};
  boolean[] frameAdded={false};
  try {
   org.json.JSONObject saved=new org.json.JSONObject(value("editorDrawing"));
   frameAdded[0]=saved.optBoolean("frame");
    int scale=saved.optInt("coordinateScale",1000)==1000?1000:1;
   for(String key:new String[]{"xs","ys","openings","left","empty"}){
    org.json.JSONArray a=saved.getJSONArray(key);
    java.util.Set<Integer> target=key.equals("xs")?xs:key.equals("ys")?ys:key.equals("openings")?openings:key.equals("left")?leftCells:emptyCells;
    for(int i=0;i<a.length();i++)target.add(a.getInt(i)*(key.equals("xs")||key.equals("ys")?scale:1));
   }
   org.json.JSONArray lines=saved.getJSONArray("lines");
   for(int i=0;i<lines.length();i++){org.json.JSONArray a=lines.getJSONArray(i);int[] q=new int[5];for(int j=0;j<5;j++)q[j]=a.getInt(j)*(j<4?scale:1);localLines.add(q);}
   org.json.JSONObject p=saved.getJSONObject("profiles");java.util.Iterator<String> keys=p.keys();while(keys.hasNext()){String k=keys.next();lineProfiles.put(scale!=1&&(k.startsWith("v")||k.startsWith("h"))?k.substring(0,1)+(Integer.parseInt(k.substring(1))*scale):k,p.getInt(k));}
   org.json.JSONObject g=saved.getJSONObject("gaps");keys=g.keys();while(keys.hasNext()){String k=keys.next();gaps.put(Integer.parseInt(k),g.getInt(k));}
   org.json.JSONArray a=saved.getJSONArray("picks");for(int i=0;i<3;i++)picks[i]=a.getInt(i);
   for(int i=0;i<2;i++)sizes[i].setText(saved.getString(sizeKeys[i]));
  }catch(org.json.JSONException | RuntimeException ignored){}

  if(imported!=null){
   frameAdded[0]=true;xs.clear();ys.clear();localLines.clear();openings.clear();leftCells.clear();emptyCells.clear();lineProfiles.clear();gaps.clear();
   if(imported.width>0)sizes[0].setText(mm(imported.width));
   if(imported.height>0)sizes[1].setText(mm(imported.height));
   int nx=Math.max(0,Math.min(12,imported.vertical)),ny=Math.max(0,Math.min(12,imported.horizontal));
   for(int i=1;i<=nx;i++)xs.add(Math.round(1000000f*i/(nx+1)));
   for(int i=1;i<=ny;i++)ys.add(Math.round(1000000f*i/(ny+1)));
  }
  java.util.ArrayDeque<Runnable> redo=new java.util.ArrayDeque<>();
  java.util.function.Supplier<Runnable> snapshot=()->{
   java.util.TreeSet<Integer> oldX=new java.util.TreeSet<>(xs),oldY=new java.util.TreeSet<>(ys);
   java.util.Set<Integer> oldO=new java.util.HashSet<>(openings),oldL=new java.util.HashSet<>(leftCells),oldE=new java.util.HashSet<>(emptyCells);
   java.util.Map<String,Integer> oldP=new java.util.HashMap<>(lineProfiles);
   java.util.Map<Integer,Integer> oldGaps=new java.util.HashMap<>(gaps);
   java.util.ArrayList<int[]> oldLines=new java.util.ArrayList<>();for(int[] q:localLines)oldLines.add(q.clone());
   boolean oldFrame=frameAdded[0];int[] oldPicks=picks.clone();
   String[] oldSizes={sizes[0].getText().toString(),sizes[1].getText().toString()};
   return ()->{xs.clear();xs.addAll(oldX);ys.clear();ys.addAll(oldY);openings.clear();openings.addAll(oldO);leftCells.clear();leftCells.addAll(oldL);emptyCells.clear();emptyCells.addAll(oldE);lineProfiles.clear();lineProfiles.putAll(oldP);gaps.clear();gaps.putAll(oldGaps);localLines.clear();localLines.addAll(oldLines);frameAdded[0]=oldFrame;System.arraycopy(oldPicks,0,picks,0,picks.length);for(int i=0;i<3;i++)profileButtons[i].setText(names[i]+" · "+groups[i][picks[i]].label());for(int i=0;i<2;i++)sizes[i].setText(oldSizes[i]);};
  };
  Runnable checkpoint=()->{undo.push(snapshot.get());redo.clear();};
  beforeProfileChange[0]=checkpoint;
  TextView drawingStatus=new TextView(app);drawingStatus.setText(imported!=null?"Fotoğraf taslağı: kayıt yerlerini, kanatları ve ölçüleri doğrulayın.":frameAdded[0]?"Araç seçin ve çizime dokunun.":"Önce kasa ekleyin.");panel.addView(drawingStatus);
  EditText jointEdit=new EditText(app); jointEdit.setInputType(SIGNED); jointEdit.setText(fields.get("joint").getText());
  app.planLabel(panel,"Kasa bağlantı payı (mm/uç)",14,Color.DKGRAY);panel.addView(jointEdit);
  EditText recordJoint=new EditText(app);recordJoint.setInputType(SIGNED);recordJoint.setText(fields.get("mullionJoint").getText());panel.addView(recordJoint);recordJoint.setHint("Orta kayda bağlantı payı (mm/uç)");
  EditText sashGapEdit=new EditText(app);sashGapEdit.setInputType(SIGNED);sashGapEdit.setText(fields.get("sashGap").getText());
  app.planLabel(panel,"Kanat kasa boşluğu (mm/kenar)",14,Color.DKGRAY);panel.addView(sashGapEdit);
  Runnable persistDrawing=()->{
   try{
    org.json.JSONObject o=new org.json.JSONObject();
    o.put("coordinateScale",1000000);o.put("frame",frameAdded[0]);o.put("xs",new org.json.JSONArray(xs));o.put("ys",new org.json.JSONArray(ys));o.put("openings",new org.json.JSONArray(openings));o.put("left",new org.json.JSONArray(leftCells));o.put("empty",new org.json.JSONArray(emptyCells));
    org.json.JSONArray lines=new org.json.JSONArray();for(int[] q:localLines){org.json.JSONArray a=new org.json.JSONArray();for(int n:q)a.put(n);lines.put(a);}o.put("lines",lines);
    o.put("profiles",new org.json.JSONObject(lineProfiles));org.json.JSONObject g=new org.json.JSONObject();for(int k:gaps.keySet())g.put(String.valueOf(k),gaps.get(k));o.put("gaps",g);
    org.json.JSONArray p=new org.json.JSONArray();for(int n:picks)p.put(n);o.put("picks",p);
    for(int i=0;i<2;i++)o.put(sizeKeys[i],sizes[i].getText().toString());
    o.put("joint",jointEdit.getText().toString());o.put("recordJoint",recordJoint.getText().toString());o.put("sashGap",sashGapEdit.getText().toString());
    draft.put("editorDrawing",o.toString());
   }catch(org.json.JSONException ex){toast("Çizim kaydedilemedi.");}
  };
  try{org.json.JSONObject o=new org.json.JSONObject(value("editorDrawing"));jointEdit.setText(o.optString("joint",jointEdit.getText().toString()));recordJoint.setText(o.optString("recordJoint",recordJoint.getText().toString()));sashGapEdit.setText(o.optString("sashGap",sashGapEdit.getText().toString()));}catch(org.json.JSONException ignored){}
  java.util.function.Supplier<java.util.ArrayList<int[]>> records=()->{
   java.util.ArrayList<int[]> all=new java.util.ArrayList<>(localLines);
   for(int x:xs)all.add(new int[]{x,0,x,1000000,lineProfiles.getOrDefault("v"+x,picks[1])});
   for(int y:ys)all.add(new int[]{0,y,1000000,y,lineProfiles.getOrDefault("h"+y,picks[1])});
   return all;
  };
  java.util.function.Function<int[],int[]> net=z->{
   int f=workingFace(groups[0][picks[0]]);
   int iw=ShopDimensions.tenths(sizes[0].getText().toString(),"En")-2*f,ih=ShopDimensions.tenths(sizes[1].getText().toString(),"Boy")-2*f;
   int lf=0,rf=0,tf=0,bf=0;
   for(int[] q:records.get()){int face=workingFace(groups[1][q[4]]);
    if(q[0]==q[2]&&q[1]<=z[1]&&q[3]>=z[3]){if(q[0]==z[0])lf=Math.max(lf,face);if(q[0]==z[2])rf=Math.max(rf,face);}
    if(q[1]==q[3]&&q[0]<=z[0]&&q[2]>=z[2]){if(q[1]==z[1])tf=Math.max(tf,face);if(q[1]==z[3])bf=Math.max(bf,face);}
   }
   return new int[]{(int)Math.round((double)iw*z[2]/1000000.0)-(int)Math.round((double)iw*z[0]/1000000.0)-(lf-lf/2)-rf/2,(int)Math.round((double)ih*z[3]/1000000.0)-(int)Math.round((double)ih*z[1]/1000000.0)-(tf-tf/2)-bf/2,iw,ih};
  };
  android.view.View board=new android.view.View(app){
   android.graphics.Paint paint=new android.graphics.Paint(3);
   float left=24,top=24,right,bottom;
   final android.graphics.Paint dim=new android.graphics.Paint(3);
   private void measure(android.graphics.Canvas c,float x,float y,float X,float Y,String label){
    dim.setColor(0xffc5eaff);dim.setStrokeWidth(app.dp(1));dim.setStyle(android.graphics.Paint.Style.STROKE);c.drawLine(x,y,X,Y,dim);
    float dx=X-x,dy=Y-y,len=(float)Math.hypot(dx,dy);if(len<1)return;dx/=len;dy/=len;float k=app.dp(5);
    for(int i=0;i<2;i++){float px=i==0?x:X,py=i==0?y:Y,sign=i==0?1:-1;c.drawLine(px-dy*k,py+dx*k,px+dy*k,py-dx*k,dim);c.drawLine(px,py,px+sign*dx*k-dy*k/2,py+sign*dy*k+dx*k/2,dim);c.drawLine(px,py,px+sign*dx*k+dy*k/2,py+sign*dy*k-dx*k/2,dim);}
    c.save();c.translate((x+X)/2,(y+Y)/2);if(Math.abs(dy)>Math.abs(dx))c.rotate(-90);dim.setStyle(android.graphics.Paint.Style.FILL);dim.setTextSize(app.dp(10));dim.setTextAlign(android.graphics.Paint.Align.CENTER);float w=dim.measureText(label)/2+app.dp(3);dim.setColor(0xfff8fafc);c.drawRoundRect(-w,-app.dp(8),w,app.dp(8),4,4,dim);dim.setColor(0xff075985);c.drawText(label,0,app.dp(3),dim);c.restore();
   }
   @Override protected void onDraw(android.graphics.Canvas c){
    super.onDraw(c); left=app.dp(30);top=app.dp(42);right=getWidth()-app.dp(24);bottom=getHeight()-app.dp(28);try{float ratio=Float.parseFloat(sizes[0].getText().toString().replace(',','.'))/Float.parseFloat(sizes[1].getText().toString().replace(',','.'));if(ratio>0&&Float.isFinite(ratio)){float w=right-left,h=bottom-top;if(w/h>ratio){float d=(w-h*ratio)/2;left+=d;right-=d;}else{float d=(h-w/ratio)/2;top+=d;bottom-=d;}}}catch(RuntimeException ignored){} paint.setStyle(android.graphics.Paint.Style.FILL);
    c.drawColor(0xff061426);paint.setShader(new android.graphics.LinearGradient(0,0,getWidth(),getHeight(),0xff07192e,0xff020b18,android.graphics.Shader.TileMode.CLAMP));c.drawRect(0,0,getWidth(),getHeight(),paint);paint.setShader(null); paint.setColor(0xff18334d);paint.setStrokeWidth(1);for(int x=0;x<getWidth();x+=app.dp(20))c.drawLine(x,0,x,getHeight(),paint);for(int y=0;y<getHeight();y+=app.dp(20))c.drawLine(0,y,getWidth(),y,paint); paint.setColor(0xff34495e); paint.setStrokeWidth(7);paint.setStyle(android.graphics.Paint.Style.STROKE);
    if(!frameAdded[0])return;
    paint.setStyle(android.graphics.Paint.Style.FILL);
    for(int depth=app.dp(12);depth>0;depth--){paint.setColor(0xff64748b);c.drawRect(left+depth,top+depth,right+depth,bottom+depth,paint);}
    paint.setShader(new android.graphics.LinearGradient(left,top,right,bottom,0xffd5f6ff,0xff72c5e8,android.graphics.Shader.TileMode.CLAMP));c.drawRect(left,top,right,bottom,paint);paint.setShader(null);
    paint.setStyle(android.graphics.Paint.Style.STROKE);paint.setColor(0xff303b45);paint.setStrokeWidth(app.dp(9));c.drawRect(left,top,right,bottom,paint);
    paint.setColor(0xff9da8b0);paint.setStrokeWidth(app.dp(2));c.drawRect(left-2,top-2,right-2,bottom-2,paint);
    profileLabel(c,groups[0][lineProfiles.getOrDefault("frame",picks[0])].code,left+8,top+15);
    dim.setColor(0xff64748b);dim.setStrokeWidth(1);float off=app.dp(22);c.drawLine(left,top,left,top-off,dim);c.drawLine(right,top,right,top-off,dim);c.drawLine(left,top,left-off,top,dim);c.drawLine(left,bottom,left-off,bottom,dim);measure(c,left,top-off,right,top-off,sizes[0].getText()+" mm");measure(c,left-off,top,left-off,bottom,sizes[1].getText()+" mm");paint.setStyle(android.graphics.Paint.Style.STROKE);
    paint.setColor(0xff46535e);paint.setStrokeWidth(app.dp(8));
    for(int x:xs){float px=left+(right-left)*x/1000000f;c.drawLine(px,top,px,bottom,paint);profileLabel(c,groups[1][lineProfiles.getOrDefault("v"+x,picks[1])].code,px+4,top+30);}
    for(int y:ys){float py=top+(bottom-top)*y/1000000f;c.drawLine(left,py,right,py,paint);profileLabel(c,groups[1][lineProfiles.getOrDefault("h"+y,picks[1])].code,left+8,py-5);}
    for(int[] q:localLines){float x1=left+(right-left)*q[0]/1000000f,y1=top+(bottom-top)*q[1]/1000000f,x2=left+(right-left)*q[2]/1000000f,y2=top+(bottom-top)*q[3]/1000000f;c.drawLine(x1,y1,x2,y2,paint);profileLabel(c,groups[1][q[4]].code,x1+4,y1+15);}
    java.util.ArrayList<Integer> a=new java.util.ArrayList<>(xs),b=new java.util.ArrayList<>(ys);a.add(0,0);a.add(1000000);b.add(0,0);b.add(1000000);
    paint.setColor(0xff008577);paint.setStrokeWidth(3);
    java.util.ArrayList<int[]> cells=bays.get();
    for(int i=0;i<cells.size();i++)if(openings.contains(i+1)){
     int[] z=cells.get(i);
     float l=left+(right-left)*z[0]/1000000f+7,r=left+(right-left)*z[2]/1000000f-7,t=top+(bottom-top)*z[1]/1000000f+7,d=top+(bottom-top)*z[3]/1000000f-7;
     if(r<=l||d<=t)continue; c.save();c.clipRect(l,t,r,d);paint.setStyle(android.graphics.Paint.Style.STROKE);paint.setColor(0xff087e79);paint.setStrokeWidth(app.dp(3));c.drawRect(l,t,r,d,paint);profileLabel(c,groups[2][lineProfiles.getOrDefault("s"+(i+1),picks[2])].code,l+3,t+14);boolean isLeft=leftCells.contains(i+1);float hinge=isLeft?l:r,handle=isLeft?r:l;c.drawLine(hinge,t,handle,(t+d)/2,paint);c.drawLine(handle,(t+d)/2,hinge,d,paint);c.restore();
    }
    paint.setStyle(android.graphics.Paint.Style.FILL);
    for(int[] z:cells){try{int[] n=net.apply(z);float cx=left+(right-left)*(z[0]+z[2])/2000000f,cy=top+(bottom-top)*(z[1]+z[3])/2000000f;
     float l=left+(right-left)*z[0]/1000000f,r=left+(right-left)*z[2]/1000000f,t=top+(bottom-top)*z[1]/1000000f,bm=top+(bottom-top)*z[3]/1000000f;if(r-l>app.dp(65)&&bm-t>app.dp(65)){measure(c,l,t+(bm-t)*0.25f,r,t+(bm-t)*0.25f,"Net "+mm(n[0]));measure(c,l+(r-l)*0.22f,t,l+(r-l)*0.22f,bm,"Net "+mm(n[1]));}
     String label=mm(n[0])+" × "+mm(n[1]);paint.setTextSize(app.dp(12));float available=r-l-app.dp(12);if(available<app.dp(25)||bm-t<app.dp(28))continue;paint.setTextSize(Math.min(app.dp(12),app.dp(12)*available/Math.max(1,paint.measureText(label))));float w=paint.measureText(label)/2+app.dp(4);
     paint.setColor(0xfff8fafc);c.drawRoundRect(cx-w,cy-app.dp(12),cx+w,cy+app.dp(12),5,5,paint);paint.setColor(0xff173041);c.drawText(label,cx-w+app.dp(5),cy+app.dp(4),paint);
    }catch(RuntimeException ignored){}}
   }
   private void profileLabel(android.graphics.Canvas c,String code,float x,float y){
    android.graphics.Paint label=new android.graphics.Paint(3);label.setColor(0xff172b3c);label.setTextSize(app.dp(10));label.setStyle(android.graphics.Paint.Style.FILL);c.drawText(code,x,y,label);
   }
   @Override public boolean onTouchEvent(android.view.MotionEvent e){
    if(e.getActionMasked()==android.view.MotionEvent.ACTION_DOWN){getParent().requestDisallowInterceptTouchEvent(true);return true;}
    if(e.getActionMasked()==android.view.MotionEvent.ACTION_CANCEL){getParent().requestDisallowInterceptTouchEvent(false);return true;}
    if(e.getActionMasked()!=android.view.MotionEvent.ACTION_UP)return true;
    getParent().requestDisallowInterceptTouchEvent(false);
    int x=Math.round(1000000f*(e.getX()-left)/(right-left)),y=Math.round(1000000f*(e.getY()-top)/(bottom-top));
    if(!frameAdded[0]){toast("Önce kasa ekleyin.");return true;}
    if(mode[0]==9){
     int cell=hit.apply(x,y);if(cell==0)return true;int[] z=bays.get().get(cell-1);
     try{int[] n=net.apply(z);LinearLayout p=new LinearLayout(app);p.setOrientation(LinearLayout.VERTICAL);
      EditText en=new EditText(app),boy=new EditText(app);en.setInputType(DECIMAL);boy.setInputType(DECIMAL);en.setHint("Net en (mm)");boy.setHint("Net boy (mm)");en.setText(mm(n[0]));boy.setText(mm(n[1]));p.addView(en);p.addView(boy);
      new AlertDialog.Builder(app).setTitle(cell+". bölme net ölçüsü (mm)").setView(p).setNegativeButton("Vazgeç",null).setPositiveButton("Uygula",(d,k)->{
       Runnable restore=snapshot.get();int countBefore=bays.get().size();
       try{int[] wanted={ShopDimensions.tenths(en.getText().toString(),"Net en"),ShopDimensions.tenths(boy.getText().toString(),"Net boy")};
        for(int axis=0;axis<2;axis++){if(wanted[axis]<=0)throw new IllegalArgumentException("Ölçü pozitif olmalı.");if(wanted[axis]==n[axis])continue;
         int lo=z[axis],hi=z[axis+2],delta=Math.round(1000000f*(wanted[axis]-n[axis])/n[axis+2]);
         int old=hi<1000000?hi:lo, next=hi<1000000?hi+delta:lo-delta;
         java.util.TreeSet<Integer> bounds=axis==0?xs:ys;
         Integer upper=bounds.higher(old);
         if(hi<1000000&&lo>0&&(next>=1000000||(upper!=null&&next>=upper)||next<=lo)){old=lo;next=lo-delta;}
         if(delta==0)continue;
         if(old==0||old==1000000||next<=0||next>=1000000)throw new IllegalArgumentException("Dış kasa ölçüsünü veya komşu kaydı kontrol edin.");
         java.util.TreeSet<Integer> set=axis==0?xs:ys;
         if(set.contains(old)){Integer prev=set.lower(old),after=set.higher(old);if((prev!=null&&next<=prev)||(after!=null&&next>=after))throw new IllegalArgumentException("Kayıt diğer bölmeyi geçemez.");set.remove(old);set.add(next);String prefix=axis==0?"v":"h";Integer profile=lineProfiles.remove(prefix+old);if(profile!=null)lineProfiles.put(prefix+next,profile);}
         else {boolean found=false;for(int[] q:localLines)if(q[axis]==old&&q[axis+2]==old&&q[1-axis]<=z[1-axis]&&q[3-axis]>=z[3-axis])found=true;if(!found)throw new IllegalArgumentException("Bölme sınırı bulunamadı.");}
         for(int[] q:localLines){if(q[axis]==old)q[axis]=next;if(q[axis+2]==old)q[axis+2]=next;if(q[0]>q[2]||q[1]>q[3]||(q[0]==q[2]&&q[1]==q[3]))throw new IllegalArgumentException("Kayıt çakışması.");}
        }
        java.util.ArrayList<int[]> updated=bays.get();if(updated.size()!=countBefore)throw new IllegalArgumentException("Bölme yapısı değişemez."); 
        for(int[] b:updated){int[] a=net.apply(b);if(a[0]<=0||a[1]<=0)throw new IllegalArgumentException("Komşu bölme çok dar.");}
        undo.push(restore);redo.clear();persistDrawing.run();invalidate();
       }catch(RuntimeException ex){restore.run();invalidate();toast(ex.getMessage());}
      }).show();
     }catch(RuntimeException ex){toast(ex.getMessage());}return true;
    }
    if(mode[0]==8){
     int cell=hit.apply(x,y);
     if(!openings.contains(cell)){toast("Önce bir kanat ekleyin.");return true;}
     EditText gap=new EditText(app);gap.setInputType(DECIMAL);gap.setText(gaps.containsKey(cell)?mm(gaps.get(cell)):sashGapEdit.getText().toString());
     new AlertDialog.Builder(app).setTitle(cell+". kanat kasa boşluğu (mm/kenar)").setView(gap).setNegativeButton("Vazgeç",null).setPositiveButton("Uygula",(d,k)->{try{int n=ShopDimensions.tenths(gap.getText().toString(),"Kanat kasa boşluğu");if(n<0)throw new IllegalArgumentException("Boşluk negatif olamaz.");checkpoint.run();gaps.put(cell,n);invalidate();}catch(IllegalArgumentException ex){toast(ex.getMessage());}}).show();return true;
    }
    if(mode[0]==7){
     String key=null;int role=1;int best=65000;
     for(int q:xs)if(Math.abs(q-x)<best){best=Math.abs(q-x);key="v"+q;}
     for(int q:ys)if(Math.abs(q-y)<best){best=Math.abs(q-y);key="h"+q;}
     int edge=Math.min(Math.min(Math.abs(x),Math.abs(1000000-x)),Math.min(Math.abs(y),Math.abs(1000000-y)));
     if(edge<best){role=0;key="frame";}
     if(key==null){int cell=hit.apply(x,y);if(openings.contains(cell)){role=2;key="s"+cell;}}
     if(key==null){toast("Bir profil çizgisine dokunun.");return true;}
     final String selectedKey=key;final int selectedRole=role;
     chooseProfile("Profil değiştir",groups[role],lineProfiles.getOrDefault(key,picks[role]),index->{checkpoint.run();lineProfiles.put(selectedKey,index);invalidate();});return true;
    }
    if(x<=0||x>=1000000||y<=0||y>=1000000)return true;
    checkpoint.run();
    if(mode[0]==0||mode[0]==1){
     if((mode[0]==0&&xs.contains(x))||(mode[0]==1&&ys.contains(y))){undo.pop();return true;}
     openings.clear();leftCells.clear();emptyCells.clear();gaps.clear();
     int l=xs.floor(x)==null?0:xs.floor(x),r=xs.ceiling(x)==null?1000000:xs.ceiling(x);
     int u=ys.floor(y)==null?0:ys.floor(y),d=ys.ceiling(y)==null?1000000:ys.ceiling(y);
     for(int[] q:localLines){if(q[0]==q[2]&&y>=q[1]&&y<=q[3]){if(q[0]<x)l=Math.max(l,q[0]);if(q[0]>x)r=Math.min(r,q[0]);}if(q[1]==q[3]&&x>=q[0]&&x<=q[2]){if(q[1]<y)u=Math.max(u,q[1]);if(q[1]>y)d=Math.min(d,q[1]);}}
     if(mode[0]==0){if(u==0&&d==1000000)xs.add(x);else localLines.add(new int[]{x,u,x,d,picks[1]});}
     else{if(l==0&&r==1000000)ys.add(y);else localLines.add(new int[]{l,y,r,y,picks[1]});}
    }
    else if(mode[0]==3){Integer nearest=null;int distance=60000;boolean vertical=true;for(int q:xs)if(Math.abs(q-x)<distance){nearest=q;distance=Math.abs(q-x);vertical=true;}for(int q:ys)if(Math.abs(q-y)<distance){nearest=q;distance=Math.abs(q-y);vertical=false;}if(nearest!=null){if(vertical)xs.remove(nearest);else ys.remove(nearest);openings.clear();leftCells.clear();emptyCells.clear();}}
    else {int cell=hit.apply(x,y);if(mode[0]==4){openings.remove(cell);leftCells.remove(cell);emptyCells.add(cell);}else if(mode[0]==5){openings.remove(cell);leftCells.remove(cell);emptyCells.remove(cell);}else{if(cell==0){undo.pop();return true;}emptyCells.remove(cell);openings.add(cell);if(mode[0]==2)leftCells.add(cell);else leftCells.remove(cell);}}
    drawingStatus.setText("Kasa: 1 | Dikey: "+xs.size()+" | Yatay: "+ys.size()+" | Kanat: "+openings.size());persistDrawing.run();invalidate();return true;
   }
  };
  refreshDrawing[0]=board::invalidate;
  LinearLayout historyTools=new LinearLayout(app);panel.addView(historyTools);
  Button undoButton=new Button(app);undoButton.setText("↶ Geri al");historyTools.addView(undoButton,new LinearLayout.LayoutParams(0,app.dp(52),1));
  Button redoButton=new Button(app);redoButton.setText("↷ İleri al");historyTools.addView(redoButton,new LinearLayout.LayoutParams(0,app.dp(52),1));
  redoButton.setOnClickListener(v->{if(redo.isEmpty()){toast("İleri alınacak işlem yok.");return;}undo.push(snapshot.get());redo.pop().run();persistDrawing.run();board.invalidate();});
  undoButton.setOnClickListener(v->{if(undo.isEmpty()){toast("Geri alınacak işlem yok.");return;}redo.push(snapshot.get());undo.pop().run();persistDrawing.run();drawingStatus.setText("Son işlem geri alındı.");board.invalidate();});
  Button frameButton=new Button(app);frameButton.setText("+ Kasa ekle");panel.addView(frameButton);frameButton.setOnClickListener(v->{checkpoint.run();frameAdded[0]=true;drawingStatus.setText("Kasa eklendi: "+groups[0][picks[0]].label());board.invalidate();});
  Button sashButton=new Button(app);sashButton.setText("+ Kanat ekle (ilk boş bölme)");panel.addView(sashButton);sashButton.setOnClickListener(v->{if(!frameAdded[0]){toast("Önce kasa ekleyin.");return;}int count=bays.get().size();for(int i=1;i<=count;i++)if(!openings.contains(i)){checkpoint.run();openings.add(i);drawingStatus.setText("Kanat: "+openings.size()+" / "+count);board.invalidate();return;}toast("Tüm bölmelerde kanat var.");});
  panel.addView(board,new LinearLayout.LayoutParams(-1,app.dp(560)));
  LinearLayout toolsRow=new LinearLayout(app);toolsRow.setOrientation(LinearLayout.HORIZONTAL);panel.addView(toolsRow);
  String[] modes={"↕ Dikey kayıt","↔ Yatay kayıt","Sol kanat","Sil","Boş kasa","Sabit cam","Sağ kanat","Profil seç","Kanat boşluğu","Net ölçü gir"};
  Button[] toolButtons=new Button[modes.length];
  for(int i=0;i<modes.length;i++){final int m=i;Button b=new Button(app);b.setAllCaps(false);b.setText(modes[i]);b.setTextSize(12);b.setPadding(app.dp(4),app.dp(4),app.dp(4),app.dp(4));toolButtons[i]=b;b.setTextColor(Color.WHITE);b.setBackground(app.rgbPanel(i==mode[0]?0xff086bc1:0xff102b48,0xff12385d,0xff32caff));if(i%3==0&&i!=0){toolsRow=new LinearLayout(app);toolsRow.setOrientation(LinearLayout.HORIZONTAL);panel.addView(toolsRow);}LinearLayout.LayoutParams toolLp=new LinearLayout.LayoutParams(0,app.dp(56),1);toolLp.setMargins(app.dp(3),app.dp(3),app.dp(3),app.dp(3));toolsRow.addView(b,toolLp);b.setOnClickListener(v->{mode[0]=m;for(int j=0;j<toolButtons.length;j++)toolButtons[j].setBackground(app.rgbPanel(j==m?0xff087e79:0xff173041,0xff245462,series.accent));drawingStatus.setText("Seçili araç: "+modes[m]+" · çizime dokunun.");});}

  for(int axisIndex=0;axisIndex<2;axisIndex++){
   final int axis=axisIndex;
   Button equal=new Button(app);equal.setAllCaps(false);equal.setText(axis==0?"Kapı/kanat hariç dik boşlukları eşitle":"Kapı/kanat hariç yatay boşlukları eşitle");panel.addView(equal);
   equal.setOnClickListener(v->{
    if(!frameAdded[0]){toast("Önce kasa ekleyin.");return;}
    Runnable restore=snapshot.get();
    try{
     int originalCount=bays.get().size();
     java.util.TreeSet<Integer> set=axis==0?xs:ys;
     java.util.ArrayList<Integer> pts=new java.util.ArrayList<>(set);pts.add(0,0);pts.add(1000000);
     java.util.TreeSet<Integer> fixed=new java.util.TreeSet<>();fixed.add(0);fixed.add(1000000);
     java.util.ArrayList<int[]> cells=bays.get();
     for(int i=0;i<cells.size();i++)if(openings.contains(i+1)||emptyCells.contains(i+1)){int[] protectedCell=cells.get(i);for(int p:pts)if(p>=protectedCell[axis]&&p<=protectedCell[axis+2])fixed.add(p);Integer lower=set.floor(protectedCell[axis]),upper=set.ceiling(protectedCell[axis+2]);if(lower!=null)fixed.add(lower);if(upper!=null)fixed.add(upper);}
     int total=net.apply(cells.get(0))[axis+2];if(total<=0)throw new IllegalArgumentException("Kasa ölçüsünü kontrol edin.");
     String prefix=axis==0?"v":"h";
     java.util.HashMap<Integer,Integer> faces=new java.util.HashMap<>();
     for(int p:pts)faces.put(p,p==0||p==1000000?0:workingFace(groups[1][lineProfiles.getOrDefault(prefix+p,picks[1])]));
     java.util.HashMap<Integer,Integer> moves=new java.util.HashMap<>();
     int start=0;
     for(int end:fixed){if(end==0)continue;
      int l=pts.indexOf(start),h=pts.indexOf(end);
      if(l<0||h<=l)throw new IllegalArgumentException("Bölme sınırı bulunamadı.");
      double available=(double)total*(end-start)/1000000.0-(faces.get(start)-faces.get(start)/2)-faces.get(end)/2;
      for(int j=l+1;j<h;j++)available-=faces.get(pts.get(j));
      double gap=available/(h-l);if(gap<=0)throw new IllegalArgumentException("Net boşluk yetersiz.");
      double cursor=(double)total*start/1000000.0+(faces.get(start)-faces.get(start)/2);
      int previous=start;
      for(int j=l+1;j<h;j++){int p=pts.get(j);cursor+=gap+faces.get(p)/2;int next=(int)Math.round(cursor*1000000/total);if(next<=previous||next>=end)throw new IllegalArgumentException("Kayıt çakışması.");moves.put(p,next);previous=next;cursor+=faces.get(p)-faces.get(p)/2;}
      start=end;
     }
     java.util.HashMap<Integer,Integer> profiles=new java.util.HashMap<>();
     for(int p:set){Integer profile=lineProfiles.remove(prefix+p);if(profile!=null)profiles.put(moves.getOrDefault(p,p),profile);}
     java.util.TreeSet<Integer> updated=new java.util.TreeSet<>();for(int p:set)updated.add(moves.getOrDefault(p,p));set.clear();set.addAll(updated);
     for(java.util.Map.Entry<Integer,Integer> e:profiles.entrySet())lineProfiles.put(prefix+e.getKey(),e.getValue());
     for(int[] q:localLines){q[axis]=moves.getOrDefault(q[axis],q[axis]);q[axis+2]=moves.getOrDefault(q[axis+2],q[axis+2]);}
     if(bays.get().size()!=originalCount)throw new IllegalArgumentException("Kayıt bağlantıları korunamadı; işlem geri alındı.");
     for(int[] cell:bays.get()){int[] n=net.apply(cell);if(n[0]<=0||n[1]<=0)throw new IllegalArgumentException("Net boşluk yetersiz.");}
     undo.push(restore);redo.clear();persistDrawing.run();board.invalidate();drawingStatus.setText("Net boşluklar eşitlendi; kapı ve kanatlar korundu.");
    }catch(RuntimeException ex){restore.run();board.invalidate();toast(ex.getMessage());}
   });
  }
  Button addV=new Button(app);addV.setText("+ Dikey kayıt (eşit böl)");panel.addView(addV);addV.setOnClickListener(v->{if(!frameAdded[0]){toast("Önce kasa ekleyin.");return;}if(xs.size()>=12)return;checkpoint.run();int n=xs.size()+2;xs.clear();for(int i=1;i<n;i++)xs.add(Math.round(1000000f*i/n));openings.clear();leftCells.clear();emptyCells.clear();drawingStatus.setText("Dikey: "+xs.size()+" | Yatay: "+ys.size());board.invalidate();});
  Button addH=new Button(app);addH.setText("+ Yatay kayıt (eşit böl)");panel.addView(addH);addH.setOnClickListener(v->{if(!frameAdded[0]){toast("Önce kasa ekleyin.");return;}if(ys.size()>=12)return;checkpoint.run();int n=ys.size()+2;ys.clear();for(int i=1;i<n;i++)ys.add(Math.round(1000000f*i/n));openings.clear();leftCells.clear();emptyCells.clear();board.invalidate();});
  for(EditText size:sizes)size.addTextChangedListener(watcher(board::invalidate));
  Button exact=new Button(app);exact.setText("Kayıt konumunu mm olarak gir");panel.addView(exact);exact.setOnClickListener(v->{if(!frameAdded[0]){toast("Önce kasa ekleyin.");return;}EditText pos=new EditText(app);pos.setInputType(DECIMAL);new AlertDialog.Builder(app).setTitle("Kasa içi net konum (mm)").setView(pos).setNegativeButton("Vazgeç",null).setPositiveButton("Ekle",(d,k)->{try{boolean vertical=mode[0]!=1;int total=ShopDimensions.tenths(sizes[vertical?0:1].getText().toString(),"Dış ölçü")-2*workingFace(groups[0][picks[0]]);int point=Math.round(1000000f*ShopDimensions.tenths(pos.getText().toString(),"Konum")/total);java.util.TreeSet<Integer> target=vertical?xs:ys;if(point<=0||point>=1000000||target.size()>=12)throw new IllegalArgumentException("Konum kasa içinde olmalı.");checkpoint.run();target.add(point);openings.clear();leftCells.clear();emptyCells.clear();board.invalidate();}catch(IllegalArgumentException ex){toast(ex.getMessage());}}).show();});
  Button netSizes=new Button(app);netSizes.setText("Kasa içi net en ve boy gir");panel.addView(netSizes);
  netSizes.setOnClickListener(v->{
   LinearLayout p=new LinearLayout(app);p.setOrientation(LinearLayout.VERTICAL);
   EditText nw=new EditText(app),nh=new EditText(app);nw.setInputType(DECIMAL);nh.setInputType(DECIMAL);nw.setHint("Net en (mm)");nh.setHint("Net boy (mm)");p.addView(nw);p.addView(nh);
   new AlertDialog.Builder(app).setTitle("Kasa içi net açıklık").setView(p).setNegativeButton("Vazgeç",null).setPositiveButton("Uygula",(d,k)->{try{
    if(!xs.isEmpty()||!ys.isEmpty())throw new IllegalArgumentException("Bölmeli kasada dış ölçüleri kullanın.");
    int w=ShopDimensions.tenths(nw.getText().toString(),"Net en"),h=ShopDimensions.tenths(nh.getText().toString(),"Net boy");
    if(w<=0||h<=0)throw new IllegalArgumentException("Net ölçüler pozitif olmalı.");
    checkpoint.run();int face=workingFace(groups[0][picks[0]]);sizes[0].setText(mm(w+2*face));sizes[1].setText(mm(h+2*face));frameAdded[0]=true;board.invalidate();
   }catch(IllegalArgumentException ex){toast(ex.getMessage());}}).show();
  });
  Button clear=new Button(app);clear.setText("Çizimi temizle");panel.addView(clear);clear.setOnClickListener(v->{checkpoint.run();frameAdded[0]=false;localLines.clear();lineProfiles.clear();xs.clear();ys.clear();openings.clear();leftCells.clear();emptyCells.clear();board.invalidate();});
  // Dark CAD workspace with a horizontal tool ribbon and scrollable settings.
  panel.removeView(board);
  for(Button b:toolButtons)((android.view.ViewGroup)b.getParent()).removeView(b);
  ((android.view.ViewGroup)historyTools.getParent()).removeView(historyTools);
  for(int i=panel.getChildCount()-1;i>=0;i--){View child=panel.getChildAt(i);if(child instanceof LinearLayout&&((LinearLayout)child).getChildCount()==0)panel.removeViewAt(i);}
  LinearLayout workspace=new LinearLayout(app);workspace.setOrientation(LinearLayout.VERTICAL);workspace.setBackgroundColor(0xff061426);workspace.setPadding(app.dp(5),app.dp(4),app.dp(5),app.dp(4));
  TextView heading=new TextView(app);heading.setText("Profil Kesim Pro  |  "+series.label+"  ·  Kendin Çiz");heading.setTextColor(0xffe2f6ff);heading.setTextSize(16);heading.setPadding(app.dp(8),app.dp(4),app.dp(8),app.dp(4));workspace.addView(heading);
  android.widget.HorizontalScrollView ribbonScroll=new android.widget.HorizontalScrollView(app);ribbonScroll.setHorizontalScrollBarEnabled(true);
  LinearLayout ribbon=new LinearLayout(app);ribbon.setOrientation(LinearLayout.HORIZONTAL);ribbonScroll.addView(ribbon);workspace.addView(ribbonScroll,new LinearLayout.LayoutParams(-1,app.dp(54)));
  java.util.ArrayList<Button> ribbonButtons=new java.util.ArrayList<>();
  ribbonButtons.add(undoButton);ribbonButtons.add(redoButton);for(Button b:toolButtons)ribbonButtons.add(b);
  for(Button b:new Button[]{frameButton,sashButton,addV,addH,clear}){((android.view.ViewGroup)b.getParent()).removeView(b);ribbonButtons.add(b);}
  for(Button b:ribbonButtons){b.setAllCaps(false);b.setTextSize(11);b.setTextColor(Color.WHITE);b.setMaxLines(2);b.setPadding(app.dp(8),app.dp(2),app.dp(8),app.dp(2));b.setBackground(app.rgbPanel(0xff12385d,0xff081a32, b==clear?0xffff5267:b==undoButton?0xff45f59a:0xff32caff));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(app.dp(108),app.dp(46));lp.setMargins(app.dp(3),app.dp(3),app.dp(3),app.dp(3));ribbon.addView(b,lp);}
  LinearLayout body=new LinearLayout(app);body.setOrientation(LinearLayout.HORIZONTAL);workspace.addView(body,new LinearLayout.LayoutParams(-1,0,1));
  panel.setPadding(app.dp(5),app.dp(5),app.dp(5),app.dp(5));panel.setBackgroundColor(0xff0b2037);
  for(int i=0;i<panel.getChildCount();i++){View child=panel.getChildAt(i);if(child instanceof Button){Button b=(Button)child;b.setAllCaps(false);b.setTextSize(12);b.setTextColor(Color.WHITE);b.setSingleLine(false);b.setBackground(app.rgbPanel(0xff153858,0xff091c32,0xff35bfff));}else if(child instanceof TextView){((TextView)child).setTextColor(0xffe2f6ff);((TextView)child).setTextSize(12);if(child instanceof EditText)((EditText)child).setHintTextColor(0xff9cc7e5);}}
  scroll.setFillViewport(true);int screen=app.getResources().getDisplayMetrics().widthPixels;int sidebar=Math.min(app.dp(200),Math.max(app.dp(108),screen/4));body.addView(scroll,new LinearLayout.LayoutParams(sidebar,-1));scroll.setVisibility(View.GONE);Button settings=new Button(app);settings.setText("Ölçü ve profil ayarları");settings.setAllCaps(false);workspace.addView(settings,2,new LinearLayout.LayoutParams(-1,app.dp(44)));settings.setOnClickListener(v->scroll.setVisibility(scroll.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE));
  LinearLayout canvasPane=new LinearLayout(app);canvasPane.setOrientation(LinearLayout.VERTICAL);body.addView(canvasPane,new LinearLayout.LayoutParams(0,-1,1));canvasPane.addView(board,new LinearLayout.LayoutParams(-1,0,1));
  panel.removeView(drawingStatus);drawingStatus.setTextColor(0xff70f5be);drawingStatus.setPadding(app.dp(8),app.dp(4),app.dp(8),app.dp(4));workspace.addView(drawingStatus,new LinearLayout.LayoutParams(-1,-2));
  AlertDialog dialog=new AlertDialog.Builder(app).setTitle("Kendin çiz · "+series.label).setView(workspace).setNegativeButton("Kaydet ve çık",null).setPositiveButton("Kesim hesapla",null).create();
  dialog.setOnDismissListener(v->{persistDrawing.run();Bundle history=new Bundle();saveState(history);history.putBoolean("temaEditing",true);app.saveHistory(series.label,history);});
  dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(w->{
   try{
    if(!frameAdded[0])throw new IllegalArgumentException("Önce kasa ekleyin.");

    int width=ShopDimensions.tenths(sizes[0].getText().toString(),"Dış en"),height=ShopDimensions.tenths(sizes[1].getText().toString(),"Dış yükseklik");
    int frame=workingFace(groups[0][picks[0]]),record=workingFace(groups[1][picks[1]]);
    String cols=drawSpans(xs,width-2*frame-xs.size()*record),rows=drawSpans(ys,height-2*frame-ys.size()*record);
    for(int i=0;i<2;i++)fields.get(sizeKeys[i]).setText(sizes[i].getText());
    for(int i=0;i<3;i++){profileIndices[i]=picks[i];fields.get("code"+i).setText(groups[i][picks[i]].code);fields.get("face"+i).setText(mm(workingFace(groups[i][picks[i]])));}
    fields.get("vertical").setText(String.valueOf(xs.size()));fields.get("horizontal").setText(String.valueOf(ys.size()));
    fields.get("columns").setText(cols);fields.get("rows").setText(rows);fields.get("joint").setText(jointEdit.getText());fields.get("sashGap").setText(sashGapEdit.getText());fields.get("mullionJoint").setText(recordJoint.getText());fields.get("leftCells").setText(cellList(leftCells));fields.get("emptyCells").setText(cellList(emptyCells));
    StringBuilder gapData=new StringBuilder();for(int cell:openings)if(gaps.containsKey(cell))gapData.append(cell).append(':').append(gaps.get(cell)).append(';');draft.put("individualSashGaps",gapData.toString());
    StringBuilder bayData=new StringBuilder(),recordData=new StringBuilder();
    {
     for(int[] z:bays.get())bayData.append(z[0]).append(',').append(z[1]).append(',').append(z[2]).append(',').append(z[3]).append(';');
     for(int x:xs)recordData.append(x).append(",0,").append(x).append(",1000000,").append(lineProfiles.getOrDefault("v"+x,picks[1])).append(';');
     for(int y:ys)recordData.append("0,").append(y).append(",1000000,").append(y).append(',').append(lineProfiles.getOrDefault("h"+y,picks[1])).append(';');
     for(int[] q:localLines)recordData.append(q[0]).append(',').append(q[1]).append(',').append(q[2]).append(',').append(q[3]).append(',').append(q[4]).append(';');
    }
    draft.put("regionalScale","1000000");draft.put("regionalBays",bayData.toString());draft.put("regionalRecords",recordData.toString());
    chosenSashes.clear();chosenSashes.addAll(openings);expectedFixed=-1;updateLayout();invalidate();dialog.dismiss();
    toast("Çizim aktarıldı. Montaj ve fiş paylarını kontrol edip hesaplayın.");
   }catch(IllegalArgumentException ex){toast(ex.getMessage());}
  }));dialog.show();
  if(dialog.getWindow()!=null)dialog.getWindow().setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT,android.view.ViewGroup.LayoutParams.MATCH_PARENT);
 }

 private String cellList(java.util.Set<Integer> cells){StringBuilder b=new StringBuilder();for(int n:cells){if(b.length()>0)b.append(";");b.append(n);}return b.toString();}

 private String drawSpans(java.util.TreeSet<Integer> points,int total){
  if(total<=0)throw new IllegalArgumentException("Profil payları dış ölçüyü aşıyor.");
  StringBuilder result=new StringBuilder();int previous=0;
  java.util.ArrayList<Integer> ends=new java.util.ArrayList<>(points);ends.add(1000000);
  for(int end:ends){int next=(int)Math.round((double)total*end/1000000.0);if(next<=previous)throw new IllegalArgumentException("Bölme çok dar.");if(result.length()>0)result.append(";");result.append(mm(next-previous));previous=next;}
  return result.toString();
 }

 private void dimensionDialog() {
  ScrollView scroll = new ScrollView(app);
  LinearLayout panel = new LinearLayout(app);
  panel.setOrientation(LinearLayout.VERTICAL);
  panel.setPadding(app.dp(16), app.dp(8), app.dp(16), app.dp(8));
  scroll.addView(panel);
  String[] keys = {"width","height","face0","face1","face2","columns","rows","joint","sashGap","beadEdge"};
  String[] labels = {"Dış en (mm)","Dış yükseklik (mm)","Kasa düşüm yüzü (mm)","Kayıt yüzü (mm)","Kanat yüzü (mm)","Net yatay açıklıklar (; ile ayır)","Net dikey açıklıklar (; ile ayır)","Fiş / kayıt bağlantı farkı (mm/uç, eksi düşer)","Kanat boşluğu (mm/kenar)","Çıta oturma farkı (mm/kenar)"};
  EditText[] edits = new EditText[keys.length];
  for (int i = 0; i < keys.length; i++) {
   TextView label = new TextView(app);
   label.setText(labels[i]);
   panel.addView(label);
   EditText edit = new EditText(app);
   edit.setSingleLine(true);
   edit.setInputType(i == 5 || i == 6 ? InputType.TYPE_CLASS_TEXT : SIGNED);
   edit.setText(fields.get(keys[i]).getText().toString());
   panel.addView(edit);
   edits[i] = edit;
  }
  new AlertDialog.Builder(app).setTitle("İmalat ölçülendirme").setView(scroll)
   .setNegativeButton("Vazgeç", null).setPositiveButton("Ölçüleri uygula", (dialog, which) -> {
    for (int i = 0; i < keys.length; i++) fields.get(keys[i]).setText(edits[i].getText().toString());
    invalidate();
   }).show();
 }

 private void chooseBays() {
  int columns = recordCount("vertical") + 1, rows = recordCount("horizontal") + 1;
  Set<Integer> selected = new LinkedHashSet<>(chosenSashes);
  ScrollView scroll = new ScrollView(app);
  LinearLayout panel = new LinearLayout(app);
  panel.setOrientation(LinearLayout.VERTICAL);
  panel.setPadding(app.dp(12), app.dp(8), app.dp(12), app.dp(8));
  scroll.addView(panel);
  TextView summary = app.planLabel(panel, "", 15, Color.DKGRAY);
  List<Button> cells = new ArrayList<>();
  Runnable redraw = () -> {
   summary.setText((rows * columns - selected.size()) + " sabit  ·  " + selected.size() + " açılır. Bölmeye dokunarak değiştirin.");
   for (int i = 0; i < cells.size(); i++) {
    int number = i + 1;
    Button cell = cells.get(i);
    boolean opening = selected.contains(number);
    cell.setText(number + (opening ? " ↗" : " ▪"));
    cell.setContentDescription(number + (opening ? ". bölme, açılır" : ". bölme, sabit"));
    cell.setBackground(app.rgbPanel(opening ? 0xff195957 : 0xff1c3040, opening ? 0xff287a70 : 0xff284658, series.accent));
   }
  };
  android.widget.HorizontalScrollView rail = new android.widget.HorizontalScrollView(app);
  panel.addView(rail);
  LinearLayout board = new LinearLayout(app);
  board.setOrientation(LinearLayout.VERTICAL);
  rail.addView(board);
  for (int y = 0; y < rows; y++) {
   LinearLayout row = horizontal(board);
   for (int x = 0; x < columns; x++) {
    int number = y * columns + x + 1;
    Button button = new Button(app);
    button.setTextColor(Color.WHITE);
    button.setTextSize(14);
    button.setAllCaps(false);
    LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(app.dp(54), app.dp(46));
    size.setMargins(app.dp(2), app.dp(2), app.dp(2), app.dp(2));
    row.addView(button, size);
    cells.add(button);
    button.setOnClickListener(v -> {
     if (!selected.add(number)) selected.remove(number);
     redraw.run();
    });
   }
  }
  redraw.run();
  new AlertDialog.Builder(app).setTitle("Sabit ve açılır bölmeler").setView(scroll)
   .setNegativeButton("Vazgeç", null).setPositiveButton("Uygula", (dialog, which) -> {
    chosenSashes.clear();
    chosenSashes.addAll(selected);
    expectedFixed = -1;
    updateLayout();
    invalidate();
   }).show();
 }

 private LinearLayout section(String heading) {
  LinearLayout card = app.planCard(app.body, 0xfa132634, 0xfa223e4a, 0xff608b94);
  app.planLabel(card, heading, 18, series.accent).setTypeface(null, android.graphics.Typeface.BOLD);
  return card;
 }

 private EditText field(LinearLayout parent, String key, String label, String fallback, int inputType) {
  String value = draft.containsKey(key) ? draft.get(key) : fallback;
  EditText edit = app.input(parent, label, value);
  edit.setInputType(inputType);
  edit.setSingleLine(true);
  edit.addTextChangedListener(new TextWatcher() {
   @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
   @Override public void onTextChanged(CharSequence s, int start, int before, int count) { invalidate(); }
   @Override public void afterTextChanged(Editable s) {}
  });
  fields.put(key, edit);
  return edit;
 }

 private LinearLayout profile(LinearLayout parent, int role, String label, TemaCatalog.Profile[] options) {
  LinearLayout box = app.planCard(parent, 0xff112839, 0xff1a3e48, series.accent);
  app.planLabel(box, label, 14, series.accent).setTypeface(null, android.graphics.Typeface.BOLD);
  LinearLayout preview = horizontal(box);
  ImageView image = new ImageView(app);
  image.setScaleType(ImageView.ScaleType.FIT_CENTER);
  image.setBackground(app.rgbPanel(0xfff1f7fa, 0xffd9edf4, series.accent));
  image.setPadding(app.dp(5), app.dp(4), app.dp(5), app.dp(4));
  preview.addView(image, new LinearLayout.LayoutParams(app.dp(82), app.dp(68)));
  LinearLayout identity = column(preview);
  identity.setPadding(app.dp(6), 0, 0, 0);
  TextView name = app.planLabel(identity, "", 16, Color.WHITE);
  name.setTypeface(null, android.graphics.Typeface.BOLD);
  TextView dimensions = app.planLabel(identity, "", 13, 0xffbbdbe5);
  Runnable refresh = () -> {
   TemaCatalog.Profile item = options[profileIndices[role]];
   image.setImageResource(item.image);
   image.setContentDescription(item.code + " profil kesiti");
   name.setText(item.label());
   dimensions.setText(item.dimensions() + " · s. " + item.page);
  };
  refresh.run();
  box.setFocusable(true);
  InterfaceMotion.buttonFeedback(box);
  box.setOnClickListener(v -> chooseProfile(label, options, profileIndices[role], index -> {
   profileIndices[role] = index;
   TemaCatalog.Profile item = options[index];
   app.getPreferences(Activity.MODE_PRIVATE).edit().putString(preference(role), item.code).apply();
   if (role < 3) {
    fields.get("code" + role).setText(item.code);
    fields.get("face" + role).setText(mm(workingFace(item)));
   }
   refresh.run();
   invalidate();
  }));
  return box;
 }

 private void chooseProfile(String label, TemaCatalog.Profile[] options, int selected, Option callback) {
  android.widget.ListView choices = new android.widget.ListView(app);
  choices.setDividerHeight(app.dp(7));
  choices.setAdapter(new android.widget.BaseAdapter() {
   @Override public int getCount() { return options.length; }
   @Override public TemaCatalog.Profile getItem(int index) { return options[index]; }
   @Override public long getItemId(int index) { return index; }
   @Override public View getView(int index, View previous, android.view.ViewGroup parent) {
    TemaCatalog.Profile item = getItem(index);
    LinearLayout row = new LinearLayout(app);
    row.setGravity(android.view.Gravity.CENTER_VERTICAL);
    row.setPadding(app.dp(14), app.dp(7), app.dp(14), app.dp(7));
    ImageView section = new ImageView(app);
    section.setImageResource(item.image);
    section.setScaleType(ImageView.ScaleType.FIT_CENTER);
    row.addView(section, new LinearLayout.LayoutParams(app.dp(97), app.dp(72)));
    LinearLayout identity = column(row);
    app.planLabel(identity, item.label() + (index == selected ? " ✓" : ""), 16, Color.BLACK)
     .setTypeface(null, android.graphics.Typeface.BOLD);
    app.planLabel(identity, item.dimensions() + " · s. " + item.page, 13, Color.DKGRAY);
    return row;
   }
  });
  AlertDialog dialog = new AlertDialog.Builder(app).setTitle(label + " kesitini seç").setView(choices)
   .setNegativeButton("Geri", null).create();
  choices.setOnItemClickListener((parent, row, index, id) -> {
   callback.selected(index);
   dialog.dismiss();
  });
  dialog.show();
 }

 private Button chooseButton(LinearLayout parent, String label, Runnable action) {
  Button button = new Button(app);
  button.setAllCaps(false);
  button.setText(label);
  button.setTextColor(0xffeff8fd);
  button.setBackground(app.rgbPanel(0xff173041, 0xff245462, series.accent));
  InterfaceMotion.buttonFeedback(button);
  button.setPadding(app.dp(10), app.dp(6), app.dp(10), app.dp(6));
  button.setMinHeight(app.dp(48));
  LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(-1, -2);
  size.topMargin = app.dp(7);
  parent.addView(button, size);
  if (action != null) button.setOnClickListener(v -> action.run());
  return button;
 }

 private interface Option { void selected(int index); }

 private void choose(String label, String[] choices, int selected, Option callback) {
  new AlertDialog.Builder(app).setTitle(label).setSingleChoiceItems(choices, selected, (dialog, index) -> {
   dialog.dismiss();
   callback.selected(index);
  }).setNegativeButton("Vazgeç", null).show();
 }

 private void openUrl(String url) {
  try {
   app.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
  } catch (android.content.ActivityNotFoundException e) {
   toast("Katalog bağlantısını açabilecek uygulama bulunamadı.");
  }
 }

 private void invalidate() {
  if (confirmed != null) confirmed.setChecked(false);
 }

 private void remember() {
  for (Map.Entry<String, EditText> entry : fields.entrySet()) {
   draft.put(entry.getKey(), entry.getValue().getText().toString());
  }
  if (adapter != null) draft.put("adapter", adapter.isChecked() ? "1" : "0");
 }

 void saveState(Bundle out) {
  boolean editing = app.body == form && form != null;
  if (editing) remember();
  Bundle state = new Bundle();
  for (Map.Entry<String, String> entry : draft.entrySet()) state.putString(entry.getKey(), entry.getValue());
  out.putBundle("temaDraft", state);
  out.putIntArray("temaSelection", new int[]{series.ordinal(), corner, continuous,
   profileIndices[0], profileIndices[1], profileIndices[2], sashCorner, profileIndices[3], profileIndices[4], beadCorner});
  int[] sashes = new int[chosenSashes.size()];
  int index = 0;
  for (int bay : chosenSashes) sashes[index++] = bay;
  out.putIntArray("temaSashes", sashes);
  out.putInt("temaExpectedFixed", expectedFixed);
  out.putBoolean("temaEditing", editing);
  if (cameraUri != null) out.putString("temaCamera", cameraUri.toString());
  if (drawingUri != null) out.putString("temaDrawing", drawingUri.toString());
 }

 void restoreHistory(Bundle in){draft.clear();chosenSashes.clear();restoreState(in);}
 boolean restoreState(Bundle in) {
  if (in == null) return false;
  Bundle state = in.getBundle("temaDraft");
  if (state != null) for (String key : state.keySet()) draft.put(key, state.getString(key));
  int[] selection = in.getIntArray("temaSelection");
  if (selection != null && selection.length >= 7 && series.ordinal() == selection[0]) {
   corner = selection[1];
   continuous = selection[2];
   System.arraycopy(selection, 3, profileIndices, 0, 3);
   sashCorner = selection[6];
   if (selection.length == 10) {
    profileIndices[3] = selection[7];
    profileIndices[4] = selection[8];
    beadCorner = selection[9];
   }
  }
  expectedFixed = in.getInt("temaExpectedFixed", -1);
  int[] sashes = in.getIntArray("temaSashes");
  if (sashes != null) {
   for (int bay : sashes) chosenSashes.add(bay);
  } else {
   for (String item : value("sash").split("[,;\\s]+")) {
    try { chosenSashes.add(Integer.parseInt(item)); } catch (NumberFormatException ignored) { }
   }
  }
  String camera = in.getString("temaCamera"), photo = in.getString("temaDrawing");
  if (camera != null) cameraUri = Uri.parse(camera);
  if (photo != null) loadDrawing(Uri.parse(photo), false);
  if (!in.getBoolean("temaEditing")) return false;
  open();
  return true;
 }

 private String value(String key) { return draft.containsKey(key) ? draft.get(key).trim() : ""; }
 private int mm(String key, String label) { return ShopDimensions.tenths(value(key), label); }
 private int count(String key, String label) { return MainActivity.cutNumber(value(key), label); }

 private String profileCode(int role) {
  String code = TemaCatalog.normalizeCode(value("code" + role));
  if (code.isEmpty()) throw new IllegalArgumentException("Kullanılan her profilin kodunu girin.");
  return code;
 }

 private List<Integer> openings(String key) {
  List<Integer> lengths = new ArrayList<>();
  if (!value(key).isEmpty()) for (String part : value(key).split(";", -1)) {
   lengths.add(ShopDimensions.tenths(part, "Özel net açıklık"));
  }
  return lengths;
 }

 private void calculate() {
  remember();
  if (!confirmed.isChecked()) {
   toast("Önce dış ölçüleri ve seçilen montajı onaylayın.");
   return;
  }
  try {
   TemaJoinery.Order order = new TemaJoinery.Order();
   order.width = mm("width", "Dış en");
   order.height = mm("height", "Dış yükseklik");
   order.quantity = count("qty", "Doğrama adedi");
   if (order.quantity < 1 || order.quantity > 100) throw new IllegalArgumentException("Doğrama adedi 1–100 arasında olmalı.");
   order.frameCode = profileCode(0);
   order.frameFace = mm("face0", "Kasa yüz ölçüsü");
   order.corner = TemaJoinery.Corner.values()[corner];
   order.vertical = recordCount("vertical");
   order.horizontal = recordCount("horizontal");
   if (order.vertical < 0 || order.horizontal < 0) throw new IllegalArgumentException("Kayıt adetlerini 0–12 arasında girin.");
   order.continuous = TemaJoinery.Continuous.values()[continuous];
   boolean regional = !value("regionalBays").isEmpty();
   boolean hasRecords = order.vertical + order.horizontal > 0 || !value("regionalRecords").isEmpty();
   if (hasRecords) {
    order.mullionCode = profileCode(1);
    order.mullionFace = mm("face1", "Orta kayıt yüz ölçüsü");
    order.joint = optionalMm("joint", "Kasa bağlantı farkı", 0);
    order.frameJoint = order.joint;
    order.mullionJoint = optionalMm("mullionJoint", "Orta kayda bağlantı farkı", order.joint);
   }
   order.columnOpenings = openings("columns");
   order.rowOpenings = openings("rows");
   order.openingSashes = new LinkedHashSet<>(chosenSashes);
   int cellCount = regional ? value("regionalBays").split(";").length : (order.vertical + 1) * (order.horizontal + 1);
   if (expectedFixed >= 0 && cellCount - chosenSashes.size() != expectedFixed) {
    bayButton.requestFocus();
    throw new IllegalArgumentException("Fotoğrafta " + expectedFixed + " sabit bölme var. Bölmeleri seçerek yerleşimi doğrulayın.");
   }
   boolean hasSashes = !chosenSashes.isEmpty();
   for(String cell : value("emptyCells").split(";")) if(!cell.trim().isEmpty()) {
    int n=Integer.parseInt(cell.trim()); if(n<1||n>cellCount||chosenSashes.contains(n)) throw new IllegalArgumentException("Boş kasa bölmesini kontrol edin.");
    if(order.emptyCells.isEmpty()) order.emptyCells=new LinkedHashSet<>(); order.emptyCells.add(n);
   }
   boolean hasFixed = chosenSashes.size()+order.emptyCells.size() < cellCount;
   int beadEdge = optionalMm("beadEdge", "Çıta oturma farkı", 0);
   if (hasFixed) order.fixedBead = bead(3, beadEdge);
   if (hasSashes) {
    order.sashCorner = TemaJoinery.Corner.values()[sashCorner];
    order.sashCode = profileCode(2);
    order.sashFace = mm("face2", "Kanat yüz ölçüsü");
    boolean catalogConnection = catalogSash(order, hasRecords);
    if (catalogConnection) order.adapterCode = TemaCatalog.C60_ADAPTER.code;
    if (!catalogConnection && value("sashGap").isEmpty() && value("individualSashGaps").isEmpty()) {
     advancedPanel.setVisibility(View.VISIBLE);
     fields.get("sashGap").requestFocus();
     throw new IllegalArgumentException("Bu kanat birleşiminde ölçülen kenar boşluğunu girin.");
    }
    order.sashGap = optionalMm("sashGap", "Kanat boşluğu", 50);
    for(String entry:value("individualSashGaps").split(";")){try{String[] pair=entry.split(":");order.sashGaps.put(Integer.parseInt(pair[0]),Integer.parseInt(pair[1]));}catch(RuntimeException ignored){}}
    order.sashBead = bead(4, beadEdge);
   }
   order.glassSizesVerified = (!hasFixed || (!value("frameGlass").isEmpty() && (!hasRecords || !value("mullionGlass").isEmpty())))
    && (!hasSashes || !value("sashGlass").isEmpty());
   if (order.glassSizesVerified) {
    if (hasFixed) order.frameGlass = mm("frameGlass", "Sabit cam kasa payı");
    if (hasFixed && hasRecords) order.mullionGlass = mm("mullionGlass", "Sabit cam kayıt payı");
    if (hasSashes) order.sashGlass = mm("sashGlass", "Kanat camı payı");
   }
   if(regional){
    int coordinateScale="1000000".equals(value("regionalScale"))?1000000:1000;
    int iw=order.width-2*order.frameFace,ih=order.height-2*order.frameFace;
    java.util.ArrayList<int[]> lines=new java.util.ArrayList<>();
    for(String entry:value("regionalRecords").split(";")){if(entry.isEmpty())continue;String[] p=entry.split(",");int[] q=new int[5];for(int i=0;i<5;i++)q[i]=Integer.parseInt(p[i]);lines.add(q);}
    order.localRecords=new ArrayList<>();order.localBays=new ArrayList<>();
    for(int[] q:lines){
     boolean v=q[0]==q[2];int start=v?q[1]:q[0],end=v?q[3]:q[2],axis=v?ih:iw;
     int len=(int)Math.round((double)axis*end/coordinateScale)-(int)Math.round((double)axis*start/coordinateScale);
     len+=start==0?order.frameJoint:order.mullionJoint-order.mullionFace/2;
     len+=end==coordinateScale?order.frameJoint:order.mullionJoint-order.mullionFace/2;
     order.localRecords.add(new TemaJoinery.LocalRecord(v,len,series.mullions[q[4]].code));
    }
    int n=0;
    for(String entry:value("regionalBays").split(";")){
     String[] p=entry.split(",");int l=Integer.parseInt(p[0]),t=Integer.parseInt(p[1]),r=Integer.parseInt(p[2]),b=Integer.parseInt(p[3]);n++;
     int lf=0,rf=0,tf=0,bf=0;
     for(int[] q:lines){
      int face=workingFace(series.mullions[q[4]]);
      if(q[0]==q[2] && q[1]<=t && q[3]>=b){
       if(q[0]==l && l!=0)lf=Math.max(lf,face);
       if(q[0]==r && r!=coordinateScale)rf=Math.max(rf,face);
      }
      if(q[1]==q[3] && q[0]<=l && q[2]>=r){
       if(q[1]==t && t!=0)tf=Math.max(tf,face);
       if(q[1]==b && b!=coordinateScale)bf=Math.max(bf,face);
      }
     }
     int w=(int)Math.round((double)iw*r/coordinateScale)-(int)Math.round((double)iw*l/coordinateScale)-(lf-lf/2)-rf/2;
     int h=(int)Math.round((double)ih*b/coordinateScale)-(int)Math.round((double)ih*t/coordinateScale)-(tf-tf/2)-bf/2;
     if(w<=0||h<=0)throw new IllegalArgumentException(n+". bölmenin net kasa açıklığı geçersiz.");
     order.localBays.add(new TemaJoinery.LocalBay(w,h,chosenSashes.contains(n),order.emptyCells.contains(n),l==0?order.frameGlass:order.mullionGlass,r==coordinateScale?order.frameGlass:order.mullionGlass,t==0?order.frameGlass:order.mullionGlass,b==coordinateScale?order.frameGlass:order.mullionGlass));
    }
   }
   TemaJoinery.Plan plan = TemaJoinery.calculate(order);
   TemaJoinery.addManualCuts(plan, value("extraCuts"));
   Bundle history=new Bundle();saveState(history);history.putBoolean("temaEditing",true);app.saveHistory(series.label,history);
   showPlan(order, plan);
  } catch (IllegalArgumentException error) {
   toast(error.getMessage());
  }
 }

 private int optionalMm(String key, String label, int fallback) {
  return value(key).isEmpty() ? fallback : mm(key, label);
 }

 private boolean catalogSash(TemaJoinery.Order order, boolean hasRecords) {
  return series == TemaCatalog.Series.C60 && adapter != null && adapter.isChecked()
   && "T.3101".equals(order.frameCode) && order.frameFace == 490 && order.corner == TemaJoinery.Corner.MITER
   && "T.3105".equals(order.sashCode) && order.sashFace == 660 && order.sashCorner == TemaJoinery.Corner.MITER
   && (!hasRecords || ("T.3101".equals(order.mullionCode) && order.mullionFace == 490));
 }

 private TemaJoinery.Bead bead(int role, int edge) {
  TemaCatalog.Profile profile = series.beads[profileIndices[role]];
  return new TemaJoinery.Bead(profile.code, profile.face, edge, TemaJoinery.Corner.VERTICAL_FULL);
 }

 private void showPlan(TemaJoinery.Order order, TemaJoinery.Plan plan) {
  app.page("Tema " + series.label + " · imalat föyü");
  LinearLayout drawingCard = section("Doğrama görünüşü");
  app.lightPanel(drawingCard);
  drawingCard.addView(new TemaDiagramView(app, order, plan, series.accent, value("regionalBays"), value("leftCells")), new LinearLayout.LayoutParams(-1, app.dp(242)));
  app.planLabel(drawingCard, mm(order.width) + " × " + mm(order.height) + " mm  ·  " + order.quantity + " doğrama", 15, 0xffecf5f7);
  int open = order.openingSashes.size();
  app.planLabel(drawingCard, (plan.panes.size() - open) + " sabit  ·  " + open + " açılır  ·  "
   + order.vertical + " dikey, " + order.horizontal + " yatay kayıt", 14, 0xffa7e9e3);
  LinearLayout profiles = section("Profil ve orta kayıt kesimleri");
  showCuts(profiles, plan, false);
  app.planLabel(profiles, "Kasa köşesi " + order.quantity * 4 + " · Kayıt bağlantı ucu "
   + plan.mullionEnds + " · Kanat köşesi " + open * order.quantity * 4, 14, 0xffa7e9e3);
  LinearLayout beads = section("Cam çıtası kesimleri");
  showCuts(beads, plan, true);
  LinearLayout panes = section(order.glassSizesVerified ? "Net cam ölçüleri" : "Cam açıklıkları");
  if (!order.glassSizesVerified) app.planLabel(panes, "Net cam boyu için Montaj ve stok ayarlarına cam paylarını girin.", 13, 0xffffce83);
  for (TemaJoinery.Pane pane : plan.panes) {
   app.planLabel(panes, "Bölme " + pane.number + (pane.sash ? " · Kanat" : " · Sabit") + "  ·  "
    + mm(pane.width) + " × " + mm(pane.height) + " mm  ·  " + order.quantity + " adet", 15, Color.WHITE);
  }
  app.planLabel(panes, "Toplam " + ShopDimensions.squareMeters(plan.glassArea)
   + " m² · " + plan.panes.size() * order.quantity + " cam", 17, 0xffa7ffdd);
  app.button("Profilleri stoklara yerleştir", () -> optimize(order, plan));
  app.button("Ölçüleri düzenle", this::open);
  app.button("Ana menü", app::home);
 }

 private void showCuts(LinearLayout parent, TemaJoinery.Plan plan, boolean beads) {
  Map<String, List<TemaJoinery.Cut>> groups = new LinkedHashMap<>();
  for (TemaJoinery.Cut cut : plan.cuts) {
   if (cut.role.contains("cam çıtası") != beads) continue;
   List<TemaJoinery.Cut> cuts = groups.get(cut.code);
   if (cuts == null) {
    cuts = new ArrayList<>();
    groups.put(cut.code, cuts);
   }
   cuts.add(cut);
  }
  int index = 0;
  for (Map.Entry<String, List<TemaJoinery.Cut>> group : groups.entrySet()) {
   LinearLayout card = app.planCard(parent, 0xff172b3c, 0xff224655, series.accent);
   TemaCatalog.Profile item = series.find(group.getKey());
   android.graphics.Bitmap bitmap = item == null ? null : BitmapFactory.decodeResource(app.getResources(), item.image);
   app.profileHeading(card, item == null ? group.getKey() : item.label(), bitmap);
   for (TemaJoinery.Cut cut : group.getValue()) {
    String angle = cut.angle == 45 ? "45° gönye · uzun uç" : "90° düz";
    app.planLabel(card, cut.role + "\n" + mm(cut.length) + " mm × " + cut.quantity + " adet  ·  " + angle, 15, Color.WHITE);
   }
   app.reveal(card, Math.min(160, index++ * 35));
  }
 }

 private void optimize(TemaJoinery.Order order, TemaJoinery.Plan plan) {
  try {
   int kerf = mm("kerf", "Testere payı");
   List<Integer> stock = ShopDimensions.stocks(value("stock"));
   Map<String, List<Integer>> pieces = new LinkedHashMap<>();
   Map<String, Map<Integer, ArrayDeque<Integer>>> angles = new LinkedHashMap<>();
   int pieceCount = 0;
   long net = 0, remaining = 0;
   for (TemaJoinery.Cut cut : plan.cuts) {
    pieceCount += cut.quantity;
    if (pieceCount > 3000) throw new IllegalArgumentException("Bir stok planına en fazla 3000 parça yerleştirilebilir.");
    List<Integer> list = pieces.get(cut.code);
    Map<Integer, ArrayDeque<Integer>> byLength = angles.get(cut.code);
    if (list == null) {
     list = new ArrayList<>();
     pieces.put(cut.code, list);
     byLength = new LinkedHashMap<>();
     angles.put(cut.code, byLength);
    }
    ArrayDeque<Integer> ends = byLength.get(cut.length);
    if (ends == null) {
     ends = new ArrayDeque<>();
     byLength.put(cut.length, ends);
    }
    for (int i = 0; i < cut.quantity; i++) {
     list.add(cut.length);
     ends.addLast(cut.angle);
    }
    net += (long) cut.length * cut.quantity;
   }
   int bars = 0, common = -1;
   Map<Integer, Integer> stockCounts = new TreeMap<>();
   Map<String, List<CutPacking.Bar>> packing = new LinkedHashMap<>();
   for (Map.Entry<String, List<Integer>> entry : pieces.entrySet()) {
    List<CutPacking.Bar> result = CutPacking.pack(entry.getValue(), stock, kerf);
    packing.put(entry.getKey(), result);
    bars += result.size();
    for (CutPacking.Bar bar : result) {
     remaining += bar.remaining();
     common = common == -1 || common == bar.stock ? bar.stock : 0;
     Integer previous = stockCounts.get(bar.stock);
     stockCounts.put(bar.stock, previous == null ? 1 : previous + 1);
    }
   }
   app.page("Tema " + series.label + " · stok planı");
   app.planSummary(common, kerf, bars, pieceCount, net, remaining, ShopDimensions.PER_MM);
   StringBuilder summary = new StringBuilder();
   for (Map.Entry<Integer, Integer> entry : stockCounts.entrySet()) {
    if (summary.length() != 0) summary.append("  ·  ");
    summary.append(mm(entry.getKey())).append(" mm × ").append(entry.getValue());
   }
   app.note(summary.toString());
   int animated = 0;
   for (Map.Entry<String, List<CutPacking.Bar>> entry : packing.entrySet()) {
    LinearLayout group = section(entry.getKey());
    int number = 0;
    for (CutPacking.Bar bar : entry.getValue()) {
     app.planBar(group, ++number, bar.pieces, bar.stock, kerf, bar.remaining(), animated++ < 12, ShopDimensions.PER_MM);
     StringBuilder orientations = new StringBuilder();
     for (int length : bar.pieces) {
      if (orientations.length() != 0) orientations.append("  ·  ");
      orientations.append(mm(length)).append(" mm / ").append(angles.get(entry.getKey()).get(length).removeFirst()).append("°");
     }
     app.planLabel(group, orientations.toString(), 13, 0xffc9dbe3);
    }
   }
   app.button("İmalat föyüne dön", () -> showPlan(order, plan));
   app.button("Ölçüleri düzenle", this::open);
   app.button("Ana menü", app::home);
  } catch (IllegalArgumentException error) {
   toast(error.getMessage());
  }
 }

 private void showPhotoCard() {
  while (photoCard.getChildCount() > 1) photoCard.removeViewAt(1);
  chooseButton(photoCard, "Çizim fotoğrafı ekle", () -> new AlertDialog.Builder(app).setTitle("Çizim fotoğrafı")
   .setItems(new String[]{"Galeriden seç", "Kamerayla çek"}, (dialog, index) -> {
    if (index == 1) takePhoto();
    else {
     Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT);
     picker.addCategory(Intent.CATEGORY_OPENABLE);
     picker.setType("image/*");
     picker.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
     app.startActivityForResult(picker, DRAWING_FILE);
    }
   }).show());
  if (drawingReading) {
   app.planLabel(photoCard, "Çizim taranıyor…", 14, series.accent);
   photoCard.addView(new PhotoScanView(app, CutPhotoReader.thumbnail(drawing, 840), null, true),
    new LinearLayout.LayoutParams(-1, app.dp(drawing == null ? 50 : 152)));
   return;
  }
  if (drawing == null) return;
  photoCard.addView(new PhotoScanView(app, CutPhotoReader.thumbnail(drawing, 840), null, false),
   new LinearLayout.LayoutParams(-1, app.dp(168)));
  if (recognized.hasValues()) {
   List<String> details = new ArrayList<>();
   if (recognized.width > 0) details.add("En " + mm(recognized.width) + " mm");
   if (recognized.height > 0) details.add("Yükseklik " + mm(recognized.height) + " mm");
   if (recognized.vertical >= 0) details.add(recognized.vertical + " dikey kayıt");
   if (recognized.horizontal >= 0) details.add(recognized.horizontal + " yatay kayıt");
   if (recognized.fixed >= 0) details.add(recognized.fixed + " sabit");
   app.planLabel(photoCard, android.text.TextUtils.join(" · ", details), 14, series.accent);
   chooseButton(photoCard, "Fotoğrafı çizime aktar", () -> {
    if(recognized.width<=0||recognized.height<=0||recognized.vertical<0||recognized.horizontal<0){
     toast("Eksik ölçü veya kayıt sayısı var; çizimde doğrulayın.");
    }
    new AlertDialog.Builder(app).setTitle("Fotoğraf taslağı").setMessage("Okunan kayıt sayıları eşit aralıklarla çizilir. Gerçek konumları ve kanatları kontrol edin. Mevcut çizim yerine yeni taslak açılır.").setNegativeButton("Vazgeç",null).setPositiveButton("Çizime aktar",(d,k)->drawEditor(recognized)).show();
   });
   chooseButton(photoCard, "Okunan değerleri forma al", () -> applyDimensions(recognized));
  } else {
   app.planLabel(photoCard, "Okunabilir dış ölçü bulunamadı. Çizime en ve yüksekliği yazabilir veya referansla ölçebilirsiniz.", 13, 0xffffce83);
  }
  chooseButton(photoCard, "Referansla çizim üzerinde ölç", this::measurePhoto);
 }

 private void takePhoto() {
  try {
   cameraUri = app.cameraFile();
   app.capturePhoto(cameraUri, DRAWING_CAMERA);
  } catch (java.io.IOException | RuntimeException e) {
   toast("Kamera açılamadı. Çizimi galeriden seçebilirsiniz.");
  }
 }

 boolean handlePhotoResult(int request, int result, Intent data) {
  if (request != DRAWING_FILE && request != DRAWING_CAMERA) return false;
  if (result != Activity.RESULT_OK) return true;
  Uri uri = request == DRAWING_CAMERA ? cameraUri : data == null ? null : data.getData();
  if (uri == null) return true;
  if (request == DRAWING_FILE) {
   try {
    app.getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
   } catch (SecurityException ignored) {
    // The provider may only offer temporary access for the current activity.
   }
  }
  loadDrawing(uri, true);
  return true;
 }

 private void loadDrawing(Uri uri, boolean announce) {
  int requestId = ++photoRequest;
  drawingReading = true;
  if (photoCard != null && app.body == form) showPhotoCard();
  new Thread(() -> {
   Bitmap image;
   try {
    if (Build.VERSION.SDK_INT >= 28) {
     image = ImageDecoder.decodeBitmap(ImageDecoder.createSource(app.getContentResolver(), uri), (decoder, info, source) -> {
      decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
      int width = info.getSize().getWidth(), height = info.getSize().getHeight();
      float scale = Math.min(1f, 2400f / Math.max(width, height));
      decoder.setTargetSize(Math.max(1, Math.round(width * scale)), Math.max(1, Math.round(height * scale)));
     });
    } else {
     BitmapFactory.Options options = new BitmapFactory.Options();
     options.inJustDecodeBounds = true;
     try (java.io.InputStream stream = app.getContentResolver().openInputStream(uri)) {
      BitmapFactory.decodeStream(stream, null, options);
     }
     options.inSampleSize = 1;
     while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > 2400) {
      options.inSampleSize *= 2;
     }
     options.inJustDecodeBounds = false;
     try (java.io.InputStream stream = app.getContentResolver().openInputStream(uri)) {
      image = BitmapFactory.decodeStream(stream, null, options);
     }
    }
    if (image == null) throw new java.io.IOException("Çizim boş");
   } catch (java.io.IOException | RuntimeException e) {
    app.runOnUiThread(() -> {
     if (app.isDestroyed() || requestId != photoRequest) return;
     drawingReading = false;
     if (photoCard != null && app.body == form) showPhotoCard();
     if (announce) toast("Çizim fotoğrafı açılamadı.");
    });
    return;
   }
   app.runOnUiThread(() -> {
    if (app.isDestroyed() || requestId != photoRequest) return;
    drawing = image;
    drawingUri = uri;
    if (photoCard != null && app.body == form) showPhotoCard();
   });
   DrawingDimensions read = new DrawingDimensions(-1, -1);
   try (TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)) {
    read = DrawingPhotoReader.scan(image, recognizer);
   } catch (InterruptedException e) {
    Thread.currentThread().interrupt();
   } catch (java.util.concurrent.ExecutionException | RuntimeException ignored) {
    // Manual reference measurement remains available for the loaded drawing.
   }
   DrawingDimensions resultRead = read;
   app.runOnUiThread(() -> {
    if (app.isDestroyed() || requestId != photoRequest) return;
    drawing = image;
    drawingUri = uri;
    drawingReading = false;
    recognized = resultRead;
    if (photoCard != null && app.body == form) showPhotoCard();
   });
  }, "tema-drawing").start();
 }

 private void measurePhoto() {
  LinearLayout content = new LinearLayout(app);
  content.setOrientation(LinearLayout.VERTICAL);
  content.setPadding(app.dp(14), app.dp(5), app.dp(14), app.dp(12));
  ScrollView scroll = new ScrollView(app);
  scroll.addView(content);
  TextView step = new TextView(app);
  step.setText("1–2 · Ölçüsünü bildiğiniz referansın iki ucunu seçin.");
  content.addView(step);
  DrawingMeasureView[] view = new DrawingMeasureView[1];
  view[0] = new DrawingMeasureView(app, drawing, () -> {
   int count = view[0].pointCount();
   step.setText(count < 2 ? "1–2 · Referans uçları" : count < 4 ? "3–4 · Dış en uçları"
    : count < 6 ? "5–6 · Dış yükseklik uçları" : "Altı nokta seçildi. Ölçüyü önizleyin.");
  });
  content.addView(view[0], new LinearLayout.LayoutParams(-1, app.dp(290)));
  Button undo = new Button(app);
  undo.setText("Son noktayı geri al");
  undo.setOnClickListener(v -> view[0].undo());
  content.addView(undo);
  EditText reference = new EditText(app);
  reference.setInputType(DECIMAL);
  reference.setHint("Gerçek referans boyu (mm)");
  content.addView(reference);
  Button useCatalog = new Button(app);
  TemaCatalog.Profile[] choices = {series.frames[profileIndices[0]], series.mullions[profileIndices[1]], series.sashes[profileIndices[2]]};
  useCatalog.setText("Katalogdan referans ölçüsü seç");
  content.addView(useCatalog);
  useCatalog.setOnClickListener(v -> choose("İşaretlediğiniz profil yüzü",
   new String[]{"Kasa · " + choices[0].label(), "Kayıt · " + choices[1].label(), "Kanat · " + choices[2].label()},
   -1, index -> reference.setText(mm(choices[index].face))));
  CheckBox straight = new CheckBox(app);
  straight.setText("Çizim ölçekli ve karşıdan çekilmiş. Referans, ölçülen kasa ile aynı düzlemde.");
  content.addView(straight);
  AlertDialog dialog = new AlertDialog.Builder(app).setTitle("Referansla ölçüm")
   .setView(scroll).setNegativeButton("Vazgeç", null).setPositiveButton("Önizle", null).create();
  dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button -> {
   try {
    if (!straight.isChecked()) throw new IllegalArgumentException("Çizimin ölçeğini ve referans düzlemini doğrulayın.");
    int known = ShopDimensions.tenths(reference.getText().toString(), "Referans boyu");
    DrawingDimensions measured = view[0].measurements(known);
    new AlertDialog.Builder(app).setTitle("Dış kasa ölçüsünü kontrol edin")
     .setMessage("En " + mm(measured.width) + " mm\nYükseklik " + mm(measured.height)
      + " mm\nFotoğraf ölçümünü imalat öncesi fiziksel ölçüyle doğrulayın.")
     .setNegativeButton("Geri", null).setPositiveButton("Forma al", (preview, which) -> {
      applyDimensions(measured);
      dialog.dismiss();
     }).show();
   } catch (IllegalArgumentException error) {
    toast(error.getMessage());
   }
  }));
  dialog.show();
 }

 private void applyDimensions(DrawingDimensions size) {
  if (size.width > 0) fields.get("width").setText(mm(size.width));
  if (size.height > 0) fields.get("height").setText(mm(size.height));
  if (size.vertical >= 0 && recordCount("vertical") != size.vertical) fields.get("vertical").setText(String.valueOf(size.vertical));
  if (size.horizontal >= 0 && recordCount("horizontal") != size.horizontal) fields.get("horizontal").setText(String.valueOf(size.horizontal));
  if (size.fixed >= 0) {
   int cellCount = (recordCount("vertical") + 1) * (recordCount("horizontal") + 1);
   expectedFixed = size.fixed;
   if (size.fixed == cellCount) chosenSashes.clear();
   if (size.fixed == 0) for (int cell = 1; cell <= cellCount; cell++) chosenSashes.add(cell);
  }
  updateLayout();
  invalidate();
 }

 private void toast(String message) { Toast.makeText(app, message, Toast.LENGTH_LONG).show(); }
 private static String mm(int value) { return ShopDimensions.mm(value); }
}
