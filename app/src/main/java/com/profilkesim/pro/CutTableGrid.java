package com.profilkesim.pro;

import java.util.ArrayList;
import java.util.List;

/** Locates printed table borders around OCR-confirmed cutting column headings. */
final class CutTableGrid {
 interface Pixels {
  void readRow(int y, int[] colors);
 }

 final int left, right, bottom;
 private final List<Integer> dividers;

 CutTableGrid(int left, int right, int bottom, List<Integer> dividers) {
  this.left = left;
  this.right = right;
  this.bottom = bottom;
  this.dividers = dividers;
 }

 static CutTableGrid find(Pixels pixels, int width, int height, CutSheet.Bounds names,
                          CutSheet.Bounds lengths, CutSheet.Bounds quantities, int headingBottom, int textHeight) {
  if (pixels == null || width <= 0 || headingBottom >= height) return null;
  int start = headingBottom + Math.max(2, textHeight / 3);
  int end = Math.min(height, start + Math.max(textHeight * 5, height / 4));
  if (end - start < textHeight) return null;
  int[] colors = new int[width];
  int[] hits = new int[width];
  int sampled = 0;
  for (int y = start; y < end; y += 2) {
   pixels.readRow(y, colors);
   for (int x = 0; x < width; x++) {
    if (dark(colors[x])) hits[x]++;
   }
   sampled++;
  }
  List<Integer> lines = new ArrayList<>();
  for (int x = 0; x < width; x++) {
   if (hits[x] * 4 < sampled * 3) continue;
   int first = x;
   while (x + 1 < width && hits[x + 1] * 4 >= sampled * 3) x++;
   if (x - first <= Math.max(3, textHeight / 5)) lines.add((first + x) / 2);
  }
  CutSheet.Bounds firstNumber = lengths.x() < quantities.x() ? lengths : quantities;
  CutSheet.Bounds secondNumber = firstNumber == lengths ? quantities : lengths;
  int nameDivider = between(lines, names.x(), firstNumber.x());
  int numberDivider = between(lines, firstNumber.x(), secondNumber.x());
  if (nameDivider < 0 || numberDivider < 0) return null;

  int left = -1, right = -1;
  for (int x : lines) {
   if (x < names.left) left = x;
   if (x > secondNumber.right && right < 0) right = x;
  }
  if (left < 0 || right < 0) return null;

  int lastBorder = headingBottom;
  for (int y = headingBottom; y < height; y++) {
   pixels.readRow(y, colors);
   if (darkNear(colors, nameDivider) && darkNear(colors, numberDivider)) lastBorder = y;
   if (y - lastBorder > Math.max(12, textHeight)) break;
  }
  if (lastBorder - headingBottom < textHeight) return null;
  return new CutTableGrid(left, right, lastBorder + 1, lines);
 }

 private static int between(List<Integer> lines, int left, int right) {
  int middle = (left + right) / 2;
  int best = -1;
  for (int x : lines) {
   if (x > left && x < right && (best < 0 || Math.abs(x - middle) < Math.abs(best - middle))) best = x;
  }
  return best;
 }

 int cellLeft(int x) {
  int l = left;
  for (int divider : dividers) {
   if (divider >= x) break;
   l = Math.max(l, divider);
  }
  return l;
 }

 int cellRight(int x) {
  for (int divider : dividers) {
   if (divider > x) return Math.min(right, divider);
  }
  return right;
 }

 private static boolean darkNear(int[] colors, int x) {
  for (int i = Math.max(0, x - 2); i < Math.min(colors.length, x + 3); i++) {
   if (dark(colors[i])) return true;
  }
  return false;
 }

 private static boolean dark(int color) {
  int red = (color >> 16) & 255;
  int green = (color >> 8) & 255;
  int blue = color & 255;
  return 3 * red + 6 * green + blue < 1850;
 }
}
