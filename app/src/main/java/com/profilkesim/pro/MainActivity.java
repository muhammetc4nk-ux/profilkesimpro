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
  brand.setBackgroundColor(navy);
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

  ScrollView scroll=new ScrollView(this);root.addView(scroll);
  body=new LinearLayout(this);body.setOrientation(1);
  body.setPadding(dp(14),dp(12),dp(14),dp(20));scroll.addView(body);
  TextView h=text(title,23);h.setTypeface(null,Typeface.BOLD);
  h.setBackground(box(0xf2ffffff));body.addView(h);
 }
 void note(String s){
  TextView t=text(s,14);t.setBackground(box(0xeeffffff));body.addView(t);
 }
 void button(String s,Runnable r){
  Button b=new Button(this);b.setText(s);b.setAllCaps(false);
  b.setTextColor(Color.WHITE);b.setBackground(box(teal));
  LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(54));
  p.setMargins(0,dp(6),0,dp(6));body.addView(b,p);
  b.setOnClickListener(v->r.run());
 }
 EditText input(String hint,String val){
  TextView label=text(hint,15);label.setTypeface(null,Typeface.BOLD);
  label.setBackground(box(0xeeffffff));body.addView(label);
  EditText e=new EditText(this);e.setText(val);e.setHint(hint);
  e.setTextSize(17);e.setPadding(dp(12),dp(12),dp(12),dp(12));
  e.setBackground(box(0xf8ffffff));
  LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(55));
  p.setMargins(0,dp(6),0,dp(6));body.addView(e,p);return e;
 }
 int val(EditText e){return Integer.parseInt(e.getText().toString().trim());}
 void result(String s){
  TextView t=text(s,16);t.setTextIsSelectable(true);
  t.setBackground(box(0xf8ffffff));body.addView(t);
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
  Pattern codes=Pattern.compile(
   "[B8]\\s*1\\s*5\\s*0\\s*[0-9]{2}",Pattern.CASE_INSENSITIVE);
  for(Text.TextBlock block:doc.getTextBlocks())
   for(Text.Line line:block.getLines()){
    Matcher match=codes.matcher(
     line.getText().toUpperCase(Locale.ROOT));
    if(!match.find())continue;
    String code=match.group().replaceAll("\\s+","")
     .replaceFirst("^8","B");
    if(!seen.add(code))continue;
    Rect r=line.getBoundingBox();if(r==null)continue;
    int cy=r.centerY();Bitmap icon=null;
    try{
     int x=(int)(bmp.getWidth()*.07);
     int w=(int)(bmp.getWidth()*.16);
     int h=Math.max(32,r.height()*3);
     int y=Math.max(0,cy-h/2);
     h=Math.min(h,bmp.getHeight()-y);
     if(w>0&&h>0)icon=Bitmap.createBitmap(bmp,x,y,w,h);
    }catch(Exception ignored){}
    rows.add(new CutRow(
     code,profileName(code),-1,-1,icon,cy));
   }
  Collections.sort(rows,(a,b)->Integer.compare(a.y,b.y));
  return rows;
 }
 void readColumns(Bitmap bmp){
  if(bmp==null||detected.isEmpty()){
   showDetected();return;
  }
  scanColumn(bmp,.555,.645,false,()->
   scanColumn(bmp,.680,.765,true,()->showDetected()));
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
   LinearLayout line=new LinearLayout(this);
   line.setOrientation(0);
   line.setGravity(Gravity.CENTER_VERTICAL);
   line.setBackground(box(0xf5ffffff));
   body.addView(line);
   if(r.icon!=null){
    ImageView iv=new ImageView(this);
    iv.setImageBitmap(r.icon);
    iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
    line.addView(iv,
     new LinearLayout.LayoutParams(dp(95),dp(75)));
   }
   TextView title=text(r.code+"\n"+r.name,14);
   line.addView(title,
    new LinearLayout.LayoutParams(0,dp(85),1));
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
  confirmed.setTextColor(navy);
  confirmed.setBackground(box(0xf5ffffff));
  body.addView(confirmed);
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
    for(EditText[] e:edits){
     String name=e[0].getText().toString().trim();
     String ms=e[1].getText().toString().trim();
     String qs=e[2].getText().toString().trim();
     if(ms.isEmpty()||qs.isEmpty())
      throw new Exception("Okunamayan ölçü/adet var");
     int m=Integer.parseInt(ms),q=Integer.parseInt(qs);
     if(q==0)continue;
     if(m<=0||q<0||q>1000||m+kerf>length)
      throw new Exception();
     if(!groups.containsKey(name))
      groups.put(name,new ArrayList<>());
     for(int j=0;j<q;j++)
      groups.get(name).add(m);
    }
    if(!extraName.getText().toString().trim().isEmpty()&&
     !extraMm.getText().toString().trim().isEmpty()){
     String n=extraName.getText().toString().trim();
     int m=val(extraMm),q=val(extraQty);
     if(m<=0||q<0||q>1000||m+kerf>length)
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
    StringBuilder out=new StringBuilder(
     "STOK BOYU: "+length+
     " mm\nTESTERE PAYI: "+kerf+" mm / parça\n\n");
    int allBars=0;long allWaste=0;
    for(Map.Entry<String,ArrayList<Integer>> entry:
     groups.entrySet()){
     ArrayList<Integer> parts=entry.getValue();
     Collections.sort(parts,Collections.reverseOrder());
     ArrayList<ArrayList<Integer>> bins=new ArrayList<>();
     ArrayList<Integer> used=new ArrayList<>();
     for(int size:parts){
      int target=-1;
      for(int i=0;i<used.size();i++)
       if(used.get(i)+size+kerf<=length){
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
     out.append("■ ").append(entry.getKey())
      .append("\n").append(parts.size())
      .append(" parça / ").append(bins.size())
      .append(" ham profil\n");
     allBars+=bins.size();
     for(int i=0;i<bins.size();i++){
      int waste=length-used.get(i);
      allWaste+=waste;
      out.append("Boy ").append(i+1)
       .append(" | KESİM ").append(bins.get(i))
       .append(" | KALAN/FİRE ").append(waste)
       .append(" mm\n");
     }
     out.append("\n");
    }
    out.append("TOPLAM HAM PROFİL: ")
     .append(allBars)
     .append(" boy\nTOPLAM KALAN/FİRE: ")
     .append(allWaste).append(" mm\n");
    page("Kesim optimizasyon sonucu");
    result(out.toString());
    note("Kalan parça tekrar kullanılabiliyorsa gerçek hurda/fire sayılmayabilir. Kesim sırası ve testere payını imalattan önce doğrulayın.");
    button("Ana menü",()->home());
   }catch(Exception e){bad();}
  });
  button("Yeni fotoğraf çek / seç",()->photo());
  button("Ana menü",()->home());
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
      size+saw>length||
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
      if(used.get(i)+size+saw<=length){
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
    StringBuilder out=new StringBuilder(
     "Gerekli profil: "+bins.size()+
     " boy\nParça: "+pieces.size()+
     " adet\nNet kesim: "+total+
     " mm\nKalan: "+
     ((long)bins.size()*length-total-
      (long)pieces.size()*saw)+" mm\n\n");
    for(int i=0;i<bins.size();i++)
     out.append("Boy ").append(i+1)
      .append(": ").append(bins.get(i))
      .append("\nKalan: ")
      .append(length-used.get(i))
      .append(" mm\n\n");
    result(out.toString());
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
