package com.profilkesim.pro;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;

final class TemaDiagramView extends View {
 private final TemaJoinery.Order order;
 private final TemaJoinery.Plan plan;
 private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
 private final float density;
 private final int accent;
 private final Typeface typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL);
 private Shader metal, fixedGlass, openingGlass;
 private final String leftCells;

 TemaDiagramView(Context context, TemaJoinery.Order order, TemaJoinery.Plan plan, int accent, String leftCells) {
  super(context);
  this.order = order;
  this.plan = plan;
  this.accent = accent;
  this.leftCells = leftCells;
  density = getResources().getDisplayMetrics().density;
  setContentDescription("Numaralı doğrama şeması, " + plan.layout.bays.size() + " bölme");
 }

 @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
  metal = new LinearGradient(0, 0, width, height, new int[]{accent, 0xff608c9f, 0xffb499ff, accent},
   null, Shader.TileMode.CLAMP);
  fixedGlass = new LinearGradient(0, 0, width, height, 0xff315d70, 0xff162c43, Shader.TileMode.CLAMP);
  openingGlass = new LinearGradient(0, 0, width, height, 0xff4e436a, 0xff234951, Shader.TileMode.CLAMP);
 }

 @Override protected void onDraw(Canvas canvas) {
  float margin = 22f * density;
  if (getWidth() <= 2 * margin || getHeight() <= 2 * margin) return;
  float scale = Math.min((getWidth() - 2 * margin) / order.width, (getHeight() - 2 * margin) / order.height);
  float width = order.width * scale, height = order.height * scale;
  float left = (getWidth() - width) / 2f, top = (getHeight() - height) / 2f;
  paint.setColor(0x1238cfe0);
  paint.setStrokeWidth(density / 2f);
  for (float x = margin; x < getWidth() - margin; x += 18f * density) canvas.drawLine(x, margin, x, getHeight() - margin, paint);
  for (float y = margin; y < getHeight() - margin; y += 18f * density) canvas.drawLine(margin, y, getWidth() - margin, y, paint);
  paint.setColor(0xffffffff);
  paint.setShader(metal);
  drawStructure(canvas, paint, plan.layout, order.continuous, left, top, scale, scale);
  paint.setShader(null);
  for (int i = 0; i < plan.layout.bays.size(); i++) {
   TemaLayout.Opening bay = plan.layout.bays.get(i);
   drawBay(canvas, i + 1, left + bay.left * scale, top + bay.top * scale,
    left + bay.right * scale, top + bay.bottom * scale, scale);
  }
  paint.setColor(0xffecc78f);
  paint.setStrokeWidth(density);
  float dimensionY = top - 6f * density;
  canvas.drawLine(left, dimensionY, left + width, dimensionY, paint);
  canvas.drawLine(left, dimensionY - 2f * density, left, dimensionY + 2f * density, paint);
  canvas.drawLine(left + width, dimensionY - 2f * density, left + width, dimensionY + 2f * density, paint);
  drawLabel(canvas, ShopDimensions.mm(order.width) + " mm", getWidth() / 2f,
   dimensionY - 3f * density, 11f * density);
  drawLabel(canvas, "Yükseklik " + ShopDimensions.mm(order.height) + " mm", getWidth() / 2f,
   top + height + 16f * density, 11f * density);
 }

 static void drawStructure(Canvas canvas, Paint paint, TemaLayout layout, TemaJoinery.Continuous continuous,
                           float x, float y, float sx, float sy) {
  TemaLayout.Frame frame = layout.frame;
  canvas.drawRect(x, y, x + layout.width * sx, y + frame.top.face * sy, paint);
  canvas.drawRect(x, y, x + frame.left.face * sx, y + layout.height * sy, paint);
  canvas.drawRect(x + (layout.width - frame.right.face) * sx, y, x + layout.width * sx, y + layout.height * sy, paint);
  for (TemaLayout.Span span : layout.rails) {
   canvas.drawRect(x + span.start * sx, y + (layout.height - layout.bottomFace) * sy,
    x + span.end * sx, y + layout.height * sy, paint);
  }
  for (TemaLayout.Member record : layout.members(continuous)) {
   int low = record.axis - record.section.face / 2, high = low + record.section.face;
   if (record.vertical) canvas.drawRect(x + low * sx, y + record.start * sy, x + high * sx, y + record.end * sy, paint);
   else canvas.drawRect(x + record.start * sx, y + low * sy, x + record.end * sx, y + high * sy, paint);
  }
 }

 private void drawBay(Canvas canvas, int number, float left, float top, float right, float bottom, float scale) {
  if (order.emptyCells.contains(number)) return;
  boolean sash = order.openingSashes.contains(number);
  paint.setColor(0xffffffff);
  paint.setShader(sash ? openingGlass : fixedGlass);
  canvas.drawRect(left, top, right, bottom, paint);
  paint.setShader(null);
  if (sash) {
   float gap = order.sashGaps.getOrDefault(number, order.sashGap) * scale;
   left += gap;
   top += gap;
   right -= gap;
   bottom -= gap;
   paint.setColor(0xffc2a7ff);
   paint.setStrokeWidth(2 * density);
   paint.setStyle(Paint.Style.STROKE);
   canvas.drawRect(left, top, right, bottom, paint);
   paint.setStyle(Paint.Style.FILL);
   boolean isLeft = java.util.Arrays.asList(leftCells.split(";")).contains(String.valueOf(number));
   float hinge = isLeft ? left : right, handle = isLeft ? right : left;
   canvas.drawLine(hinge, top, handle, (top + bottom) / 2, paint);
   canvas.drawLine(handle, (top + bottom) / 2, hinge, bottom, paint);
  }
  if (right - left >= 16 * density && bottom - top >= 14 * density) {
   drawLabel(canvas, String.valueOf(number), (left + right) / 2, (top + bottom) / 2, 11 * density);
  }
 }
 private void drawLabel(Canvas canvas, String value, float x, float y, float size) {
  paint.setColor(0xffe8f8f8);
  paint.setTextAlign(Paint.Align.CENTER);
  paint.setTypeface(typeface);
  paint.setTextSize(size);
  canvas.drawText(value, x, y, paint);
 }
}
