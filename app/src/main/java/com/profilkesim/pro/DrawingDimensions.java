package com.profilkesim.pro;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class DrawingDimensions {
 private static final String NUMBER = "[0-9]{1,6}(?:[.,][0-9]{1,2})?";
 private static final String VALUE = "[ \\t]*[:=]?[ \\t]*(" + NUMBER + ")[ \\t]*(MM|CM|M)?(?=$|[^A-Z0-9.,])";
 private static final Pattern WIDTH = Pattern.compile("(?m)\\b(?:GENISLIGI|GENISLIK|ENI|EN)" + VALUE);
 private static final Pattern HEIGHT = Pattern.compile("(?m)\\b(?:YUKSEKLIGI|YUKSEKLIK|BOYU|BOY)" + VALUE);
 private static final Pattern NON_FRAME = Pattern.compile(".*\\b(?:CAM|KANAT|PROFIL|KAYIT|STOK|CITA)(?: DIS)?[ \\t]*$");
 private static final Pattern PAIR = Pattern.compile("(?m)^\\s*(?:(?:KASA|DOGRAMA|SISTEM)(?:NIN|IN)?\\s+)?"
  + "(?:DIS\\s+)?(?:OLCU(?:SU|LERI?)?|BOYUT(?:U|LARI?)?)[ \\t]*[:=]?[ \\t]*(" + NUMBER + ")"
  + "[ \\t]*(MM|CM|M)?[ \\t]*[X×*][ \\t]*(" + NUMBER + ")[ \\t]*(MM|CM|M)?(?=$|[^A-Z0-9.,])");
 private static final Pattern VERTICAL = countPattern("DIKEY (?:ORTA )?KAYIT");
 private static final Pattern HORIZONTAL = countPattern("YATAY (?:ORTA )?KAYIT");
 private static final Pattern FIXED = countPattern("SABIT (?:BOLME|CAM)");

 final int width, height, vertical, horizontal, fixed;

 DrawingDimensions(int width, int height) { this(width, height, -1, -1, -1); }

 DrawingDimensions(int width, int height, int vertical, int horizontal, int fixed) {
  this.width = width;
  this.height = height;
  this.vertical = vertical;
  this.horizontal = horizontal;
  this.fixed = fixed;
 }

 boolean hasValues() { return width > 0 || height > 0 || vertical >= 0 || horizontal >= 0 || fixed >= 0; }

 static DrawingDimensions read(String text) {
  String normalized = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
   .replace('ı', 'i').toUpperCase(Locale.ROOT);
  Set<Integer> widths = axisValues(normalized, WIDTH), heights = axisValues(normalized, HEIGHT);
  Matcher pair = PAIR.matcher(normalized);
  while (pair.find()) {
   String widthUnit = pair.group(2), heightUnit = pair.group(4);
   widths.add(length(pair.group(1), widthUnit == null ? heightUnit : widthUnit));
   heights.add(length(pair.group(3), heightUnit == null ? widthUnit : heightUnit));
  }
  return new DrawingDimensions(single(widths), single(heights), count(normalized, VERTICAL, 12),
   count(normalized, HORIZONTAL, 12), count(normalized, FIXED, 169));
 }

 private static Set<Integer> axisValues(String text, Pattern pattern) {
  Set<Integer> values = new LinkedHashSet<>();
  Matcher matcher = pattern.matcher(text);
  while (matcher.find()) {
   int line = text.lastIndexOf('\n', matcher.start() - 1) + 1;
   if (NON_FRAME.matcher(text.substring(Math.max(line, matcher.start() - 32), matcher.start())).matches()) continue;
   values.add(length(matcher.group(1), matcher.group(2)));
  }
  return values;
 }

 private static int length(String number, String unit) {
  BigDecimal printed = new BigDecimal(number.replace(',', '.'));
  if ("CM".equals(unit)) printed = printed.movePointRight(1);
  if ("M".equals(unit)) printed = printed.movePointRight(3);
  try {
   int tenths = ShopDimensions.tenths(printed.stripTrailingZeros().toPlainString(), "Çizim ölçüsü");
   return tenths > 0 ? tenths : -1;
  } catch (IllegalArgumentException ignored) {
   return -1;
  }
 }

 private static Pattern countPattern(String label) {
  return Pattern.compile("(?m)^\\s*" + label + "(?: (?:ADEDI|SAYISI))?[ \\t]*[:=]?[ \\t]*([0-9]{1,3})(?=$|[^0-9.,A-Z])");
 }

 private static int count(String text, Pattern pattern, int maximum) {
  Matcher matcher = pattern.matcher(text);
  Set<Integer> values = new LinkedHashSet<>();
  while (matcher.find()) {
   int count = Integer.parseInt(matcher.group(1));
   values.add(count <= maximum ? count : -1);
  }
  return single(values);
 }

 private static int single(Set<Integer> values) { return values.size() == 1 ? values.iterator().next() : -1; }

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
