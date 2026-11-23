package com.profilkesim.pro;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads cutting tables from the positioned words of a single photo. */
final class CutSheet {
 private static final Pattern INLINE_CODE = Pattern.compile("(?i)(?<![\\p{L}\\d])[A-Z]{1,5}(?:[-.]\\s*)?[0-9]{3,}(?![\\p{L}\\d])");
 private static final Pattern CODE_CELL = Pattern.compile("[A-Z0-9]+(?:[-./ ][A-Z0-9]+)*");
 private static final Pattern FORM_METADATA = Pattern.compile(
  "\\b(?:RAL|RENK|ANTRASIT|MILIMETRE|OLCU BIRIMI|PROFIL BOYU|STOK BOYU|SISTEM|SISTEMLERI|"
   + "CAM BALKON|BALKON CAM|KESIM LISTESI|LISTESI KESIM|ORNEK|TEMSILI|IMALAT OLCUSU|UYGULAMA TESTI)\\b");
 private static final Pattern PROFILE_PART = Pattern.compile(
  "\\b(?:PROFIL(?:I|LERI)?|KASA|KANAT|RAY(?:I)?|DIKME|CITA|KAPAK|KUSAK|KAYIT|FITIL|"
   + "BIRLESIM|CEKTIRME|KILIT|KARSILIK|ESIK|LAMEL|BORU|KUTU|ISPANYOLET)\\b");
 private static final Pattern FOOTER = Pattern.compile(
  "^(?:(?:CAM )?BALKON(?: SISTEMI?)? (?:OLCU(?:SU|LERI)?|BOYUT(?:U|LARI)?|GENISLIGI|YUKSEKLIGI|ENI|BOYU)|"
   + "(?:SISTEM|CAM|MONTAJ|BALKON) (?:OLCU\\w*|BOYUT\\w*|GENISLIK|YUKSEKLIK|EN|BOY)|"
   + "(?:GENEL |ARA )?TOPLAM|NOT(?:LAR)?|ACIKLAMALAR|SAYFA|SIPARIS|MUSTERI|PROJE|TARIH|"
   + "(?:BALKON|SISTEM|CAM|MONTAJ) BILGILERI|GENISLIK|YUKSEKLIK|OLCULER|BOYUTLAR)(?: |$)");

 static final class Bounds {
  final int left, top, right, bottom;

  Bounds(int left, int top, int right, int bottom) {
   this.left = left;
   this.top = top;
   this.right = right;
   this.bottom = bottom;
  }

  int x() { return (left + right) / 2; }
  int y() { return (top + bottom) / 2; }
  int height() { return bottom - top; }

  Bounds union(Bounds other) {
   return new Bounds(Math.min(left, other.left), Math.min(top, other.top),
    Math.max(right, other.right), Math.max(bottom, other.bottom));
  }
 }

 static final class Word {
  final String text;
  final Bounds box;
  final float confidence;

  Word(String text, Bounds box, float confidence) {
   this.text = text;
   this.box = box;
   this.confidence = confidence;
  }
 }

 static final class Row {
  final String name, code;
  final int length, quantity; // lengths are tenths of a millimetre
  final Bounds identity;
  Bounds section, lengthCell, quantityCell;

  Row(String name, String code, int length, int quantity, Bounds identity) {
   this.name = name;
   this.code = code;
   this.length = length;
   this.quantity = quantity;
   this.identity = identity;
  }
 }

 private enum Field { NAME, CODE, SECTION, LENGTH, QUANTITY, OTHER }

 private static final class Column {
  final Field field;
  final Bounds box;
  final boolean needsCutLabel;
  int left, right;

  Column(Field field, Bounds box, String label) {
   this.field = field;
   this.box = box;
   needsCutLabel = field == Field.NAME || field == Field.CODE ? !label.startsWith("PROFIL")
    : field == Field.LENGTH && !label.startsWith("KESIM") && !label.startsWith("PARCA ") && !label.startsWith("CUT ");
  }
 }

