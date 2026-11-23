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
 EditText recognized;
 Uri cameraUri; Bitmap sourceBitmap;
 ArrayList<CutRow> detected=new ArrayList<>();
 static class CutRow {
  String code,name;int mm,qty,y;Bitmap icon;
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
 void page(String title){
  root=new LinearLayout(this);root.setOrientation(1);
  root.setBackgroundResource(R.drawable.cambalkon);
  setContentView(root);
  TextView brand=text("muhammet can . K",12);
  brand.setTextColor(Color.WHITE);brand.setGravity(Gravity.CENTER);
  brand.setBackground(rgbPanel(0xff0e1933, 0xff233953, 0xff3ab6c9));
  root.addView(brand,new LinearLayout.LayoutParams(-1,dp(44)));
  
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
  TextView label=text(hint,15);label.setTypeface(null,Typeface.BOLD);
  label.setTextColor(0xfff0f9ff);
  addTextPanel(label, 0xff19253f, 0xff254965, 0xff627ea7);
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
  p.setMargins(0,dp(6),0,dp(6));body.addView(e,p);return e;
 }
 int val(EditText e){return Integer.parseInt(e.getText().toString().trim());}
 void result(String s){
  TextView t=text(s,16);t.setTextIsSelectable(true);
  t.setTextColor(0xffeffaff);
  addTextPanel(t, 0xff14233e, 0xff285575, 0xff4ecade);
  reveal(t, 0);
 }
 void bad(){Toast.makeText(this,"Ölçü veya adet hatalı",Toast.LENGTH_LONG).show();}
 @Override public void onCreate(Bundle b){super.onCreate(b);home();}
 @Override public void onBackPressed(){home();}
 void home(){
  page("Profil Kesim Pro");
  note("Cam balkon ve doğrama hesaplama • Ölçüler mm");
  button("Fotoğraftan kesim listesi oku",()->photo());
  button("Profil kesim optimizasyonu",()->opt());
  button("Cam alanı ve adet hesabı",()->glass());
  button("Kanat eşit bölme hesabı",()->panels());
  for(String s:new String[]{
   "Tiara Twin","Statü","Sürme","Giyotin","Cam tavan",
   "PVC","Sineklik","Duşakabin","ASAŞ RST116 Hebeschiebe"
  })button(s,()->system(s));
 }
 void photo(){
  page("Fotoğraftan kesim listesi");
  note("Albert Genau kesim föyü: kod, profil adı, ÖLÇÜ (mm), MİKTAR (adet) ve soldaki kesit çizimini okumaya çalışır. Net ve düz fotoğraf çekin. Sonuçları imalattan önce kontrol edin.");
  button("Galeriden fotoğraf seç",()->{
   Intent i=new Intent(Intent.ACTION_GET_CONTENT);
   i.setType("image/*");startActivityForResult(i,PICK_IMAGE);
  });
  button("Tam çözünürlüklü fotoğraf çek",()->{
   try{
    ContentValues v=new ContentValues();
    v.put(MediaStore.Images.Media.DISPLAY_NAME,
     "profil_foy_"+System.currentTimeMillis()+".jpg");
    v.put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg");
    cameraUri=getContentResolver().insert(
     MediaStore.Images.Media.EXTERNAL_CONTENT_URI,v);
    if(cameraUri==null)throw new Exception("Fotoğraf dosyası oluşturulamadı");
    Intent i=new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
    i.putExtra(MediaStore.EXTRA_OUTPUT,cameraUri);
    i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION|
     Intent.FLAG_GRANT_READ_URI_PERMISSION);
    startActivityForResult(i,TAKE_PHOTO);
   }catch(Exception e){
    Toast.makeText(this,"Kamera açılamadı: "+e.getMessage(),
     Toast.LENGTH_LONG).show();
   }
  });
  recognized=input("Okunan ham metin (kontrol amaçlı)","");
  recognized.setSingleLine(false);recognized.setMinLines(5);
  recognized.getLayoutParams().height=dp(160);
  button("Okunan profil satırlarını göster",()->showDetected());
  button("Ana menü",()->home());
 }
 @Override protected void onActivityResult(int request,int result,Intent data){
  super.onActivityResult(request,result,data);
  if(result!=RESULT_OK)return;
  try{
   Uri uri=request==TAKE_PHOTO?cameraUri:
    (data==null?null:data.getData());
   if(uri==null)throw new Exception("Fotoğraf seçilemedi");
   sourceBitmap=MediaStore.Images.Media.getBitmap(getContentResolver(),uri);
   InputImage image=InputImage.fromFilePath(this,uri);
   Toast.makeText(this,"Fotoğraf okunuyor...",Toast.LENGTH_SHORT).show();
   TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    .process(image).addOnSuccessListener(t->{
     detected=parseSheet(t,sourceBitmap);
     if(recognized!=null)recognized.setText(t.getText());
     readColumns(sourceBitmap);
    }).addOnFailureListener(e->
     Toast.makeText(this,"Okuma hatası: "+e.getMessage(),
      Toast.LENGTH_LONG).show());
  }catch(Exception e){
   Toast.makeText(this,"Fotoğraf açılamadı: "+e.getMessage(),
    Toast.LENGTH_LONG).show();
  }
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

void readColumns(Bitmap bmp) {
    if (bmp == null || detected.isEmpty()) {
        showDetected();
        return;
    }

    TextRecognition.getClient(
        TextRecognizerOptions.DEFAULT_OPTIONS
    ).process(InputImage.fromBitmap(bmp, 0))
    .addOnSuccessListener(doc -> {

        // Varsayılan sütun merkezleri
        float measureX = bmp.getWidth() * 0.60f;
        float quantityX = bmp.getWidth() * 0.72f;

        // Föydeki gerçek başlıkları bul
        for (Text.TextBlock block : doc.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                String word = line.getText()
                    .toUpperCase(Locale.ROOT)
                    .replace("Ü", "U")
                    .replace("İ", "I")
                    .replace("Ö", "O")
                    .trim();

                Rect rect = line.getBoundingBox();
                if (rect == null) continue;

                if (word.equals("OLCU")) {
                    measureX = rect.centerX();
                }

                if (word.equals("MIKTAR")) {
                    quantityX = rect.centerX();
                }
            }
        }

        scanCells(bmp, measureX, quantityX, 0);

    }).addOnFailureListener(e -> showDetected());
}

