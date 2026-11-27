package com.profilkesim.pro;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import java.util.Set;

final class TemaDiagramRenderer {
 private final TemaJoinery.Order order;
 private final TemaJoinery.Plan plan;
 private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
 private final float density;
 private final TemaElevation elevation;
 private final Typeface typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL);
 private final Set<Integer> leftCells;
 private final TemaJoinery.Pane[] panes;
 private final boolean print;

 TemaDiagramRenderer(TemaJoinery.Order order, TemaJoinery.Plan plan, TemaCatalog.Series series,
                     Set<Integer> leftCells, float density, boolean print) {
  this.order = order;
  this.plan = plan;
  this.leftCells = leftCells;
  this.density = density;
  this.print = print;
  elevation = new TemaElevation(series, density);
  panes = new TemaJoinery.Pane[plan.layout.bays.size()];
  for (TemaJoinery.Pane pane : plan.panes) panes[pane.number - 1] = pane;
 }

 void draw(Canvas canvas, int viewWidth, int viewHeight) {
  float margin = 50f * density;
  if (viewWidth <= 2 * margin || viewHeight <= 2 * margin) return;
  float scale = Math.min((viewWidth - 2 * margin) / order.width, (viewHeight - 2 * margin) / order.height);
  float width = order.width * scale, height = order.height * scale;
  float left = (viewWidth - width) / 2f, top = (viewHeight - height) / 2f;
  paint.setColor(print ? 0x16224e65 : 0x1238cfe0);
  paint.setStrokeWidth(density / 2f);
  Rect visible = canvas.getClipBounds();
  float gridStep = 18 * density;
  for (float x = margin + Math.max(0, (int)((visible.left - margin) / gridStep)) * gridStep;
       x < Math.min(visible.right, viewWidth - margin); x += gridStep) {
   canvas.drawLine(x, margin, x, viewHeight - margin, paint);
  }
  for (float y = margin + Math.max(0, (int)((visible.top - margin) / gridStep)) * gridStep;
       y < Math.min(visible.bottom, viewHeight - margin); y += gridStep) {
   canvas.drawLine(margin, y, viewWidth - margin, y, paint);
  }
  elevation.structure(canvas, plan.layout, plan.layout.members(order.continuous), order.corner, left, top, scale, scale);
  for (int i = 0; i < panes.length; i++) {
   drawBay(canvas, i + 1, plan.layout.bays.get(i), panes[i], left, top, scale);
  }
  for (TemaJoinery.ProfileMark mark : plan.profileMarks) drawProfileMark(canvas, mark, left, top, scale);
  paint.setColor(print ? 0xff526775 : 0xffecc78f);
  paint.setStrokeWidth(density);
  float dimensionY = top - 29f * density;
  canvas.drawLine(left, dimensionY, left + width, dimensionY, paint);
  canvas.drawLine(left, dimensionY - 2f * density, left, dimensionY + 2f * density, paint);
  canvas.drawLine(left + width, dimensionY - 2f * density, left + width, dimensionY + 2f * density, paint);
  drawLabel(canvas, "Dış en " + ShopDimensions.mm(order.width) + " mm", viewWidth / 2f,
   dimensionY - 3f * density, 11f * density, print ? 0xff243645 : 0xffe8f8f8);
  paint.setColor(print ? 0xff526775 : 0xffecc78f);
  float dimensionX = left + width + 29 * density;
  canvas.drawLine(dimensionX, top, dimensionX, top + height, paint);
  canvas.drawLine(dimensionX - 2 * density, top, dimensionX + 2 * density, top, paint);
  canvas.drawLine(dimensionX - 2 * density, top + height, dimensionX + 2 * density, top + height, paint);
  float middle = top + height / 2;
  canvas.save();
  canvas.rotate(90, dimensionX, middle);
  drawLabel(canvas, "Dış boy " + ShopDimensions.mm(order.height) + " mm", dimensionX,
   middle - 3 * density, 11f * density, print ? 0xff243645 : 0xffe8f8f8);
  canvas.restore();
 }

 private void drawBay(Canvas canvas, int number, TemaLayout.Opening bay, TemaJoinery.Pane pane,
                      float x, float y, float scale) {
  float left = x + bay.left * scale, right = x + bay.right * scale;
  float top = y + bay.top * scale, bottom = y + bay.bottom * scale;
  if (canvas.quickReject(left, top, right, bottom, Canvas.EdgeType.AA)) return;
  if (pane == null) {
   drawChip(canvas, number + " · Boş kasa", (left + right) / 2, (top + bottom) / 2,
    right - left - 4 * density, 0xff24424f, 11);
   return;
  }
  boolean sash = pane.sash != null;
  RectF inside = elevation.opening(canvas, bay, pane.sash, leftCells.contains(number), order.sashCorner, x, y, scale, scale);
  if (inside == null) return;
  left = inside.left;
  right = inside.right;
  top = inside.top;
  bottom = inside.bottom;
  float center = (left + right) / 2, middle = (top + bottom) / 2;
  float width = right - left - 4 * density;
  float rows = (bottom - top) / (16 * density);
  int color = 0xff1d3448;
  String title = "Bölme " + number + " · " + (bay.door ? "Kapı" : sash ? "Kanat" : "Sabit");
  String glass = "Cam " + size(pane.width, pane.height);
  if (rows < 2) {
   if (rows >= 0.8f) drawChip(canvas, number + " · " + glass, center, middle, width, color, 11);
   return;
  }
  drawChip(canvas, title, center, middle - 8 * density, width, color, 11);
  drawChip(canvas, glass, center, middle + 8 * density, width, color, 11);
  if (rows >= 4) {
   drawChip(canvas, "İç " + size(pane.insideWidth, pane.insideHeight), center, middle - 24 * density, width, color, 10);
   if (pane.beadCode != null) elevation.accessoryLabel(canvas, pane.beadCode, center, middle + 24 * density, width, color);
  }
  if (rows >= 6 && pane.adapterCode != null) {
   elevation.accessoryLabel(canvas, pane.adapterCode, center, middle + 40 * density, width, color);
  }
 }

 private static String size(int width, int height) {
  return ShopDimensions.mm(width) + " × " + ShopDimensions.mm(height) + " mm";
 }

 private void drawProfileMark(Canvas canvas, TemaJoinery.ProfileMark mark, float left, float top, float scale) {
  float x = left + (mark.vertical ? mark.axis : (mark.start + mark.end) / 2f) * scale;
  float y = top + (mark.vertical ? (mark.start + mark.end) / 2f : mark.axis) * scale;
  int color;
  switch (mark.role) {
   case FRAME:
    color = 0xff284756;
    float profileX = x, profileY = y;
    if (mark.vertical) x = mark.axis < order.width / 2 ? left - 9 * density : left + order.width * scale + 9 * density;
    else y = mark.axis < order.height / 2 ? top - 8 * density : top + order.height * scale + 8 * density;
    elevation.profileLeader(canvas, x, y, profileX, profileY);
    break;
   case SASH: color = 0xff49376f; break;
   default: color = 0xff184f55;
  }
  elevation.fabricationLabel(canvas, mark, x, y, (mark.end - mark.start) * scale, color);
 }

 private void drawChip(Canvas canvas, String value, float x, float y, float maxWidth, int background, float sp) {
  float pad = 3 * density;
  if (maxWidth <= 2 * pad) return;
  paint.setTypeface(typeface);
  paint.setTextSize(sp * density);
  float textWidth = paint.measureText(value);
  if (textWidth + 2 * pad > maxWidth) {
   float fitted = sp * density * (maxWidth - 2 * pad) / textWidth;
   if (fitted < 8 * density) return;
   paint.setTextSize(fitted);
   textWidth = paint.measureText(value);
  }
  float halfWidth = textWidth / 2 + pad;
  float halfHeight = (paint.descent() - paint.ascent()) / 2 + density;
  float baseline = y - (paint.ascent() + paint.descent()) / 2;
  paint.setColor(background);
  canvas.drawRoundRect(x - halfWidth, y - halfHeight, x + halfWidth, y + halfHeight,
   3 * density, 3 * density, paint);
  drawLabel(canvas, value, x, baseline, paint.getTextSize(), 0xffe8f8f8);
 }
 private void drawLabel(Canvas canvas, String value, float x, float y, float size, int color) {
  paint.setColor(color);
  paint.setTextAlign(Paint.Align.CENTER);
  paint.setTypeface(typeface);
  paint.setTextSize(size);
  canvas.drawText(value, x, y, paint);
 }
}
