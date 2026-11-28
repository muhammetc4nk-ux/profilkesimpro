package com.profilkesim.pro;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.widget.Button;
import java.util.Locale;

final class WorkshopActions {
 enum Tone {
  PRIMARY(0xff123653, 0xff057b78, 0xff63eacd),
  ACTIVE(0xff086366, 0xff118a7b, 0xffb5ffda),
  IMPORT(0xff162c4e, 0xff244c83, 0xff87baff),
  DOCUMENT(0xff46302b, 0xff733f24, 0xfff0ba68),
  EDIT(0xff302545, 0xff593a74, 0xffc7a4fa),
  DELETE(0xff3c2333, 0xff7d3445, 0xffff9aa5),
  NAVIGATION(0xff192d3e, 0xff325365, 0xff96b7c9);

  final int start, end, accent;
  Tone(int start, int end, int accent) {
   this.start = start;
   this.end = end;
   this.accent = accent;
  }
 }

 private WorkshopActions() {}

 static Tone byLabel(CharSequence label) {
  String text = label.toString().toLowerCase(new Locale("tr", "TR"));
  if (text.startsWith("sil") || text.contains("çıkar") || text.contains("temizle")) return Tone.DELETE;
  if (text.contains("pdf al") || text.contains("indir") || text.contains("föyü aç")) return Tone.DOCUMENT;
  if (text.contains("tara") || text.contains("kamera") || text.contains("fotoğraf") || text.contains("dosya")) return Tone.IMPORT;
  if (text.contains("düzenle") || text.contains("seç") || text.contains("hizala") || text.contains("ölçü")
   || text.contains("geri al") || text.contains("ileri al") || text.contains("göster") || text.contains("gizle")) return Tone.EDIT;
  if (text.contains("menü") || text.endsWith("dön") || text.equals("kapat") || text.equals("vazgeç")) return Tone.NAVIGATION;
  return Tone.PRIMARY;
 }

 static Drawable surface(MainActivity app, Tone tone, int accent) {
  return new RippleDrawable(ColorStateList.valueOf((accent & 0x00ffffff) | 0x55000000),
   app.rgbPanel(tone.start, tone.end, accent), null);
 }

 static void style(MainActivity app, Button button, Tone tone) {
  button.setAllCaps(false);
  button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
  button.setTextColor(Color.WHITE);
  button.setBackgroundTintList(null);
  button.setBackground(surface(app, tone, tone.accent));
 }
}
