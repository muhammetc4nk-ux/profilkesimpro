package com.profilkesim.pro;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import java.io.IOException;
import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

final class TemaPdfExport {
 private static final int MARGIN = 38;
 private static final int INK = 0xff192f3e;
 private static final int MUTED = 0xff4d6372;
 private static final int RULE = 0xffd7e4ea;

 private TemaPdfExport() {}

 static void write(OutputStream output, String name, TemaCatalog.Series series, TemaJoinery.Order order,
                   TemaJoinery.Plan plan, Set<Integer> leftHinges) throws IOException {
  boolean landscape = order.width > order.height;
  int pageWidth = landscape ? 842 : 595, pageHeight = landscape ? 595 : 842;
  PdfDocument pdf = new PdfDocument();
  Pages pages = new Pages(pdf, pageWidth, pageHeight, series.label + " · " + name);
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
   pages.line("Tüm ölçüler mm. Orta kayıt payları iki komşu bölme için sırayla gösterilir.", "", false);

   pages.next("Profil payları ve kesim listesi");
   pages.heading("Çizimdeki profil payları");
   Map<String, TemaJoinery.ProfileMark> marks = new LinkedHashMap<>();
   for (TemaJoinery.ProfileMark mark : plan.profileMarks) {
    marks.putIfAbsent(mark.code + ":" + mark.face + ":" + mark.role, mark);
   }
   for (TemaJoinery.ProfileMark mark : marks.values()) {
    String role = mark.role == TemaJoinery.ProfileRole.FRAME ? "Kasa"
     : mark.role == TemaJoinery.ProfileRole.MULLION ? "Orta kayıt" : "Kanat";
    pages.line(series.nameOf(mark.code) + " · " + role, mark.allowanceText(), true);
   }
   pages.heading("Profil kesimleri");
   for (TemaJoinery.Cut cut : plan.cuts) {
    String angles = cut.angle == cut.endAngle ? cut.angle + "°" : cut.angle + "° / " + cut.endAngle + "°";
    pages.line(series.displayOf(cut.code) + " · " + cut.role,
     mm(cut.length) + " mm × " + cut.quantity + " adet · " + angles, true);
   }
   if (!plan.panes.isEmpty()) {
    pages.heading("Cam kesimleri");
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
    pdf.close();
   }
  }
 }

 private static String mm(int value) { return ShopDimensions.mm(value); }
 private static String size(int width, int height) { return mm(width) + " × " + mm(height) + " mm"; }

 private static final class Pages {
  final PdfDocument pdf;
  final int width, height;
  final String title;
  final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  PdfDocument.Page page;
  int number;
  float y;

  Pages(PdfDocument pdf, int width, int height, String title) {
   this.pdf = pdf;
   this.width = width;
   this.height = height;
   this.title = title;
  }

  void next(String label) {
   finish();
   page = pdf.startPage(new PdfDocument.PageInfo.Builder(width, height, ++number).create());
   text(label, MARGIN, MARGIN, 10, MUTED, true, width - 2 * MARGIN);
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