void scanCells(
    Bitmap bmp,
    float measureX,
    float quantityX,
    int index
) {
    if (index >= detected.size() * 2) {
        for (CutRow row : detected) {
            if (row.mm == 0 && row.qty < 0) {
                row.qty = 0;
            }
        }
        showDetected();
        return;
    }

    int rowIndex = index / 2;
    boolean quantity = index % 2 == 1;

    CutRow row = detected.get(rowIndex);

    // Satırın üst ve alt sınırlarını hesapla
    int top;
    int bottom;

    if (rowIndex == 0) {
        top = row.y - 35;
    } else {
        top = (detected.get(rowIndex - 1).y + row.y) / 2;
    }

    if (rowIndex == detected.size() - 1) {
        bottom = row.y + 35;
    } else {
        bottom = (row.y + detected.get(rowIndex + 1).y) / 2;
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
        scanCells(bmp, measureX, quantityX, index + 1);
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

        TextRecognition.getClient(
            TextRecognizerOptions.DEFAULT_OPTIONS
        ).process(InputImage.fromBitmap(enlarged, 0))
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
                bmp, measureX, quantityX, index + 1
            );

        }).addOnFailureListener(e -> {
            scanCells(
                bmp, measureX, quantityX, index + 1
            );
        });

    } catch (Exception e) {
        scanCells(
            bmp, measureX, quantityX, index + 1
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
 void showDetected(){
  page("Profil kesim föyü – kontrol");
  note("DİKKAT: OCR rakam eksiltebilir! Örneğin 2040 mm, 204 mm okunabilir. Her profilin KESİM ÖLÇÜSÜ ve ADEDİNİ orijinal föyle tek tek karşılaştırın. Onay verilmeden hesap yapılmaz. 0 adet kesime katılmaz.");
  ArrayList<EditText[]> edits=new ArrayList<>();
  ArrayList<CutRow> list=new ArrayList<>(detected);
  if(list.isEmpty())note(
   "Otomatik profil kodu bulunamadı. Fotoğrafı daha yakından ve düz çekin veya aşağıdan elle satır ekleyin.");
  for(CutRow r:list){
   if(r.mm<0||r.qty<0)
    note("OKUNAMADI: "+r.code+
     " için ölçü veya adet elle girilmeli.");
   LinearLayout profile = planCard(body, 0xff121f38, 0xff244361, 0xff5685a4);
   profileHeading(profile, r.code + "\n" + r.name, r.icon);
   if(r.mm>0&&r.mm<300)
    note("UYARI: "+r.code+" için "+r.mm+
     " mm okundu. Son rakam eksik olabilir; föyden doğrulayın!");
   EditText name=input(
    "PROFİL KODU VE ADI",r.code+" "+r.name);
   
EditText mm = input(
    "KESİM ÖLÇÜSÜ (mm)",
    r.mm < 0 ? "" : String.valueOf(r.mm));

EditText qty = input(
    "KESİM ADEDİ",
    r.qty < 0 ? "" : String.valueOf(r.qty));

if (r.mm < 0) {
    mm.setHint("OKUNAMADI - ölçüyü girin");
    mm.setError("Kesim ölçüsü okunamadı");
}

if (r.qty < 0) {
    qty.setHint("OKUNAMADI - adedi girin");
    qty.setError("Kesim adedi okunamadı");
}

if (r.mm == 0 && r.qty == 0) {
    note(r.code + " - 0 mm / 0 adet: Kesim yapılmayacak.");
}

   edits.add(new EditText[]{name,mm,qty});
  }
  note("Eksik veya yanlış okunan satırları aşağıya ekleyebilirsiniz.");
  EditText extraName=input("Ek profil kodu / adı","");
  EditText extraMm=input("Ek kesim ölçüsü (mm)","");
  EditText extraQty=input("Ek kesim adedi","");
  EditText stock=input(
   "STOK BOYU (mm) – ham profil uzunluğu","6000");
  EditText saw=input(
   "TESTERE PAYI (mm) – her parça için ayrılan pay","3");
  CheckBox confirmed=new CheckBox(this);
  confirmed.setText(
   "Fotoğraftaki TÜM profil kodlarını, kesim ölçülerini (mm) ve adetleri tek tek karşılaştırdım, doğruladım.");
  confirmed.setTextColor(0xffeffaff);
  confirmed.setButtonTintList(android.content.res.ColorStateList.valueOf(0xff5af4ca));
  addTextPanel(confirmed, 0xff15283b, 0xff244d5a, 0xff51cfc0);
  button("Kontrol edilen kesim planını ve kalanları hesapla",()->{
   if(!confirmed.isChecked()){
    Toast.makeText(this,
     "Önce fotoğraftaki bütün ölçüleri kontrol edip onaylayın.",
     Toast.LENGTH_LONG).show();return;
   }
   try{
    int length=val(stock),kerf=val(saw);
    if(length<=0||kerf<0)throw new Exception();
    TreeMap<String,ArrayList<Integer>> groups=new TreeMap<>();
    HashMap<String, Bitmap> profileIcons = new HashMap<>();
    for (int rowIndex = 0; rowIndex < edits.size(); rowIndex++) {
     EditText[] e = edits.get(rowIndex);
     String name=e[0].getText().toString().trim();
     String ms=e[1].getText().toString().trim();
     String qs=e[2].getText().toString().trim();
     if(ms.isEmpty()||qs.isEmpty())
      throw new Exception("Okunamayan ölçü/adet var");
     int m=Integer.parseInt(ms),q=Integer.parseInt(qs);
     if(q==0)continue;
     if(m<=0||q<0||q>1000||(long)m+kerf>length)
      throw new Exception();
     if(!groups.containsKey(name))
      groups.put(name,new ArrayList<>());
     Bitmap icon = list.get(rowIndex).icon;
     if (icon != null) profileIcons.putIfAbsent(name, icon);
     for(int j=0;j<q;j++)
      groups.get(name).add(m);
    }
    if(!extraName.getText().toString().trim().isEmpty()&&
     !extraMm.getText().toString().trim().isEmpty()){
     String n=extraName.getText().toString().trim();
     int m=val(extraMm),q=val(extraQty);
     if(m<=0||q<0||q>1000||(long)m+kerf>length)
      throw new Exception();
     if(!groups.containsKey(n))
      groups.put(n,new ArrayList<>());
     for(int j=0;j<q;j++)
      groups.get(n).add(m);
    }
    if(groups.isEmpty()){
     Toast.makeText(this,
      "Önce geçerli ölçü ve adet girin",
      Toast.LENGTH_LONG).show();return;
    }
    LinearLayout plans = new LinearLayout(this);
    plans.setOrientation(LinearLayout.VERTICAL);
    int allBars = 0, allPieces = 0;
    long allWaste = 0, net = 0;
    for(Map.Entry<String,ArrayList<Integer>> entry:
     groups.entrySet()){
     ArrayList<Integer> parts=entry.getValue();
     Collections.sort(parts,Collections.reverseOrder());
     ArrayList<ArrayList<Integer>> bins=new ArrayList<>();
     ArrayList<Integer> used=new ArrayList<>();
     for(int size:parts){
      int target=-1;
      for(int i=0;i<used.size();i++)
       if((long)used.get(i)+size+kerf<=length){
        target=i;break;
       }
      if(target<0){
       target=bins.size();
       bins.add(new ArrayList<>());
       used.add(0);
      }
      bins.get(target).add(size);
      used.set(target,used.get(target)+size+kerf);
     }
     if (bins.isEmpty()) continue;
     LinearLayout group = planCard(plans, 0xff172b44, 0xff254662, 0xff597d9b);
     profileHeading(group, entry.getKey(), profileIcons.get(entry.getKey()));
     allPieces += parts.size();
     for (int i = 0; i < bins.size(); i++) {
      int waste = length - used.get(i);
      allWaste += waste;
      net += used.get(i) - (long) kerf * bins.get(i).size();
      planBar(group, i + 1, bins.get(i), length, kerf, waste, allBars < 12);
      allBars++;
     }
    }
    page("Kesim optimizasyon sonucu");
    planSummary(length, kerf, allBars, allPieces, net, allWaste);
    body.addView(plans);
    note("Kalan parça tekrar kullanılabiliyorsa gerçek hurda/fire sayılmayabilir. Kesim sırası ve testere payını imalattan önce doğrulayın.");
    button("Ana menü",()->home());
   }catch(Exception e){bad();}
  });
  button("Yeni fotoğraf çek / seç",()->photo());
  button("Ana menü",()->home());
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
  view.setBackground(rgbPanel(start, end, edge));
  view.setPadding(dp(14), dp(12), dp(14), dp(12));
  LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
  params.setMargins(0, dp(7), 0, dp(3));
  body.addView(view, params);
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
  planLabel(card, "Stok " + length + " mm  ·  Testere payı " + kerf + " mm/parça", 14, 0xffd2e8f4);
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
