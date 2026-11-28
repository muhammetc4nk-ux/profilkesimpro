package com.profilkesim.pro;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import java.util.List;

/** Front elevation drawn from the same frame, records and openings used for fabrication. */
final class TemaElevation {
 private final TemaCatalog.Series series;
 private final float density;
 private float annotationDensity;
 private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
 private final Path outline = new Path();
 private final RectF bounds = new RectF();

 TemaElevation(TemaCatalog.Series series, float density) {
  this.series = series;
  this.density = density;
  annotationDensity = density;
  paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
 }

 void structure(Canvas canvas, TemaLayout layout, List<TemaLayout.Member> members, TemaJoinery.Corner corner,
                float x, float y, float sx, float sy) {
  float right = x + layout.width * sx, bottom = y + layout.height * sy;
  float leftFace = layout.frame.left.face * sx, topFace = layout.frame.top.face * sy;
  float rightFace = layout.frame.right.face * sx, bottomFace = layout.bottomFace * sy;
  boolean leftSill = !layout.rails.isEmpty() && layout.rails.get(0).start == 0;
  boolean rightSill = !layout.rails.isEmpty() && layout.rails.get(layout.rails.size() - 1).end == layout.width;
  TemaJoinery.Corner tl = TemaJoinery.frameCorner(layout.frame, corner, true, true);
  TemaJoinery.Corner tr = TemaJoinery.frameCorner(layout.frame, corner, true, false);
  TemaJoinery.Corner bl = TemaJoinery.frameCorner(layout.frame, corner, false, true);
  TemaJoinery.Corner br = TemaJoinery.frameCorner(layout.frame, corner, false, false);
  jamb(canvas, x, y, bottom, leftFace, topFace, leftSill ? bottomFace : 0, false, tl, bl, false);
  jamb(canvas, right - rightFace, y, bottom, rightFace, topFace, rightSill ? bottomFace : 0, true, tr, br, false);
  rail(canvas, x, right, y, topFace, leftFace, rightFace, false, tl, tr, false);
  for (TemaLayout.Span span : layout.rails) {
   rail(canvas, x + span.start * sx, x + span.end * sx, bottom - bottomFace, bottomFace,
    span.start == 0 ? leftFace : 0, span.end == layout.width ? rightFace : 0, true, bl, br, false);
  }
  for (TemaLayout.Member member : members) {
   int low = member.axis - member.section.face / 2;
   if (member.vertical) {
    float edge = x + low * sx;
    band(canvas, true, y + member.start * sy, y + member.end * sy, y + member.end * sy, y + member.start * sy,
     edge, edge + member.section.face * sx, false);
   } else {
    float edge = y + low * sy;
    band(canvas, false, x + member.start * sx, x + member.end * sx, x + member.end * sx, x + member.start * sx,
     edge, edge + member.section.face * sy, false);
   }
  }
 }

 void opening(Canvas canvas, TemaLayout.Opening bay, TemaLayout.Section sash, TemaLayout.Section skirt, boolean leftHinge,
               TemaJoinery.Corner sashCorner, float x, float y, float sx, float sy) {
  float left = x + bay.left * sx, top = y + bay.top * sy;
  float right = x + bay.right * sx, bottom = y + bay.bottom * sy;
  paint.setColor(0xff14212b);
  canvas.drawRect(left, top, right, bottom, paint);
  if (sash == null) {
   glass(canvas, left, top, right, bottom, sx, sy);
   return;
  }

  float lx = left + TemaJoinery.SASH_GAP * sx, rx = right - TemaJoinery.SASH_GAP * sx;
  float ty = top + TemaJoinery.SASH_GAP * sy, by = bottom - TemaJoinery.SASH_GAP * sy;
  float fx = sash.face * sx, fy = sash.face * sy;
  float bottomFace = skirt == null ? fy : skirt.face * sy;
  if (rx - lx <= 2 * fx || by - ty <= fy + bottomFace) {
   paint.setColor(0xff704552);
   canvas.drawRect(left, top, right, bottom, paint);
   return;
  }
  jamb(canvas, lx, ty, by, fx, fy, bottomFace, false, sashCorner, true);
  jamb(canvas, rx - fx, ty, by, fx, fy, bottomFace, true, sashCorner, true);
  rail(canvas, lx, rx, ty, fy, fx, fx, false, sashCorner, true);
  rail(canvas, lx, rx, by - bottomFace, bottomFace, fx, fx, true, sashCorner, true);
  if (skirt != null) {
   float center = (lx + rx) / 2;
   String pay = "Pay " + ShopDimensions.mm(skirt.face) + " mm";
   drawProfileText(canvas, skirt.code + " · Etek", skirt.code, pay, pay, false, center, by - bottomFace / 2,
    rx - lx - 2 * fx, 0xee284756);
  }
  float insideLeft = lx + fx, insideRight = rx - fx, insideTop = ty + fy, insideBottom = by - bottomFace;
  glass(canvas, insideLeft, insideTop, insideRight, insideBottom, sx, sy);
  openingDirection(canvas, leftHinge, insideLeft, insideTop, insideRight, insideBottom);
  hardware(canvas, bay.door, leftHinge, lx, ty, rx, by, fx);
 }