 private static final class Line {
  final List<Word> words = new ArrayList<>();
  final Bounds baseline;

  Line(Word first) {
   baseline = first.box;
   words.add(first);
  }
 }

 static List<Row> read(List<Word> words, int width, int height) {
  return read(words, width, height, null);
 }

 static List<Row> read(List<Word> words, int width, int height, CutTableGrid.Pixels pixels) {
  return read(words, width, height, pixels, null);
 }

 static List<Row> read(List<Word> words, int width, int height, CutTableGrid.Pixels pixels, TableOcrRegion region) {
  List<Line> lines = linesFrom(words);
  List<Row> result = new ArrayList<>();
  int consumedThrough = -1;
  for (int i = 0; i < lines.size(); i++) {
   Line line = lines.get(i);
   if (line.baseline.y() <= consumedThrough) continue;
   Header header = findHeader(line.words, region != null);
   if (header == null) {
    List<Word> lower = new ArrayList<>();
    for (int j = i + 1; j < Math.min(i + 3, lines.size()) && header == null; j++) {
     Line next = lines.get(j);
     int gap = next.baseline.y() - line.baseline.y();
     if (gap > Math.max(line.baseline.height(), next.baseline.height()) * 2.1f) break;
     lower.addAll(next.words);
     header = splitHeader(line.words, lower, region != null);
    }
   }
   if (header == null) continue;
   CutTableGrid grid = region == null ? CutTableGrid.find(pixels, width, height, header.identityColumn().box,
    header.length.box, header.quantity.box, header.bottom, header.textHeight) : region.grid();
   if (region != null) {
    if (header.bottom > region.dataTop() + header.textHeight
      || grid.cellLeft(header.identityColumn().box.x()) != grid.left
      || grid.cellLeft(header.length.box.x()) == grid.cellLeft(header.quantity.box.x())) continue;
    header.bottom = Math.max(header.bottom, region.dataTop());
   }
   int firstColumnLeft = header.identityColumn().box.left;
   if (header.section != null) firstColumnLeft = Math.min(firstColumnLeft, header.section.box.left);
   if (header.code != null) firstColumnLeft = Math.min(firstColumnLeft, header.code.box.left);
   int left = grid == null ? Math.max(0, firstColumnLeft - header.textHeight * 5) : grid.left;
   int right = grid == null || width <= 0 ? Integer.MAX_VALUE : grid.right;
   int bottom = grid == null ? height : grid.bottom;
   List<Word> inside = new ArrayList<>();
   for (Word word : words) {
    if (word.box.left >= left && word.box.right <= right
      && word.box.y() > header.bottom && word.box.y() < bottom) inside.add(word);
   }
   List<Row> table = new ArrayList<>();
   int lastY = header.bottom;
   for (Line body : tableLines(inside, pixels, grid, width, header)) {
    String label = identity(join(body.words));
    if (table.isEmpty() && label.matches("MM|MILIMETRE(?: MM)?")) continue;
    int maxGap = Math.max(header.textHeight * 5, (int) (height * .09f));
    if (FOOTER.matcher(label).find() || findHeader(body.words, false) != null
      || (grid == null && body.baseline.y() - lastY > maxGap)) {
     bottom = Math.min(bottom, body.baseline.top);
     break;
    }
    Row row = header.readRow(body.words, grid);
    if (row == null) {
     if (grid != null) continue;
     bottom = Math.min(bottom, body.baseline.top);
     break;
    }
    table.add(row);
    lastY = body.baseline.y();
   }
   setSections(table, header, width, bottom, grid, left);
   result.addAll(table);
   consumedThrough = grid == null || table.isEmpty() ? lastY : bottom - 1;
  }
  return result;
 }

