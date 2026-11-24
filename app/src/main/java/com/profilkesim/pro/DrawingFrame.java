package com.profilkesim.pro;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Finds the outline of a frame in an A4 sketch from its four long strokes. */
final class DrawingFrame {
 private DrawingFrame() {}

 static CutSheet.Bounds find(CutTableGrid.Pixels pixels, int width, int height) {
  if (width < 100 || height < 100) return null;
  boolean[][] ink = new boolean[height][width];
  int[] row = new int[width];
  for (int y = 0; y < height; y++) {
   pixels.readRow(y, row);
   for (int x = 0; x < width; x++) {
    int color = row[x];
    ink[y][x] = 3 * ((color >> 16) & 255) + 6 * ((color >> 8) & 255) + (color & 255) < 1590;
   }
  }
  List<Stroke> horizontal = strokes(ink, width, height, false);
  List<Stroke> vertical = strokes(ink, height, width, true);
  CutSheet.Bounds best = null;
  int bestArea = 0;
  for (Stroke a : horizontal) {
   for (Stroke b : horizontal) {
    if (b.axis - a.axis < height * .13f) continue;
    int left = (a.start + b.start) / 2, right = (a.end + b.end) / 2, span = right - left;
    int tolerance = Math.max(7, span / 16);
    if (Math.abs(a.start - b.start) > tolerance || Math.abs(a.end - b.end) > tolerance) continue;
    Stroke leftEdge = side(vertical, left, a.axis, b.axis, tolerance);
    Stroke rightEdge = side(vertical, right, a.axis, b.axis, tolerance);
    if (leftEdge == null || rightEdge == null) continue;
    int area = (rightEdge.axis - leftEdge.axis) * (b.axis - a.axis);
    if (area <= bestArea) continue;
    bestArea = area;
    best = new CutSheet.Bounds(leftEdge.axis, a.axis, rightEdge.axis, b.axis);
   }
  }
  return best;
 }

 private static List<Stroke> strokes(boolean[][] ink, int length, int limit, boolean vertical) {
  List<Stroke> result = new ArrayList<>();
  int gap = Math.max(4, length / 105);
  int radius = Math.max(2, limit / 240);
  for (int fixed = radius + 2; fixed < limit - radius - 2; fixed++) {
   int start = -1, last = -1, count = 0;
   Stroke best = null;
   for (int at = 0; at < length; at++) {
    if (!nearInk(ink, fixed, at, vertical, radius)) continue;
    if (at - last > gap || start < 0) {
     start = at;
     count = 0;
    }
    last = at;
    count++;
    if (at - start >= length * .16f && count >= (at - start) * .78f
      && (best == null || at - start > best.length())) best = new Stroke(fixed, start, at);
   }
   if (best == null) continue;
   Stroke previous = result.isEmpty() ? null : result.get(result.size() - 1);
   if (previous != null && fixed - previous.axis <= radius * 2 + 2
     && Math.abs(best.start - previous.start) <= gap * 2 && Math.abs(best.end - previous.end) <= gap * 2) {
    if (best.length() >= previous.length()) result.set(result.size() - 1, best);
   } else result.add(best);
  }
  Collections.sort(result, (a, b) -> Integer.compare(b.length(), a.length()));
  return result.subList(0, Math.min(48, result.size()));
 }

 private static boolean nearInk(boolean[][] ink, int at, int along, boolean vertical, int radius) {
  for (int shift = -radius; shift <= radius; shift++) {
   if (vertical ? ink[along][at + shift] : ink[at + shift][along]) return true;
  }
  return false;
 }

 private static Stroke side(List<Stroke> strokes, int x, int top, int bottom, int tolerance) {
  Stroke closest = null;
  int difference = Integer.MAX_VALUE;
  int span = bottom - top;
  for (Stroke line : strokes) {
   int dx = Math.abs(line.axis - x), dy = Math.abs(line.start - top) + Math.abs(line.end - bottom);
   if (dx > tolerance || dy > Math.max(tolerance * 3, span / 6) || dx * 2 + dy >= difference) continue;
   closest = line;
   difference = dx * 2 + dy;
  }
  return closest;
 }

 private static final class Stroke {
  final int axis, start, end;
  Stroke(int axis, int start, int end) {
   this.axis = axis;
   this.start = start;
   this.end = end;
  }
  int length() { return end - start; }
 }
}