 private void rail(Canvas c, float left, float right, float y, float face, float leftFace, float rightFace,
                   boolean bottom, TemaJoinery.Corner corner, boolean leaf) {
  rail(c, left, right, y, face, leftFace, rightFace, bottom, corner, corner, leaf);
 }

 private void rail(Canvas c, float left, float right, float y, float face, float leftFace, float rightFace,
                   boolean bottom, TemaJoinery.Corner leftJoint, TemaJoinery.Corner rightJoint, boolean leaf) {
  float outsideLeft = left + (leftJoint == TemaJoinery.Corner.VERTICAL_FULL ? leftFace : 0);
  float outsideRight = right - (rightJoint == TemaJoinery.Corner.VERTICAL_FULL ? rightFace : 0);
  float insideLeft = left + (leftJoint == TemaJoinery.Corner.HORIZONTAL_FULL ? 0 : leftFace);
  float insideRight = right - (rightJoint == TemaJoinery.Corner.HORIZONTAL_FULL ? 0 : rightFace);
  if (bottom) band(c, false, insideLeft, insideRight, outsideRight, outsideLeft, y, y + face, leaf);
  else band(c, false, outsideLeft, outsideRight, insideRight, insideLeft, y, y + face, leaf);
 }

 private void jamb(Canvas c, float x, float top, float bottom, float face, float topFace, float bottomFace,
                   boolean right, TemaJoinery.Corner corner, boolean leaf) {
  jamb(c, x, top, bottom, face, topFace, bottomFace, right, corner, corner, leaf);
 }

 private void jamb(Canvas c, float x, float top, float bottom, float face, float topFace, float bottomFace,
                   boolean right, TemaJoinery.Corner topJoint, TemaJoinery.Corner bottomJoint, boolean leaf) {
  float outsideTop = top + (topJoint == TemaJoinery.Corner.HORIZONTAL_FULL ? topFace : 0);
  float outsideBottom = bottom - (bottomJoint == TemaJoinery.Corner.HORIZONTAL_FULL ? bottomFace : 0);
  float insideTop = top + (topJoint == TemaJoinery.Corner.VERTICAL_FULL ? 0 : topFace);
  float insideBottom = bottom - (bottomJoint == TemaJoinery.Corner.VERTICAL_FULL ? 0 : bottomFace);
  if (right) band(c, true, insideTop, insideBottom, outsideBottom, outsideTop, x, x + face, leaf);
  else band(c, true, outsideTop, outsideBottom, insideBottom, insideTop, x, x + face, leaf);
 }

 private void band(Canvas c, boolean vertical, float first, float last, float innerLast, float innerFirst,
                   float low, float high, boolean leaf) {
  if (high <= low || last <= first || innerLast <= innerFirst) return;
  outline.reset();
  point(outline, vertical, first, low, true);
  point(outline, vertical, last, low, false);
  point(outline, vertical, innerLast, high, false);
  point(outline, vertical, innerFirst, high, false);
  outline.close();
  paint.setColor(0xffffffff);
  paint.setShader(new LinearGradient(vertical ? low : 0, vertical ? 0 : low, vertical ? high : 0, vertical ? 0 : high,
   leaf ? new int[]{0xff344654, 0xffd5e1e7, 0xff899eae, 0xff637b8c, 0xff304653}
    : new int[]{0xff425868, 0xffdce6ec, 0xff94a6b1, 0xff788d9d, 0xff344753},
   new float[]{0f, .16f, .28f, .82f, 1f}, Shader.TileMode.CLAMP));
  c.drawPath(outline, paint);
  paint.setShader(null);
  paint.setStyle(Paint.Style.STROKE);
  paint.setStrokeWidth(Math.min(.8f * density, (high - low) / 5f));
  paint.setColor(0xff243846);
  c.drawPath(outline, paint);
  paint.setStyle(Paint.Style.FILL);
  if (high - low >= 3 * density) {
   groove(c, vertical, first, last, innerFirst, innerLast, low, high, .77f, 0x88324756);
   groove(c, vertical, first, last, innerFirst, innerLast, low, high, .89f, 0x99e0edf2);
  }
 }