 private static List<Line> tableLines(List<Word> words, CutTableGrid.Pixels pixels, CutTableGrid grid,
                                     int width, Header header) {
  if (grid == null || pixels == null) return linesFrom(words);
  List<Integer> rules = grid.rowRules(pixels, width, header.bottom, header.textHeight);
  if (rules.size() < 3) return linesFrom(words);
  List<Line> rows = new ArrayList<>();
  for (int i = 1; i < rules.size(); i++) {
   int start = rules.get(i - 1), end = rules.get(i);
   Line row = null;
   for (Word word : words) {
    if (word.box.y() <= start || word.box.y() >= end) continue;
    if (row == null) row = new Line(word);
    else row.words.add(word);
   }
   if (row != null) {
    List<Line> textLines = linesFrom(row.words);
    int lengths = 0, quantities = 0;
    for (Line textLine : textLines) {
     Row entry = header.readRow(textLine.words, grid);
     if (entry == null) continue;
     if (entry.length > 0) lengths++;
     if (entry.quantity >= 0) quantities++;
    }
    if (lengths > 1 || quantities > 1) {
     // A pale or broken rule left more than one actual row inside this band.
     rows.addAll(textLines);
     continue;
    }
    row.words.clear();
    for (Line textLine : textLines) row.words.addAll(textLine.words);
    rows.add(row);
   }
  }
  return rows;
 }

 private static List<Line> linesFrom(List<Word> input) {
  List<Word> words = new ArrayList<>(input);
  Collections.sort(words, (first, second) -> Integer.compare(first.box.y(), second.box.y()));
  List<Line> lines = new ArrayList<>();
  Line current = null;
  for (Word word : words) {
   if (current == null || word.box.y() - current.baseline.y()
       > Math.max(3, Math.min(word.box.height(), current.baseline.height()) * 0.6f)) {
    current = new Line(word);
    lines.add(current);
   } else {
    current.words.add(word);
   }
  }
  for (Line line : lines) {
   Collections.sort(line.words, (first, second) -> Integer.compare(first.box.left, second.box.left));
  }
  return lines;
 }

 private static Header findHeader(List<Word> words, boolean isolatedTable) {
  List<Column> columns = new ArrayList<>();
  for (int from = 0; from < words.size(); from++) {
   int end = from + 1;
   for (int to = from + 1; to < Math.min(from + 4, words.size()); to++) {
    Bounds before = words.get(to - 1).box;
    Bounds after = words.get(to).box;
    if (after.left - before.right > Math.max(before.height(), after.height()) * 1.5f) break;
    if (Math.abs(after.y() - before.y()) > Math.max(before.height(), after.height()) * .65f) break;
    end = to + 1;
   }
   for (int to = end; to > from; to--) {
    String label = identity(join(words.subList(from, to)));
    Field field = headerField(label);
    if (field == null && isolatedTable && label.equals("KESIM")) field = Field.LENGTH;
    if (field == null) continue;
    columns.add(new Column(field, words.get(from).box.union(words.get(to - 1).box), label));
    from = to - 1;
    break;
   }
  }
  return checkedHeader(columns);
 }

 private static Header splitHeader(List<Word> top, List<Word> bottom, boolean isolated) {
  List<Word> joined = new ArrayList<>(top);
  joined.addAll(bottom);
  for (Word above : top) {
   for (Word below : bottom) {
    if (!joined.contains(below)) continue;
    if (Math.abs(above.box.x() - below.box.x()) > Math.max(above.box.height(), below.box.height()) * 1.8f) continue;
    String combined = above.text + " " + below.text;
    if (headerField(identity(combined)) == null) continue;
    joined.remove(above);
    joined.remove(below);
    joined.add(new Word(combined, above.box.union(below.box), Math.min(above.confidence, below.confidence)));
    break;
   }
  }
  Collections.sort(joined, (a, b) -> Integer.compare(a.box.left, b.box.left));
  return findHeader(joined, isolated);
 }

 private static Header checkedHeader(List<Column> columns) {
  Header header = new Header(columns);
  Column identity = header.identityColumn();
  if (identity == null || header.length == null || header.quantity == null) return null;
  if (header.length.needsCutLabel && (identity.needsCutLabel
    || identity.box.x() >= Math.min(header.length.box.x(), header.quantity.box.x()))) return null;
  return header;
 }

