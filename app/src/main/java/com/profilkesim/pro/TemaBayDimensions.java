package com.profilkesim.pro;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;

final class TemaBayDimensions {
 private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

 TemaBayDimensions() {
  paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
  paint.setTextAlign(Paint.Align.CENTER);
 }

 static String kind(TemaLayout.Opening bay, boolean opening, boolean empty) {
  return empty ? "Boş kasa" : bay.door ? "Kapı" : opening ? "Kanat" : "Sabit";
 }

 static String size(int width, int height) {
  return ShopDimensions.mm(width) + " × " + ShopDimensions.mm(height) + " mm";
 }

 static String caption(int number, String kind) { return "Bölme " + number + " · " + kind; }

 void draw(Canvas canvas, RectF bounds, TemaLayout.Opening bay, int number, String kind,
           boolean measured, float unit, String glassSize) {
  float width = bounds.width(), height = bounds.height();
  if (width < 7 * unit || height < 7 * unit) return;
  float x = bounds.centerX(), y = bounds.centerY();
  String badge = String.valueOf(number);
  String first = caption(number, kind);
  paint.setTextSize(8 * unit);
  if (!measured) {
   chip(canvas, width, height, x, y, unit, paint.measureText(first) + 6 * unit <= width ? first : badge);
   return;
  }
  if (width >= 70 * unit && height >= 55 * unit) guidelines(canvas, bounds, unit);
  String en = "En " + ShopDimensions.mm(bay.width()) + " mm";
  String boy = "Boy " + ShopDimensions.mm(bay.height()) + " mm";
  if (height >= 48 * unit && width >= 60 * unit && glassSize != null) {
   chip(canvas, width, height, x, y, unit, "B" + badge + " · " + kind, en, boy, "Cam " + glassSize);
  } else if (height >= 32 * unit && width >= 45 * unit) {
   chip(canvas, width, height, x, y, unit, "B" + badge + " · " + kind, en, boy);
  } else {
   chip(canvas, width, height, x, y, unit, ShopDimensions.mm(bay.width()) + " × " + ShopDimensions.mm(bay.height()));
  }
 }

 private void chip(Canvas canvas, float width, float height, float x, float y, float unit, String... lines) {
  float max = 0;
  for (String line : lines) max = Math.max(max, paint.measureText(line));
  float gap = 3 * unit;
  float lineHeight = paint.descent() - paint.ascent();
  float scale = Math.min(1f, Math.min((width - 2 * gap) / max, (height - 2 * gap) / (lineHeight * lines.length)));
  if (scale <= .15f) return;
  paint.setTextSize(paint.getTextSize() * scale);
  lineHeight = paint.descent() - paint.ascent();
  float w = max * scale / 2 + gap;
  float h = lineHeight * lines.length / 2 + gap;
  paint.setColor(0xf2204457);
  canvas.drawRoundRect(x - w, y - h, x + w, y + h, 3 * unit, 3 * unit, paint);
  paint.setColor(0xffecfbff);
  float baseline = y - h + gap - paint.ascent();
  for (String line : lines) {
   canvas.drawText(line, x, baseline, paint);
   baseline += lineHeight;
  }
 }

 private void guidelines(Canvas canvas, RectF bounds, float unit) {
  paint.setStrokeWidth(unit * .65f);
  paint.setColor(0xc97dd4e6);
  float tick = 3 * unit;
  float y = bounds.bottom - 8 * unit, x = bounds.left + 8 * unit;
  canvas.drawLine(bounds.left, y, bounds.right, y, paint);
  canvas.drawLine(bounds.left, y - tick, bounds.left, y + tick, paint);
  canvas.drawLine(bounds.right, y - tick, bounds.right, y + tick, paint);
  canvas.drawLine(x, bounds.top, x, bounds.bottom, paint);
  canvas.drawLine(x - tick, bounds.top, x + tick, bounds.top, paint);
  canvas.drawLine(x - tick, bounds.bottom, x + tick, bounds.bottom, paint);
 }
}