 private static void point(Path path, boolean vertical, float along, float across, boolean move) {
  float x = vertical ? across : along, y = vertical ? along : across;
  if (move) path.moveTo(x, y);
  else path.lineTo(x, y);
 }

 private void groove(Canvas c, boolean vertical, float start, float end, float innerStart, float innerEnd,
                     float low, float high, float offset, int color) {
  float axis = low + (high - low) * offset;
  float first = start + (innerStart - start) * offset, last = end + (innerEnd - end) * offset;
  paint.setColor(color);
  c.drawLine(vertical ? axis : first, vertical ? first : axis, vertical ? axis : last, vertical ? last : axis, paint);
 }

 private void glass(Canvas c, float left, float top, float right, float bottom, float sx, float sy) {
  float trim = TemaJoinery.GLASS_DEDUCTION / 2f;
  left += trim * sx;
  right -= trim * sx;
  top += trim * sy;
  bottom -= trim * sy;
  if (right <= left || bottom <= top) return;
  paint.setColor(0xffffffff);
  paint.setShader(new LinearGradient(left, top, right, bottom, new int[]{0xff507589, 0xff244451, 0xff365f74},
   new float[]{0f, .68f, 1f}, Shader.TileMode.CLAMP));
  c.drawRect(left, top, right, bottom, paint);
  paint.setShader(null);
  c.save();
  c.clipRect(left, top, right, bottom);
  paint.setColor(0x24dff9ff);
  paint.setStrokeWidth(Math.min(right - left, bottom - top) * .07f);
  c.drawLine(left, top + (bottom - top) * .9f, right, top + (bottom - top) * .12f, paint);
  paint.setColor(0x1788ddee);
  paint.setStrokeWidth(Math.min(right - left, bottom - top) * .02f);
  c.drawLine(left, top + (bottom - top) * .97f, right, top + (bottom - top) * .19f, paint);
  c.restore();
  paint.setColor(0xff192a35);
  paint.setStyle(Paint.Style.STROKE);
  paint.setStrokeWidth(Math.min(density * 1.3f, Math.min(right - left, bottom - top) / 4f));
  c.drawRect(left, top, right, bottom, paint);
  paint.setStyle(Paint.Style.FILL);
 }

 private void openingDirection(Canvas canvas, boolean leftHinge, float left, float top, float right, float bottom) {
  float inset = Math.min(right - left, bottom - top) * .08f;
  float tip = leftHinge ? right - inset : left + inset;
  float base = leftHinge ? left + inset : right - inset;
  paint.setColor(0xeef0faff);
  paint.setStrokeWidth(annotationDensity);
  canvas.drawLine(base, top + inset, tip, (top + bottom) / 2, paint);
  canvas.drawLine(tip, (top + bottom) / 2, base, bottom - inset, paint);
 }

 private void hardware(Canvas c, boolean door, boolean leftHinge, float left, float top, float right,
                       float bottom, float face) {
  if (face < density || right - left < 20 * density || bottom - top < 22 * density) return;
  float hingeX = leftHinge ? left + face * .22f : right - face * .22f;
  float handleX = leftHinge ? right - face * .48f : left + face * .48f;
  float unit = Math.min(2.9f * density, face * .26f);
  float height = bottom - top;
  paint.setColor(0xff263743);
  hardwareHinge(c, hingeX, top + height * .17f, unit);
  hardwareHinge(c, hingeX, bottom - height * .17f, unit);
  float handleY = door ? top + height * .59f : (top + bottom) / 2;
  bounds.set(handleX - unit, handleY - 2 * unit, handleX + unit, handleY + 2 * unit);
  c.drawRoundRect(bounds, unit, unit, paint);
  float leverLength = Math.min(10 * density, 4.5f * unit);
  paint.setColor(0xffecf4f8);
  paint.setStrokeWidth(1.22f * unit);
  paint.setStrokeCap(Paint.Cap.ROUND);
  c.drawLine(handleX, handleY, handleX + (leftHinge ? -leverLength : leverLength), handleY, paint);
  paint.setStrokeCap(Paint.Cap.BUTT);
 }

