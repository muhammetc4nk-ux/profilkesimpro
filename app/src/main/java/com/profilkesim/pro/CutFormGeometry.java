package com.profilkesim.pro;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Finds the long rulings around a photographed cutting form, even when they converge. */
final class CutFormGeometry {
 final float[] corners;
 final List<Rule> columns, rows;
 final int columnSampleY, rowSampleX;

 private CutFormGeometry(float[] corners, List<Rule> columns, List<Rule> rows, int columnSampleY, int rowSampleX) {
  this.corners = corners;
  this.columns = columns;
  this.rows = rows;
  this.columnSampleY = columnSampleY;
  this.rowSampleX = rowSampleX;
 }

 static CutFormGeometry find(int[][] grey, CutSheet.Bounds caption) {
  int h = grey.length, w = grey[0].length;
  int top = caption.bottom + Math.max(6, caption.height() * 2);
  int end = Math.min(h - 2, top + (int) (w * 1.12f));
  int dark = 0;
  for (int y = top; y < end; y++) {
   int light = 0;
   for (int x = w / 4; x < w * 3 / 4; x += 4) if (grey[y][x] >= 154) light++;
   dark = light * 12 >= w ? 0 : dark + 1;
   if (dark >= 10) {
    end = y - dark + 1;
    break;
   }
  }
  if (end - top < 88) return null;
  int middleY = (top + end) / 2;
  List<Rule> columns = project(grey, true, top, end, 4, w - 4, 45, .43f);
  if (columns.size() < 7) return null;
  Rule left = columns.get(0), right = columns.get(columns.size() - 1);
  if (right.position - left.position < w * .35f || caption.x() <= left.at(top, middleY)
    || caption.x() >= right.at(top, middleY)) return null;
  int x1 = Math.round(Math.max(left.at(top, middleY), left.at(end, middleY))) + 3;
  int x2 = Math.round(Math.min(right.at(top, middleY), right.at(end, middleY))) - 3;
  if (x1 < 4 || x2 >= w - 4 || x2 - x1 < 100) return null;
  int middleX = (x1 + x2) / 2;
  List<Rule> rows = project(grey, false, x1, x2, caption.y() + 2, end, 21, .58f);
  if (rows.size() < 5) return null;
  float[] horizontal = new float[rows.size()];
  for (int i = 0; i < rows.size(); i++) horizontal[i] = rows.get(i).slope;
  Arrays.sort(horizontal);
  float angle = horizontal[horizontal.length / 2];
  float startY = Math.max(2, caption.top - 2 * caption.height());
  float endY = Math.min(h - 2, rows.get(rows.size() - 1).position
   + Math.max(24, (rows.get(rows.size() - 1).position - rows.get(rows.size() - 2).position) * 1.25f));
  float[] corners = new float[8];
  intersect(corners, 0, left, startY, angle, middleY, middleX);
  intersect(corners, 2, right, startY, angle, middleY, middleX);
  intersect(corners, 4, right, endY, angle, middleY, middleX);
  intersect(corners, 6, left, endY, angle, middleY, middleX);
  for (int i = 0; i < corners.length; i++) {
   if (corners[i] < 0 || corners[i] >= (i % 2 == 0 ? w : h)) return null;
  }
  return new CutFormGeometry(corners, columns, rows, middleY, middleX);
 }

 private static void intersect(float[] corners, int i, Rule vertical, float level, float angle, int middleY, int middleX) {
  float x = (vertical.position + vertical.slope * (level - middleY - angle * middleX)) / (1 - vertical.slope * angle);
  corners[i] = x;
  corners[i + 1] = level + angle * (x - middleX);
 }

 private static List<Rule> project(int[][] grey, boolean vertical, int start, int end, int low, int high,
                                    int steps, float support) {
  int span = vertical ? grey[0].length : grey.length;
  boolean[][] ink = new boolean[end - start][span];
  int flank = 4;
  for (int t = start; t < end; t += 3) {
   for (int p = flank; p < span - flank; p++) {
    int c = vertical ? grey[t][p] : grey[p][t];
    int a = vertical ? grey[t][p - flank] : grey[p - flank][t];
    int b = vertical ? grey[t][p + flank] : grey[p + flank][t];
    ink[t - start][p] = c + 7 <= Math.min(a, b);
   }
  }
  int[] votes = new int[span];
  float[] slopes = new float[span];
  int[] offsets = new int[end - start];
  int middle = (start + end) / 2;
  for (int s = -steps; s <= steps; s++) {
   float slope = s * .004f;
   for (int t = start; t < end; t += 3) offsets[t - start] = Math.round((t - middle) * slope);
   for (int p = low; p < high; p++) {
    int score = 0;
    for (int t = 0; t < offsets.length; t += 3) {
     int x = p + offsets[t];
     if (x >= 0 && x < span && ink[t][x]) score++;
    }
    if (score > votes[p]) {
     votes[p] = score;
     slopes[p] = slope;
    }
   }
  }
  List<Rule> result = new ArrayList<>();
  int minimum = Math.max(10, Math.round((end - start) / 3f * support));
  while (true) {
   int best = low;
   for (int p = low + 1; p < high; p++) if (votes[p] > votes[best]) best = p;
   if (votes[best] < minimum) break;
   result.add(new Rule(best, slopes[best]));
   Arrays.fill(votes, Math.max(low, best - 6), Math.min(high, best + 7), 0);
  }
  Collections.sort(result, (a, b) -> Integer.compare(a.position, b.position));
  return result;
 }

 static final class Rule {
  final int position;
  final float slope;
  Rule(int position, float slope) {
   this.position = position;
   this.slope = slope;
  }
  float at(int offset, int origin) { return position + slope * (offset - origin); }
 }
}
