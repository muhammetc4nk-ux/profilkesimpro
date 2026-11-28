package com.profilkesim.pro;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
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
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;

final class TemaWorkshop {
 private static final int DRAWING_FILE = 46, DRAWING_CAMERA = 47;
 private static final int DRAWING_SCALE = 1_000_000;
 private static final String[] FRAME_EDGES = {"frameLeft", "frameTop", "frameRight", "frameBottom"};
 private static final int DECIMAL = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL;
 private static final int SIGNED = DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED;
 private final MainActivity app;
 private final Map<String, String> draft = new LinkedHashMap<>();
 private final Map<String, EditText> fields = new LinkedHashMap<>();
 private final int[] profileIndices = new int[5];
 private final TemaCatalog.Series series;
 private final Set<Integer> chosenSashes = new LinkedHashSet<>();
 private final List<TemaPosition> positions = new ArrayList<>();
 private final SharedPreferences preferences;
 private LinearLayout photoCard, form;
 private CheckBox adapter;
 private TemaPosition activePosition;
 private Runnable saveOpenEditor;
 private String stockText, kerfText;
 private boolean drawingReading;
 private int corner, sashCorner, continuous, photoRequest;
 private Uri cameraUri;
 private Uri drawingUri;
 private Bitmap drawing;
 private DrawingDimensions recognized = new DrawingDimensions(-1, -1);

 TemaWorkshop(MainActivity app, TemaCatalog.Series series) {
  this.app = app;
  this.series = series;
  preferences = app.getPreferences(Activity.MODE_PRIVATE);
  stockText = preferences.getString(batchKey("stock"), "6000");
  kerfText = preferences.getString(batchKey("kerf"), String.valueOf(MainActivity.DEFAULT_KERF));
  loadPositions();
  selectDefaultProfiles();
 }

 private String batchKey(String suffix) { return "tema.batch." + series.name() + "." + suffix; }

 private void selectDefaultProfiles() {
  TemaCatalog.Profile[][] groups = {series.frames, series.mullions, series.sashes, series.beads, series.beads};
  for (int i = 0; i < groups.length; i++) {
   profileIndices[i] = 0;
   String saved = preferences.getString(preference(i), "");
   for (int j = 0; j < groups[i].length; j++) if (groups[i][j].code.equals(saved)) profileIndices[i] = j;
  }
 }

 private static TemaLayout.Section section(TemaCatalog.Profile profile) {
  return new TemaLayout.Section(profile.code, profile.face);
 }

 private TemaLayout.Frame frame(TemaLayout.Section fallback, Map<String,Integer> selections) {
  TemaLayout.Section[] sides = new TemaLayout.Section[4];
  for (int i = 0; i < sides.length; i++) {
   Integer index = selections.get(FRAME_EDGES[i]);
   if (index == null) index = selections.get("frame");
   sides[i] = index == null ? fallback : section(series.frames[index]);
  }
  return new TemaLayout.Frame(sides[0], sides[1], sides[2], sides[3]);
 }

 private List<TemaLayout.Record> layoutRecords(List<int[]> lines) {
  return layoutRecords(lines, null);
 }

 private List<TemaLayout.Record> layoutRecords(List<int[]> lines, TemaLayout.Section defaultProfile) {
  List<TemaLayout.Record> records = new ArrayList<>();
  for (int[] line : lines) {
   TemaLayout.Section profile = line[4] == -1 ? defaultProfile
    : line[4] >= 0 && line[4] < series.mullions.length ? section(series.mullions[line[4]]) : null;
   if (profile == null) throw new IllegalArgumentException("Kayıt profilini yeniden seçin.");
   records.add(new TemaLayout.Record(line[0], line[1], line[2], line[3], profile));
  }
  return records;
 }

 private void migrateCatalogueFaces() {
  if (series == TemaCatalog.Series.ALDOKS && !draft.containsKey("catalogueFaces")) {
   if ("T.3004".equals(value("code0")) && "58,5".equals(value("face0"))) draft.put("face0", "42");
   if ("T.3002".equals(value("code2")) && "60".equals(value("face2"))) draft.put("face2", "59");
   draft.put("catalogueFaces", "1");
  }
  if (series == TemaCatalog.Series.ALDOKS && "T.3005".equals(value("code2"))) {
   draft.put("face2", mm(series.find("T.3005").face));
  }
 }

 private String preference(int role) { return "tema.profile." + series.name() + "." + role; }

 void open() {
  app.page("Tema " + series.label + " · İmalat");
  form = app.body;
  LinearLayout material = section("Stok ve testere");
  LinearLayout row = horizontal(material);
  EditText stock = app.input(column(row), "Stok boyu (mm)", stockText);
  EditText kerf = app.input(column(row), "Testere payı (mm/ara)", kerfText);
  stock.setInputType(DECIMAL);
  kerf.setInputType(DECIMAL);
  Runnable save = () -> {
   stockText = stock.getText().toString();
   kerfText = kerf.getText().toString();
   preferences.edit().putString(batchKey("stock"), stockText).putString(batchKey("kerf"), kerfText).apply();
  };
  stock.addTextChangedListener(watcher(save));
  kerf.addTextChangedListener(watcher(save));

  LinearLayout jobs = section("Doğrama pozları");
  chooseButton(jobs, "+ Yeni poz çiz", () -> newPosition(null));
  chooseButton(jobs, "Kesim listesi tara", () -> app.openTemaCutReview(series, this::acceptScannedCuts, this::open));
  if (positions.isEmpty()) app.planLabel(jobs, "Poz ekleyerek çizime başlayın veya bir kesim föyü tarayın.", 14, 0xffc9dbe3);
  for (TemaPosition position : positions) {
   LinearLayout card = app.planCard(jobs, 0xff183447, 0xff214951, series.accent);
   String state = position.cuts.isEmpty() ? "Çizim taslağı" : position.drawing == null ? "Onaylı tarama" : "Onaylı çizim";
   app.planLabel(card, position.name + " · " + state, 16, Color.WHITE)
    .setTypeface(null, android.graphics.Typeface.BOLD);
   if (!position.source.isEmpty()) app.planLabel(card, position.source, 13, 0xffc9dbe3);
   LinearLayout actions = horizontal(card);
   if (position.drawing != null || position.cuts.isEmpty()) {
    chooseButton(column(actions), "Çizimi aç", () -> editPosition(position, null));
   }
   if (!position.cuts.isEmpty()) chooseButton(column(actions), "Föyü aç", () -> openPositionSheet(position));
   chooseButton(column(actions), "Poz ayarları", () -> positionOptions(position));
   if (position.drawing != null) chooseButton(card, "Çizimi PDF al", () -> exportSavedDrawing(position));
  }
  if (!readyCuts().isEmpty()) app.button("Birleşik stok planını aç", () -> openOptimization(null));
  photoCard = section("Çizim dosyasından ölçü al");
  showPhotoCard();
  app.button("Ana menü", app::home);
 }

 private void buildAdvanced(LinearLayout panel) {
  chooseButton(panel, "Tema katalog PDF’ini aç", () -> openUrl(series.url));
  String[] corners = {"45° gönye · uzun uç", "90° düz · dikeyler tam boy", "90° düz · yataylar tam boy"};
  choiceControl(panel, "Kasa köşesi", corners, corner, i -> corner = i);
  choiceControl(panel, "Kanat köşesi", corners, sashCorner, i -> sashCorner = i);
  choiceControl(panel, "Kayıt birleşimi", new String[]{"Dikey kayıtlar sürekli", "Yatay kayıtlar sürekli"},
   continuous, i -> continuous = i);
  if (series == TemaCatalog.Series.ALDOKS) {
   chooseBead(panel, 3, "Sabit cam çıtası");
   chooseBead(panel, 4, "Kanat cam çıtası");
  }
  if (series == TemaCatalog.Series.C60) {
   adapter = new CheckBox(app);
   adapter.setText(TemaCatalog.C60_ADAPTER.label());
   adapter.setTextColor(0xffeefbff);
   adapter.setChecked(!"0".equals(value("adapter")));
   panel.addView(adapter);
  }
  EditText extra = field(panel, "extraCuts", "Ek kesimler · kod;ölçü;adet;açı", "", InputType.TYPE_CLASS_TEXT);
  extra.setSingleLine(false);
  extra.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
 }

 private void chooseBead(LinearLayout panel, int role, String label) {
  String[] choices = new String[series.beads.length];
  for (int i = 0; i < choices.length; i++) choices[i] = series.beads[i].label();
  choiceControl(panel, label, choices, profileIndices[role], index -> profileIndices[role] = index);
 }

