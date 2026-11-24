package com.profilkesim.pro;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
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
 private static final Pattern BARE_PAIR = Pattern.compile("^(" + NUMBER + ")\\s*(MM|CM|M)?\\s*[X×*]\\s*("
  + NUMBER + ")\\s*(MM|CM|M)?$");
 private static final Pattern BARE_VALUE = Pattern.compile("^(" + NUMBER + ")\\s*(MM|CM|M)?$");
 private static final Pattern VERTICAL = countPattern("DIKEY (?:ORTA )?KAYIT");
 private static final Pattern HORIZONTAL = countPattern("YATAY (?:ORTA )?KAYIT");
 private static final Pattern FIXED = countPattern("SABIT (?:BOLME|CAM)");

 final int width, height, vertical, horizontal, fixed;
 private final boolean widthConflict, heightConflict;

 DrawingDimensions(int width, int height) { this(width, height, -1, -1, -1); }

 DrawingDimensions(int width, int height, int vertical, int horizontal, int fixed) {
  this(width, height, vertical, horizontal, fixed, false, false);
 }

 private DrawingDimensions(int width, int height, int vertical, int horizontal, int fixed,
                           boolean widthConflict, boolean heightConflict) {
  this.width = width;
  this.height = height;
  this.vertical = vertical;
  this.horizontal = horizontal;
  this.fixed = fixed;
  this.widthConflict = widthConflict;
  this.heightConflict = heightConflict;
 }

 boolean hasValues() { return width > 0 || height > 0 || vertical >= 0 || horizontal >= 0 || fixed >= 0; }

 static DrawingDimensions read(String text) {
  return readSketch(text, Collections.emptyList(), null);
 }

 static DrawingDimensions readSketch(String text, List<CutSheet.Word> lines, CutSheet.Bounds frame) {
  String normalized = normalize(text);
  Set<Integer> widths = axisValues(normalized, WIDTH), heights = axisValues(normalized, HEIGHT);
  Matcher pair = PAIR.matcher(normalized);
  while (pair.find()) {
   String widthUnit = pair.group(2), heightUnit = pair.group(4);
   widths.add(length(pair.group(1), widthUnit == null ? heightUnit : widthUnit));
   heights.add(length(pair.group(3), heightUnit == null ? widthUnit : heightUnit));
  }
  Set<Integer> looseWidths = new LinkedHashSet<>(), looseHeights = new LinkedHashSet<>();
  for (CutSheet.Word line : lines) {
   Matcher values = BARE_PAIR.matcher(normalize(line.text).trim());
   if (line.confidence < .55f || !values.matches() || !nearFrame(line.box, frame)) continue;
   String widthUnit = values.group(2), heightUnit = values.group(4);
   int w = length(values.group(1), widthUnit == null ? heightUnit : widthUnit);
   int h = length(values.group(3), heightUnit == null ? widthUnit : heightUnit);
   if (w >= 100 && h >= 100) {
    looseWidths.add(w);
    looseHeights.add(h);
   }
  }
  if (widths.isEmpty()) widths.addAll(looseWidths);
  if (heights.isEmpty()) heights.addAll(looseHeights);
  if (frame != null) {
   if (widths.isEmpty()) widths.addAll(outsideValues(lines, frame, true));
   if (heights.isEmpty()) heights.addAll(outsideValues(lines, frame, false));
  }
  return new DrawingDimensions(single(widths), single(heights), count(normalized, VERTICAL, 12),
   count(normalized, HORIZONTAL, 12), count(normalized, FIXED, 169), widths.size() > 1, heights.size() > 1);
 }

 DrawingDimensions withFallback(DrawingDimensions fallback) {
  return new DrawingDimensions(
   widthConflict || fallback.widthConflict ? -1 : reconcile(width, fallback.width),
   heightConflict || fallback.heightConflict ? -1 : reconcile(height, fallback.height),
   reconcile(vertical, fallback.vertical), reconcile(horizontal, fallback.horizontal), reconcile(fixed, fallback.fixed),
   widthConflict || fallback.widthConflict || width > 0 && fallback.width > 0 && width != fallback.width,
   heightConflict || fallback.heightConflict || height > 0 && fallback.height > 0 && height != fallback.height);
 }

 private static int reconcile(int a, int b) {
  return a < 0 ? b : b < 0 || b == a ? a : -1;
 }

 private static String normalize(String text) {
  return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
   .replace('ı', 'i').toUpperCase(Locale.ROOT);
 }

 private static boolean nearFrame(CutSheet.Bounds box, CutSheet.Bounds frame) {
  if (frame == null) return true;
  int dx = (frame.right - frame.left) / 5, dy = frame.height() / 4;
  return box.x() >= frame.left - dx && box.x() <= frame.right + dx
   && box.y() >= frame.top - dy && box.y() <= frame.bottom + dy;
 }

 private static Set<Integer> outsideValues(List<CutSheet.Word> lines, CutSheet.Bounds frame, boolean horizontal) {
  Set<Integer> values = new LinkedHashSet<>();
  int start = horizontal ? frame.left : frame.top, end = horizontal ? frame.right : frame.bottom;
  int before = horizontal ? frame.top : frame.left, after = horizontal ? frame.bottom : frame.right;
  int length = end - start, cross = after - before, centered = length / 8;
  int firstOnSide = -1, secondOnSide = -1;
  for (CutSheet.Word line : lines) {
   if (line.confidence < .55f) continue;
   Matcher text = BARE_VALUE.matcher(normalize(line.text).trim());
   if (!text.matches()) continue;
   int position = horizontal ? line.box.x() : line.box.y(), axis = horizontal ? line.box.y() : line.box.x();
   int crossSize = horizontal ? line.box.height() : line.box.right - line.box.left;
   if (position <= start + centered || position >= end - centered) continue;
   int distance = axis < before ? before - axis : axis > after ? axis - after : -1;
   if (distance < Math.max(2, crossSize / 5) || distance > Math.max(cross / 4, crossSize * 3)) continue;
   int value = length(text.group(1), text.group(2));
   if (value < (text.group(2) == null ? 700 : 100)) continue;
   int prior = axis < before ? firstOnSide : secondOnSide;
   if (prior >= 0 && Math.abs(position - prior) > length / 9) {
    // Separate labels on the same side may be individual panel widths.
    values.add(-1);
   }
   if (axis < before) firstOnSide = position;
   else secondOnSide = position;
   values.add(value);
  }
  return values;
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
