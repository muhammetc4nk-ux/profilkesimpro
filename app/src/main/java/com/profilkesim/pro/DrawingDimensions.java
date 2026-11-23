package com.profilkesim.pro;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class DrawingDimensions {
 private static final String VALUE = "[ \\t]*[:=]?[ \\t]*([0-9]{1,6}(?:[.,][0-9]{1,2})?)[ \\t]*(MM|CM|M)?(?=$|[^A-Z0-9.,])";
 private static final Pattern WIDTH = Pattern.compile("(?m)\\b(?:GENISLIGI|GENISLIK|ENI|EN)" + VALUE);
 private static final Pattern HEIGHT = Pattern.compile("(?m)\\b(?:YUKSEKLIGI|YUKSEKLIK|BOYU|BOY)" + VALUE);
 private static final Pattern NON_FRAME = Pattern.compile(".*\\b(?:CAM|KANAT|PROFIL|KAYIT|STOK)(?: DIS)?[ \\t]*$");

 final int width, height;

 DrawingDimensions(int width, int height) {
  this.width = width;
  this.height = height;
 }

 static DrawingDimensions read(String text) {
  String normalized = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
   .replace('ı', 'i').toUpperCase(Locale.ROOT);
  return new DrawingDimensions(singleValue(normalized, WIDTH), singleValue(normalized, HEIGHT));
 }

 private static int singleValue(String text, Pattern pattern) {
  Set<Integer> values = new LinkedHashSet<>();
  Matcher matcher = pattern.matcher(text);
  while (matcher.find()) {
   int line = text.lastIndexOf('\n', matcher.start() - 1) + 1;
   String prefix = text.substring(Math.max(line, matcher.start() - 32), matcher.start());
   if (NON_FRAME.matcher(prefix).matches()) continue;
   BigDecimal printed = new BigDecimal(matcher.group(1).replace(',', '.'));
   String unit = matcher.group(2);
   if ("CM".equals(unit)) printed = printed.movePointRight(1);
   if ("M".equals(unit)) printed = printed.movePointRight(3);
   try {
    int tenths = ShopDimensions.tenths(printed.stripTrailingZeros().toPlainString(), "Çizim ölçüsü");
    if (tenths > 0) values.add(tenths);
   } catch (IllegalArgumentException ignored) {
    return -1;
   }
  }
  return values.size() == 1 ? values.iterator().next() : -1;
 }

 static DrawingDimensions scaled(int reference, double referencePixels, double widthPixels, double heightPixels) {
  if (reference <= 0 || referencePixels < 12 || widthPixels < 12 || heightPixels < 12) {
   throw new IllegalArgumentException("Referans ve ölçüm noktalarını daha uzağa yerleştirin.");
  }
  long width = Math.round(reference * widthPixels / referencePixels);
  long height = Math.round(reference * heightPixels / referencePixels);
  if (width < 1 || height < 1 || width > 1_000_000 || height > 1_000_000) {
   throw new IllegalArgumentException("Ölçekli ölçü aralık dışında. Referansı kontrol edin.");
  }
  return new DrawingDimensions((int) width, (int) height);
 }
}
