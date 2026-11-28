package com.profilkesim.pro;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class TemaPdfExport {
 private static final int MARGIN = 38;
 private static final int INK = 0xff192f3e;
 private static final int MUTED = 0xff4d6372;
 private static final int RULE = 0xffd7e4ea;
 private static final int CUT = 0xff16864d, WASTE = 0xffc53340, KERF = 0xffd48b08;

 private TemaPdfExport() {}

 static void write(OutputStream output, Resources resources, String name, TemaCatalog.Series series,
                   TemaJoinery.Order order, TemaJoinery.Plan plan, Set<Integer> leftHinges, long timestamp, int stock, int kerf) throws IOException {
  boolean landscape = order.width > order.height;
  int pageWidth = landscape ? 842 : 595, pageHeight = landscape ? 595 : 842;
  PdfDocument pdf = new PdfDocument();
  Pages pages = new Pages(pdf, pageWidth, pageHeight, series.label + " · " + name,
   WorkshopClock.format(timestamp, "dd.MM.yyyy  HH:mm:ss"));
  Map<Integer, Bitmap> sections = new LinkedHashMap<>();
  List<TemaCatalog.Profile> usedSections = new ArrayList<>();
  try {
   pages.next("İmalat çizimi");
   pages.line("Dış kasa  " + size(order.width, order.height) + "   ·   " + order.quantity + " doğrama", "", false);
   Canvas canvas = pages.page.getCanvas();
   int top = Math.round(pages.y);
   int width = pageWidth - 2 * MARGIN, height = pageHeight - top - MARGIN - 20;
   canvas.save();
   canvas.translate(MARGIN, top);
   canvas.clipRect(0, 0, width, height);
   new TemaDiagramRenderer(order, plan, series, leftHinges, .86f, true).draw(canvas, width, height);
   canvas.restore();
   pages.y = pageHeight - MARGIN - 15;
   pages.line("Tüm ölçüler mm. Profil payı toplam düşümü gösterir.", "", false);

   writeOptimization(pages, name, series, plan, stock, kerf);
   String[] titles = {"Kasa kesim ölçüleri", "Kanat kesim ölçüleri", "Çıta kesim ölçüleri", "Orta kayıt ve diğer kesimler"};
   List<List<TemaJoinery.Cut>> groups = new ArrayList<>();
   for (String ignored : titles) groups.add(new ArrayList<>());
   for (TemaJoinery.Cut cut : plan.cuts) groups.get(cutSection(series, cut)).add(cut);
   for (int group = 0; group < titles.length; group++) {
    if (groups.get(group).isEmpty()) continue;
    pages.next(titles[group]);
    pages.cutHeader();
    for (TemaJoinery.Cut cut : groups.get(group)) {
     TemaCatalog.Profile profile = series.find(cut.code);
     Bitmap symbol = null;
     if (profile != null && profile.image != 0) {
      if (!sections.containsKey(profile.image)) sections.put(profile.image, BitmapFactory.decodeResource(resources, profile.image));
      symbol = sections.get(profile.image);
      if (symbol != null && !usedSections.contains(profile)) usedSections.add(profile);
     }
     String label = profile == null ? cut.code : cut.code + " · " + profile.label();
     pages.cutRow(label, cut, symbol, titles[group]);
    }
   }

   if (!usedSections.isEmpty()) pages.gallery(usedSections, sections);
   pages.next("Bölme ölçüleri ve profil payları");
   pages.heading("Dış en hesabı");
   for (List<Integer> chain : plan.layout.widthChains) pages.line(plan.layout.widthBreakdown(chain), "", true);
   pages.heading("Bölme iç ölçüleri · En × Boy");
   for (int i = 0; i < plan.layout.bays.size(); i++) {
    int number = i + 1;
    TemaLayout.Opening bay = plan.layout.bays.get(i);
    String kind = TemaBayDimensions.kind(bay, order.openingSashes.contains(number), order.emptyCells.contains(number));
    pages.line(TemaBayDimensions.caption(number, kind), size(bay.width(), bay.height()), true);
   }
   pages.heading("Çizimdeki profil payları");
   Map<String, TemaJoinery.ProfileMark> marks = new LinkedHashMap<>();
   for (TemaJoinery.ProfileMark mark : plan.profileMarks) {
    if (mark.role == TemaJoinery.ProfileRole.SASH) continue;
    marks.putIfAbsent(mark.code + ":" + mark.face + ":" + mark.role, mark);
   }
   for (TemaJoinery.ProfileMark mark : marks.values()) {
    String role = mark.role == TemaJoinery.ProfileRole.FRAME ? "Kasa" : "Orta kayıt";
    pages.line(series.nameOf(mark.code) + " · " + role, mark.allowanceText(), true);
   }
   if (!plan.panes.isEmpty()) {
    pages.heading("Cam kesim ölçüleri · En × Boy");
    for (TemaJoinery.Pane pane : plan.panes) {
     pages.line("Bölme " + pane.number + " · İç " + size(pane.insideWidth, pane.insideHeight),
      size(pane.width, pane.height) + " × " + order.quantity + " adet", true);
    }
   }
   pages.finish();
   pdf.writeTo(output);
  } finally {
   try {
    pages.finish();
   } finally {
    try {
     pdf.close();
    } finally {
     for (Bitmap bitmap : sections.values()) if (bitmap != null) bitmap.recycle();
    }
   }
  }
 }

 private static int cutSection(TemaCatalog.Series series, TemaJoinery.Cut cut) {
  if (series.isBead(cut.code, cut.role)) return 2;
  if (cut.role.startsWith("Kasa")) return 0;
  if (cut.role.startsWith("Kanat")) return 1;
  return 3;
 }

 private static void writeOptimization(Pages pages, String name, TemaCatalog.Series series,
                                       TemaJoinery.Plan plan, int stock, int kerf) {
  pages.next("Profil bazında kesim optimizasyonu");
  pages.line("Stok boyu " + metres(stock) + " · Testere payı " + mm(kerf) + " mm/ara", "", false);
  TemaCutBatch.Result result;
  try {
   result = TemaCutBatch.packPlan(name, plan, stock, kerf);
  } catch (IllegalArgumentException error) {
   pages.line("Optimizasyon oluşturulamadı", "", false);
   pages.line(error.getMessage(), "", true);
   return;
  }
  pages.line("Toplam " + result.barCount + " boy · " + result.pieceCount + " parça",
   "Stok " + metres((long) stock * result.barCount) + " · Fire " + metres(result.remaining), true);
  pages.legend();
  for (TemaCutBatch.Profile profile : result.profiles.values()) {
   long net = 0, fire = 0, saw = 0;
   for (TemaCutBatch.Bar bar : profile.bars) {
    fire += bar.layout.remaining();
    saw += (long) kerf * (bar.pieces.size() - 1);
    net += bar.layout.used - (long) kerf * (bar.pieces.size() - 1);
   }
   String title = profile.key + " · " + series.nameOf(profile.key);
   if (pages.y + 100 > pages.height - MARGIN) pages.next("Kesim optimizasyonu · Devam");
   pages.heading(title);
   pages.line(profile.bars.size() + " boy × " + metres(stock) + " = " + metres((long) stock * profile.bars.size()), "", false);
   pages.line("Net kesim " + metres(net) + " · Testere " + metres(saw) + " · Fire " + metres(fire), "", true);
   for (int index = 0; index < profile.bars.size(); index++) {
    pages.stockBar(title, index + 1, profile.bars.get(index), kerf);
   }
  }
 }

 private static String metres(long amount) {
  return java.math.BigDecimal.valueOf(amount, 4).stripTrailingZeros().toPlainString().replace('.', ',') + " m";
 }

 private static String mm(int value) { return ShopDimensions.mm(value); }
 private static String size(int width, int height) { return TemaBayDimensions.size(width, height); }

 private static final class Pages {
  final PdfDocument pdf;
  final int width, height;
  final String title, timestamp;
  final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
  final RectF picture = new RectF();
  PdfDocument.Page page;
  boolean alternate;
  int number;
  float y;

  Pages(PdfDocument pdf, int width, int height, String title, String timestamp) {
   this.pdf = pdf;
   this.width = width;
   this.height = height;
   this.title = title;
   this.timestamp = timestamp;
  }

  void next(String label) {
   finish();
   page = pdf.startPage(new PdfDocument.PageInfo.Builder(width, height, ++number).create());
   float timestampWidth = 132;
   text(label, MARGIN, MARGIN, 10, MUTED, true, width - 2 * MARGIN - timestampWidth - 8);
   text(timestamp, width - MARGIN - timestampWidth, MARGIN, 10, MUTED, true, timestampWidth);
   text(title, MARGIN, MARGIN + 26, 22, INK, true, width - 2 * MARGIN);
   y = MARGIN + 49;
  }

  void heading(String value) {
   if (y > height - MARGIN - 40) next("İmalat föyü");
   text(value, MARGIN, y, 13, INK, true, width - 2 * MARGIN);
   y += 23;
  }

  void line(String label, String measurement, boolean ruled) {
   if (y > height - MARGIN) next("İmalat föyü");
   paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
   paint.setTextSize(10);
   float measureWidth = paint.measureText(measurement);
   text(label, MARGIN, y, 10, INK, false, width - 2 * MARGIN - (measurement.isEmpty() ? 0 : measureWidth + 18));
   if (!measurement.isEmpty()) text(measurement, width - MARGIN - measureWidth, y, 10, MUTED, false, measureWidth + 1);
   if (ruled) {
    paint.setColor(RULE);
    paint.setStrokeWidth(.55f);
    page.getCanvas().drawLine(MARGIN, y + 6, width - MARGIN, y + 6, paint);
   }
   y += 20;
  }

  private float infoColumn() { return MARGIN + 73; }

  private float quantityColumn() { return width - MARGIN - 226; }

  void cutHeader() {
   Canvas canvas = page.getCanvas();
   paint.setColor(0xffdcecf4);
   canvas.drawRect(MARGIN, y, width - MARGIN, y + 27, paint);
   float baseline = y + 17;
   float quantity = quantityColumn();
   text("Kesit", MARGIN + 8, baseline, 10, INK, true, 48);
   text("Profil / Parça", infoColumn(), baseline, 10, INK, true, quantity - infoColumn() - 10);
   text("Adet", quantity, baseline, 10, INK, true, 41);
   text("Boy (mm)", quantity + 44, baseline, 10, INK, true, 67);
   text("1. uç", quantity + 118, baseline, 10, INK, true, 46);
   text("2. uç", quantity + 171, baseline, 10, INK, true, 47);
   y += 27;
   alternate = false;
  }

  void cutRow(String profile, TemaJoinery.Cut cut, Bitmap symbol, String heading) {
   float rowHeight = 49;
   if (y + rowHeight > height - MARGIN) {
    next(heading + " · Devam");
    cutHeader();
   }
   Canvas canvas = page.getCanvas();
   if (alternate) {
    paint.setColor(0xfff1f7fa);
    canvas.drawRect(MARGIN, y, width - MARGIN, y + rowHeight, paint);
   }
   alternate = !alternate;
   drawSection(symbol, rowHeight);
   float quantity = quantityColumn(), nameWidth = quantity - infoColumn() - 10;
   text(profile, infoColumn(), y + 20, 11, INK, true, nameWidth);
   text(cut.role, infoColumn(), y + 36, 9, MUTED, false, nameWidth);
   text(String.valueOf(cut.quantity), quantity, y + 29, 11, INK, false, 41);
   text(mm(cut.length), quantity + 44, y + 29, 11, INK, false, 67);
   text(cut.angle + "°", quantity + 118, y + 29, 11, INK, false, 46);
   text(cut.endAngle + "°", quantity + 171, y + 29, 11, INK, false, 47);
   y += rowHeight;
   paint.setColor(RULE);
   paint.setStrokeWidth(.55f);
   canvas.drawLine(MARGIN, y, width - MARGIN, y, paint);
  }

  void legend() {
   String[] labels = {"Kesilen parça", "Kalan fire", "Testere payı"};
   int[] colors = {CUT, WASTE, KERF};
   float x = MARGIN;
   for (int i = 0; i < labels.length; i++) {
    paint.setColor(colors[i]);
    page.getCanvas().drawRect(x, y - 9, x + 9, y, paint);
    text(labels[i], x + 14, y, 10, INK, false, 106);
    x += 118;
   }
   y += 24;
  }

  void roomForBar(String profile, float space) {
   if (y + space <= height - MARGIN) return;
   next("Kesim optimizasyonu · Devam");
   heading(profile);
  }

  void stockBar(String profile, int number, TemaCutBatch.Bar bar, int kerf) {
   roomForBar(profile, 56);
   String label = "Boy " + number + " · " + mm(bar.layout.stock) + " mm";
   text(label, MARGIN, y, 10, INK, true, width / 2f);
   String fire = "Fire " + mm(bar.layout.remaining()) + " mm";
   paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
   paint.setTextSize(10);
   text(fire, width - MARGIN - paint.measureText(fire), y, 10, WASTE, true, width / 2f);
   y += 6;
   float left = MARGIN, scale = (width - 2f * MARGIN) / bar.layout.stock;
   for (int i = 0; i < bar.pieces.size(); i++) {
    if (i > 0 && kerf > 0) left = segment(left, kerf * scale, KERF);
    left = segment(left, bar.pieces.get(i).length * scale, CUT);
    if (i + 1 < bar.pieces.size()) {
     paint.setColor(0xffeef8f0);
     paint.setStrokeWidth(.45f);
     page.getCanvas().drawLine(left, y, left, y + 9, paint);
    }
   }
   segment(left, bar.layout.remaining() * scale, WASTE);
   y += 24;
   StringBuilder row = new StringBuilder();
   for (TemaCutBatch.Piece piece : bar.pieces) {
    String entry = mm(piece.length) + " mm (" + piece.ends + ")";
    paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
    paint.setTextSize(9);
    if (row.length() > 0 && paint.measureText(row + "  ·  " + entry) > width - 2 * MARGIN) {
     roomForBar(profile + " · Boy " + number, 16);
     text(row.toString(), MARGIN, y, 9, CUT, false, width - 2 * MARGIN);
     y += 14;
     row.setLength(0);
    }
    if (row.length() > 0) row.append("  ·  ");
    row.append(entry);
   }
   roomForBar(profile + " · Boy " + number, 16);
   text(row.toString(), MARGIN, y, 9, CUT, false, width - 2 * MARGIN);
   y += 25;
  }

  private float segment(float left, float size, int color) {
   paint.setColor(color);
   page.getCanvas().drawRect(left, y, left + size, y + 9, paint);
   return left + size;
  }

  private void drawSection(Bitmap symbol, float rowHeight) {
   if (symbol == null) {
    text("—", MARGIN + 27, y + 29, 10, MUTED, false, 28);
    return;
   }
   drawPicture(symbol, MARGIN + 32, y + rowHeight / 2, 56, rowHeight - 10);
  }

  void gallery(List<TemaCatalog.Profile> profiles, Map<Integer, Bitmap> images) {
   next("Kesim tablosu · Katalog kesitleri");
   heading("Kullanılan profillerin katalog çizimleri");
   int columns = width > height ? 3 : 2;
   float gap = 14, cardHeight = 164;
   float cardWidth = (width - 2 * MARGIN - (columns - 1) * gap) / columns;
   for (int i = 0; i < profiles.size(); i++) {
    int column = i % columns;
    if (column == 0 && y + cardHeight > height - MARGIN) next("Katalog kesitleri · Devam");
    TemaCatalog.Profile profile = profiles.get(i);
    float left = MARGIN + column * (cardWidth + gap);
    paint.setColor(0xffeef5f8);
    page.getCanvas().drawRoundRect(left, y, left + cardWidth, y + cardHeight, 7, 7, paint);
    text(profile.name, left + 12, y + 19, 11, INK, true, cardWidth - 24);
    text(profile.dimensions(), left + 12, y + cardHeight - 12, 9, MUTED, false, cardWidth - 24);
    drawPicture(images.get(profile.image), left + cardWidth / 2, y + 83, cardWidth - 32, 110);
    if (column == columns - 1) y += cardHeight + gap;
   }
  }

  private void drawPicture(Bitmap symbol, float cx, float cy, float width, float height) {
   float scale = Math.min(width / symbol.getWidth(), height / symbol.getHeight());
   float halfWidth = symbol.getWidth() * scale / 2, halfHeight = symbol.getHeight() * scale / 2;
   picture.set(cx - halfWidth, cy - halfHeight, cx + halfWidth, cy + halfHeight);
   page.getCanvas().drawBitmap(symbol, null, picture, bitmapPaint);
  }

  void finish() {
   if (page == null) return;
   text(String.valueOf(number), width - MARGIN - 12, height - 20, 9, MUTED, false, 40);
   pdf.finishPage(page);
   page = null;
  }

  private void text(String value, float x, float baseline, int size, int color, boolean bold, float max) {
   paint.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
   paint.setTextSize(size);
   paint.setColor(color);
   if (paint.measureText(value) > max) {
    int fit = paint.breakText(value, true, Math.max(0, max - paint.measureText("…")), null);
    value = value.substring(0, fit) + "…";
   }
   page.getCanvas().drawText(value, x, baseline, paint);
  }
 }
}
