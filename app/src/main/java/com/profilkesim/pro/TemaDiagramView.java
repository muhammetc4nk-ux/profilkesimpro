package com.profilkesim.pro;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.View;

final class TemaDiagramView extends View {
 private final TemaJoinery.Order order;
 private final TemaJoinery.Plan plan;
 private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
 private final float density;

 TemaDiagramView(Context context, TemaJoinery.Order order, TemaJoinery.Plan plan) {
  super(context);
  this.order = order;
  this.plan = plan;
  density = getResources().getDisplayMetrics().density;
  setContentDescription("Numaralı doğrama şeması, " + plan.columns.size() + " sütun ve "
   + plan.rows.size() + " sıra");
 }

 @Override protected void onDraw(Canvas canvas) {
  float margin = 22f * density;
  if (getWidth() <= 2 * margin || getHeight() <= 2 * margin) return;
  float scale = Math.min((getWidth() - 2 * margin) / order.width, (getHeight() - 2 * margin) / order.height);
  float width = order.width * scale, height = order.height * scale;
  float left = (getWidth() - width) / 2f, top = (getHeight() - height) / 2f;
  paint.setColor(0xff83d2d3);
  canvas.drawRect(left, top, left + width, top + height, paint);
  float y = top + order.frameFace * scale;
  for (int row = 0; row < plan.rows.size(); row++) {
   float x = left + order.frameFace * scale;
   float paneHeight = plan.rows.get(row) * scale;
   for (int col = 0; col < plan.columns.size(); col++) {
    float paneWidth = plan.columns.get(col) * scale;
    int index = row * plan.columns.size() + col;
    boolean sash = plan.panes.get(index).sash;
    paint.setColor(sash ? 0xff225d70 : 0xff1f3d52);
    canvas.drawRect(x, y, x + paneWidth, y + paneHeight, paint);
    if (sash) {
     paint.setColor(0xff659ca7);
     paint.setStrokeWidth(density);
     canvas.drawLine(x + paneWidth, y, x, y + paneHeight / 2f, paint);
     canvas.drawLine(x, y + paneHeight / 2f, x + paneWidth, y + paneHeight, paint);
    }
    if (paneWidth >= 16f * density && paneHeight >= 14f * density) {
     drawLabel(canvas, Integer.toString(index + 1), x + paneWidth / 2f,
      y + paneHeight / 2f + 4f * density, Math.min(12f * density, paneWidth / 3f));
    }
    x += paneWidth + order.mullionFace * scale;
   }
   y += paneHeight + order.mullionFace * scale;
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

 private void drawLabel(Canvas canvas, String value, float x, float y, float size) {
  paint.setColor(0xffe8f8f8);
  paint.setTextAlign(Paint.Align.CENTER);
  paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
  paint.setTextSize(size);
  canvas.drawText(value, x, y, paint);
 }
}
