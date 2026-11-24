package com.profilkesim.pro;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Separates a ruled table from the surrounding photo before OCR. */
final class TableOcrRegion {
 final CutSheet.Bounds box;
 private final int firstNumber, secondNumber, dataTop, nameStart;

 private TableOcrRegion(CutSheet.Bounds box, int firstNumber, int secondNumber, int dataTop, int nameStart) {
  this.box = box;
  this.firstNumber = firstNumber;
  this.secondNumber = secondNumber;
  this.dataTop = dataTop;
  this.nameStart = nameStart;
 }

 CutTableGrid grid() {
  return new CutTableGrid(box.left, box.right, box.bottom, Arrays.asList(firstNumber, secondNumber));
 }

 int dataTop() { return dataTop; }

 int columnEdge(int index) {
  switch (index) {
   case 0: return box.left;
   case 1: return firstNumber;
   case 2: return secondNumber;
   default: return box.right;
  }
 }

 TableOcrRegion cropped() {
  return new TableOcrRegion(new CutSheet.Bounds(0, 0, box.right - box.left, box.height()),
   firstNumber - box.left, secondNumber - box.left, dataTop - box.top, nameStart - box.left);
 }

 static TableOcrRegion selected(CutTableGrid.Pixels pixels, int width, int height, int first, int second) {
  int start = nameStart(pixels, new int[width], 4, first - 4, 0, height);
  return new TableOcrRegion(new CutSheet.Bounds(0, 0, width, height), first, second, 0, start);
 }

 static TableOcrRegion find(CutTableGrid.Pixels pixels, int width, int height) {
  if (width < 200 || height < 200) return null;
  int[] colors = new int[width];
  List<Rule> columns = verticalRules(pixels, colors, height);
  TableOcrRegion best = null;
  int bestRows = 0;
  for (int i = 0; i < columns.size(); i++) {
   Rule length = columns.get(i);
   for (int j = i + 1; j < columns.size(); j++) {
    Rule quantity = columns.get(j);
    int distance = quantity.x - length.x;
    if (distance > width / 4) break;
    if (distance < width / 14 || Math.abs(length.top - quantity.top) > height / 28) continue;
    int start = Math.max(length.top, quantity.top);
    int end = Math.min(length.bottom, quantity.bottom);
    if (end - start < height / 4) continue;

    List<Integer> leftEdges = new ArrayList<>();
    List<Integer> rightEdges = new ArrayList<>();
    int lastRule = start;
    for (int y = start + 6; y < end; y++) {
     pixels.readRow(y, colors);
     if (darkShare(colors, length.x + 3, quantity.x - 3) < .82f) continue;
     int left = darkEdge(colors, length.x + 3, -1);
     int right = darkEdge(colors, quantity.x - 3, 1);
     if (length.x - left < distance * .8f || right - quantity.x < distance / 4) continue;
     if (y - lastRule > 5) {
      leftEdges.add(left);
      rightEdges.add(right);
     }
     lastRule = y;
    }
    if (leftEdges.size() < 3 || leftEdges.size() <= bestRows) continue;
    int left = median(leftEdges);
    int right = median(rightEdges);
    int top = start;
    // The inner vertical rules start where the dark header meets the pale body.
    int lastDark = start;
    for (int y = start - 1; y >= Math.max(0, start - height / 7); y--) {
     pixels.readRow(y, colors);
     if (darkShare(colors, length.x + 3, quantity.x - 3) >= .52f) {
      top = y;
      lastDark = y;
     }
     if (lastDark - y > Math.max(16, height / 38)) break;
    }
    if (start - top < height / 40) continue;
    int bottom = Math.min(height, Math.max(length.bottom, quantity.bottom) + 5);
    int nameStart = nameStart(pixels, colors, left + 5, length.x - 5, start, bottom);
    best = new TableOcrRegion(new CutSheet.Bounds(left + 2, top + 1, right - 1, bottom),
     length.x, quantity.x, start, nameStart);
    bestRows = leftEdges.size();
   }
  }
  return best;
 }

 static TableOcrRegion findWithoutHeading(CutTableGrid.Pixels pixels, int width, int height) {
  if (width < 180 || height < 100) return null;
  int[] colors = new int[width];
  List<Rule> columns = verticalRules(pixels, colors, height);
  TableOcrRegion best = null;
  int bestRows = 2;
  for (int i = 3; i < columns.size(); i++) {
   Rule left = columns.get(i - 3), first = columns.get(i - 2);
   Rule second = columns.get(i - 1), right = columns.get(i);
   int names = first.x - left.x, lengths = second.x - first.x, counts = right.x - second.x;
   int span = right.x - left.x;
   if (span < Math.max(150, width / 4) || names < lengths || lengths < span / 9
     || counts < span / 14 || counts > lengths * 1.35f) continue;
   int top = Math.max(first.top, second.top);
   int bottom = Math.min(first.bottom, second.bottom);
   if (bottom - top < Math.max(75, height / 4)
     || Math.abs(first.top - second.top) > (bottom - top) / 10) continue;
   int rows = countAlignedRules(pixels, colors, left.x, first.x, second.x, right.x, top, bottom);
   if (rows <= bestRows) continue;
   bestRows = rows;
   int nameStart = nameStart(pixels, colors, left.x + 5, first.x - 5, top, bottom);
   best = new TableOcrRegion(new CutSheet.Bounds(left.x, top, right.x + 1, Math.min(height, bottom + 2)),
    first.x, second.x, top, nameStart);
  }
  return best;
 }

