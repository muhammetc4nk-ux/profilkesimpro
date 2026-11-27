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
  choiceControl(advancedPanel, "Çıta köşesi", corners, beadCorner, i -> beadCorner = i);
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

 private void drawEditor() {
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
  String[] names = {"Kasa","Orta kayıt","Kanat"};
  for(int i=0;i<3;i++){
   final int role=i;
   Button b=new Button(app); b.setAllCaps(false); b.setText(names[i]+" · "+groups[i][picks[i]].label()); panel.addView(b);
   b.setOnClickListener(v->chooseProfile(names[role],groups[role],picks[role],index->{picks[role]=index;b.setText(names[role]+" · "+groups[role][index].label());}));
  }
  java.util.TreeSet<Integer> xs=new java.util.TreeSet<>(), ys=new java.util.TreeSet<>();
  java.util.Set<Integer> openings=new java.util.HashSet<>();
  java.util.Set<Integer> leftCells=new java.util.HashSet<>(), emptyCells=new java.util.HashSet<>();
  int[] mode={0};
  boolean[] frameAdded={false};
  TextView drawingStatus=new TextView(app);drawingStatus.setText("Önce kasa ekleyin.");panel.addView(drawingStatus);
  EditText jointEdit=new EditText(app); jointEdit.setInputType(SIGNED); jointEdit.setText(fields.get("joint").getText());
  app.planLabel(panel,"Kasa bağlantı payı (mm/uç)",14,Color.DKGRAY);panel.addView(jointEdit);
  EditText recordJoint=new EditText(app);recordJoint.setInputType(SIGNED);recordJoint.setText(fields.get("mullionJoint").getText());panel.addView(recordJoint);recordJoint.setHint("Orta kayda bağlantı payı (mm/uç)");
  EditText sashGapEdit=new EditText(app);sashGapEdit.setInputType(SIGNED);sashGapEdit.setText(fields.get("sashGap").getText());
  app.planLabel(panel,"Kanat çalışma boşluğu (mm/kenar)",14,Color.DKGRAY);panel.addView(sashGapEdit);
  android.view.View board=new android.view.View(app){
   android.graphics.Paint paint=new android.graphics.Paint(3);
   float left=24,top=24,right,bottom;
   @Override protected void onDraw(android.graphics.Canvas c){
    super.onDraw(c); right=getWidth()-24; bottom=getHeight()-24;
    c.drawColor(0xffe8f1f5); paint.setColor(0xffcbd9e1);paint.setStrokeWidth(1);for(int i=0;i<20;i++){float px=left+(right-left)*i/20f,py=top+(bottom-top)*i/20f;c.drawLine(px,top,px,bottom,paint);c.drawLine(left,py,right,py,paint);} paint.setColor(0xff34495e); paint.setStrokeWidth(7);paint.setStyle(android.graphics.Paint.Style.STROKE);
    if(!frameAdded[0])return;
    c.drawRect(left,top,right,bottom,paint);
    paint.setStyle(android.graphics.Paint.Style.FILL);paint.setTextSize(14);c.drawText(sizes[0].getText()+" mm",left+10,18,paint);c.drawText(sizes[1].getText()+" mm",left+10,bottom+18,paint);paint.setStyle(android.graphics.Paint.Style.STROKE);
    paint.setColor(0xff1565c0);paint.setStrokeWidth(9);
    for(int x:xs){float px=left+(right-left)*x/1000f;c.drawLine(px,top,px,bottom,paint);}
    for(int y:ys){float py=top+(bottom-top)*y/1000f;c.drawLine(left,py,right,py,paint);}
    java.util.ArrayList<Integer> a=new java.util.ArrayList<>(xs),b=new java.util.ArrayList<>(ys);a.add(0,0);a.add(1000);b.add(0,0);b.add(1000);
    paint.setColor(0xff008577);paint.setStrokeWidth(3);
    for(int y=0;y<b.size()-1;y++)for(int x=0;x<a.size()-1;x++)if(openings.contains(y*(a.size()-1)+x+1)){
     float l=left+(right-left)*a.get(x)/1000f+7,r=left+(right-left)*a.get(x+1)/1000f-7,t=top+(bottom-top)*b.get(y)/1000f+7,d=top+(bottom-top)*b.get(y+1)/1000f-7;
     c.drawRect(l,t,r,d,paint);boolean isLeft=leftCells.contains(y*(a.size()-1)+x+1);float hinge=isLeft?l:r,handle=isLeft?r:l;c.drawLine(hinge,t,handle,(t+d)/2,paint);c.drawLine(handle,(t+d)/2,hinge,d,paint);
    }
   }
   @Override public boolean onTouchEvent(android.view.MotionEvent e){
    if(e.getActionMasked()==android.view.MotionEvent.ACTION_DOWN){getParent().requestDisallowInterceptTouchEvent(true);return true;}
    if(e.getActionMasked()==android.view.MotionEvent.ACTION_CANCEL){getParent().requestDisallowInterceptTouchEvent(false);return true;}
    if(e.getActionMasked()!=android.view.MotionEvent.ACTION_UP)return true;
    getParent().requestDisallowInterceptTouchEvent(false);
    int x=Math.round(1000*(e.getX()-left)/(right-left)),y=Math.round(1000*(e.getY()-top)/(bottom-top));
    if(!frameAdded[0]){toast("Önce kasa ekleyin.");return true;}
    if(x<=0||x>=1000||y<=0||y>=1000)return true;
    if(mode[0]==0){if(xs.size()<12)xs.add(x);openings.clear();leftCells.clear();emptyCells.clear();}
    else if(mode[0]==1){if(ys.size()<12)ys.add(y);openings.clear();leftCells.clear();emptyCells.clear();}
    else if(mode[0]==3){Integer nearest=null;int distance=60;boolean vertical=true;for(int q:xs)if(Math.abs(q-x)<distance){nearest=q;distance=Math.abs(q-x);vertical=true;}for(int q:ys)if(Math.abs(q-y)<distance){nearest=q;distance=Math.abs(q-y);vertical=false;}if(nearest!=null){if(vertical)xs.remove(nearest);else ys.remove(nearest);openings.clear();leftCells.clear();emptyCells.clear();}}
    else {int cell=ys.headSet(y).size()*(xs.size()+1)+xs.headSet(x).size()+1;if(mode[0]==4){openings.remove(cell);leftCells.remove(cell);emptyCells.add(cell);}else if(mode[0]==5){openings.remove(cell);leftCells.remove(cell);emptyCells.remove(cell);}else{emptyCells.remove(cell);openings.add(cell);if(mode[0]==2)leftCells.add(cell);else leftCells.remove(cell);}}
    drawingStatus.setText("Kasa: 1 | Dikey: "+xs.size()+" | Yatay: "+ys.size()+" | Kanat: "+openings.size());invalidate();return true;
   }
  };
  Button frameButton=new Button(app);frameButton.setText("+ Kasa ekle");panel.addView(frameButton);frameButton.setOnClickListener(v->{frameAdded[0]=true;drawingStatus.setText("Kasa eklendi: "+groups[0][picks[0]].label());board.invalidate();});
  Button sashButton=new Button(app);sashButton.setText("+ Kanat ekle (ilk boş bölme)");panel.addView(sashButton);sashButton.setOnClickListener(v->{if(!frameAdded[0]){toast("Önce kasa ekleyin.");return;}int count=(xs.size()+1)*(ys.size()+1);for(int i=1;i<=count;i++)if(openings.add(i)){drawingStatus.setText("Kanat: "+openings.size()+" / "+count);board.invalidate();return;}toast("Tüm bölmelerde kanat var.");});
  String[] modes={"Dikey kayıt ekle","Yatay kayıt ekle","Sol menteşeli kanat","Profil sil","Boş kasa","Sabit cam","Sağ menteşeli kanat"};
  for(int i=0;i<modes.length;i++){final int m=i;Button b=new Button(app);b.setText(modes[i]);panel.addView(b);b.setOnClickListener(v->{mode[0]=m;toast(modes[m]);});}
  panel.addView(board,new LinearLayout.LayoutParams(-1,app.dp(320)));
  Button addV=new Button(app);addV.setText("+ Dikey kayıt (eşit böl)");panel.addView(addV);addV.setOnClickListener(v->{if(!frameAdded[0]){toast("Önce kasa ekleyin.");return;}if(xs.size()>=12)return;int n=xs.size()+2;xs.clear();for(int i=1;i<n;i++)xs.add(Math.round(1000f*i/n));openings.clear();leftCells.clear();emptyCells.clear();drawingStatus.setText("Dikey: "+xs.size()+" | Yatay: "+ys.size());board.invalidate();});
  Button addH=new Button(app);addH.setText("+ Yatay kayıt (eşit böl)");panel.addView(addH);addH.setOnClickListener(v->{if(!frameAdded[0]){toast("Önce kasa ekleyin.");return;}if(ys.size()>=12)return;int n=ys.size()+2;ys.clear();for(int i=1;i<n;i++)ys.add(Math.round(1000f*i/n));openings.clear();leftCells.clear();emptyCells.clear();board.invalidate();});
  for(EditText size:sizes)size.addTextChangedListener(watcher(board::invalidate));
  Button exact=new Button(app);exact.setText("Kayıt konumunu mm olarak gir");panel.addView(exact);exact.setOnClickListener(v->{if(!frameAdded[0]){toast("Önce kasa ekleyin.");return;}EditText pos=new EditText(app);pos.setInputType(DECIMAL);new AlertDialog.Builder(app).setTitle("Kasa içi net konum (mm)").setView(pos).setNegativeButton("Vazgeç",null).setPositiveButton("Ekle",(d,k)->{try{boolean vertical=mode[0]!=1;int total=ShopDimensions.tenths(sizes[vertical?0:1].getText().toString(),"Dış ölçü")-2*workingFace(groups[0][picks[0]]);int point=Math.round(1000f*ShopDimensions.tenths(pos.getText().toString(),"Konum")/total);java.util.TreeSet<Integer> target=vertical?xs:ys;if(point<=0||point>=1000||target.size()>=12)throw new IllegalArgumentException("Konum kasa içinde olmalı.");target.add(point);openings.clear();leftCells.clear();emptyCells.clear();board.invalidate();}catch(IllegalArgumentException ex){toast(ex.getMessage());}}).show();});
  Button netSizes=new Button(app);netSizes.setText("Kasa içi net en ve boy gir");panel.addView(netSizes);
  netSizes.setOnClickListener(v->{
   LinearLayout p=new LinearLayout(app);p.setOrientation(LinearLayout.VERTICAL);
   EditText nw=new EditText(app),nh=new EditText(app);nw.setInputType(DECIMAL);nh.setInputType(DECIMAL);nw.setHint("Net en (mm)");nh.setHint("Net boy (mm)");p.addView(nw);p.addView(nh);
   new AlertDialog.Builder(app).setTitle("Kasa içi net açıklık").setView(p).setNegativeButton("Vazgeç",null).setPositiveButton("Uygula",(d,k)->{try{
    if(!xs.isEmpty()||!ys.isEmpty())throw new IllegalArgumentException("Bölmeli kasada dış ölçüleri kullanın.");
    int w=ShopDimensions.tenths(nw.getText().toString(),"Net en"),h=ShopDimensions.tenths(nh.getText().toString(),"Net boy");
    if(w<=0||h<=0)throw new IllegalArgumentException("Net ölçüler pozitif olmalı.");
    int face=workingFace(groups[0][picks[0]]);sizes[0].setText(mm(w+2*face));sizes[1].setText(mm(h+2*face));frameAdded[0]=true;board.invalidate();
   }catch(IllegalArgumentException ex){toast(ex.getMessage());}}).show();
  });
  Button clear=new Button(app);clear.setText("Çizimi temizle");panel.addView(clear);clear.setOnClickListener(v->{frameAdded[0]=false;xs.clear();ys.clear();openings.clear();leftCells.clear();emptyCells.clear();board.invalidate();});
  AlertDialog dialog=new AlertDialog.Builder(app).setTitle("Kendin çiz · "+series.label).setView(scroll).setNegativeButton("Vazgeç",null).setPositiveButton("Kesim hesapla",null).create();
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
    chosenSashes.clear();chosenSashes.addAll(openings);expectedFixed=-1;updateLayout();invalidate();Bundle history=new Bundle();saveState(history);history.putBoolean("temaEditing",true);app.saveHistory(series.label,history);dialog.dismiss();
    toast("Çizim aktarıldı. Montaj ve fiş paylarını kontrol edip hesaplayın.");
   }catch(IllegalArgumentException ex){toast(ex.getMessage());}
  }));dialog.show();
 }

 private String cellList(java.util.Set<Integer> cells){StringBuilder b=new StringBuilder();for(int n:cells){if(b.length()>0)b.append(";");b.append(n);}return b.toString();}

 private String drawSpans(java.util.TreeSet<Integer> points,int total){
  if(total<=0)throw new IllegalArgumentException("Profil payları dış ölçüyü aşıyor.");
  StringBuilder result=new StringBuilder();int previous=0;
  java.util.ArrayList<Integer> ends=new java.util.ArrayList<>(points);ends.add(1000);
  for(int end:ends){int next=Math.round(total*end/1000f);if(next<=previous)throw new IllegalArgumentException("Bölme çok dar.");if(result.length()>0)result.append(";");result.append(mm(next-previous));previous=next;}
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
   boolean hasRecords = order.vertical + order.horizontal > 0;
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
   int cellCount = (order.vertical + 1) * (order.horizontal + 1);
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
    if (!catalogConnection && value("sashGap").isEmpty()) {
     advancedPanel.setVisibility(View.VISIBLE);
     fields.get("sashGap").requestFocus();
     throw new IllegalArgumentException("Bu kanat birleşiminde ölçülen kenar boşluğunu girin.");
    }
    order.sashGap = optionalMm("sashGap", "Kanat boşluğu", 50);
    order.sashBead = bead(4, beadEdge);
   }
   order.glassSizesVerified = (!hasFixed || (!value("frameGlass").isEmpty() && (!hasRecords || !value("mullionGlass").isEmpty())))
    && (!hasSashes || !value("sashGlass").isEmpty());
   if (order.glassSizesVerified) {
    if (hasFixed) order.frameGlass = mm("frameGlass", "Sabit cam kasa payı");
    if (hasFixed && hasRecords) order.mullionGlass = mm("mullionGlass", "Sabit cam kayıt payı");
    if (hasSashes) order.sashGlass = mm("sashGlass", "Kanat camı payı");
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
  return new TemaJoinery.Bead(profile.code, profile.face, edge, TemaJoinery.Corner.values()[beadCorner]);
 }

 private void showPlan(TemaJoinery.Order order, TemaJoinery.Plan plan) {
  app.page("Tema " + series.label + " · imalat föyü");
  LinearLayout drawingCard = section("Doğrama görünüşü");
  app.lightPanel(drawingCard);
  drawingCard.addView(new TemaDiagramView(app, order, plan, series.accent), new LinearLayout.LayoutParams(-1, app.dp(242)));
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
