
package com.profilkesim.pro;
import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.graphics.Bitmap;
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
  button("Fotoğraftan kesim listesi oku",()->photo());
  button("Profil kesim optimizasyonu",()->opt());
  button("Cam alanı ve adet hesabı",()->glass());
  button("Kanat eşit bölme hesabı",()->panels());
  for(String s:new String[]{"Tiara Twin","Statü","Sürme","Giyotin","Cam tavan","PVC","Sineklik","Duşakabin","ASAŞ RST116 Hebeschiebe"})button(s,()->system(s));
 }
 void photo(){
  page("Fotoğraftan kesim listesi");
  note("El yazısı ve bilgisayar çıktısı desteklenir. Yanlış okunan ölçüleri mutlaka düzeltin. Örnek: KASA 1250x3. Her satıra bir profil ve ölçü yazın.");
  button("Galeriden fotoğraf seç",()->{
    Intent i=new Intent(Intent.ACTION_GET_CONTENT);i.setType("image/*");startActivityForResult(i,PICK_IMAGE);
  });
  button("Kamerayla fotoğraf çek",()->{
    Intent i=new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
    try{startActivityForResult(i,TAKE_PHOTO);}catch(Exception e){note("Kamera açılamadı.");}
  });
  recognized=input("Okunan kesim listesi","");
  recognized.setSingleLine(false);recognized.setMinLines(7);recognized.getLayoutParams().height=dp(230);
  note("Örnek satırlar: KASA 1250x3 veya KANAT 850x4. Ölçüler mm cinsindedir.");
  button("Profilleri ayır ve optimize et",()->analyzePhoto());
  button("Ana menü",()->home());
 }
 @Override protected void onActivityResult(int request,int result,Intent data){
  super.onActivityResult(request,result,data);
  if(result!=RESULT_OK||data==null)return;
  try{
    InputImage image;
    if(request==PICK_IMAGE){Uri uri=data.getData();if(uri==null)return;image=InputImage.fromFilePath(this,uri);}
    else if(request==TAKE_PHOTO){Bitmap bmp=(Bitmap)data.getExtras().get("data");image=InputImage.fromBitmap(bmp,0);}
    else return;
    if(recognized==null)photo();
    TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(image)
      .addOnSuccessListener(r->{recognized.setText(r.getText());Toast.makeText(this,"Metin okundu; ölçüleri kontrol edin",Toast.LENGTH_LONG).show();})
      .addOnFailureListener(e->Toast.makeText(this,"Fotoğraf okunamadı: "+e.getMessage(),Toast.LENGTH_LONG).show());
  }catch(Exception e){Toast.makeText(this,"Fotoğraf açılmadı: "+e.getMessage(),Toast.LENGTH_LONG).show();}
 }
 void analyzePhoto(){
  if(recognized==null)return;
  String raw=recognized.getText().toString();
  TreeMap<String,ArrayList<Integer>> groups=new TreeMap<>();
  StringBuilder ignored=new StringBuilder();
  java.util.regex.Pattern pattern=java.util.regex.Pattern.compile("(\\d{2,5})\\s*(?:[xX×*]\\s*(\\d{1,3})|(?:adet|ad)\\s*(\\d{1,3}))?",java.util.regex.Pattern.CASE_INSENSITIVE);
  for(String line:raw.split("[\\r\\n]+")){
    String clean=line.trim();if(clean.isEmpty())continue;
    java.util.regex.Matcher m=pattern.matcher(clean);
    if(!m.find()){ignored.append(clean).append("\n");continue;}
    try{
      int size=Integer.parseInt(m.group(1));
      String q=m.group(2)!=null?m.group(2):m.group(3);
      int qty=q==null?1:Integer.parseInt(q);
      if(size<100||size>6500||qty<1||qty>100){ignored.append(clean).append("\n");continue;}
      String name=(clean.substring(0,m.start())+" "+clean.substring(m.end())).replaceAll("[^\\p{L}0-9 ]"," ").trim().toUpperCase(new Locale("tr","TR"));
      if(name.isEmpty())name="PROFİL TÜRÜ BELİRSİZ";
      if(!groups.containsKey(name))groups.put(name,new ArrayList<Integer>());
      for(int i=0;i<qty;i++)groups.get(name).add(size);
    }catch(Exception ex){ignored.append(clean).append("\n");}
  }
  page("Fotoğraf kesim sonuçları");
  if(groups.isEmpty()){note("Ölçü bulunamadı. Fotoğraftaki metni düzeltip tekrar deneyin.");button("Fotoğrafa dön",()->photo());return;}
  EditText stock=input("Stok boyu mm","6000"),saw=input("Testere payı mm","3");
  button("Profil bazında hesapla",()->{
    try{
      int length=val(stock),kerf=val(saw);if(length<=0||kerf<0)throw new Exception();
      StringBuilder out=new StringBuilder();
      for(Map.Entry<String,ArrayList<Integer>> entry:groups.entrySet()){
        ArrayList<Integer> parts=new ArrayList<>(entry.getValue());Collections.sort(parts,Collections.reverseOrder());
        ArrayList<ArrayList<Integer>> bins=new ArrayList<>();ArrayList<Integer> used=new ArrayList<>();boolean impossible=false;
        for(int piece:parts){if(piece+kerf>length){impossible=true;break;}int target=-1;
          for(int j=0;j<used.size();j++)if(used.get(j)+piece+kerf<=length){target=j;break;}
          if(target<0){target=bins.size();bins.add(new ArrayList<Integer>());used.add(0);}
          bins.get(target).add(piece);used.set(target,used.get(target)+piece+kerf);
        }
        out.append("\n■ ").append(entry.getKey()).append("\n");
        if(impossible){out.append("Stok boyuna sığmayan parça var.\n");continue;}
        out.append(bins.size()).append(" stok boyu / ").append(parts.size()).append(" parça\n");
        for(int j=0;j<bins.size();j++)out.append("Boy ").append(j+1).append(": ").append(bins.get(j)).append(" | Fire: ").append(length-used.get(j)).append(" mm\n");
      }
      result(out.toString());
    }catch(Exception ex){bad();}
  });
  if(ignored.length()>0)note("Okunamayan satırlar (kontrol edin):\n"+ignored.toString());
  note("Profil kesit görselleri ve üreticiye özel kesim payları henüz eklenmedi. Bu liste fotoğraftan okunan isimlere göre ayrılır.");
  button("Yeni fotoğraf",()->photo());button("Ana menü",()->home());
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
    ArrayList<ArrayList<Integer>> bins=new ArrayList<>();ArrayList<Integer> used=new ArrayList<>();
    long total=0;
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
                                                                                                                          
