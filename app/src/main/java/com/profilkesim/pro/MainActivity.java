package com.profilkesim.pro;
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
 static class CutRow {String code,name;int mm,qty;Bitmap icon;CutRow(String c,String n,int m,int q,Bitmap b){code=c;name=n;mm=m;qty=q;icon=b;}}
 static final int PICK_IMAGE=41, TAKE_PHOTO=42;
 int navy=0xff173447, teal=0xff087e89;
 int dp(int n){return (int)(n*getResources().getDisplayMetrics().density);}
 GradientDrawable box(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(12));return d;}
 TextView text(String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(navy);t.setPadding(dp(12),dp(12),dp(12),dp(12));return t;}
 void page(String title){
  root=new LinearLayout(this);root.setOrientation(1);
  root.setBackgroundResource(com.profilkesim.pro.R.drawable.cambalkon);
  setContentView(root);
  TextView brand=text("muhammet can . K",12);brand.setTextColor(Color.WHITE);brand.setGravity(Gravity.CENTER);brand.setBackgroundColor(navy);
  root.addView(brand,new LinearLayout.LayoutParams(-1,dp(44)));
  ScrollView scroll=new ScrollView(this);root.addView(scroll);
  body=new LinearLayout(this);body.setOrientation(1);body.setPadding(dp(14),dp(12),dp(14),dp(20));scroll.addView(body);
  TextView h=text(title,23);h.setTypeface(null,Typeface.BOLD);h.setBackground(box(0xf2ffffff));body.addView(h);
 }
 void note(String s){TextView t=text(s,14);t.setBackground(box(0xeeffffff));body.addView(t);}
 void button(String s,Runnable r){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextColor(Color.WHITE);b.setBackground(box(teal));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(54));p.setMargins(0,dp(6),0,dp(6));body.addView(b,p);b.setOnClickListener(v->r.run());}
 EditText input(String hint,String val){EditText e=new EditText(this);e.setText(val);e.setHint(hint);e.setTextSize(17);e.setPadding(dp(12),dp(12),dp(12),dp(12));e.setBackground(box(0xf8ffffff));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(55));p.setMargins(0,dp(6),0,dp(6));body.addView(e,p);return e;}
 int val(EditText e){return Integer.parseInt(e.getText().toString().trim());}
 void result(String s){TextView t=text(s,16);t.setTextIsSelectable(true);t.setBackground(box(0xf8ffffff));body.addView(t);}
 void bad(){Toast.makeText(this,"Ölçü veya adet hatalı",Toast.LENGTH_LONG).show();}
 @Override public void onCreate(Bundle b){super.onCreate(b);home();}
 @Override public void onBackPressed(){home();}
 void home(){
  page("Profil Kesim Pro");note("Cam balkon ve doğrama hesaplama • Ölçüler mm");
  button("Fotoğraftan kesim föyü oku",()->photo());
  button("Profil kesim optimizasyonu",()->opt());
  button("Cam alanı ve adet hesabı",()->glass());
  button("Kanat eşit bölme hesabı",()->panels());
  for(String s:new String[]{"Tiara Twin","Statü","Sürme","Giyotin","Cam tavan","PVC","Sineklik","Duşakabin","ASAŞ RST116 Hebeschiebe"})button(s,()->system(s));
 }
 void photo(){
  page("Fotoğraftan kesim föyü");
  note("Albert Genau kesim föyünü düz zeminde, tamamı kadraja girecek şekilde çekin. Profil kodu, ölçü ve adet ayrı sütunlardan okunur.");
  button("Galeriden fotoğraf seç",()->{Intent i=new Intent(Intent.ACTION_GET_CONTENT);i.setType("image/*");startActivityForResult(i,PICK_IMAGE);});
  button("Kamerayla tam çözünürlüklü fotoğraf çek",()->{
   try{
    ContentValues values=new ContentValues();values.put(MediaStore.Images.Media.DISPLAY_NAME,"profil_foy_"+System.currentTimeMillis()+".jpg");values.put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg");
    cameraUri=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);
    Intent i=new Intent(MediaStore.ACTION_IMAGE_CAPTURE);i.putExtra(MediaStore.EXTRA_OUTPUT,cameraUri);i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);startActivityForResult(i,TAKE_PHOTO);
   }catch(Exception e){note("Kamera açılamadı: "+e.getMessage());}
  });
  recognized=input("Fotoğraftan okunan metin","");
  recognized.setSingleLine(false);recognized.setMinLines(6);recognized.getLayoutParams().height=dp(180);
  button("Okunan profilleri göster",()->showDetected());
  button("Ana menü",()->home());
 }
 @Override protected void onActivityResult(int request,int result,Intent data){
  super.onActivityResult(request,result,data);
  if(result!=RESULT_OK)return;
  try{
   Uri uri=request==TAKE_PHOTO?cameraUri:(data==null?null:data.getData());
   if(uri==null)return;
   sourceBitmap=MediaStore.Images.Media.getBitmap(getContentResolver(),uri);
   InputImage image=InputImage.fromFilePath(this,uri);
   if(recognized==null)photo();
   TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(image)
    .addOnSuccessListener(r->{recognized.setText(r.getText());detected=parseFoy(r);Toast.makeText(this,detected.size()+" profil satırı algılandı",Toast.LENGTH_LONG).show();showDetected();})
    .addOnFailureListener(e->Toast.makeText(this,"Okuma hatası: "+e.getMessage(),Toast.LENGTH_LONG).show());
  }catch(Exception e){Toast.makeText(this,"Fotoğraf hatası: "+e.getMessage(),Toast.LENGTH_LONG).show();}
 }
 ArrayList<CutRow> parseFoy(Text doc){
  ArrayList<CutRow> rows=new ArrayList<>();
  ArrayList<Text.Line> lines=new ArrayList<>();
  for(Text.TextBlock b:doc.getTextBlocks())lines.addAll(b.getLines());
  Collections.sort(lines,(a,b)->Integer.compare(a.getBoundingBox()==null?0:a.getBoundingBox().centerY(),b.getBoundingBox()==null?0:b.getBoundingBox().centerY()));
  Pattern codePattern=Pattern.compile("B\\s*1\\s*5\\s*0\\s*\\d{2}",Pattern.CASE_INSENSITIVE);
  for(int i=0;i<lines.size();i++){
   Text.Line line=lines.get(i);Matcher match=codePattern.matcher(line.getText().replace("O","0"));
   if(!match.find())continue;
   String code=match.group().replaceAll("\\s+","").toUpperCase(Locale.ROOT);
   Rect r=line.getBoundingBox();if(r==null)continue;
   StringBuilder joined=new StringBuilder(line.getText());
   int y=r.centerY(),height=Math.max(18,r.height());
   for(Text.Line next:lines){
    Rect nr=next.getBoundingBox();
    if(nr!=null&&next!=line&&Math.abs(nr.centerY()-y)<height*1.2)joined.append(" ").append(next.getText());
   }
   String content=joined.toString();
   String name=content.replaceFirst("(?i)B\\s*1\\s*5\\s*0\\s*\\d{2}","").replaceAll("\\d{3,5}\\s*(mm)?"," ").replaceAll("\\s+"," ").trim();
   if(name.length()>75)name=name.substring(0,75);
   if(name.isEmpty())name=profileName(code);
   int mm=0,qty=0;
   ArrayList<Integer> nums=new ArrayList<>();
   Matcher nm=Pattern.compile("\\b\\d{1,5}\\b").matcher(content.replace(code," "));
   while(nm.find())try{nums.add(Integer.parseInt(nm.group()));}catch(Exception ignored){}
   for(int n:nums)if(n>=100&&n<=6500){mm=n;break;}
   if(mm>0){boolean after=false;for(int n:nums){if(after&&n>=0&&n<=100){qty=n;break;}if(n==mm)after=true;}}
   if(mm==0||qty==0){
    for(Text.Line other:lines){
     Rect nr=other.getBoundingBox();
     if(nr==null||Math.abs(nr.centerY()-y)>height*1.2||nr.centerX()<=r.centerX())continue;
     Matcher numbers=Pattern.compile("\\b\\d{1,5}\\b").matcher(other.getText());
     while(numbers.find()){int n=Integer.parseInt(numbers.group());if(mm==0&&n>=100&&n<=6500)mm=n;else if(mm>0&&qty==0&&n>=0&&n<=100)qty=n;}
    }
   }
   Bitmap icon=null;
   try{
    if(sourceBitmap!=null){
     int left=Math.max(0,r.left-(int)(sourceBitmap.getWidth()*0.19));
     int top=Math.max(0,r.top-height/2);
     int right=Math.min(sourceBitmap.getWidth(),Math.max(left+1,r.left-5));
     int bottom=Math.min(sourceBitmap.getHeight(),Math.max(top+1,r.bottom+height/2));
     if(right>left&&bottom>top)icon=Bitmap.createBitmap(sourceBitmap,left,top,right-left,bottom-top);
    }
   }catch(Exception ignored){}
   if(!code.isEmpty()&&rows.stream().noneMatch(z->z.code.equals(code)))rows.add(new CutRow(code,name,mm,qty,icon));
  }
  return rows;
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
   default:return "Profil";
  }
 }
 void showDetected(){
  page("Okunan profil kesim listesi");
  if(detected.isEmpty()){note("Profil satırı bulunamadı. Daha net, düz çekilmiş bir fotoğraf deneyin.");button("Yeniden fotoğraf çek",()->photo());return;}
  note("Her profilin ölçüsünü ve adedini kontrol edip düzeltin. Sıfır adetli satırlar kesime girmez.");
  ArrayList<EditText> lengths=new ArrayList<>(),quantities=new ArrayList<>();
  for(CutRow row:detected){
   LinearLayout item=new LinearLayout(this);item.setOrientation(1);item.setPadding(dp(8),dp(8),dp(8),dp(8));item.setBackground(box(0xf5ffffff));
   LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(-1,-2);ip.setMargins(0,dp(6),0,dp(6));body.addView(item,ip);
   if(row.icon!=null){ImageView im=new ImageView(this);im.setImageBitmap(row.icon);im.setAdjustViewBounds(true);item.addView(im,new LinearLayout.LayoutParams(-1,dp(75)));}
   TextView name=text(row.code+" • "+profileName(row.code),15);name.setTypeface(null,Typeface.BOLD);item.addView(name);
   EditText m=new EditText(this);m.setSingleLine(true);m.setText(String.valueOf(row.mm));m.setHint("Kesim ölçüsü mm");m.setInputType(2);item.addView(m);
   EditText q=new EditText(this);q.setSingleLine(true);q.setText(String.valueOf(row.qty));q.setHint("Kesim adedi");q.setInputType(2);item.addView(q);
   lengths.add(m);quantities.add(q);
  }
  button("Onayla ve kesim planını hesapla",()->{
   try{
    for(int i=0;i<detected.size();i++){detected.get(i).mm=val(lengths.get(i));detected.get(i).qty=val(quantities.get(i));}
    optimizeDetected();
   }catch(Exception e){bad();}
  });
  button("Yeni fotoğraf",()->photo());
 }
 void optimizeDetected(){
  page("Profil bazında kesim planı");
  note("Stok boyu: Fabrikadan gelen ham profil uzunluğu. Kesim ölçüsü: Föydeki parça uzunluğu. Testere payı: Her kesimde testerenin tükettiği mm. Fire/kalan: Kesim sonrası boyda kalan mm.");
  EditText stock=input("Stok profil boyu (mm)","6000"),saw=input("Testere payı (mm / kesim)","3");
  button("Kesim planını oluştur",()->{
   try{
    int length=val(stock),kerf=val(saw);if(length<=0||kerf<0)throw new Exception();
    StringBuilder out=new StringBuilder();
    for(CutRow row:detected){
     if(row.qty==0)continue;
     if(row.mm<=0||row.qty<0||row.qty>1000||row.mm+kerf>length)throw new Exception();
     ArrayList<ArrayList<Integer>> bins=new ArrayList<>();ArrayList<Integer> used=new ArrayList<>();
     for(int k=0;k<row.qty;k++){
      int target=-1;
      for(int j=0;j<used.size();j++)if(used.get(j)+row.mm+kerf<=length){target=j;break;}
      if(target<0){target=bins.size();bins.add(new ArrayList<Integer>());used.add(0);}
      bins.get(target).add(row.mm);used.set(target,used.get(target)+row.mm+kerf);
     }
     out.append("\n").append(row.code).append(" - ").append(profileName(row.code)).append("\n");
     out.append("Kesim ölçüsü: ").append(row.mm).append(" mm | Adet: ").append(row.qty).append("\n");
     out.append("Gerekli stok: ").append(bins.size()).append(" boy\n");
     for(int j=0;j<bins.size();j++)out.append("Boy ").append(j+1).append(": ").append(bins.get(j)).append(" | Kalan: ").append(length-used.get(j)).append(" mm\n");
    }
    if(out.length()==0)out.append("Kesilecek parça bulunamadı.");
    result(out.toString());
   }catch(Exception e){bad();}
  });
  button("Ana menü",()->home());
 }
 void opt(){
  page("Profil kesim optimizasyonu");
  note("Kesimler: Her satır 1250x3 biçiminde. Testere payı parça başına eklenir. Azalan ilk uygun yerleştirme yöntemi kullanılır.");
  EditText stock=input("Stok boyu","6000"),kerf=input("Testere payı","3"),cuts=input("Ölçü x adet","1250x3\n850x4");
  cuts.setSingleLine(false);cuts.setMinLines(4);cuts.getLayoutParams().height=dp(140);
  button("Kesim planı oluştur",()->{
   try{
    int length=val(stock),saw=val(kerf);if(length<=0||saw<0)throw new Exception();
    ArrayList<Integer> pieces=new ArrayList<>();
    for(String line:cuts.getText().toString().split("[\\n,;]+")){
     String[] a=line.trim().toLowerCase(Locale.ROOT).replace(" ","").split("[x×*]");
     if(a[0].isEmpty())continue;
     int size=Integer.parseInt(a[0]),qty=a.length>1?Integer.parseInt(a[1]):1;
     if(size<=0||qty<1||qty>1000||size+saw>length||pieces.size()+qty>3000)throw new Exception();
     for(int i=0;i<qty;i++)pieces.add(size);
    }
    if(pieces.isEmpty())throw new Exception();
    Collections.sort(pieces,Collections.reverseOrder());
    ArrayList<ArrayList<Integer>> bins=new ArrayList<>();ArrayList<Integer> used=new ArrayList<>();long total=0;
    for(int size:pieces){
     total+=size;int target=-1;
     for(int i=0;i<used.size();i++)if(used.get(i)+size+saw<=length){target=i;break;}
     if(target<0){target=bins.size();bins.add(new ArrayList<Integer>());used.add(0);}
     bins.get(target).add(size);used.set(target,used.get(target)+size+saw);
    }
    StringBuilder out=new StringBuilder("Gerekli profil: "+bins.size()+" boy\nParça: "+pieces.size()+" adet\nNet kesim: "+total+" mm\nKalan: "+((long)bins.size()*length-total-(long)pieces.size()*saw)+" mm\n\n");
    for(int i=0;i<bins.size();i++)out.append("Boy ").append(i+1).append(": ").append(bins.get(i)).append("\nKalan: ").append(length-used.get(i)).append(" mm\n\n");
    result(out.toString());
   }catch(Exception ex){bad();}
  });button("Ana menü",()->home());
 }
 void glass(){
  page("Cam alan hesabı");EditText w=input("En mm","870"),h=input("Boy mm","1265"),n=input("Adet","1");
  button("Hesapla",()->{try{int a=val(w),b=val(h),c=val(n);if(a<=0||b<=0||c<=0)throw new Exception();result(String.format(Locale.US,"Tek cam: %.3f m²\nToplam: %.3f m²",a*b/1000000.0,a*b*c/1000000.0));}catch(Exception e){bad();}});
  button("Ana menü",()->home());
 }
 void panels(){
  page("Kanat bölme hesabı");note("Sistem payları hariç teorik eşit bölme.");
  EditText w=input("Açıklık eni mm","4100"),n=input("Kanat adedi","6"),ded=input("Toplam düşüm mm","0");
  button("Hesapla",()->{try{int a=val(w),b=val(n),c=val(ded);if(a<=0||b<=0||c<0||c>=a)throw new Exception();result(String.format(Locale.US,"Teorik kanat eni: %.1f mm",(a-c)/(double)b));}catch(Exception e){bad();}});
  button("Ana menü",()->home());
 }
 void system(String s){
  page(s);note("Ön ölçülendirme. Gerçek profil/cam kesim payları için üretici kataloğu gereklidir.");
  EditText w=input("Açıklık eni mm","3000"),h=input("Açıklık boyu mm","2200"),n=input("Bölüm adedi","4");
  button("Ön hesapla",()->{try{int a=val(w),b=val(h),c=val(n);if(a<=0||b<=0||c<=0)throw new Exception();result(String.format(Locale.US,"Sistem: %s\nAlan: %.2f m²\nTeorik bölüm eni: %.1f mm\n\nBunlar nihai imalat kesim ölçüsü değildir.",s,a*b/1000000.0,a/(double)c));}catch(Exception e){bad();}});
  button("Ana menü",()->home());
 }
}