 private static int countAlignedRules(CutTableGrid.Pixels pixels, int[] colors, int left, int first,
                                      int second, int right, int top, int bottom) {
  int rules = 0, leftHits = 0, rightHits = 0;
  int previous = top;
  int gap = Math.max(10, (bottom - top) / 40);
  for (int y = top; y <= bottom; y++) {
   pixels.readRow(y, colors);
   if (darkNear(colors, left)) leftHits++;
   if (darkNear(colors, right)) rightHits++;
   if (y - previous >= gap && hasRule(colors, left, first) && hasRule(colors, first, second)
     && hasRule(colors, second, right)) {
    rules++;
    previous = y;
   }
  }
  int rows = bottom - top + 1;
  return leftHits * 4 >= rows * 3 && rightHits * 4 >= rows * 3 ? rules : 0;
 }

 private static boolean hasRule(int[] colors, int left, int right) {
  int ink = 0;
  for (int x = left + 3; x < right - 3; x += 2) if (brightness(colors[x]) < 2120) ink++;
  return ink * 2 >= (right - left - 6) * .82f;
 }

 private static boolean darkNear(int[] colors, int x) {
  for (int i = Math.max(0, x - 2); i <= Math.min(colors.length - 1, x + 2); i++) {
   if (dark(colors[i])) return true;
  }
  return false;
 }

 void prepareRow(int y, int[] colors) {
  boolean heading = y < dataTop;
  boolean rule = y < box.top + 3 || Math.abs(y - dataTop) <= 2 || y >= box.bottom - 4
   || (!heading && (darkShare(colors, firstNumber + 3, secondNumber - 3) > .55f
    || (dataTop == box.top && hasRule(colors, firstNumber, secondNumber))));
  for (int x = box.left; x < box.right; x++) {
   boolean divider = x < box.left + 4 || x >= box.right - 4
    || Math.abs(x - firstNumber) <= 2 || Math.abs(x - secondNumber) <= 2;
   boolean drawing = !heading && x < nameStart;
   int level = brightness(colors[x]);
   boolean ink = heading ? level > 1950 : level < 1690;
   colors[x] = !rule && !divider && !drawing && ink ? 0xff000000 : 0xffffffff;
  }
 }

 private static int nameStart(CutTableGrid.Pixels pixels, int[] colors, int left, int right, int top, int bottom) {
  int[] hits = new int[right - left];
  int samples = 0;
  for (int y = top + 3; y < bottom - 3; y += 2) {
   pixels.readRow(y, colors);
   if (darkShare(colors, left, right) > .55f) continue;
   samples++;
   for (int x = left; x < right; x++) if (brightness(colors[x]) < 1700) hits[x - left]++;
  }
  int bestWidth = 0;
  int boundary = left;
  for (int i = hits.length / 7; i < hits.length / 2; i++) {
   if (hits[i] * 25 > samples) continue;
   int start = i;
   while (i + 1 < hits.length / 2 && hits[i + 1] * 25 <= samples) i++;
   int gap = i - start + 1;
   if (gap > bestWidth && hasInk(hits, start - 12, start, samples) && hasInk(hits, i + 1, i + 13, samples)) {
    boundary = left + (i + start) / 2;
    bestWidth = gap;
   }
  }
  return bestWidth >= 5 ? boundary : left;
 }

 private static boolean hasInk(int[] hits, int from, int to, int samples) {
  for (int x = Math.max(0, from); x < Math.min(hits.length, to); x++) {
   if (hits[x] * 8 > samples) return true;
  }
  return false;
 }

 private static List<Rule> verticalRules(CutTableGrid.Pixels pixels, int[] colors, int height) {
  int width = colors.length;
  int[] starts = new int[width];
  int[] lengths = new int[width];
  int[] last = new int[width];
  Rule[] longest = new Rule[width];
  for (int y = 0; y < height; y += 2) {
   pixels.readRow(y, colors);
   for (int x = 1; x < width - 1; x++) {
    if (!dark(colors[x]) || (dark(colors[Math.max(0, x - 5)]) && dark(colors[Math.min(width - 1, x + 5)]))) continue;
    if (lengths[x] == 0 || y - last[x] > 6) {
     starts[x] = y;
     lengths[x] = 0;
    }
    last[x] = y;
    lengths[x]++;
    int span = y - starts[x];
    if (span >= height / 4 && lengths[x] * 5 >= span * 2
      && (longest[x] == null || span > longest[x].bottom - longest[x].top)) {
     longest[x] = new Rule(x, starts[x], y);
    }
   }
  }
  List<Rule> rules = new ArrayList<>();
  for (int x = 0; x < width; x++) {
   if (longest[x] == null) continue;
   Rule best = longest[x];
   while (x + 1 < width && longest[x + 1] != null) {
    Rule next = longest[++x];
    if (next.bottom - next.top > best.bottom - best.top) best = next;
   }
   rules.add(best);
  }
  return rules;
 }

 private static float darkShare(int[] colors, int left, int right) {
  int dark = 0;
  for (int x = left; x < right; x++) if (dark(colors[x])) dark++;
  return (float) dark / Math.max(1, right - left);
 }

 private static int darkEdge(int[] colors, int start, int direction) {
  int last = start;
  for (int x = start; x >= 0 && x < colors.length; x += direction) {
   if (dark(colors[x])) last = x;
   if (Math.abs(x - last) > 7) break;
  }
  return last;
 }

 private static int median(List<Integer> values) {
  Collections.sort(values);
  return values.get(values.size() / 2);
 }

 private static boolean dark(int color) { return brightness(color) < 1850; }

 private static int brightness(int color) {
  return 3 * ((color >> 16) & 255) + 6 * ((color >> 8) & 255) + (color & 255);
 }

 private static final class Rule {
  final int x, top, bottom;

  Rule(int x, int top, int bottom) {
   this.x = x;
   this.top = top;
   this.bottom = bottom;
  }
 }
}