 private static Field headerField(String label) {
  if (label.matches("PROFIL (?:KESITI|KESIT|SEKLI|GORSELI|RESMI|CIZIMI)|KESIT(?: SEKLI| GORSELI)?|SEKIL|GORSEL|RESIM|CIZIM")) return Field.SECTION;
  if (label.matches("(?:PROFIL|PROFILE) (?:KODU|KOD|NO|CODE|REF)|(?:MALZEME|PARCA) KODU|KOD(?:U)?|CODE")) return Field.CODE;
  if (label.matches("PROFIL(?: ADI| ISMI| CINSI| TURU| TANIMI| KODU ADI| ADI KODU)?|(?:MALZEME|PARCA)(?: ADI| ISMI| CINSI| TANIMI)?|ACIKLAMA|PROFILE(?: NAME)?|DESCRIPTION")) return Field.NAME;
  if (label.matches("(?:KESIM|KESILECEK|PARCA) (?:OLCU(?:SU)?|BOY(?:U)?|UZUNLUK|UZUNLUGU|EBAT(?:I)?)(?: MM)?|(?:OLCU(?:SU)?|BOY|UZUNLUK|CUT LENGTH|LENGTH)(?: MM)?|L MM")) return Field.LENGTH;
  if (label.matches("(?:KESIM |PARCA )?ADET(?:I)?(?: PCS)?|MIKTAR(?: ADET)?|SAYI(?:SI)?|QTY|QUANTITY|PCS")) return Field.QUANTITY;
  if (label.matches("(?:SISTEM|BALKON|CAM|HAM|STOK|PROFIL) (?:OLCU\\w*|BOY\\w*|UZUNLUGU|EN|YUKSEKLIK|ADET\\w*)(?: MM)?|"
    + "(?:SON|GENEL )?TOPLAM|(?:SIRA )?NO|BOYUT|AGIRLIK|FIRE|OLCU BIRIMI|BIRIM|RENK|GENISLIK|YUKSEKLIK")) return Field.OTHER;
  return null;
 }

 private static final class Header {
  final List<Column> columns;
  Column name, code, section, length, quantity;
  int bottom, textHeight;

  Header(List<Column> columns) {
   this.columns = columns;
   for (Column column : columns) {
    bottom = Math.max(bottom, column.box.bottom);
    textHeight = Math.max(textHeight, column.box.height());
    switch (column.field) {
     case NAME: name = column; break;
     case CODE: code = column; break;
     case SECTION: section = column; break;
     case LENGTH: length = column; break;
     case QUANTITY: quantity = column; break;
     default: break;
    }
   }
   for (Column column : columns) {
    int center = column.box.x();
    int halfWidth = Math.max(textHeight * 2, (column.box.right - column.box.left) / 2 + textHeight);
    column.left = center - halfWidth;
    column.right = center + halfWidth;
    for (Column other : columns) {
     if (other.box.x() < center) column.left = Math.max(column.left, (other.box.x() + center) / 2);
     if (other.box.x() > center) column.right = Math.min(column.right, (other.box.x() + center) / 2);
    }
   }
  }

  Column identityColumn() { return name == null ? code : name; }

