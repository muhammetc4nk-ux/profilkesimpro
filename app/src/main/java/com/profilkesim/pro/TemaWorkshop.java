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
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
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
 private final int[] profileIndices = new int[3];
 private TemaCatalog.Series series = TemaCatalog.Series.C60;
 private int corner, sashCorner, continuous, photoRequest;
 private Uri cameraUri;
 private Uri drawingUri;
 private Bitmap drawing;
 private DrawingDimensions recognized = new DrawingDimensions(-1, -1);
 private CheckBox confirmed;
 private LinearLayout photoCard, form;

 TemaWorkshop(MainActivity app) { this.app = app; }

 void open() {
  app.page("Tema C60 / Aldoks imalat hesabı");
  form = app.body;
  fields.clear();
  confirmed = null;
  LinearLayout dimensions = section("Kasa dış ölçüleri");
  field(dimensions, "width", "Dış en (mm)", "", DECIMAL);
  field(dimensions, "height", "Dış yükseklik (mm)", "", DECIMAL);
  field(dimensions, "qty", "Aynı doğrama adedi", "1", InputType.TYPE_CLASS_NUMBER);
  photoCard = section("Çizimden dış kasa ölçüsü");
  showPhotoCard();

  LinearLayout system = section("Seri ve kesitler");
  chooseButton(system, "Seri · " + series.label, () -> choose("Tema serisi",
   new String[]{"C60", "Aldoks"}, series.ordinal(), index -> {
    remember();
    if (index != series.ordinal()) {
     series = TemaCatalog.Series.values()[index];
     for (int i = 0; i < 3; i++) {
      profileIndices[i] = 0;
      draft.remove("face" + i);
      draft.remove("code" + i);
     }
    }
    open();
   }));
  chooseButton(system, "Tema katalog PDF’ini aç", () -> openUrl(series.url));
  chooseButton(system, "Montaj kesiti kataloğunu aç", () -> openUrl(TemaCatalog.DETAILS));
  app.planLabel(system, "Katalogdaki yüz ölçüsü başlangıç değeridir. Kesim çizgisini seçtiğiniz montaj kesitine göre doğrulayın.", 13, 0xffc9dbe3);
  profile(system, 0, "Kasa", series.frames);
  profile(system, 1, "Orta kayıt", series.mullions);
  profile(system, 2, "Kanat", series.sashes);

  LinearLayout divisions = section("Kesim ve orta kayıt");
  String[] corners = {"45° gönye · uzun uç", "90° düz · dikeyler tam boy", "90° düz · yataylar tam boy"};
  Button cornerButton = chooseButton(divisions, corners[corner], null);
  cornerButton.setOnClickListener(v -> choose("Kasa köşe düzeni", corners, corner, i -> {
   corner = i;
   cornerButton.setText(corners[i]);
   invalidate();
  }));
  field(divisions, "vertical", "Dikey orta kayıt adedi", "0", InputType.TYPE_CLASS_NUMBER);
  field(divisions, "horizontal", "Yatay orta kayıt adedi", "0", InputType.TYPE_CLASS_NUMBER);
  String[] directions = {"Dikey kayıtlar sürekli", "Yatay kayıtlar sürekli"};
  Button directionButton = chooseButton(divisions, directions[continuous], null);
  directionButton.setOnClickListener(v -> choose("Kayıt birleşim sırası", directions, continuous, i -> {
   continuous = i;
   directionButton.setText(directions[i]);
   invalidate();
  }));
  field(divisions, "joint", "Kayıt bağlantı farkı (mm / uç, + veya −)", "", SIGNED);
  app.planLabel(divisions, "Kayıt uçları 90°. Düz temas için 0, bağlantı detayı için ölçtüğünüz uç farkını girin.", 13, 0xffc9dbe3);
  field(divisions, "columns", "Özel net sütun enleri (mm, noktalı virgülle)", "", InputType.TYPE_CLASS_TEXT);
  field(divisions, "rows", "Özel net sıra yükseklikleri (mm, noktalı virgülle)", "", InputType.TYPE_CLASS_TEXT);
  app.planLabel(divisions, "Özel açıklıklar boş bırakıldığında eşit bölünür. Hücreler yukarıdan aşağıya ve soldan sağa numaralanır.", 13, 0xffc9dbe3);

  LinearLayout glazing = section("Cam ve açılır kanatlar");
  field(glazing, "sash", "Açılır kanat hücreleri (ör. 2, 4)", "", InputType.TYPE_CLASS_TEXT);
  Button sashButton = chooseButton(glazing, "Kanat · " + corners[sashCorner], null);
  sashButton.setOnClickListener(v -> choose("Kanat köşe düzeni", corners, sashCorner, i -> {
   sashCorner = i;
   sashButton.setText("Kanat · " + corners[i]);
   invalidate();
  }));
  field(glazing, "sashGap", "Kanat boşluğu (mm / kenar, + veya −)", "", SIGNED);
  field(glazing, "frameGlass", "Sabit camın kasa tarafı net payı (mm / kenar)", "", SIGNED);
  field(glazing, "mullionGlass", "Sabit camın kayıt tarafı net payı (mm / kenar)", "", SIGNED);
  field(glazing, "sashGlass", "Kanat camı net payı (mm / kenar)", "", SIGNED);
  app.planLabel(glazing, "Net cam payı = camın profile girme miktarı − bırakılan boşluk. Uygun detayda fark yoksa 0 girin.", 13, 0xffc9dbe3);

  LinearLayout material = section("Stok ve doğrulama");
  EditText extra = field(material, "extraCuts", "Ek katalog kesimleri · kod;ölçü;adet;açı", "", InputType.TYPE_CLASS_TEXT);
  extra.setSingleLine(false);
  extra.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
  extra.getLayoutParams().height = app.dp(92);
  app.planLabel(material, "İsteğe bağlı · Her ek kesim ayrı satır. Örnek T.3101;850;2;90", 13, 0xffc9dbe3);
  field(material, "stock", "Stok boyları (mm, noktalı virgülle)", "6000;7000", InputType.TYPE_CLASS_TEXT);
  field(material, "kerf", "Parça arası testere payı (mm)", String.valueOf(MainActivity.DEFAULT_KERF), DECIMAL);
  app.planLabel(material, "Gönye boyu uzun uçtan alınır. Testere payını profil boyu doğrultusunda girin.", 13, 0xffc9dbe3);
  confirmed = new CheckBox(app);
  confirmed.setText("Dış ölçüleri, katalog kesitini ve montaj paylarını kontrol ettim.");
  confirmed.setTextColor(0xffeefbff);
  material.addView(confirmed, new LinearLayout.LayoutParams(-1, -2));
  app.savePhotoReview = this::remember;
  app.button("İmalat ve cam föyünü hesapla", this::calculate);
  app.button("Ana menü", () -> {
   remember();
   app.home();
  });
 }

 private LinearLayout section(String heading) {
  LinearLayout card = app.planCard(app.body, 0xfa132634, 0xfa223e4a, 0xff608b94);
  app.planLabel(card, heading, 18, 0xfff8dbab).setTypeface(null, android.graphics.Typeface.BOLD);
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

 private void profile(LinearLayout parent, int role, String label, TemaCatalog.Profile[] profiles) {
  TemaCatalog.Profile initial = profiles[profileIndices[role]];
  Button selector = chooseButton(parent, label + " · " + initial.label(), null);
  EditText code = field(parent, "code" + role, label + " profil kodu", initial.code, InputType.TYPE_CLASS_TEXT);
  EditText face = field(parent, "face" + role, label + " imalatta kullanılan yüz ölçüsü (mm)",
   ShopDimensions.mm(initial.face), DECIMAL);
  TextView source = app.planLabel(parent, "Katalog s. " + initial.page, 12, 0xffaac5d1);
  String[] labels = new String[profiles.length];
  for (int i = 0; i < labels.length; i++) labels[i] = profiles[i].label();
  selector.setOnClickListener(v -> choose(label + " katalog profili", labels, profileIndices[role], index -> {
   profileIndices[role] = index;
   TemaCatalog.Profile selected = profiles[index];
   selector.setText(label + " · " + selected.label());
   code.setText(selected.code);
   face.setText(ShopDimensions.mm(selected.face));
   source.setText("Katalog s. " + selected.page);
  }));
 }

 private Button chooseButton(LinearLayout parent, String label, Runnable action) {
  Button button = new Button(app);
  button.setAllCaps(false);
  button.setText(label);
  button.setTextColor(0xffeff8fd);
  button.setBackground(app.rgbPanel(0xff173041, 0xff245462, 0xff638f9e));
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
 }

 void saveState(Bundle out) {
  boolean editing = app.body == form && form != null;
  if (editing) remember();
  Bundle state = new Bundle();
  for (Map.Entry<String, String> entry : draft.entrySet()) state.putString(entry.getKey(), entry.getValue());
  out.putBundle("temaDraft", state);
  out.putIntArray("temaSelection", new int[]{series.ordinal(), corner, continuous,
   profileIndices[0], profileIndices[1], profileIndices[2], sashCorner});
  out.putBoolean("temaEditing", editing);
  if (cameraUri != null) out.putString("temaCamera", cameraUri.toString());
  if (drawingUri != null) out.putString("temaDrawing", drawingUri.toString());
 }

 boolean restoreState(Bundle in) {
  if (in == null) return false;
  Bundle state = in.getBundle("temaDraft");
  if (state != null) for (String key : state.keySet()) draft.put(key, state.getString(key));
  int[] selection = in.getIntArray("temaSelection");
  if (selection != null && selection.length == 7) {
   series = TemaCatalog.Series.values()[selection[0]];
   corner = selection[1];
   continuous = selection[2];
   System.arraycopy(selection, 3, profileIndices, 0, 3);
   sashCorner = selection[6];
  }
  String camera = in.getString("temaCamera"), photo = in.getString("temaDrawing");
  if (camera != null) cameraUri = Uri.parse(camera);
  if (photo != null) loadDrawing(Uri.parse(photo), false);
  if (!in.getBoolean("temaEditing")) return false;
  open();
  return true;
 }

 private String value(String key) { return draft.get(key).trim(); }
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

 private Set<Integer> sashes() {
  Set<Integer> numbers = new LinkedHashSet<>();
  if (!value("sash").isEmpty()) for (String item : value("sash").split("[,;\\s]+")) {
   numbers.add(MainActivity.cutNumber(item, "Kanat hücresi"));
  }
  return numbers;
 }

 private void calculate() {
  remember();
  if (!confirmed.isChecked()) {
   toast("Önce dış ölçüleri, seçilen kesitleri ve montaj paylarını onaylayın.");
   return;
  }
  try {
   TemaJoinery.Order o = new TemaJoinery.Order();
   o.width = mm("width", "Dış en");
   o.height = mm("height", "Dış yükseklik");
   o.quantity = count("qty", "Doğrama adedi");
   if (o.quantity < 1 || o.quantity > 100) throw new IllegalArgumentException("Doğrama adedi 1–100 arasında olmalı.");
   o.frameCode = profileCode(0);
   o.frameFace = mm("face0", "Kasa yüz ölçüsü");
   o.corner = TemaJoinery.Corner.values()[corner];
   o.vertical = count("vertical", "Dikey kayıt adedi");
   o.horizontal = count("horizontal", "Yatay kayıt adedi");
   if (o.vertical < 0 || o.horizontal < 0 || o.vertical > 12 || o.horizontal > 12) {
    throw new IllegalArgumentException("Yatay ve dikey kayıt adedi 0–12 arasında olmalı.");
   }
   o.continuous = TemaJoinery.Continuous.values()[continuous];
   boolean hasRecords = o.vertical > 0 || o.horizontal > 0;
   if (hasRecords) {
    o.mullionCode = profileCode(1);
    o.mullionFace = mm("face1", "Orta kayıt yüz ölçüsü");
    o.joint = mm("joint", "Kayıt bağlantı farkı");
   }
   o.columnOpenings = openings("columns");
   o.rowOpenings = openings("rows");
   o.openingSashes = sashes();
   if (!o.openingSashes.isEmpty()) {
    o.sashCorner = TemaJoinery.Corner.values()[sashCorner];
    o.sashCode = profileCode(2);
    o.sashFace = mm("face2", "Kanat yüz ölçüsü");
    o.sashGap = mm("sashGap", "Kanat boşluğu");
    o.sashGlass = mm("sashGlass", "Kanat camı net payı");
   }
   int cells = (o.vertical + 1) * (o.horizontal + 1);
   if (o.openingSashes.size() < cells) {
    o.frameGlass = mm("frameGlass", "Kasa tarafı cam net payı");
    if (hasRecords) o.mullionGlass = mm("mullionGlass", "Kayıt tarafı cam net payı");
   }
   TemaJoinery.Plan plan = TemaJoinery.calculate(o);
   TemaJoinery.addManualCuts(plan, value("extraCuts"));
   showPlan(o, plan);
  } catch (IllegalArgumentException error) {
   toast(error.getMessage());
  }
 }

 private void showPlan(TemaJoinery.Order order, TemaJoinery.Plan plan) {
  app.page("Tema " + series.label + " · imalat föyü");
  LinearLayout drawingCard = section("Doğrama görünüşü");
  drawingCard.addView(new TemaDiagramView(app, order, plan), new LinearLayout.LayoutParams(-1, app.dp(250)));
  app.planLabel(drawingCard, mm(order.width) + " × " + mm(order.height) + " mm dış ölçü  ·  "
   + order.quantity + " doğrama", 15, 0xffecf5f7);
  app.planLabel(drawingCard, "Ölçüler 0,1 mm çözünürlükle hazırlanır.", 13, 0xffc9dbe3);
  LinearLayout aluminium = section("Profil kesimleri");
  for (TemaJoinery.Cut cut : plan.cuts) {
   String angle = cut.angle == 45 ? "45° gönye · uzun uç" : "90° düz";
   app.planLabel(aluminium, cut.code + " · " + cut.role + "\n" + mm(cut.length) + " mm × "
    + cut.quantity + " adet  ·  " + angle, 15, Color.WHITE);
  }
  app.planLabel(aluminium, "Kasa köşesi " + order.quantity * 4 + " · Orta kayıt bağlantı ucu "
   + plan.mullionEnds + " · Kanat köşesi " + order.openingSashes.size() * order.quantity * 4, 14, 0xffa7e9e3);
  LinearLayout panes = section("Net cam ölçüleri");
  for (TemaJoinery.Pane pane : plan.panes) {
   app.planLabel(panes, "Hücre " + pane.number + (pane.sash ? " · Kanat" : " · Sabit") + "  ·  "
    + mm(pane.width) + " × " + mm(pane.height) + " mm  ·  " + order.quantity + " adet", 15, Color.WHITE);
  }
  app.planLabel(panes, "Toplam " + ShopDimensions.squareMeters(plan.glassArea)
   + " m² · " + plan.panes.size() * order.quantity + " cam", 17, 0xffa7ffdd);
  app.button("Profilleri stoklara yerleştir", () -> optimize(order, plan));
  app.button("Ölçüleri düzenle", this::open);
  app.button("Ana menü", app::home);
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
  chooseButton(photoCard, "Çizim fotoğrafı seç", () -> {
   Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT);
   picker.addCategory(Intent.CATEGORY_OPENABLE);
   picker.setType("image/*");
   picker.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
   app.startActivityForResult(picker, DRAWING_FILE);
  });
  chooseButton(photoCard, "Çizimin fotoğrafını çek", this::takePhoto);
  if (drawing == null) return;
  ImageView preview = new ImageView(app);
  preview.setImageBitmap(drawing);
  preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
  preview.setContentDescription("Yüklenen çizim");
  photoCard.addView(preview, new LinearLayout.LayoutParams(-1, app.dp(166)));
  if (recognized.width > 0 || recognized.height > 0) {
   String w = recognized.width > 0 ? mm(recognized.width) + " mm" : "okunamadı";
   String h = recognized.height > 0 ? mm(recognized.height) + " mm" : "okunamadı";
   app.planLabel(photoCard, "Etiketli ölçü önerisi · En " + w + " · Yükseklik " + h, 14, 0xffa7e9e3);
   chooseButton(photoCard, "Okunan ölçüleri kontrol için al", () -> applyDimensions(recognized));
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
  if (announce) toast("Çizim açılıyor…");
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
     if (announce && !app.isDestroyed() && requestId == photoRequest) toast("Çizim fotoğrafı açılamadı.");
    });
    return;
   }
   DrawingDimensions read = new DrawingDimensions(-1, -1);
   try (TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)) {
    read = DrawingDimensions.read(Tasks.await(recognizer.process(InputImage.fromBitmap(image, 0))).getText());
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
    recognized = resultRead;
    if (app.body == form) showPhotoCard();
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
  invalidate();
 }

 private void toast(String message) { Toast.makeText(app, message, Toast.LENGTH_LONG).show(); }
 private static String mm(int value) { return ShopDimensions.mm(value); }
}