 private void hardwareHinge(Canvas c, float x, float y, float unit) {
  bounds.set(x - unit, y - unit * 1.9f, x + unit, y + unit * 1.9f);
  c.drawRoundRect(bounds, unit * .4f, unit * .4f, paint);
 }

 void setAnnotationDensity(float density) { annotationDensity = density; }

 void profileLeader(Canvas canvas, float x, float y, float targetX, float targetY) {
  paint.setColor(0xaa96bdcf);
  paint.setStrokeWidth(annotationDensity);
  canvas.drawLine(x, y, targetX, targetY, paint);
 }

 void profileLabel(Canvas c, String code, int face, boolean vertical, float x, float y, float span, int background) {
  TemaCatalog.Profile profile = series.find(code);
  if (face <= 0 && profile != null) face = profile.face;
  String name = series.nameOf(code);
  String width = face > 0 ? ShopDimensions.mm(face) + " mm" : "";
  String combined = width.isEmpty() ? name : name + " · " + width;
  paint.setTextSize(8 * annotationDensity);
  if (paint.measureText(combined) <= (span - 8 * annotationDensity) / .8f) {
   drawProfileText(c, combined, combined, "", "", vertical, x, y, span, background);
  } else {
   drawProfileText(c, name, name, width, width, vertical, x, y, span, background);
  }
 }

 void fabricationLabel(Canvas c, TemaJoinery.ProfileMark mark, float x, float y, float span, int background) {
  String name = series.nameOf(mark.code);
  String allowance = mark.allowanceText();
  String detail = allowance + " · Kesim " + ShopDimensions.mm(mark.length) + " mm";
  drawProfileText(c, name, name, detail, allowance, mark.vertical, x, y, span, background);
 }

 void accessoryLabel(Canvas c, String code, float x, float y, float span, int background) {
  TemaCatalog.Profile profile = series.find(code);
  String name = series.nameOf(code);
  String face = profile == null ? "" : ShopDimensions.mm(profile.face) + " mm";
  drawProfileText(c, name, name, face, face, false, x, y, span, background);
 }

 private void drawProfileText(Canvas c, String text, String shortName, String detail, String shortDetail, boolean vertical,
                              float x, float y, float span, int background) {
  paint.setTextAlign(Paint.Align.CENTER);
  paint.setTextSize(8 * annotationDensity);
  float max = span - 8 * annotationDensity;
  if (max <= 0) return;
  if (paint.measureText(text) > max / .8f) text = shortName;
  if (paint.measureText(detail) > max / .8f) detail = shortDetail;
  if (paint.measureText(text) > max / .76f) {
   int count = paint.breakText(text, true, Math.max(0f, max / .76f - paint.measureText("…")), null);
   if (count < 2) return;
   text = text.substring(0, count) + "…";
  }
  float width = Math.max(paint.measureText(text), paint.measureText(detail));
  if (width <= 0) return;
  float sizeRatio = Math.min(1f, max / width);
  if (sizeRatio < .76f) return;
  paint.setTextSize(8 * sizeRatio * annotationDensity);
  float line = paint.descent() - paint.ascent();
  float halfHeight = (detail.isEmpty() ? .5f : 1f) * line + annotationDensity;
  float halfWidth = width * sizeRatio / 2 + 3 * annotationDensity;
  float baseline = y - (paint.ascent() + paint.descent()) / 2;
  c.save();
  if (vertical) c.rotate(-90, x, y);
  paint.setColor(background);
  c.drawRoundRect(x - halfWidth, y - halfHeight, x + halfWidth, y + halfHeight, 3 * annotationDensity, 3 * annotationDensity, paint);
  paint.setColor(0xffeaf8fc);
  c.drawText(text, x, detail.isEmpty() ? baseline : baseline - line / 2, paint);
  if (!detail.isEmpty()) {
   paint.setColor(0xffb9d9e6);
   c.drawText(detail, x, baseline + line / 2, paint);
  }
  c.restore();
 }
}