  Row readRow(List<Word> words, CutTableGrid grid) {
   List<Word> names = new ArrayList<>();
   List<Word> codes = new ArrayList<>();
   List<Word> lengths = new ArrayList<>();
   List<Word> quantities = new ArrayList<>();
   int nameLeft = 0, nameRight = Integer.MAX_VALUE;
   for (Column column : columns) {
    if (name == null || column == name) continue;
    if (column.box.x() < name.box.x()) nameLeft = Math.max(nameLeft, column.box.right);
    if (column.box.x() > name.box.x()) nameRight = Math.min(nameRight, column.box.left);
   }
   for (Word word : words) {
    Column nearest = null;
    int shortest = Integer.MAX_VALUE;
    for (Column column : columns) {
     int d = Math.abs(word.box.x() - column.box.x());
     if (d < shortest) {
      shortest = d;
      nearest = column;
     }
    }
    if ((grid != null || nearest == length) && centerInCell(word, length, grid)) {
     lengths.add(boundedWord(word, length, grid));
    } else if ((grid != null || nearest == quantity) && centerInCell(word, quantity, grid)) {
     quantities.add(boundedWord(word, quantity, grid));
    } else if (code != null && (grid != null || nearest == code) && fullyInCell(word, code, grid)) {
     codes.add(word);
    } else if (name != null && (grid == null ? word.box.left >= nameLeft && word.box.right <= nameRight : fullyInCell(word, name, grid))) {
     names.add(word);
    }
   }
   String profileName = join(names);
   String profileCode = normalizeCode(join(codes));
   if (!CODE_CELL.matcher(profileCode).matches() || profileCode.matches("YOK|KODSUZ|N/A")) profileCode = "";
   if (code == null) {
    Matcher inline = INLINE_CODE.matcher(profileName);
    if (inline.find()) {
     profileCode = normalizeCode(inline.group());
     profileName = (profileName.substring(0, inline.start()) + " " + profileName.substring(inline.end()))
      .replaceAll("^[\\s/|:()–-]+|[\\s/|:()–-]+$", "").replaceAll("\\s+", " ");
    }
   }
   if (!profileName.matches(".*\\p{L}.*")) profileName = "";
   if (profileName.isEmpty() && profileCode.isEmpty()) return null;
   String profileLabel = identity(profileName);
   if (FORM_METADATA.matcher(profileLabel).find()) return null;
   int size = number(lengths, false);
   int count = number(quantities, true);
   if (size < 0 && count < 0 && profileCode.isEmpty() && !PROFILE_PART.matcher(profileLabel).find()) return null;
   Bounds anchor = bounds(names.isEmpty() ? codes : names);
   return new Row(profileName, profileCode, size, count, anchor);
  }

  private Word boundedWord(Word word, Column column, CutTableGrid grid) {
   return fullyInCell(word, column, grid) ? word : new Word(word.text, word.box, 0);
  }

  private boolean centerInCell(Word word, Column column, CutTableGrid grid) {
   int x = word.box.x();
   int left = grid == null ? column.left : grid.cellLeft(column.box.x());
   int right = grid == null ? column.right : grid.cellRight(column.box.x());
   return x > left && x < right;
  }

  private boolean fullyInCell(Word word, Column column, CutTableGrid grid) {
   int left = grid == null ? column.left : grid.cellLeft(column.box.x());
   int right = grid == null ? column.right : grid.cellRight(column.box.x());
   return word.box.left >= left && word.box.right <= right;
  }
 }

 private static String normalizeCode(String code) {
  return code.trim().toUpperCase(Locale.ROOT).replaceAll("\\s*([./-])\\s*", "$1");
 }

 static int number(List<Word> words, boolean quantity) {
  if (words.isEmpty()) return -1;
  for (Word word : words) if (word.confidence < .55f) return -1;
  String raw = join(words).toLowerCase(Locale.ROOT);
  if (quantity) {
   if (!raw.matches("[0-9]{1,4}(?:\\s*(?:adet|pcs))?")) return -1;
   int count = Integer.parseInt(raw.replaceFirst("\\s*(?:adet|pcs)$", ""));
   return count <= 1000 ? count : -1;
  }
  if (!raw.matches("[0-9]{1,6}(?:[.,][0-9]{1,2})?(?:\\s*(?:mm|cm|m))?")) return -1;
  try {
   java.math.BigDecimal value = new java.math.BigDecimal(raw.replaceFirst("\\s*(?:mm|cm|m)$", "").replace(',', '.'));
   if (raw.endsWith("cm")) value = value.movePointRight(1);
   if (raw.endsWith("m") && !raw.endsWith("mm") && !raw.endsWith("cm")) value = value.movePointRight(3);
   return ShopDimensions.tenths(value.stripTrailingZeros().toPlainString(), "Kesim boyu");
  } catch (IllegalArgumentException error) {
   return -1;
  }
 }

