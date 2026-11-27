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
 private String regional="", leftCells="";
 TemaDiagramView(Context context,TemaJoinery.Order order,TemaJoinery.Plan plan,int accent,String regional,String leftCells){this(context,order,plan,accent);this.regional=regional;this.leftCells=leftCells;}

 TemaDiagramView(Context context, TemaJoinery.Order order, TemaJoinery.Plan plan, int accent) {
  super(context);
  this.order = order;
  this.plan = plan;
  this.accent = accent;
  density = getResources().getDisplayMetrics().density;
  setContentDescription("Numaralı doğrama şeması, " + plan.columns.size() + " sütun ve "
   + plan.rows.size() + " sıra");
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
  canvas.drawRect(left, top, left + width, top + height, paint);
  paint.setShader(null);
  if(!regional.isEmpty()){
   float ix=left+order.frameFace*scale,iy=top+order.frameFace*scale,iw=width-2*order.frameFace*scale,ih=height-2*order.frameFace*scale;
   int n=0;for(String cell:regional.split(";")){String[] p=cell.split(",");n++;float l=ix+iw*Integer.parseInt(p[0])/1000f,t=iy+ih*Integer.parseInt(p[1])/1000f,r=ix+iw*Integer.parseInt(p[2])/1000f,b=iy+ih*Integer.parseInt(p[3])/1000f;float f=order.mullionFace*scale/2; if(!p[0].equals("0"))l+=f;if(!p[1].equals("0"))t+=f;if(!p[2].equals("1000"))r-=f;if(!p[3].equals("1000"))b-=f;drawBay(canvas,n,l,t,r,b);}
  }else{
  float y = top + order.frameFace * scale;
  for (int row = 0; row < plan.rows.size(); row++) {
   float x = left + order.frameFace * scale;
   float paneHeight = plan.rows.get(row) * scale;
   for (int col = 0; col < plan.columns.size(); col++) {
    float paneWidth = plan.columns.get(col) * scale;
    int index = row * plan.columns.size() + col;
    boolean sash = order.openingSashes.contains(index+1);
    paint.setColor(0xffffffff);
    paint.setShader(sash ? openingGlass : fixedGlass);
    canvas.drawRect(x, y, x + paneWidth, y + paneHeight, paint);
    paint.setShader(null);
    if (sash) {
     paint.setColor(0xffc2a7ff);
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

 private void drawBay(Canvas canvas,int n,float l,float t,float r,float b){
  boolean sash=order.openingSashes.contains(n);
  paint.setColor(0xffffffff);paint.setShader(order.emptyCells.contains(n)?null:sash?openingGlass:fixedGlass);canvas.drawRect(l,t,r,b,paint);paint.setShader(null);
  if(sash){float gap=order.sashGaps.getOrDefault(n,order.sashGap)*Math.min((getWidth()-44*density)/order.width,(getHeight()-44*density)/order.height);l+=gap;t+=gap;r-=gap;b-=gap;paint.setColor(0xffc2a7ff);paint.setStrokeWidth(2*density);paint.setStyle(Paint.Style.STROKE);canvas.drawRect(l,t,r,b,paint);paint.setStyle(Paint.Style.FILL);boolean isLeft=java.util.Arrays.asList(leftCells.split(";")).contains(String.valueOf(n));float hinge=isLeft?l:r,handle=isLeft?r:l;canvas.drawLine(hinge,t,handle,(t+b)/2,paint);canvas.drawLine(handle,(t+b)/2,hinge,b,paint);}
  drawLabel(canvas,String.valueOf(n),(l+r)/2,(t+b)/2,11*density);
 }
 private void drawLabel(Canvas canvas, String value, float x, float y, float size) {
  paint.setColor(0xffe8f8f8);
  paint.setTextAlign(Paint.Align.CENTER);
  paint.setTypeface(typeface);
  paint.setTextSize(size);
  canvas.drawText(value, x, y, paint);
 }
}