 private void choiceControl(LinearLayout parent, String name, String[] choices, int initial, Option selected) {
  Button button = chooseButton(parent, name + " · " + choices[initial], null);
  int[] current = {initial};
  button.setOnClickListener(v -> choose(name, choices, current[0], index -> {
   current[0] = index;
   selected.selected(index);
   button.setText(name + " · " + choices[index]);
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

 private static TextWatcher watcher(Runnable action) {
  return new TextWatcher() {
   @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
   @Override public void onTextChanged(CharSequence s, int start, int before, int count) { action.run(); }
   @Override public void afterTextChanged(Editable s) {}
  };
 }

 private void drawEditor(DrawingDimensions imported) {
  try { openDrawEditor(imported); } catch (RuntimeException ex) { android.util.Log.e("TemaWorkshop", "Drawing editor initialization", ex); toast("Çizim açılamadı: "+ex.getClass().getSimpleName()); }
 }
 private void openDrawEditor(DrawingDimensions imported) {
  TemaPosition editingPosition = activePosition;
  ScrollView scroll = new ScrollView(app);
  LinearLayout panel = new LinearLayout(app);
  panel.setOrientation(LinearLayout.VERTICAL);
  panel.setPadding(app.dp(12), app.dp(8), app.dp(12), app.dp(8));
  scroll.addView(panel);
  EditText[] sizes = new EditText[2];
  String[] sizeKeys = {"width", "height"};
  LinearLayout dimensions = new LinearLayout(app);
  for (int i = 0; i < 2; i++) {
   LinearLayout cell = column(dimensions);
   sizes[i] = app.input(cell, i == 0 ? "Dış en (mm)" : "Dış boy (mm)", value(sizeKeys[i]));
   sizes[i].setInputType(DECIMAL);
   sizes[i].setSingleLine(true);
  }
  field(column(dimensions), "qty", "Adet", "1", InputType.TYPE_CLASS_NUMBER);
  TemaCatalog.Profile[][] groups = {series.frames,series.mullions,series.sashes};
  Map<String,Integer> lineProfiles = new LinkedHashMap<>();
  int[] picks = profileIndices.clone();
  Runnable[] refreshDrawing={()->{}};
  Runnable[] beforeProfileChange={()->{}};
  Button[] profileButtons=new Button[3];
  String[] names = {"Kasa","Orta kayıt","Kanat"};
  for(int i=0;i<3;i++){
   final int role=i;
   Button b=new Button(app); b.setAllCaps(false); b.setText(names[i]+" · "+groups[i][picks[i]].label()); panel.addView(b);profileButtons[i]=b;
   b.setOnClickListener(v->chooseProfile(names[role],groups[role],picks[role],index->{beforeProfileChange[0].run();picks[role]=index;if(role==0)lineProfiles.remove("frame");b.setText(names[role]+" · "+groups[role][index].label());refreshDrawing[0].run();}));
  }
  java.util.TreeSet<Integer> xs=new java.util.TreeSet<>(), ys=new java.util.TreeSet<>();
  java.util.Set<Integer> openings=new java.util.HashSet<>();
  java.util.Set<Integer> leftCells=new java.util.HashSet<>(), emptyCells=new java.util.HashSet<>();
  java.util.ArrayDeque<Runnable> undo=new java.util.ArrayDeque<>();
  java.util.ArrayList<int[]> localLines=new java.util.ArrayList<>();
  java.util.function.Supplier<java.util.ArrayList<int[]>> bays = () ->
   TemaRecordPlacement.bays(xs, ys, localLines, DRAWING_SCALE);
  MainActivity.PhotoResult<List<int[]>> keepBays = before ->
   retainBays(before, bays.get(), openings, leftCells, emptyCells, lineProfiles);
  java.util.function.BiFunction<Integer,Integer,Integer> hit=(x,y)->{java.util.ArrayList<int[]> cells=bays.get();for(int i=0;i<cells.size();i++){int[] z=cells.get(i);if(x>=z[0]&&x<=z[2]&&y>=z[1]&&y<=z[3])return i+1;}return 0;};
  int[] mode={8};
  int[] placementProfile={0};
  TemaRecordPlacement.Start[] pendingRecord = {null};
  boolean[] frameAdded={editingPosition.drawing != null};
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
   org.json.JSONArray a=saved.getJSONArray("picks");for(int i=0;i<3;i++)picks[i]=a.getInt(i);
   for(int i=0;i<2;i++)sizes[i].setText(saved.getString(sizeKeys[i]));
  }catch(org.json.JSONException | RuntimeException ignored){}

  if (editingPosition.drawing != null && value("editorDrawing").isEmpty()) {
   restoreLegacyGrid(xs, ys, openings, leftCells, emptyCells, localLines, lineProfiles);
  }

  for(int i=0;i<3;i++){ if(picks[i]<0||picks[i]>=groups[i].length)picks[i]=0; profileButtons[i].setText(names[i]+" · "+groups[i][picks[i]].label()); }
  lineProfiles.entrySet().removeIf(e->{int role=e.getKey().startsWith("frame")?0:e.getKey().startsWith("s")?2:1;return e.getValue()<0||e.getValue()>=groups[role].length;});
  localLines.removeIf(q->q[4]<0||q[4]>=groups[1].length);
  if(imported!=null){
   frameAdded[0]=true;xs.clear();ys.clear();localLines.clear();openings.clear();leftCells.clear();emptyCells.clear();lineProfiles.clear();
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
   java.util.ArrayList<int[]> oldLines=new java.util.ArrayList<>();for(int[] q:localLines)oldLines.add(q.clone());
   boolean oldFrame=frameAdded[0];int[] oldPicks=picks.clone();
   String[] oldSizes={sizes[0].getText().toString(),sizes[1].getText().toString()};
   return ()->{xs.clear();xs.addAll(oldX);ys.clear();ys.addAll(oldY);openings.clear();openings.addAll(oldO);leftCells.clear();leftCells.addAll(oldL);emptyCells.clear();emptyCells.addAll(oldE);lineProfiles.clear();lineProfiles.putAll(oldP);localLines.clear();localLines.addAll(oldLines);frameAdded[0]=oldFrame;System.arraycopy(oldPicks,0,picks,0,picks.length);for(int i=0;i<3;i++)profileButtons[i].setText(names[i]+" · "+groups[i][picks[i]].label());for(int i=0;i<2;i++)sizes[i].setText(oldSizes[i]);};
  };
  Runnable checkpoint=()->{undo.push(snapshot.get());redo.clear();};
  beforeProfileChange[0]=checkpoint;
  TextView drawingStatus=new TextView(app);drawingStatus.setText(imported!=null?"Fotoğraf taslağı: kayıt yerlerini, kanatları ve ölçüleri doğrulayın.":frameAdded[0]?"Araç seçin ve çizime dokunun.":"Önce kasa ekleyin.");panel.addView(drawingStatus);
  EditText jointEdit=new EditText(app); jointEdit.setInputType(SIGNED); jointEdit.setText(value("joint"));
  app.planLabel(panel,"Kasa bağlantı payı (mm/uç)",14,Color.DKGRAY);panel.addView(jointEdit);
  EditText recordJoint=new EditText(app);recordJoint.setInputType(SIGNED);recordJoint.setText(value("mullionJoint"));panel.addView(recordJoint);recordJoint.setHint("Orta kayda bağlantı payı (mm/uç)");
  Runnable persistDrawing=()->{
   try{
    org.json.JSONObject o=new org.json.JSONObject();
    o.put("coordinateScale",1000000);o.put("frame",frameAdded[0]);o.put("xs",new org.json.JSONArray(xs));o.put("ys",new org.json.JSONArray(ys));o.put("openings",new org.json.JSONArray(openings));o.put("left",new org.json.JSONArray(leftCells));o.put("empty",new org.json.JSONArray(emptyCells));
    org.json.JSONArray lines=new org.json.JSONArray();for(int[] q:localLines){org.json.JSONArray a=new org.json.JSONArray();for(int n:q)a.put(n);lines.put(a);}o.put("lines",lines);
    o.put("profiles",new org.json.JSONObject(lineProfiles));
    org.json.JSONArray p=new org.json.JSONArray();for(int n:picks)p.put(n);o.put("picks",p);
    for(int i=0;i<2;i++)o.put(sizeKeys[i],sizes[i].getText().toString());
    o.put("joint",jointEdit.getText().toString());o.put("recordJoint",recordJoint.getText().toString());
    draft.put("editorDrawing",o.toString());
   }catch(org.json.JSONException ex){toast("Çizim kaydedilemedi.");}
  };
  try{org.json.JSONObject o=new org.json.JSONObject(value("editorDrawing"));jointEdit.setText(o.optString("joint",jointEdit.getText().toString()));recordJoint.setText(o.optString("recordJoint",recordJoint.getText().toString()));}catch(org.json.JSONException ignored){}
  java.util.function.Supplier<java.util.ArrayList<int[]>> records=()->{
   java.util.ArrayList<int[]> all=new java.util.ArrayList<>(localLines);
   for(int x:xs)all.add(new int[]{x,0,x,1000000,lineProfiles.getOrDefault("v"+x,picks[1])});
   for(int y:ys)all.add(new int[]{0,y,1000000,y,lineProfiles.getOrDefault("h"+y,picks[1])});
   return all;
  };
  java.util.function.Supplier<TemaLayout.Frame> editorFrame = () -> frame(section(groups[0][picks[0]]), lineProfiles);
  java.util.function.Supplier<TemaLayout> geometry = () -> new TemaLayout(
   ShopDimensions.tenths(sizes[0].getText().toString(),"En"), ShopDimensions.tenths(sizes[1].getText().toString(),"Boy"),
   editorFrame.get(), DRAWING_SCALE, bays.get(), layoutRecords(records.get()), openings);
  TemaOpeningResize.Resolver resizeLayout = drawing -> new TemaLayout(drawing.width, drawing.height,
   frame(section(groups[0][picks[0]]), drawing.profiles), DRAWING_SCALE,
   drawing.cells(), layoutRecords(drawing.records(picks[1])), openings);
  java.util.function.Supplier<TemaOpeningResize.Drawing> resizeDrawing = () -> new TemaOpeningResize.Drawing(
   ShopDimensions.tenths(sizes[0].getText().toString(), "Dış en"),
   ShopDimensions.tenths(sizes[1].getText().toString(), "Dış boy"), DRAWING_SCALE, xs, ys, localLines, lineProfiles);
  java.util.function.IntFunction<TemaLayout.Section> selectedSash = number -> openings.contains(number)
   ? section(groups[2][lineProfiles.getOrDefault("s" + number, picks[2])]) : null;
  TemaLayout.Section skirtProfile = section(series.skirt());
  java.util.function.BiFunction<Integer,TemaLayout.Opening,TemaLayout.Section> selectedSkirt = (number, bay) ->
   bay.door && lineProfiles.containsKey("e" + number) ? skirtProfile : null;
  java.util.function.IntFunction<int[]> allowances = count -> {
   int[] deductions = new int[count];
   for (int i = 0; i < count; i++) {
    int number = i + 1;
    if (emptyCells.contains(number)) continue;
    deductions[i] = TemaJoinery.glassAllowance(selectedSash.apply(number));
   }
   return deductions;
  };
  MainActivity.PhotoResult<TemaOpeningResize.Drawing> applyDrawing = model -> {
   checkpoint.run();
   xs.clear(); xs.addAll(model.columns);
   ys.clear(); ys.addAll(model.rows);
   localLines.clear(); localLines.addAll(model.local);
   lineProfiles.clear(); lineProfiles.putAll(model.profiles);
   sizes[0].setText(mm(model.width));
   sizes[1].setText(mm(model.height));
   persistDrawing.run();
  };
  java.util.function.Function<int[],int[]> net = cell -> {
   TemaLayout layout = geometry.get();
   List<int[]> coordinates = bays.get();
   for (int i = 0; i < coordinates.size(); i++) {
    if (!java.util.Arrays.equals(cell, coordinates.get(i))) continue;
    TemaLayout.Opening opening = layout.bays.get(i);
    return new int[]{opening.width(), opening.height(), layout.innerWidth, layout.innerHeight};
   }
   throw new IllegalArgumentException("Bölme sınırı bulunamadı.");
  };
  TemaDrawingViewport board = new TemaDrawingViewport(app) {
   android.graphics.Paint paint=new android.graphics.Paint(3);
   float left=24,top=24,right,bottom, innerLeft,innerRight,innerTop,innerBottom;
   final android.graphics.Paint dim=new android.graphics.Paint(3);
   final TemaElevation elevation = new TemaElevation(series, app.getResources().getDisplayMetrics().density);
   final TemaBayDimensions bayDimensions = new TemaBayDimensions();
   private void measure(android.graphics.Canvas c,float x,float y,float X,float Y,String label){
    dim.setColor(0xffc5eaff);dim.setStrokeWidth(app.dp(1));dim.setStyle(android.graphics.Paint.Style.STROKE);c.drawLine(x,y,X,Y,dim);
    float dx=X-x,dy=Y-y,len=(float)Math.hypot(dx,dy);if(len<1)return;dx/=len;dy/=len;float k=app.dp(5);
    for(int i=0;i<2;i++){float px=i==0?x:X,py=i==0?y:Y,sign=i==0?1:-1;c.drawLine(px-dy*k,py+dx*k,px+dy*k,py-dx*k,dim);c.drawLine(px,py,px+sign*dx*k-dy*k/2,py+sign*dy*k+dx*k/2,dim);c.drawLine(px,py,px+sign*dx*k+dy*k/2,py+sign*dy*k-dx*k/2,dim);}
    c.save();c.translate((x+X)/2,(y+Y)/2);if(Math.abs(dy)>Math.abs(dx))c.rotate(-90);dim.setStyle(android.graphics.Paint.Style.FILL);dim.setTextSize(app.dp(8));dim.setTextAlign(android.graphics.Paint.Align.CENTER);float w=dim.measureText(label)/2+app.dp(3);dim.setColor(0xfff8fafc);c.drawRoundRect(-w,-app.dp(8),w,app.dp(8),4,4,dim);dim.setColor(0xff075985);c.drawText(label,0,app.dp(3),dim);c.restore();
   }
   @Override protected void drawBoard(android.graphics.Canvas c){ if(getWidth()<=app.dp(54)||getHeight()<=app.dp(70))return; left=app.dp(42);top=app.dp(52);right=getWidth()-app.dp(42);bottom=getHeight()-app.dp(42);if(right<=left||bottom<=top)return;try{float ratio=Float.parseFloat(sizes[0].getText().toString().replace(',','.'))/Float.parseFloat(sizes[1].getText().toString().replace(',','.'));if(ratio>0&&Float.isFinite(ratio)){float w=right-left,h=bottom-top;if(w/h>ratio){float d=(w-h*ratio)/2;left+=d;right-=d;}else{float d=(h-w/ratio)/2;top+=d;bottom-=d;}}}catch(RuntimeException ignored){} paint.setStyle(android.graphics.Paint.Style.FILL);
    c.drawColor(0xff061426);paint.setShader(new android.graphics.LinearGradient(0,0,getWidth(),getHeight(),0xff07192e,0xff020b18,android.graphics.Shader.TileMode.CLAMP));c.drawRect(0,0,getWidth(),getHeight(),paint);paint.setShader(null); paint.setColor(0xff18334d);paint.setStrokeWidth(1);for(int x=0;x<getWidth();x+=app.dp(20))c.drawLine(x,0,x,getHeight(),paint);for(int y=0;y<getHeight();y+=app.dp(20))c.drawLine(0,y,getWidth(),y,paint); paint.setColor(0xff34495e); paint.setStrokeWidth(7);paint.setStyle(android.graphics.Paint.Style.STROKE);
    if(!frameAdded[0])return;
    TemaLayout layout;
    boolean measured = true;
    try {
     layout = geometry.get();
    } catch (IllegalArgumentException error) {
     measured = false;
     if (sizes[0].length() > 0 && sizes[1].length() > 0) return;
     try { layout = new TemaLayout(20000, 20000, editorFrame.get(), DRAWING_SCALE, bays.get(), layoutRecords(records.get()), openings); }
     catch (IllegalArgumentException ignored) { return; }
    }
    float sx = (right-left)/layout.width, sy = (bottom-top)/layout.height;
    innerLeft = left + layout.frame.left.face * sx;
    innerTop = top + layout.frame.top.face * sy;
    innerRight = right - layout.frame.right.face * sx;
    innerBottom = bottom - layout.bottomFace * sy;
    List<TemaLayout.Member> members = layout.members(TemaJoinery.Continuous.values()[continuous]);
    elevation.setAnnotationDensity(drawingPixels(app.getResources().getDisplayMetrics().density));
    elevation.structure(c, layout, members, TemaJoinery.Corner.values()[corner], left, top, sx, sy);
    if (measured) {
     float off = app.dp(22);
     measure(c, left, top - off, right, top - off, sizes[0].getText() + " mm");
     measure(c, left - off, top, left - off, bottom, sizes[1].getText() + " mm");
    }
    float annotation = drawingPixels(app.getResources().getDisplayMetrics().density);
    for (int i = 0; i < layout.bays.size(); i++) {
     TemaLayout.Opening bay = layout.bays.get(i);
     int number = i + 1;
     boolean empty = emptyCells.contains(number), opening = openings.contains(number);
     TemaLayout.Section sash = selectedSash.apply(number), skirt = selectedSkirt.apply(number, bay);
     if (!empty) {
      elevation.opening(c, bay, sash, skirt, leftCells.contains(number),
       TemaJoinery.Corner.values()[sashCorner], left, top, sx, sy);
     }
     android.graphics.RectF bounds = new android.graphics.RectF(left + bay.left * sx, top + bay.top * sy,
      left + bay.right * sx, top + bay.bottom * sy);
     String glass = measured && !empty ? glassSize(bay, TemaJoinery.glassAllowance(sash), skirtExtra(sash, skirt)) : null;
     bayDimensions.draw(c, bounds, bay, number, TemaBayDimensions.kind(bay, opening, empty), measured, annotation, glass);
    }
    labelStructure(c, layout, members, sx, sy);
    drawRecordGuide(c);
   }

   private void labelStructure(android.graphics.Canvas c, TemaLayout layout, List<TemaLayout.Member> members, float sx, float sy) {
    int frameColor = 0xee284756, recordColor = 0xee154a51;
    float x = (left + right) / 2, y = (top + bottom) / 2;
    float inset = drawingPixels(app.dp(9));
    elevation.profileLeader(c, x, top - inset, x, top + layout.frame.top.face * sy / 2);
    elevation.profileLeader(c, left - inset, y, left + layout.frame.left.face * sx / 2, y);
    elevation.profileLeader(c, right + inset, y, right - layout.frame.right.face * sx / 2, y);
    labelProfile(c, layout.frame.top, false, x, top - inset, right - left, frameColor);
    labelProfile(c, layout.frame.left, true, left - inset, y, bottom - top, frameColor);
    labelProfile(c, layout.frame.right, true, right + inset, y, bottom - top, frameColor);
    for (TemaLayout.Span span : layout.rails) {
     float center = left + (span.start + span.end) * sx / 2;
     elevation.profileLeader(c, center, bottom + inset, center, bottom - layout.bottomFace * sy / 2);
     labelProfile(c, layout.frame.bottom, false, center, bottom + inset, (span.end - span.start) * sx, frameColor);
    }
    for (TemaLayout.Member record : members) {
     float at = (record.start + record.end) / 2f;
     float rx = left + (record.vertical ? record.axis : at) * sx;
     float ry = top + (record.vertical ? at : record.axis) * sy;
     labelProfile(c, record.section, record.vertical, rx, ry,
      (record.end - record.start) * (record.vertical ? sy : sx), recordColor);
    }
   }

   private void labelProfile(android.graphics.Canvas c, TemaLayout.Section section, boolean vertical,
                             float x, float y, float span, int color) {
    elevation.profileLabel(c, section.code, section.face, vertical, x, y, span, color);
   }

   private void drawRecordGuide(android.graphics.Canvas canvas) {
    TemaRecordPlacement.Start start = pendingRecord[0];
    if (start == null) return;
    float x = innerLeft + (innerRight - innerLeft) * (start.vertical ? start.axis : start.support) / DRAWING_SCALE;
    float y = innerTop + (innerBottom - innerTop) * (start.vertical ? start.support : start.axis) / DRAWING_SCALE;
    dim.setColor(0xff70ffe1);
    dim.setStyle(android.graphics.Paint.Style.FILL);
    dim.setStrokeWidth(drawingPixels(app.dp(2)));
    for (int end : TemaRecordPlacement.oppositeSupports(bays.get(), start)) {
     float targetX = start.vertical ? x : innerLeft + (innerRight - innerLeft) * end / DRAWING_SCALE;
     float targetY = start.vertical ? innerTop + (innerBottom - innerTop) * end / DRAWING_SCALE : y;
     dim.setPathEffect(new android.graphics.DashPathEffect(new float[]{drawingPixels(app.dp(5)), drawingPixels(app.dp(4))}, 0));
     canvas.drawLine(x, y, targetX, targetY, dim);
     dim.setPathEffect(null);
     canvas.drawCircle(targetX, targetY, drawingPixels(app.dp(4)), dim);
    }
    canvas.drawCircle(x, y, drawingPixels(app.dp(6)), dim);
   }

   private void placeRecord(int x, int y, int tolX, int tolY) {
    boolean vertical = mode[0] == 0;
    int across = vertical ? tolX : tolY, along = vertical ? tolY : tolX;
    int snappedX = vertical ? x : Math.max(0, Math.min(DRAWING_SCALE, x));
    int snappedY = vertical ? Math.max(0, Math.min(DRAWING_SCALE, y)) : y;
    List<int[]> before = bays.get();
    TemaRecordPlacement.Start start = pendingRecord[0];
    if (start == null) {
     pendingRecord[0] = TemaRecordPlacement.start(before, vertical, snappedX, snappedY, along);
     drawingStatus.setText(pendingRecord[0] == null ? "Bir profil kenarına dokunarak başlangıcı seçin."
      : "Çizgiyi bitirmek için işaretli karşı kenara dokunun.");
     invalidate();
     return;
    }
    int[] segment = TemaRecordPlacement.finish(before, start, snappedX, snappedY, across, along, placementProfile[0]);
    if (segment == null) {
     drawingStatus.setText("Aynı bölmede işaretli karşı ucu seçin.");
     return;
    }
    Runnable restore = snapshot.get();
    int axis = segment[vertical ? 0 : 1];
    int alongIndex = vertical ? 1 : 0;
    if (segment[alongIndex] == 0 && segment[alongIndex + 2] == DRAWING_SCALE && (vertical ? xs : ys).size() < 12) {
     (vertical ? xs : ys).add(axis);
     lineProfiles.put((vertical ? "v" : "h") + axis, placementProfile[0]);
    } else {
     localLines.add(segment);
    }
    if (bays.get().size() != before.size() + 1) {
     restore.run();
     drawingStatus.setText("Seçilen kayıt bir bölmeye sığmalı.");
     return;
    }
    int previousSashes = openings.size();
    keepBays.accept(before);
    try {
     if (sizes[0].length() > 0 && sizes[1].length() > 0) geometry.get();
    } catch (IllegalArgumentException error) {
     restore.run();
     drawingStatus.setText(error.getMessage());
     return;
    }
    undo.push(restore);
    redo.clear();
    pendingRecord[0] = null;
    drawingStatus.setText(previousSashes > openings.size()
     ? "Kayıt eklendi. Bölünen bölmenin kanadını yeniden seçin."
     : "Kayıt eklendi. Yeni kayıt için başlangıç ucunu seçin.");
    persistDrawing.run();
    invalidate();
   }

   private void deleteRecord(int x, int y, int tolX, int tolY) {
    int[] line = TemaRecordPlacement.nearest(records.get(), x, y, tolX, tolY);
    if (line == null) {
     drawingStatus.setText("Silmek istediğiniz kayda dokunun.");
     return;
    }
    List<int[]> before = bays.get();
    Runnable restore = snapshot.get();
    if (!localLines.remove(line)) {
     boolean vertical = line[0] == line[2];
     int axis = line[vertical ? 0 : 1];
     (vertical ? xs : ys).remove(axis);
     lineProfiles.remove((vertical ? "v" : "h") + axis);
    }
    if (!TemaRecordPlacement.connected(records.get(), DRAWING_SCALE)) {
     restore.run();
     drawingStatus.setText("Bu kayda bağlanan kayıtları önce kaldırın.");
     invalidate();
     return;
    }
    keepBays.accept(before);
    undo.push(restore);
    redo.clear();
    drawingStatus.setText("Kayıt kaldırıldı.");
    persistDrawing.run();
    invalidate();
   }
   private void editOpening(int index) {
    try {
     TemaOpeningResize.Drawing current = resizeDrawing.get();
     TemaLayout layout = resizeLayout.layout(current);
     TemaLayout.Opening bay = layout.bays.get(index);
     int[] deductions = allowances.apply(layout.bays.size());
     int extraHeight = skirtExtra(selectedSash.apply(index + 1), selectedSkirt.apply(index + 1, bay));
     LinearLayout form = new LinearLayout(app);
     form.setOrientation(LinearLayout.VERTICAL);
     form.setPadding(app.dp(18), app.dp(8), app.dp(18), app.dp(8));
     CheckBox glass = new CheckBox(app);
     glass.setText("Cam kesim ölçüsü gir");
     if (deductions[index] > 0) form.addView(glass);
     EditText en = new EditText(app), boy = new EditText(app);
     addDimensionFields(form, en, boy, bay.width(), bay.height());
     glass.setOnCheckedChangeListener((button, checked) -> {
      int allowance = checked ? deductions[index] : 0;
      en.setHint(checked ? "Cam kesim eni (mm)" : "İç en (mm)");
      boy.setHint(checked ? "Cam kesim boyu (mm)" : "İç boy (mm)");
      en.setText(bay.width() > allowance ? mm(bay.width() - allowance) : "");
      int heightAllowance = allowance + (checked ? extraHeight : 0);
      boy.setText(bay.height() > heightAllowance ? mm(bay.height() - heightAllowance) : "");
     });
     showResizeDialog(index, current, deductions, extraHeight, form, en, boy, glass);
    } catch (IllegalArgumentException error) {
     toast(error.getMessage());
    }
   }

   private void showResizeDialog(int index, TemaOpeningResize.Drawing current, int[] deductions, int extraHeight,
                                 LinearLayout form, EditText en, EditText boy, CheckBox glass) {
    TextView feedback = app.planLabel(form, "", 13, 0xffa92843);
    AlertDialog dialog = new AlertDialog.Builder(app).setTitle((index + 1) + ". bölme ölçüsü")
     .setView(form).setNegativeButton("Vazgeç", null).setPositiveButton("Uygula", null).create();
    dialog.setOnShowListener(d -> {
   dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(0xFFFFFFFF);
   dialog.getButton(AlertDialog.BUTTON_POSITIVE).setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF087F5B));
   dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(0xFFB91C1C);
   dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
     try {
      int allowance = glass.isChecked() ? deductions[index] : 0;
      int width = ShopDimensions.tenths(en.getText().toString(), "En");
      int height = ShopDimensions.tenths(boy.getText().toString(), "Boy");
      if (width <= 0 || height <= 0) throw new IllegalArgumentException("Ölçüler sıfırdan büyük olmalı.");
      int insideHeight = height + allowance + (glass.isChecked() ? extraHeight : 0);
      if (insideHeight <= deductions[index] + extraHeight) {
       throw new IllegalArgumentException("Kapı eteğinden sonra cam açıklığı yetersiz.");
      }
      TemaOpeningResize.Drawing changed = TemaOpeningResize.resize(current, index, width + allowance,
       insideHeight, deductions, resizeLayout);
      applyDrawing.accept(changed);
      drawingStatus.setText("Ölçüler güncellendi · Dış kasa " + mm(changed.width) + " × " + mm(changed.height) + " mm");
      dialog.dismiss();
      invalidate();
     } catch (IllegalArgumentException error) {
      feedback.setText(error.getMessage());
     }
     });
     });
    dialog.show();
   }

   @Override protected void tap(float touchX, float touchY){
    if(!frameAdded[0]){toast("Önce kasa ekleyin.");return;}
    if (innerRight <= innerLeft || innerBottom <= innerTop
     || touchX < left || touchX > right || touchY < top || touchY > bottom) return;
    int x=Math.round(DRAWING_SCALE*(touchX-innerLeft)/(innerRight-innerLeft));
    int y=Math.round(DRAWING_SCALE*(touchY-innerTop)/(innerBottom-innerTop));
    int tolX = Math.max(1, Math.round(DRAWING_SCALE * drawingPixels(app.dp(16)) / (innerRight - innerLeft)));
    int tolY = Math.max(1, Math.round(DRAWING_SCALE * drawingPixels(app.dp(16)) / (innerBottom - innerTop)));
    if (mode[0] == 0 || mode[0] == 1) { placeRecord(x, y, tolX, tolY); return; }
    if (mode[0] == 3) { deleteRecord(x, y, tolX, tolY); return; }
    if(y>DRAWING_SCALE && touchY<=bottom && mode[0]!=7)y=DRAWING_SCALE-1;
    if (mode[0] == 8) {
     int cell = hit.apply(x, y);
     if (cell != 0) editOpening(cell - 1);
     return;
    }
    if (mode[0] == 9) {
     int number = hit.apply(x, y);
     try {
      if (number == 0 || !openings.contains(number)) throw new IllegalArgumentException("Alt sıradaki kapı kanadına dokunun.");
      TemaLayout.Opening bay = geometry.get().bays.get(number - 1);
      if (!bay.door) throw new IllegalArgumentException("Etek profili zemine inen kapı kanadına eklenir.");
      boolean remove = lineProfiles.containsKey("e" + number);
      TemaLayout.Section sash = selectedSash.apply(number);
      if (!remove && glassSize(bay, TemaJoinery.glassAllowance(sash), skirtExtra(sash, skirtProfile)) == null) {
       throw new IllegalArgumentException("Kapı eteğinden sonra cam açıklığı yetersiz.");
      }
      checkpoint.run();
      if (remove) lineProfiles.remove("e" + number);
      else lineProfiles.put("e" + number, 0);
      drawingStatus.setText(remove ? "Etek profili kaldırıldı." : skirtProfile.code + " etek profili eklendi.");
      persistDrawing.run();
      invalidate();
     } catch (IllegalArgumentException error) {
      toast(error.getMessage());
     }
     return;
    }
    if(mode[0]==7){
     String key=null;int role=1;int best=65000;
     for(int q:xs)if(Math.abs(q-x)<best){best=Math.abs(q-x);key="v"+q;}
     for(int q:ys)if(Math.abs(q-y)<best){best=Math.abs(q-y);key="h"+q;}
     int[] edges={Math.abs(x),Math.abs(y),Math.abs(DRAWING_SCALE-x),Math.abs(DRAWING_SCALE-y)};
     for(int side=0;side<edges.length;side++)if(edges[side]<best){best=edges[side];role=0;key=FRAME_EDGES[side];}
     int[] local=null;
     for(int[] q:localLines){
      boolean vertical=q[0]==q[2];
      int pos=vertical?y:x, at=vertical?x:y, axis=vertical?q[0]:q[1];
      if(pos>=q[vertical?1:0] && pos<=q[vertical?3:2] && Math.abs(at-axis)<best){
       best=Math.abs(at-axis);local=q;role=1;key=null;
      }
     }
     if(local!=null){
      int[] line=local;
      chooseProfile("Kayıt profili",groups[1],line[4],index->{checkpoint.run();line[4]=index;persistDrawing.run();invalidate();});
      return;
     }
     if(key==null){int cell=hit.apply(x,y);if(openings.contains(cell)){role=2;key="s"+cell;}}
     if(key==null){toast("Bir profil çizgisine dokunun.");return;}
     final String selectedKey=key;final int selectedRole=role;
     int initial=lineProfiles.getOrDefault(key,role==0?lineProfiles.getOrDefault("frame",picks[0]):picks[role]);
     chooseProfile("Profil değiştir",groups[selectedRole],initial,index->{
      Runnable restore=snapshot.get();
      try {
       TemaLayout prior=geometry.get();
       lineProfiles.put(selectedKey,index);
       if (selectedRole == 0) {
        TemaLayout updated = geometry.get();
        for (int i = 0; i < updated.bays.size(); i++) {
         int number = i + 1;
         if (emptyCells.contains(number)) continue;
         TemaLayout.Opening bay = updated.bays.get(i);
         TemaLayout.Section sash = selectedSash.apply(number);
         if (glassSize(bay, TemaJoinery.glassAllowance(sash), skirtExtra(sash, selectedSkirt.apply(number, bay))) == null) {
          throw new IllegalArgumentException(number + ". bölmede cam açıklığı yetersiz.");
         }
        }
       } else if (selectedRole == 1) {
        TemaOpeningResize.Drawing model=resizeDrawing.get();
        for(int i=0;i<prior.bays.size();i++)if(openings.contains(i+1)){
         TemaLayout.Opening target=prior.bays.get(i);
         model=TemaOpeningResize.resize(model,i,target.width(),target.height(),allowances.apply(prior.bays.size()),resizeLayout);
        }
        if(model.width!=prior.width||model.height!=prior.height)throw new IllegalArgumentException("Dış kasa ölçüsü korunamadı.");
        applyDrawing.accept(model);
       }
       undo.push(restore);redo.clear();persistDrawing.run();invalidate();
      }catch(RuntimeException ex){restore.run();invalidate();toast(ex.getMessage());}
     });return;
    }
    if(x<=0||x>=1000000||y<=0||y>=1000000)return;
    checkpoint.run();
    int cell = hit.apply(x, y);
    if (cell == 0) { undo.pop(); return; }
    if (mode[0] == 4 || mode[0] == 5) {
     openings.remove(cell);
     lineProfiles.remove("s"+cell);
     lineProfiles.remove("e"+cell);
     leftCells.remove(cell);
     if (mode[0] == 4) emptyCells.add(cell);
     else emptyCells.remove(cell);
    } else {
     emptyCells.remove(cell);
     openings.add(cell);
     lineProfiles.put("s"+cell, placementProfile[0]);
     if (mode[0] == 2) leftCells.add(cell);
     else leftCells.remove(cell);
    }
    drawingStatus.setText("Kasa: 1 | Dikey: "+xs.size()+" | Yatay: "+ys.size()+" | Kanat: "+openings.size());persistDrawing.run();invalidate();return;
   }
  };
  refreshDrawing[0]=board::invalidate;
  LinearLayout historyTools=new LinearLayout(app);panel.addView(historyTools);
  Button undoButton=new Button(app);undoButton.setText("↶ Geri al");historyTools.addView(undoButton,new LinearLayout.LayoutParams(0,app.dp(52),1));
  Button redoButton=new Button(app);redoButton.setText("↷ İleri al");historyTools.addView(redoButton,new LinearLayout.LayoutParams(0,app.dp(52),1));
  redoButton.setOnClickListener(v->{pendingRecord[0]=null;board.invalidate();if(redo.isEmpty()){toast("İleri alınacak işlem yok.");return;}undo.push(snapshot.get());redo.pop().run();persistDrawing.run();board.invalidate();});
  undoButton.setOnClickListener(v->{pendingRecord[0]=null;board.invalidate();if(undo.isEmpty()){toast("Geri alınacak işlem yok.");return;}redo.push(snapshot.get());undo.pop().run();persistDrawing.run();drawingStatus.setText("Son işlem geri alındı.");board.invalidate();});
  Button frameButton=new Button(app);frameButton.setText("+ Kasa ekle");panel.addView(frameButton);frameButton.setOnClickListener(v->{try { if(ShopDimensions.tenths(sizes[0].getText().toString(),"Dış en")<=0 || ShopDimensions.tenths(sizes[1].getText().toString(),"Dış boy")<=0) throw new IllegalArgumentException("Önce dış ölçüleri girin."); } catch(IllegalArgumentException ex){toast(ex.getMessage());return;} chooseProfile("Kasa",groups[0],picks[0],index->{checkpoint.run();frameAdded[0]=true;picks[0]=index;lineProfiles.put("frame",index);profileButtons[0].setText(names[0]+" · "+groups[0][index].label());drawingStatus.setText("Kasa eklendi: "+groups[0][index].label());persistDrawing.run();board.invalidate();});});
  Button sashButton=new Button(app);sashButton.setText("+ Kanat ekle");panel.addView(sashButton);sashButton.setOnClickListener(v->{if(!frameAdded[0]){toast("Önce kasa ekleyin.");return;}chooseProfile("Kanat",groups[2],picks[2],index->{placementProfile[0]=index;mode[0]=2;pendingRecord[0]=null;drawingStatus.setText(groups[2][index].label()+" · Kanat için bölmeye dokunun.");board.invalidate();});});
  String[] modes = {"↕ Dikey kayıt", "↔ Yatay kayıt", "Sol kanat", "Kaydı sil", "Boş kasa",
   "Sabit cam", "Sağ kanat", "Profil seç", "Net ölçü"};
  Button[] toolButtons = new Button[modes.length];
  for (int i = 0; i < modes.length; i++) {
   final int tool = i;
   Button button = new Button(app);
   toolButtons[i] = button;
   button.setText(modes[i]);
   button.setOnClickListener(v -> {
    Runnable activate = () -> {
     mode[0] = tool;
     pendingRecord[0] = null;
     for (int j = 0; j < toolButtons.length; j++) if(toolButtons[j]!=null) styleTool(toolButtons[j], j == tool);
     drawingStatus.setText(tool < 2 ? modes[tool] + " · Önce başlangıç kenarına dokunun."
      : modes[tool] + " · çizime dokunun.");
     board.invalidate();
    };
    if(tool<2 || tool==2 || tool==6){
     if(!frameAdded[0]){toast("Önce kasa ekleyin.");return;}
     int role=tool<2?1:2;
     chooseProfile(names[role],groups[role],picks[role],index->{placementProfile[0]=index;activate.run();});
    }else activate.run();
   });
  }
  java.util.function.IntConsumer equalize = option -> {
   final int axis=option/2;
   final boolean protectDoors=option%2==1;
    if(!frameAdded[0]){toast("Önce kasa ekleyin.");return;}
    Runnable restore=snapshot.get();
    try{
     int originalCount=bays.get().size();
     java.util.TreeSet<Integer> set=axis==0?xs:ys;
     java.util.ArrayList<Integer> pts=new java.util.ArrayList<>(set);pts.add(0,0);pts.add(1000000);
     java.util.TreeSet<Integer> fixed=new java.util.TreeSet<>();fixed.add(0);fixed.add(1000000);
     java.util.ArrayList<int[]> cells=bays.get();
     for(int i=0;i<cells.size();i++)if(protectDoors&&(openings.contains(i+1)||emptyCells.contains(i+1))){int[] protectedCell=cells.get(i);for(int p:pts)if(p>=protectedCell[axis]&&p<=protectedCell[axis+2])fixed.add(p);Integer lower=set.floor(protectedCell[axis]),upper=set.ceiling(protectedCell[axis+2]);if(lower!=null)fixed.add(lower);if(upper!=null)fixed.add(upper);}
     int total=net.apply(cells.get(0))[axis+2];if(total<=0)throw new IllegalArgumentException("Kasa ölçüsünü kontrol edin.");
     String prefix=axis==0?"v":"h";
     java.util.HashMap<Integer,Integer> faces=new java.util.HashMap<>();
     for(int p:pts)faces.put(p,p==0||p==1000000?0:groups[1][lineProfiles.getOrDefault(prefix+p,picks[1])].face);
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
     undo.push(restore);redo.clear();persistDrawing.run();board.invalidate();drawingStatus.setText(protectDoors?"Net boşluklar eşitlendi; kapı ve kanatlar korundu.":"Tüm net boşluklar eşitlendi.");
    }catch(RuntimeException ex){restore.run();board.invalidate();toast(ex.getMessage());}
  };
  boolean[] framePickerPending={false};
  Runnable promptFrame=()->{
   if(frameAdded[0] || framePickerPending[0])return;
   try { if(ShopDimensions.tenths(sizes[0].getText().toString(),"Dış en")<=0 || ShopDimensions.tenths(sizes[1].getText().toString(),"Dış boy")<=0)return; }catch(IllegalArgumentException ex){return;}
   frameButton.performClick();
  };
  Runnable delayedFrame=()->{framePickerPending[0]=false;promptFrame.run();};
  for (EditText size : sizes) size.addTextChangedListener(watcher(() -> {
   pendingRecord[0] = null;
   board.invalidate();
   if(!frameAdded[0]){board.removeCallbacks(delayedFrame);framePickerPending[0]=true;board.postDelayed(delayedFrame,1000);}
  }));
  Button netSizes = new Button(app);
  netSizes.setText("Kasa içi net en ve boy gir");
  panel.addView(netSizes);
  netSizes.setOnClickListener(v -> {
   LinearLayout inputs = new LinearLayout(app);
   inputs.setOrientation(LinearLayout.VERTICAL);
   inputs.setPadding(app.dp(18), app.dp(8), app.dp(18), app.dp(8));
   int initialWidth = 0, initialHeight = 0;
   try {
    TemaLayout current = geometry.get();
    initialWidth = current.innerWidth;
    initialHeight = current.innerHeight;
   } catch (IllegalArgumentException ignored) { }
   EditText width = new EditText(app), height = new EditText(app);
   addDimensionFields(inputs, width, height, initialWidth, initialHeight);
   TextView feedback = app.planLabel(inputs, "", 13, 0xffa92843);
   AlertDialog dialog = new AlertDialog.Builder(app).setTitle("Kasa yan profilleri arasındaki ölçü")
    .setView(inputs).setNegativeButton("Vazgeç", null).setPositiveButton("Uygula", null).create();
   dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button -> {
    try {
     int[] outside = TemaOpeningResize.outside(editorFrame.get(), bays.get(), openings, DRAWING_SCALE,
      ShopDimensions.tenths(width.getText().toString(), "İç en"), ShopDimensions.tenths(height.getText().toString(), "İç boy"));
     new TemaLayout(outside[0], outside[1], editorFrame.get(), DRAWING_SCALE, bays.get(), layoutRecords(records.get()), openings);
     checkpoint.run();
     sizes[0].setText(mm(outside[0]));
     sizes[1].setText(mm(outside[1]));
     frameAdded[0] = true;
     persistDrawing.run();
     drawingStatus.setText("Kasa ve bölmeler yeni iç ölçüye göre güncellendi.");
     dialog.dismiss();
     board.invalidate();
    } catch (IllegalArgumentException error) {
     feedback.setText(error.getMessage());
    }
   }));
   dialog.show();
  });
  Button clear=new Button(app);clear.setText("Çizimi temizle");panel.addView(clear);clear.setOnClickListener(v->{pendingRecord[0]=null;checkpoint.run();frameAdded[0]=false;localLines.clear();lineProfiles.clear();xs.clear();ys.clear();openings.clear();leftCells.clear();emptyCells.clear();board.invalidate();});
  buildAdvanced(panel);
  LinearLayout workspace = new LinearLayout(app);
  workspace.setOrientation(LinearLayout.VERTICAL);
  workspace.setBackgroundColor(0xff061426);
  workspace.setPadding(app.dp(6), app.dp(2), app.dp(6), app.dp(4));
  workspace.addView(dimensions);
  LinearLayout shortcuts = horizontal(workspace);
  Button settings = chooseButton(column(shortcuts), "Profil ve ayarlar", () ->
   new AlertDialog.Builder(app).setTitle("Profil ve montaj ayarları").setView(scroll)
    .setPositiveButton("Uygula", (d, which) -> board.invalidate())
    .setOnDismissListener(d -> {
     if (scroll.getParent() instanceof android.view.ViewGroup) {
      ((android.view.ViewGroup) scroll.getParent()).removeView(scroll);
     }
    }).show());
  Button positionsButton = chooseButton(column(shortcuts), "Pozlar", null);
  chooseButton(column(shortcuts), "Sığdır", board::fit);
  settings.setTextSize(12);
  positionsButton.setTextSize(12);

  for (int start = 0; start < 6; start += 3) {
   LinearLayout toolsRow = horizontal(workspace);
   int[] order = {0, 1, 8, 2, 6, 7};
   for (int i = start; i < start + 3; i++) {
    Button button = toolButtons[order[i]];
    styleTool(button, mode[0] == order[i]);
    LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(0, app.dp(44), 1);
    size.setMargins(app.dp(2), app.dp(2), app.dp(2), app.dp(2));
    toolsRow.addView(button, size);
   }
  }
  LinearLayout dimensionTools = horizontal(workspace);
  Runnable alignDoor = () -> {
   try {
    TemaOpeningResize.Drawing drawing = resizeDrawing.get();
    TemaLayout layout = resizeLayout.layout(drawing);
    List<Integer> references = TemaDoorAlignment.references(drawing, layout);
    if (references.isEmpty()) throw new IllegalArgumentException("Üstünde orta kayıt olan bir kapı çizin.");
    MainActivity.PhotoResult<Integer> apply = doorIndex -> {
     try {
      TemaDoorAlignment.Result result = TemaDoorAlignment.align(drawing, doorIndex, allowances.apply(layout.bays.size()), resizeLayout);
      if (result.moved == 0) {
       drawingStatus.setText("Hizalanacak ayrı yatay kayıt bulunamadı.");
       return;
      }
      applyDrawing.accept(result.drawing);
      drawingStatus.setText(result.moved + " kaydın alt kenarı kapı üst kaydına hizalandı.");
      board.invalidate();
     } catch (IllegalArgumentException error) {
      toast(error.getMessage());
     }
    };
    if (references.size() == 1) apply.accept(references.get(0));
    else {
     String[] options = new String[references.size()];
     for (int i = 0; i < references.size(); i++) {
      int index = references.get(i);
      options[i] = "Kapı " + (index + 1) + " · Üst nokta " + mm(layout.bays.get(index).top) + " mm (üstten)";
     }
     new AlertDialog.Builder(app).setTitle("Referans kapıyı seç")
      .setItems(options, (dialog, index) -> apply.accept(references.get(index)))
      .setNegativeButton("Vazgeç", null).show();
    }
   } catch (IllegalArgumentException error) {
    toast(error.getMessage());
   }
  };
  Button align = chooseButton(column(dimensionTools), "↔ Hizalama Sistemi", () -> {
   String[] options={"Tüm dik orta kayıtları eşitle","Kapı/kanat hariç dik orta kayıtları eşitle","Tüm yatay orta kayıtları eşitle","Kapı/kanat hariç yatay orta kayıtları eşitle","Yatay kayıtları kapı üstüne hizala"};
   new AlertDialog.Builder(app).setTitle("Hizalama Sistemi").setItems(options,(dialog,index)->{if(index==4)alignDoor.run();else equalize.accept(index);}).setNegativeButton("Vazgeç",null).show();
  });
  align.setTextSize(13);
  Button openingsButton = chooseButton(column(dimensionTools), "Bölme iç ölçüleri", () -> {
   try {
    showBayMeasurements(geometry.get(), openings, emptyCells);
   } catch (IllegalArgumentException error) {
    toast(error.getMessage());
   }
  });
  openingsButton.setTextSize(13);
  LinearLayout glassAndSashes = horizontal(workspace);
  chooseButton(column(glassAndSashes), "Cam ölçüleri", () -> {
   try {
    TemaLayout layout = geometry.get();
    int[] extraHeights = new int[layout.bays.size()];
    for (int i = 0; i < extraHeights.length; i++) {
     extraHeights[i] = skirtExtra(selectedSash.apply(i + 1), selectedSkirt.apply(i + 1, layout.bays.get(i)));
    }
    showGlassMeasurements(layout, allowances.apply(layout.bays.size()), extraHeights, emptyCells);
   } catch (IllegalArgumentException error) {
    toast(error.getMessage());
   }
  }).setTextSize(13);
  chooseButton(column(glassAndSashes), "Kanat boşluklarını eşitle", () -> {
   try {
    if (openings.size() < 2) throw new IllegalArgumentException("Çizime en az iki kanat ekleyin.");
    TemaOpeningResize.Drawing drawing = resizeDrawing.get();
    TemaLayout layout = resizeLayout.layout(drawing);
    List<Integer> sashNumbers = new ArrayList<>(new java.util.TreeSet<>(openings));
    String[] options = new String[sashNumbers.size()];
    boolean[] selected = new boolean[sashNumbers.size()];
    for (int i = 0; i < options.length; i++) {
     int number = sashNumbers.get(i);
     TemaLayout.Opening bay = layout.bays.get(number - 1);
     options[i] = "Bölme " + number + " · " + TemaBayDimensions.size(bay.width(), bay.height());
     selected[i] = true;
    }
    AlertDialog selection = new AlertDialog.Builder(app).setTitle("Eşitlenecek kanatları seç")
     .setMultiChoiceItems(options, selected, (dialog, index, checked) -> selected[index] = checked)
     .setNegativeButton("Vazgeç", null).setPositiveButton("Eşitle", null).create();
    selection.setOnShowListener(dialog -> selection.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button -> {
     try {
      List<Integer> indices = new ArrayList<>();
      for (int i = 0; i < selected.length; i++) if (selected[i]) indices.add(sashNumbers.get(i) - 1);
      TemaOpeningResize.Drawing result = TemaOpeningResize.equalize(drawing, indices,
       allowances.apply(layout.bays.size()), resizeLayout);
      if (result != drawing) applyDrawing.accept(result);
      pendingRecord[0] = null;
      drawingStatus.setText("Seçili kanatların net eni ve boyu eşit.");
      board.invalidate();
      selection.dismiss();
     } catch (IllegalArgumentException error) {
      toast(error.getMessage());
     }
    }));
    selection.show();
   } catch (IllegalArgumentException error) {
    toast(error.getMessage());
   }
  }).setTextSize(13);
  HorizontalScrollView ribbonScroll = new HorizontalScrollView(app);
  LinearLayout ribbon = new LinearLayout(app);
  ribbonScroll.addView(ribbon);
  workspace.addView(ribbonScroll, new LinearLayout.LayoutParams(-1, app.dp(46)));
  Button skirtButton = new Button(app);
  skirtButton.setText("Etek profili");
  skirtButton.setOnClickListener(view -> {
   mode[0] = 9;
   pendingRecord[0] = null;
   drawingStatus.setText(skirtProfile.code + " · " + mm(skirtProfile.face)
    + " mm · Eklemek veya kaldırmak için kapı kanadına dokunun.");
   board.invalidate();
  });
  for (Button button : new Button[]{skirtButton, undoButton, redoButton, toolButtons[3], toolButtons[4], toolButtons[5],
   frameButton, sashButton, clear}) {
   if (button.getParent() instanceof android.view.ViewGroup) {
    ((android.view.ViewGroup) button.getParent()).removeView(button);
   }
   styleTool(button, false);
   LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(-2, app.dp(42));
   size.setMargins(app.dp(2), app.dp(2), app.dp(2), app.dp(2));
   ribbon.addView(button, size);
  }
  panel.removeView(historyTools);
  panel.removeView(drawingStatus);
  panel.setBackgroundColor(0xff0b2037);
  for (int i = 0; i < panel.getChildCount(); i++) {
   View child = panel.getChildAt(i);
   if (child instanceof Button) styleTool((Button) child, false);
   else if (child instanceof TextView) ((TextView) child).setTextColor(0xffe2f6ff);
  }
  scroll.setFillViewport(true);
  workspace.addView(board, new LinearLayout.LayoutParams(-1, 0, 1));
  drawingStatus.setTextColor(0xff70f5be);
  drawingStatus.setPadding(app.dp(8), app.dp(3), app.dp(8), app.dp(3));
  drawingStatus.setText(frameAdded[0] ? "Araç seçin. İki parmakla yakınlaştırın." : "Önce kasa ekleyin.");
  workspace.addView(drawingStatus, new LinearLayout.LayoutParams(-1, -2));

  Runnable capture = () -> {
   persistDrawing.run();
   remember();
   for (int i = 0; i < 2; i++) draft.put(sizeKeys[i], sizes[i].getText().toString());
   for (int i = 0; i < 3; i++) {
    profileIndices[i] = picks[i];
    draft.put("code" + i, groups[i][picks[i]].code);
    draft.put("face" + i, mm(groups[i][picks[i]].face));
   }
   draft.put("vertical", String.valueOf(xs.size()));
   draft.put("horizontal", String.valueOf(ys.size()));
   draft.remove("columns");
   draft.remove("rows");
   draft.put("joint", jointEdit.getText().toString());
   draft.put("mullionJoint", recordJoint.getText().toString());
   draft.put("leftCells", cellList(leftCells));
   draft.put("emptyCells", cellList(emptyCells));
   StringBuilder bayData = new StringBuilder(), recordData = new StringBuilder();
   for (int[] cell : bays.get()) appendCoordinates(bayData, cell);
   for (int[] record : records.get()) appendCoordinates(recordData, record);
   draft.put("regionalScale", String.valueOf(DRAWING_SCALE));
   draft.put("regionalBays", bayData.toString());
   draft.put("regionalRecords", recordData.toString());
   draft.put("regionalProfiles", new org.json.JSONObject(lineProfiles).toString());
   chosenSashes.clear();
   chosenSashes.addAll(new java.util.TreeSet<>(openings));
  };
  capture.run();
  Map<String, String> initial = new LinkedHashMap<>(draft);
  int initialCorner = corner, initialSashCorner = sashCorner, initialContinuous = continuous;
  int[] initialProfiles = profileIndices.clone();
  Runnable saveDraft = () -> {
   capture.run();
   if (!initial.equals(draft) || initialCorner != corner || initialSashCorner != sashCorner
    || initialContinuous != continuous || !java.util.Arrays.equals(initialProfiles, profileIndices)) {
    editingPosition.cuts = java.util.Collections.emptyList();
   }
   editingPosition.drawing = snapshotDraft();
   savePositions();
  };
  Button pdf = new Button(app);
  pdf.setText("Çizimi PDF al");
  styleTool(pdf, false);
  LinearLayout.LayoutParams pdfSize = new LinearLayout.LayoutParams(-2, app.dp(42));
  pdfSize.setMargins(app.dp(2), app.dp(2), app.dp(2), app.dp(2));
  ribbon.addView(pdf, 0, pdfSize);
  pdf.setOnClickListener(v -> {
   try {
    if (!frameAdded[0]) throw new IllegalArgumentException("Önce kasa ekleyin.");
    if (pendingRecord[0] != null) throw new IllegalArgumentException("PDF için kaydın ikinci ucunu seçin.");
    saveDraft.run();
    TemaJoinery.Order order = createOrder();
    exportDrawing(editingPosition, order, buildPlan(order));
   } catch (IllegalArgumentException error) {
    toast(error.getMessage());
   }
  });
  boolean[] handled = {false};
  AlertDialog dialog = new AlertDialog.Builder(app).setTitle(editingPosition.name + " · " + series.label)
   .setView(workspace).setNegativeButton("Taslağı kaydet", null)
   .setPositiveButton("Kesim listesi oluştur", null).create();
  LinearLayout confirmation = horizontal(workspace);
  chooseButton(column(confirmation), "Tamam · Kaydet", () -> {
   saveDraft.run();
   handled[0] = true;
   saveOpenEditor = null;
   dialog.dismiss();
   open();
  });
  chooseButton(column(confirmation), "Kesim listesi", () ->
   dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick());
  positionsButton.setOnClickListener(v -> {
   saveDraft.run();
   handled[0] = true;
   dialog.dismiss();
   open();
   selectEditorPosition();
  });
  dialog.setOnDismissListener(v -> {
   saveOpenEditor = null;
   if (!handled[0]) {
    saveDraft.run();
    if (!app.isFinishing() && !app.isDestroyed()) open();
   }
  });
  dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w -> {
   try {
    if (!frameAdded[0]) throw new IllegalArgumentException("Önce kasa ekleyin.");
    if (pendingRecord[0] != null) throw new IllegalArgumentException("Kaydın bitiş ucunu seçin veya araç değiştirerek iptal edin.");
    geometry.get();
    capture.run();
    TemaJoinery.Order order = createOrder();
    TemaJoinery.Plan plan = buildPlan(order);
    editingPosition.drawing = snapshotDraft();
    editingPosition.cuts = new ArrayList<>();
    for (TemaJoinery.Cut cut : plan.cuts) {
     String ends = cut.angle + "°" + (cut.angle == cut.endAngle ? "" : "/" + cut.endAngle + "°");
     editingPosition.cuts.add(new TemaCutBatch.Line(cut.code, series.displayOf(cut.code), cut.length, cut.quantity, ends));
    }
    savePositions();
    Bundle history = new Bundle(editingPosition.drawing);
    history.putString("temaPositionId", editingPosition.id);
    app.saveHistory(series.label, history);
    handled[0] = true;
    saveOpenEditor = null;
    dialog.dismiss();
    showPlan(editingPosition, order, plan);
   } catch (IllegalArgumentException error) {
    toast(error.getMessage());
   }
  }));
  saveOpenEditor = saveDraft;
  dialog.show();
  if(!frameAdded[0]) board.post(()->{if(dialog.isShowing())promptFrame.run();});
  if (dialog.getWindow() != null) {
   dialog.getWindow().setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT);
   dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
  }
 }

 static void retainBays(List<int[]> before, List<int[]> after, Set<Integer> sashes,
                        Set<Integer> left, Set<Integer> empty, Map<String, Integer> overrides) {
  Set<Integer> keptSashes = new LinkedHashSet<>(), keptLeft = new LinkedHashSet<>(), keptEmpty = new LinkedHashSet<>();
  Map<String, Integer> keptOverrides = new LinkedHashMap<>();
  for (int current = 0; current < after.size(); current++) {
   for (int old = 0; old < before.size(); old++) {
    if (!java.util.Arrays.equals(after.get(current), before.get(old))) continue;
    int from = old + 1, to = current + 1;
    if (sashes.contains(from)) keptSashes.add(to);
    if (left.contains(from)) keptLeft.add(to);
    if (empty.contains(from)) keptEmpty.add(to);
    Integer profile = overrides.get("s" + from);
    if (profile != null && keptSashes.contains(to)) keptOverrides.put("s" + to, profile);
    if (keptSashes.contains(to) && overrides.containsKey("e" + from)) keptOverrides.put("e" + to, 0);
    break;
   }
  }
  sashes.clear();
  sashes.addAll(keptSashes);
  left.clear();
  left.addAll(keptLeft);
  empty.clear();
  empty.addAll(keptEmpty);
  overrides.keySet().removeIf(key -> key.startsWith("s") || key.startsWith("e"));
  overrides.putAll(keptOverrides);
 }

 private void restoreLegacyGrid(Set<Integer> xs, Set<Integer> ys, Set<Integer> openings,
                                Set<Integer> hinges, Set<Integer> empty, List<int[]> localLines,
                                Map<String, Integer> profileOverrides) {
  try {
   TemaJoinery.Order order = createOrder();
   TemaLayout layout = order.layout == null ? TemaJoinery.gridPlan(order).layout : order.layout;
   for (TemaLayout.Record record : layout.records) {
    int axis = (int)Math.round((double)record.axis * DRAWING_SCALE / layout.scale);
    int profile = profileIndices[1];
    for (int i = 0; i < series.mullions.length; i++) {
     if (series.mullions[i].code.equals(record.section.code)) profile = i;
    }
    if (record.start == 0 && record.end == layout.scale) {
     if (record.vertical) xs.add(axis);
     else ys.add(axis);
     profileOverrides.put((record.vertical ? "v" : "h") + axis, profile);
    } else {
     int start = (int)Math.round((double)record.start * DRAWING_SCALE / layout.scale);
     int end = (int)Math.round((double)record.end * DRAWING_SCALE / layout.scale);
     localLines.add(record.vertical ? new int[]{axis, start, axis, end, profile}
      : new int[]{start, axis, end, axis, profile});
    }
   }
   openings.addAll(order.openingSashes);
   empty.addAll(order.emptyCells);
   for (String cell : value("leftCells").split(";")) {
    if (!cell.trim().isEmpty()) hinges.add(Integer.parseInt(cell.trim()));
   }
  } catch (IllegalArgumentException error) {
   // Incomplete legacy forms still open with their saved outer measurements.
  }
 }

 private void styleTool(Button button, boolean selected) {
  WorkshopActions.style(app, button, selected ? WorkshopActions.Tone.ACTIVE : WorkshopActions.byLabel(button.getText()));
  button.setTextSize(12);
  button.setMaxLines(2);
  button.setMinHeight(0);
  button.setMinimumHeight(0);
  button.setPadding(app.dp(6), 0, app.dp(6), 0);
 }

 private static void appendCoordinates(StringBuilder result, int[] coordinates) {
  for (int i = 0; i < coordinates.length; i++) {
   if (i > 0) result.append(',');
   result.append(coordinates[i]);
  }
  result.append(';');
 }

 private static void addDimensionFields(LinearLayout form, EditText width, EditText height,
                                        int initialWidth, int initialHeight) {
  width.setInputType(DECIMAL);
  height.setInputType(DECIMAL);
  width.setHint("İç en (mm)");
  height.setHint("İç boy (mm)");
  if (initialWidth > 0) width.setText(mm(initialWidth));
  if (initialHeight > 0) height.setText(mm(initialHeight));
  form.addView(width);
  form.addView(height);
 }

 private String cellList(java.util.Set<Integer> cells){StringBuilder b=new StringBuilder();for(int n:cells){if(b.length()>0)b.append(";");b.append(n);}return b.toString();}

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
  fields.put(key, edit);
  return edit;
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
    if (item.image != 0) {
     ImageView section = new ImageView(app);
     section.setImageResource(item.image);
     section.setScaleType(ImageView.ScaleType.FIT_CENTER);
     row.addView(section, new LinearLayout.LayoutParams(app.dp(97), app.dp(72)));
    }
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
  button.setText(label);
  WorkshopActions.style(app, button, WorkshopActions.byLabel(label));
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

 private void loadPositions() {
  try {
   org.json.JSONArray saved = new org.json.JSONArray(preferences.getString(batchKey("positions"), "[]"));
   for (int i = 0; i < saved.length(); i++) {
    try {
     positions.add(TemaPosition.fromJson(saved.getJSONObject(i), app.getClassLoader()));
    } catch (org.json.JSONException | RuntimeException error) {
     android.util.Log.w("TemaWorkshop", "Skipping an unreadable position", error);
    }
   }
  } catch (org.json.JSONException error) {
   android.util.Log.w("TemaWorkshop", "Could not read saved positions", error);
  }
 }

 private void savePositions() {
  try {
   org.json.JSONArray saved = new org.json.JSONArray();
   for (TemaPosition position : positions) saved.put(position.toJson());
   preferences.edit().putString(batchKey("positions"), saved.toString()).apply();
  } catch (org.json.JSONException error) {
   toast("Pozlar kaydedilemedi.");
  }
 }

 private String nextPositionName(List<TemaPosition> added) {
  for (int number = 1; ; number++) {
   String name = String.format(Locale.ROOT, "P%02d", number);
   if (!hasPositionName(positions, name, null) && !hasPositionName(added, name, null)) return name;
  }
 }

 private static boolean hasPositionName(List<TemaPosition> entries, String name, TemaPosition except) {
  for (TemaPosition position : entries) if (position != except && position.name.equalsIgnoreCase(name)) return true;
  return false;
 }

 private void newPosition(DrawingDimensions imported) {
  positionName("Yeni doğrama pozu", nextPositionName(java.util.Collections.emptyList()), null, name -> {
   TemaPosition position = new TemaPosition(name);
   positions.add(position);
   savePositions();
   open();
   editPosition(position, imported);
  });
 }

 private void positionName(String title, String initial, TemaPosition current, MainActivity.PhotoResult<String> accept) {
  EditText name = new EditText(app);
  name.setSingleLine(true);
  name.setHint("Poz adı");
  name.setText(initial);
  name.setSelectAllOnFocus(true);
  AlertDialog dialog = new AlertDialog.Builder(app).setTitle(title).setView(name)
   .setNegativeButton("Vazgeç", null).setPositiveButton("Tamam", null).create();
  dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
   String value = name.getText().toString().trim();
   if (value.isEmpty() || value.length() > 32) {
    name.setError("1–32 karakterlik poz adı girin.");
   } else if (hasPositionName(positions, value, current)) {
    name.setError("Bu poz adı zaten kullanılıyor.");
   } else {
    dialog.dismiss();
    accept.accept(value);
   }
   }));
  dialog.show();
 }

 private void positionOptions(TemaPosition position) {
  new AlertDialog.Builder(app).setTitle(position.name)
   .setItems(new String[]{"Poz adını değiştir", "Pozu kaldır"}, (dialog, index) -> {
    if (index == 0) {
     positionName("Poz adı", position.name, position, name -> {
      position.name = name;
      savePositions();
      open();
     });
    } else {
     new AlertDialog.Builder(app).setMessage(position.name + " pozunun çizimi ve kesimleri kaldırılsın mı?")
      .setNegativeButton("Vazgeç", null).setPositiveButton("Kaldır", (confirm, which) -> {
       positions.remove(position);
       if (position == activePosition) activePosition = null;
       savePositions();
       open();
      }).show();
    }
   }).show();
 }

 private void editPosition(TemaPosition position, DrawingDimensions imported) {
  loadDraft(position.drawing);
  activePosition = position;
  drawEditor(imported);
 }

 private void selectEditorPosition() {
  List<TemaPosition> drawings = new ArrayList<>();
  for (TemaPosition position : positions) if (position.drawing != null || position.cuts.isEmpty()) drawings.add(position);
  String[] choices = new String[drawings.size() + 1];
  choices[0] = "+ Yeni poz çiz";
  for (int i = 0; i < drawings.size(); i++) choices[i + 1] = drawings.get(i).name;
  new AlertDialog.Builder(app).setTitle("Çizim pozları").setItems(choices, (dialog, index) -> {
   if (index == 0) newPosition(null);
   else editPosition(drawings.get(index - 1), null);
  }).setNegativeButton("Kapat", null).show();
 }

 private void remember() {
  for (Map.Entry<String, EditText> entry : fields.entrySet()) {
   draft.put(entry.getKey(), entry.getValue().getText().toString());
  }
  if (adapter != null) draft.put("adapter", adapter.isChecked() ? "1" : "0");
 }

 private Bundle snapshotDraft() {
  Bundle out = new Bundle();
  Bundle state = new Bundle();
  for (Map.Entry<String, String> entry : draft.entrySet()) state.putString(entry.getKey(), entry.getValue());
  out.putBundle("temaDraft", state);
  out.putIntArray("temaSelection", new int[]{series.ordinal(), corner, continuous,
   profileIndices[0], profileIndices[1], profileIndices[2], sashCorner, profileIndices[3], profileIndices[4]});
  int[] sashes = new int[chosenSashes.size()];
  int index = 0;
  for (int bay : chosenSashes) sashes[index++] = bay;
  out.putIntArray("temaSashes", sashes);
  return out;
 }

 private void loadDraft(Bundle in) {
  draft.clear();
  fields.clear();
  chosenSashes.clear();
  adapter = null;
  corner = sashCorner = continuous = 0;
  selectDefaultProfiles();
  if (in == null) return;
  Bundle state = in.getBundle("temaDraft");
  if (state != null) for (String key : state.keySet()) draft.put(key, state.getString(key, ""));
  for (String removed : new String[]{"sashGap", "individualSashGaps", "frameGlass", "mullionGlass", "sashGlass", "stock", "kerf"}) {
   draft.remove(removed);
  }
  int[] selection = in.getIntArray("temaSelection");
  if (selection != null && selection.length >= 7 && series.ordinal() == selection[0]) {
   corner = selection[1];
   continuous = selection[2];
   System.arraycopy(selection, 3, profileIndices, 0, 3);
   sashCorner = selection[6];
   if (selection.length >= 9) {
    profileIndices[3] = selection[7];
    profileIndices[4] = selection[8];
   }
  }
  int[] sashes = in.getIntArray("temaSashes");
  if (sashes != null) for (int bay : sashes) chosenSashes.add(bay);
  migrateCatalogueFaces();
 }

 void saveState(Bundle out) {
  if (saveOpenEditor != null) {
   saveOpenEditor.run();
   out.putString("temaActiveDrawing", activePosition.id);
  }
  out.putBoolean("temaEditing", app.body == form && form != null);
  if (cameraUri != null) out.putString("temaCamera", cameraUri.toString());
  if (drawingUri != null) out.putString("temaDrawing", drawingUri.toString());
 }

 void restoreHistory(Bundle in) {
  if (in == null) return;
  String id = in.getString("temaPositionId");
  for (TemaPosition position : positions) if (position.id.equals(id)) {
   open();
   openPositionSheet(position);
   return;
  }
  TemaPosition restored = new TemaPosition(nextPositionName(java.util.Collections.emptyList()));
  loadDraft(in);
  restored.drawing = snapshotDraft();
  positions.add(restored);
  savePositions();
  open();
  editPosition(restored, null);
 }

 boolean restoreState(Bundle in) {
  if (in == null) return false;
  if (positions.isEmpty() && in.getBundle("temaDraft") != null) {
   loadDraft(in);
   if (!value("editorDrawing").isEmpty() || !value("width").isEmpty()) {
    TemaPosition restored = new TemaPosition(nextPositionName(java.util.Collections.emptyList()));
    restored.drawing = snapshotDraft();
    positions.add(restored);
    savePositions();
   }
  }
  String camera = in.getString("temaCamera"), photo = in.getString("temaDrawing");
  if (camera != null) cameraUri = Uri.parse(camera);
  if (photo != null) loadDrawing(Uri.parse(photo), false);
  if (!in.getBoolean("temaEditing")) return false;
  open();
  String drawingId = in.getString("temaActiveDrawing");
  for (TemaPosition position : positions) if (position.id.equals(drawingId)) {
   editPosition(position, null);
   break;
  }
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

 private TemaJoinery.Order createOrder() {
  TemaJoinery.Order order = new TemaJoinery.Order();
  order.width = mm("width", "Dış en");
  order.height = mm("height", "Dış yükseklik");
  order.quantity = count("qty", "Doğrama adedi");
  if (order.quantity < 1 || order.quantity > 100) throw new IllegalArgumentException("Doğrama adedi 1–100 arasında olmalı.");
  order.frameCode = profileCode(0);
  order.frameFace = mm("face0", "Kasa yüz ölçüsü");
  order.corner = TemaJoinery.Corner.values()[corner];
  order.removeDoorSills = true;
  order.vertical = value("vertical").isEmpty() ? 0 : count("vertical", "Dikey kayıt");
  order.horizontal = value("horizontal").isEmpty() ? 0 : count("horizontal", "Yatay kayıt");
  if (order.vertical < 0 || order.horizontal < 0) throw new IllegalArgumentException("Kayıt adetlerini 0–12 arasında girin.");
  order.continuous = TemaJoinery.Continuous.values()[continuous];
  boolean regional = !value("regionalBays").isEmpty();
  order.aldoksBeads = series == TemaCatalog.Series.ALDOKS;
  order.aldoksDrawing = regional && order.aldoksBeads;
  if (order.aldoksBeads) {
   TemaCatalog.Profile fixed = series.beads[profileIndices[3]];
   TemaCatalog.Profile sash = series.beads[profileIndices[4]];
   order.fixedBead = new TemaJoinery.Bead(fixed.code, fixed.face, 0, TemaJoinery.Corner.HORIZONTAL_FULL);
   order.sashBead = new TemaJoinery.Bead(sash.code, sash.face, 0, TemaJoinery.Corner.HORIZONTAL_FULL);
  }
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
  if (regional) applyRegionalLayout(order);
  int cellCount = regional ? order.layout.bays.size() : (order.vertical + 1) * (order.horizontal + 1);
  boolean hasSashes = !chosenSashes.isEmpty();
  for(String cell : value("emptyCells").split(";")) if(!cell.trim().isEmpty()) {
   int n=Integer.parseInt(cell.trim()); if(n<1||n>cellCount||chosenSashes.contains(n)) throw new IllegalArgumentException("Boş kasa bölmesini kontrol edin.");
   if(order.emptyCells.isEmpty()) order.emptyCells=new LinkedHashSet<>(); order.emptyCells.add(n);
  }
  if (hasSashes) {
   order.sashCorner = TemaJoinery.Corner.values()[sashCorner];
   order.sashCode = profileCode(2);
   order.sashFace = mm("face2", "Kanat yüz ölçüsü");
   if (catalogSash(order, hasRecords)) order.adapterCode = TemaCatalog.C60_ADAPTER.code;
  }
  return order;
 }

 private void applyRegionalLayout(TemaJoinery.Order order) {
  List<int[]> cells = readDrawingCoordinates(value("regionalBays"), 4);
  List<int[]> placed = readDrawingCoordinates(value("regionalRecords"), 5);
  TemaLayout.Section defaultRecord = placed.isEmpty() ? null : new TemaLayout.Section(order.mullionCode, order.mullionFace);
  List<TemaLayout.Record> records = layoutRecords(placed, defaultRecord);
  Map<String,Integer> selections = new LinkedHashMap<>();
  try {
   org.json.JSONObject selected = value("regionalProfiles").isEmpty()
    ? new org.json.JSONObject(value("editorDrawing")).optJSONObject("profiles")
    : new org.json.JSONObject(value("regionalProfiles"));
   if (selected != null) for (java.util.Iterator<String> keys = selected.keys(); keys.hasNext();) {
    String key = keys.next();
    int index = selected.getInt(key);
    int limit = key.startsWith("frame") ? series.frames.length : key.startsWith("s") ? series.sashes.length : series.mullions.length;
    if (index >= 0 && index < limit) selections.put(key,index);
   }
  } catch (org.json.JSONException ignored) {}
  order.layout = new TemaLayout(order.width, order.height,
   frame(new TemaLayout.Section(order.frameCode, order.frameFace), selections),
   "1000000".equals(value("regionalScale")) ? DRAWING_SCALE : 1000, cells, records, order.openingSashes);
  Map<Integer,TemaLayout.Section> sashes = new LinkedHashMap<>(), skirts = new LinkedHashMap<>();
  for (int bay : order.openingSashes) {
   Integer index = selections.get("s" + bay);
   if (index != null) sashes.put(bay, section(series.sashes[index]));
   if (selections.containsKey("e" + bay)) skirts.put(bay, section(series.skirt()));
  }
  order.sashProfiles = sashes;
  order.skirtProfiles = skirts;
 }

 private List<int[]> readDrawingCoordinates(String drawing, int size) {
  List<int[]> rows = new ArrayList<>();
  for (String entry : drawing.split(";")) {
   if (entry.isEmpty()) continue;
   String[] coordinates = entry.split(",");
   if (coordinates.length != size) throw new IllegalArgumentException("Çizimi açıp ölçüleri yeniden aktarın.");
   int[] row = new int[size];
   for (int i = 0; i < size; i++) row[i] = Integer.parseInt(coordinates[i]);
   rows.add(row);
  }
  return rows;
 }

 private int optionalMm(String key, String label, int fallback) {
  return value(key).isEmpty() ? fallback : mm(key, label);
 }

 private boolean catalogSash(TemaJoinery.Order order, boolean hasRecords) {
  if (!(series == TemaCatalog.Series.C60 && !"0".equals(value("adapter"))
   && "T.3101".equals(order.frameCode) && order.frameFace == 490 && order.corner == TemaJoinery.Corner.MITER
   && "T.3105".equals(order.sashCode) && order.sashFace == 660 && order.sashCorner == TemaJoinery.Corner.MITER
   && (!hasRecords || ("T.3101".equals(order.mullionCode) && order.mullionFace == 490)))) return false;
  if (order.layout == null) return true;
  TemaLayout.Frame frame = order.layout.frame;
  for (TemaLayout.Section side : new TemaLayout.Section[]{frame.left,frame.top,frame.right,frame.bottom}) {
   if (!"T.3101".equals(side.code) || side.face != 490) return false;
  }
  for (TemaLayout.Member member : order.layout.members(order.continuous)) {
   if (!"T.3101".equals(member.section.code) || member.section.face != 490) return false;
  }
  return true;
 }

 private TemaJoinery.Plan buildPlan(TemaJoinery.Order order) {
  TemaJoinery.Plan plan = TemaJoinery.calculate(order);
  TemaJoinery.addManualCuts(plan, value("extraCuts"));
  if (!order.aldoksBeads) plan.cuts.removeIf(cut -> series.isBead(cut.code));
  return plan;
 }

 private void openPositionSheet(TemaPosition position) {
  if (position.drawing != null) {
   if (position.cuts.isEmpty()) {
    editPosition(position, null);
    return;
   }
   try {
    loadDraft(position.drawing);
    activePosition = position;
    TemaJoinery.Order order = createOrder();
    showPlan(position, order, buildPlan(order));
   } catch (IllegalArgumentException error) {
    toast(error.getMessage());
   }
   return;
  }
  app.page(position.name + " · Taranan kesim föyü");
  app.pageBack = this::open;
  if (!position.source.isEmpty()) app.note(position.source);
  addOptimizationLink(section("Birleşik stok planı"), position);
  for (TemaCutBatch.Line line : position.cuts) {
   if (series != TemaCatalog.Series.ALDOKS && series.isBead(line.key, line.label)) continue;
   LinearLayout row = section(cutTitle(line.key, line.label));
   app.planLabel(row, mm(line.length) + " mm × " + line.quantity + " adet", 15, Color.WHITE);
  }
  app.button("Pozlara dön", this::open);
 }

 private void acceptScannedCuts(Map<Integer, List<MainActivity.CutRow>> photos) {
  List<TemaPosition> added = new ArrayList<>();
  for (Map.Entry<Integer, List<MainActivity.CutRow>> photo : photos.entrySet()) {
   TemaPosition position = new TemaPosition(nextPositionName(added));
   position.source = app.cutSourceName(photo.getKey());
   List<TemaCutBatch.Line> lines = new ArrayList<>();
   for (MainActivity.CutRow row : photo.getValue()) {
    String code = TemaCatalog.normalizeCode(row.code);
    String name = row.name.trim();
    if (series != TemaCatalog.Series.ALDOKS && series.isBead(code, name)) continue;
    String label = (code + " " + name).trim();
    String context = position.source + " · " + label;
    int length = ShopDimensions.tenths(row.draft[0], context + " kesim ölçüsü");
    int quantity = MainActivity.cutNumber(row.draft[1], context + " kesim adedi");
    String key = code.isEmpty() ? "NAME:" + CutSheet.identity(name) : code;
    if (label.isEmpty() || length < 0 || quantity < 0 || quantity > 1000) {
     throw new IllegalArgumentException(context + ": profil, ölçü veya adet hatalı.");
    }
    lines.add(new TemaCutBatch.Line(key, cutTitle(key, name), length, quantity, ""));
   }
   if (!lines.isEmpty()) {
    position.cuts = lines;
    added.add(position);
   }
  }
  if (added.isEmpty()) throw new IllegalArgumentException("Bu listede aktarılacak profil kesimi yok.");
  positions.addAll(added);
  savePositions();
  openOptimization(added.get(added.size() - 1));
 }

 private Map<String, List<TemaCutBatch.Line>> readyCuts() {
  Map<String, List<TemaCutBatch.Line>> ready = new LinkedHashMap<>();
  for (TemaPosition position : positions) {
   List<TemaCutBatch.Line> visible = new ArrayList<>();
   for (TemaCutBatch.Line line : position.cuts) if (series == TemaCatalog.Series.ALDOKS || !series.isBead(line.key, line.label)) visible.add(line);
   if (!visible.isEmpty()) ready.put(position.name, visible);
  }
  return ready;
 }

 private String cutTitle(String key, String label) {
  TemaCatalog.Profile profile = series.find(key);
  if (profile != null) return profile.label();
  String name = label;
  if (name.equals(key)) name = "";
  else if (name.startsWith(key + " ")) name = name.substring(key.length()).trim();
  return name.isEmpty() ? "Özel profil" : name;
 }

 private TemaCutBatch.Result optimize() {
  int stock = ShopDimensions.tenths(stockText, "Stok boyu");
  int kerf = ShopDimensions.tenths(kerfText, "Testere payı");
  return TemaCutBatch.pack(readyCuts(), stock, kerf);
 }

 private void addOptimizationLink(LinearLayout card, TemaPosition position) {
  try {
   TemaCutBatch.Result result = optimize();
   app.planLabel(card, readyCuts().size() + " poz · " + result.barCount + " çubuk · " + result.pieceCount + " kesim", 15, Color.WHITE);
   chooseButton(card, "Yerleşimi göster", () -> showOptimization(result, position));
  } catch (IllegalArgumentException error) {
   app.planLabel(card, error.getMessage(), 14, 0xffffce83);
   chooseButton(card, "Stok ve testere ayarlarına dön", this::open);
  }
 }

 private Set<Integer> leftHinges() {
  Set<Integer> hinges = new LinkedHashSet<>();
  for (String cell : value("leftCells").split(";")) {
   if (!cell.trim().isEmpty()) hinges.add(Integer.parseInt(cell.trim()));
  }
  return hinges;
 }

 private void exportSavedDrawing(TemaPosition position) {
  try {
   loadDraft(position.drawing);
   String serialized = value("editorDrawing");
   if (!serialized.isEmpty() && !new org.json.JSONObject(serialized).optBoolean("frame")) {
    throw new IllegalArgumentException("PDF için önce kasa çizimini tamamlayın.");
   }
   TemaJoinery.Order order = createOrder();
   exportDrawing(position, order, buildPlan(order));
  } catch (org.json.JSONException | IllegalArgumentException error) {
   toast(error.getMessage());
  }
 }

 private void exportDrawing(TemaPosition position, TemaJoinery.Order order, TemaJoinery.Plan plan) {
  app.exportTemaPdf(position.name, series, order, plan, leftHinges());
 }

 private static int skirtExtra(TemaLayout.Section sash, TemaLayout.Section skirt) {
  return skirt == null ? 0 : skirt.face - sash.face;
 }

 private static String glassSize(TemaLayout.Opening bay, int allowance, int extraHeight) {
  int width = bay.width() - allowance, height = bay.height() - allowance - extraHeight;
  return width > 0 && height > 0 ? TemaBayDimensions.size(width, height) : null;
 }

 private void showGlassMeasurements(TemaLayout layout, int[] deductions, int[] extraHeights, Set<Integer> empty) {
  ScrollView scroll = new ScrollView(app);
  LinearLayout list = new LinearLayout(app);
  list.setOrientation(LinearLayout.VERTICAL);
  list.setPadding(app.dp(16), app.dp(8), app.dp(16), app.dp(8));
  scroll.addView(list);
  for (int i = 0; i < layout.bays.size(); i++) {
   if (empty.contains(i + 1)) continue;
   String size = glassSize(layout.bays.get(i), deductions[i], extraHeights[i]);
   app.planLabel(list, "Bölme " + (i + 1) + " · " + (size == null ? "Cam açıklığı yetersiz" : size), 14, 0xff1b4154);
  }
  if (list.getChildCount() == 0) app.planLabel(list, "Cam kesilecek bölme yok.", 14, 0xff1b4154);
  new AlertDialog.Builder(app).setTitle("Cam kesim ölçüleri · En × Boy")
   .setView(scroll).setPositiveButton("Kapat", null).show();
 }

 private void showBayMeasurements(TemaLayout layout, Set<Integer> openings, Set<Integer> empty) {
  ScrollView scroll = new ScrollView(app);
  LinearLayout list = new LinearLayout(app);
  list.setOrientation(LinearLayout.VERTICAL);
  list.setPadding(app.dp(16), app.dp(8), app.dp(16), app.dp(8));
  scroll.addView(list);
  listBayMeasurements(list, layout, openings, empty, 0xff1b4154);
  new AlertDialog.Builder(app).setTitle("Bölme iç ölçüleri · En × Boy").setView(scroll).setPositiveButton("Kapat", null).show();
 }

 private void listBayMeasurements(LinearLayout list, TemaLayout layout, Set<Integer> openings, Set<Integer> empty, int color) {
  app.planLabel(list, "Dış en hesabı", 14, color);
  for (List<Integer> chain : layout.widthChains) app.planLabel(list, layout.widthBreakdown(chain), 14, color);
  for (int i = 0; i < layout.bays.size(); i++) {
   int number = i + 1;
   TemaLayout.Opening bay = layout.bays.get(i);
   String kind = TemaBayDimensions.kind(bay, openings.contains(number), empty.contains(number));
   app.planLabel(list, TemaBayDimensions.caption(number, kind) + "  ·  "
    + TemaBayDimensions.size(bay.width(), bay.height()), 14, color);
  }
 }

 private void showPlan(TemaPosition position, TemaJoinery.Order order, TemaJoinery.Plan plan) {
  app.page(position.name + " · " + series.label + " imalat föyü");
  app.pageBack = this::open;
  LinearLayout drawingCard = section("Doğrama görünüşü");
  app.lightPanel(drawingCard);
  Set<Integer> leftHinges = leftHinges();
  drawingCard.addView(new TemaDiagramView(app, order, plan, series, leftHinges),
   new LinearLayout.LayoutParams(-1, app.dp(344)));
  app.planLabel(drawingCard, "Pay, profilin net açıklıktan aldığı toplam ölçüdür.", 13, 0xffc9dbe3);
  Button expand = new Button(app);
  expand.setText("Çizimi büyüt");
  WorkshopActions.style(app, expand, WorkshopActions.Tone.EDIT);
  InterfaceMotion.buttonFeedback(expand);
  drawingCard.addView(expand, new LinearLayout.LayoutParams(-1, app.dp(48)));
  expand.setOnClickListener(view -> showDetailedDrawing(order, plan, leftHinges));
  chooseButton(drawingCard, "Çizimi PDF al", () -> exportDrawing(position, order, plan));
  app.planLabel(drawingCard, mm(order.width) + " × " + mm(order.height) + " mm  ·  " + order.quantity + " doğrama", 15, 0xffecf5f7);
  int open = order.openingSashes.size();
  int verticalRecords = 0;
  for (TemaLayout.Record record : plan.layout.records) if (record.vertical) verticalRecords++;
  app.planLabel(drawingCard, (plan.panes.size() - open) + " sabit  ·  " + open + " açılır  ·  "
   + verticalRecords + " dikey, " + (plan.layout.records.size() - verticalRecords) + " yatay kayıt", 14, 0xffa7e9e3);
  listBayMeasurements(section("Bölme iç ölçüleri · En × Boy"), plan.layout,
   order.openingSashes, order.emptyCells, 0xffecf5f7);
  addOptimizationLink(section("Birleşik stok planı"), position);
  LinearLayout profiles = section("Profil ve orta kayıt kesimleri");
  showCuts(profiles, plan);
  app.planLabel(profiles, "Kasa köşesi " + plan.frameCorners + " · Kayıt bağlantı ucu "
   + plan.mullionEnds + " · Kanat köşesi " + open * order.quantity * 4, 14, 0xffa7e9e3);
  LinearLayout panes = section("Cam kesim ölçüleri");

  for (TemaJoinery.Pane pane : plan.panes) {
   app.planLabel(panes, "Bölme " + pane.number + (pane.sash != null ? " · Kanat" : " · Sabit") + "  ·  "
    + mm(pane.width) + " × " + mm(pane.height) + " mm  ·  " + order.quantity + " adet", 15, Color.WHITE);
  }
  app.planLabel(panes, "Toplam " + ShopDimensions.squareMeters(plan.glassArea)
   + " m² · " + plan.panes.size() * order.quantity + " cam", 17, 0xffa7ffdd);
  app.button("Çizimi düzenle", () -> { open(); editPosition(position, null); });
  app.button("Pozlara dön", this::open);
  app.button("Ana menü", app::home);
 }

 private void showDetailedDrawing(TemaJoinery.Order order, TemaJoinery.Plan plan, Set<Integer> leftHinges) {
  TemaDiagramRenderer renderer = new TemaDiagramRenderer(order, plan, series, leftHinges,
   app.getResources().getDisplayMetrics().density, false);
  TemaDrawingViewport viewport = new TemaDrawingViewport(app) {
   @Override protected void drawBoard(android.graphics.Canvas canvas) {
    renderer.draw(canvas, getWidth(), getHeight());
   }
   @Override protected void tap(float x, float y) { }
  };
  viewport.setContentDescription("İmalat çizimi. İki parmakla yakınlaştırın veya yakınlaştırma düğmelerini kullanın.");
  LinearLayout content = new LinearLayout(app);
  content.setOrientation(LinearLayout.VERTICAL);
  LinearLayout controls = horizontal(content);
  chooseButton(column(controls), "Uzaklaştır", () -> viewport.zoomBy(1 / 1.4f));
  chooseButton(column(controls), "Sığdır", viewport::fit);
  chooseButton(column(controls), "Yakınlaştır", () -> viewport.zoomBy(1.4f));
  int screenHeight = app.getResources().getDisplayMetrics().heightPixels;
  content.addView(viewport, new LinearLayout.LayoutParams(-1, screenHeight / 2));
  viewport.zoomBy(1.6f);
  new AlertDialog.Builder(app).setTitle("İmalat çizimi · mm").setView(content)
   .setPositiveButton("Kapat", null).show();
 }

 private void showCuts(LinearLayout parent, TemaJoinery.Plan plan) {
  int index = 0;
  for (Map.Entry<String, List<TemaJoinery.Cut>> group : plan.cutsByProfile().entrySet()) {
   if (series == TemaCatalog.Series.ALDOKS && series.isBead(group.getKey())) continue;
   LinearLayout card = app.planCard(parent, 0xff172b3c, 0xff224655, series.accent);
   TemaCatalog.Profile item = series.find(group.getKey());
   android.graphics.Bitmap bitmap = item == null || item.image == 0 ? null : BitmapFactory.decodeResource(app.getResources(), item.image);
   app.profileHeading(card, series.displayOf(group.getKey()), bitmap);
   for (TemaJoinery.Cut cut : group.getValue()) {
    String angle = cut.angle == cut.endAngle ? (cut.angle == 45 ? "45° gönye · uzun uç" : "90° düz")
     : cut.angle + "° / " + cut.endAngle + "° · uzun uç";
    app.planLabel(card, cut.role + "\n" + mm(cut.length) + " mm × " + cut.quantity + " adet  ·  " + angle, 15, Color.WHITE);
   }
   app.reveal(card, Math.min(160, index++ * 35));
  }
  LinearLayout beads = null;
  if (series == TemaCatalog.Series.ALDOKS) for (TemaJoinery.Cut cut : plan.cuts) {
   if (!series.isBead(cut.code)) continue;
   if (beads == null) beads = section("Cam çıtası kesimleri · Önce yataylar");
   app.planLabel(beads, series.displayOf(cut.code) + " · " + cut.role + "\n"
    + mm(cut.length) + " mm × " + cut.quantity + " adet · " + cut.angle + "°", 15, Color.WHITE);
  }
 }

 private void openOptimization(TemaPosition recent) {
  try {
   showOptimization(optimize(), recent);
  } catch (IllegalArgumentException error) {
   if (recent != null) openPositionSheet(recent);
   else toast(error.getMessage());
  }
 }

 private void showOptimization(TemaCutBatch.Result result, TemaPosition recent) {
  int stock = ShopDimensions.tenths(stockText, "Stok boyu");
  int kerf = ShopDimensions.tenths(kerfText, "Testere payı");
  app.page("Tema " + series.label + " · Birleşik stok planı");
  app.pageBack = this::open;
  app.note(android.text.TextUtils.join(" · ", readyCuts().keySet()));
  app.planSummary(stock, kerf, result.barCount, result.pieceCount, result.net, result.remaining, ShopDimensions.PER_MM);
  if (recent != null) app.button(recent.name + " imalat föyünü aç", () -> openPositionSheet(recent));
  int animated = 0;
  for (TemaCutBatch.Profile profile : result.profiles.values()) {
   LinearLayout group = section(cutTitle(profile.key, profile.label));
   TemaCatalog.Profile item = series.find(profile.key);
   if (item != null) app.profileHeading(group, item.dimensions(),
    item.image == 0 ? null : BitmapFactory.decodeResource(app.getResources(), item.image));
   int number = 0;
   for (TemaCutBatch.Bar bar : profile.bars) {
    app.planBar(group, ++number, bar.layout.pieces, bar.layout.stock, kerf, bar.layout.remaining(),
     animated++ < 12, ShopDimensions.PER_MM, bar.pieces);
   }
  }
  app.button("Pozlara ve stok ayarlarına dön", this::open);
  app.button("Ana menü", app::home);
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
    new AlertDialog.Builder(app).setTitle("Fotoğraf taslağı").setMessage("Okunan kayıt sayıları eşit aralıklarla çizilir. Gerçek konumları ve kanatları kontrol edin. Yeni bir poz açılır.").setNegativeButton("Vazgeç",null).setPositiveButton("Çizime aktar",(d,k)->newPosition(recognized)).show();
   });
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
     .setNegativeButton("Geri", null).setPositiveButton("Yeni poza al", (preview, which) -> {
      dialog.dismiss();
      newPosition(measured);
     }).show();
   } catch (IllegalArgumentException error) {
    toast(error.getMessage());
   }
  }));
  dialog.show();
 }

 private void toast(String message) { Toast.makeText(app, message, Toast.LENGTH_LONG).show(); }
 private static String mm(int value) { return ShopDimensions.mm(value); }
}