 private static void setSections(List<Row> rows, Header header, int width, int bottomLimit, CutTableGrid grid,
                                 int tableLeft) {
  if (width <= 0) return;
  Column section = header.section;
  int identityLeft = header.identityColumn().box.left;
  if (header.code != null) identityLeft = Math.min(identityLeft, header.code.box.left);
  for (Row row : rows) identityLeft = Math.min(identityLeft, row.identity.left);
  for (int i = 0; i < rows.size(); i++) {
   Row row = rows.get(i);
   int y = row.identity.y();
   int textHeight = row.identity.height();
   int top = Math.max(header.bottom, y - textHeight * 2);
   int bottom = Math.min(bottomLimit, y + textHeight * 2);
   if (i > 0) top = Math.max(top, (rows.get(i - 1).identity.y() + y) / 2);
   if (i + 1 < rows.size()) bottom = Math.min(bottom, (rows.get(i + 1).identity.y() + y) / 2);
   int cellTop = Math.max(top, y - textHeight);
   int cellBottom = Math.min(bottom, y + textHeight);
   row.lengthCell = cellBounds(grid, header.length, width, cellTop, cellBottom, textHeight);
   row.quantityCell = cellBounds(grid, header.quantity, width, cellTop, cellBottom, textHeight);
   int left, right;
   if (section != null) {
    left = Math.max(0, section.box.x() - textHeight * 3);
    right = Math.min(width, section.box.x() + textHeight * 3);
    for (Column column : header.columns) {
     if (column.box.x() < section.box.x()) left = Math.max(left, column.box.right);
     if (column.box.x() > section.box.x()) right = Math.min(right, column.box.left);
    }
    if (row.identity.right <= section.box.x()) left = Math.max(left, row.identity.right);
    if (row.identity.left >= section.box.x()) right = Math.min(right, row.identity.left);
   } else if (grid != null) {
    int inset = Math.max(2, textHeight / 10);
    left = grid.cellLeft(header.identityColumn().box.x()) + inset;
    right = Math.min(row.identity.left, identityLeft) - inset;
    top += inset;
    bottom -= inset;
    if (right - left < textHeight) continue;
   } else {
    // Some sheets leave the drawing column untitled, to the left of all profile text.
    right = identityLeft - Math.max(2, textHeight / 4);
    left = Math.max(tableLeft, right - textHeight * 6);
    if (right - left < textHeight * 2) continue;
   }
   if (right > left && bottom > top) row.section = new Bounds(left, top, right, bottom);
  }
 }

 private static Bounds cellBounds(CutTableGrid grid, Column column, int width, int top, int bottom, int textHeight) {
  int inset = Math.max(2, textHeight / 9);
  int left = Math.max(0, grid == null ? column.left : grid.cellLeft(column.box.x()) + inset);
  int right = Math.min(width, grid == null ? column.right : grid.cellRight(column.box.x()) - inset);
  if (right <= left || bottom - top <= 2 * inset) return null;
  return new Bounds(left, top + inset, right, bottom - inset);
 }

 static String identity(String text) {
  String upper = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
   .toUpperCase(Locale.ROOT);
  return upper.replaceAll("(?<=\\d)\\s*[X×*]\\s*(?=\\d)", "X").replaceAll("[^A-Z0-9]+", " ").trim();
 }

 private static String join(List<Word> words) {
  StringBuilder value = new StringBuilder();
  for (Word word : words) {
   if (value.length() > 0) value.append(' ');
   value.append(word.text.trim());
  }
  return value.toString();
 }

 private static Bounds bounds(List<Word> words) {
  Bounds result = words.get(0).box;
  for (int i = 1; i < words.size(); i++) result = result.union(words.get(i).box);
  return result;
 }
}
