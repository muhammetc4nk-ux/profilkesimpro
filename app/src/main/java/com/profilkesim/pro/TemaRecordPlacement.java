package com.profilkesim.pro;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;

final class TemaRecordPlacement {
 static final class Start {
  final boolean vertical;
  final int axis, support;

  Start(boolean vertical, int axis, int support) {
   this.vertical = vertical;
   this.axis = axis;
   this.support = support;
  }
 }

 private TemaRecordPlacement() {}

 static ArrayList<int[]> bays(SortedSet<Integer> xs, SortedSet<Integer> ys, List<int[]> segments, int scale) {
  List<Integer> columns = new ArrayList<>(xs), rows = new ArrayList<>(ys);
  columns.add(0, 0);
  columns.add(scale);
  rows.add(0, 0);
  rows.add(scale);
  ArrayList<int[]> cells = new ArrayList<>();
  for (int y = 0; y + 1 < rows.size(); y++) {
   for (int x = 0; x + 1 < columns.size(); x++) {
    cells.add(new int[]{columns.get(x), rows.get(y), columns.get(x + 1), rows.get(y + 1)});
   }
  }
  for (int[] segment : segments) {
   boolean vertical = segment[0] == segment[2];
   for (int i = 0; i < cells.size(); i++) {
    int[] cell = cells.get(i);
    if (vertical && cell[0] < segment[0] && segment[0] < cell[2] && segment[1] <= cell[1] && segment[3] >= cell[3]) {
     cells.set(i, new int[]{cell[0], cell[1], segment[0], cell[3]});
     cells.add(new int[]{segment[0], cell[1], cell[2], cell[3]});
    } else if (!vertical && cell[1] < segment[1] && segment[1] < cell[3] && segment[0] <= cell[0] && segment[2] >= cell[2]) {
     cells.set(i, new int[]{cell[0], cell[1], cell[2], segment[1]});
     cells.add(new int[]{cell[0], segment[1], cell[2], cell[3]});
    }
   }
  }
  return cells;
 }

 static Start start(List<int[]> cells, boolean vertical, int x, int y, int tolerance) {
  int cross = vertical ? 0 : 1, along = 1 - cross;
  int axis = vertical ? x : y, touch = vertical ? y : x;
  int support = -1, best = tolerance + 1;
  for (int[] cell : cells) {
   if (axis <= cell[cross] || axis >= cell[cross + 2]) continue;
   for (int edge = along; edge <= along + 2; edge += 2) {
    int distance = Math.abs(cell[edge] - touch);
    if (distance < best) {
     support = cell[edge];
     best = distance;
    }
   }
  }
  return support < 0 ? null : new Start(vertical, axis, support);
 }

 static List<Integer> oppositeSupports(List<int[]> cells, Start start) {
  List<Integer> supports = new ArrayList<>(2);
  int cross = start.vertical ? 0 : 1, along = 1 - cross;
  for (int[] cell : cells) {
   if (start.axis <= cell[cross] || start.axis >= cell[cross + 2]) continue;
   if (start.support == cell[along]) supports.add(cell[along + 2]);
   else if (start.support == cell[along + 2]) supports.add(cell[along]);
  }
  return supports;
 }

 static int[] finish(List<int[]> cells, Start start, int x, int y, int across, int along, int profile) {
  int axis = start.vertical ? x : y, touch = start.vertical ? y : x;
  if (Math.abs(axis - start.axis) > across) return null;
  int support = -1, best = along + 1;
  for (int candidate : oppositeSupports(cells, start)) {
   int distance = Math.abs(candidate - touch);
   if (distance < best) {
    support = candidate;
    best = distance;
   }
  }
  if (support < 0) return null;
  int first = Math.min(start.support, support), last = Math.max(start.support, support);
  return start.vertical ? new int[]{start.axis, first, start.axis, last, profile}
   : new int[]{first, start.axis, last, start.axis, profile};
 }

 static int[] nearest(List<int[]> records, int x, int y, int toleranceX, int toleranceY) {
  int[] nearest = null;
  float best = 1.01f;
  for (int[] record : records) {
   boolean vertical = record[0] == record[2];
   float distance;
   if (vertical) {
    if (y < record[1] || y > record[3]) continue;
    distance = Math.abs(x - record[0]) / (float)toleranceX;
   } else {
    if (x < record[0] || x > record[2]) continue;
    distance = Math.abs(y - record[1]) / (float)toleranceY;
   }
   if (distance < best) {
    best = distance;
    nearest = record;
   }
  }
  return nearest;
 }

 static void moveEndpoints(List<int[]> lines, int[] selected, int cross, int old, int next, int start, int end) {
  int along = 1 - cross;
  for (int[] line : lines) {
   if (line == selected) {
    line[cross] = line[cross + 2] = next;
   } else if (line[along] == line[along + 2] && line[along] >= start && line[along] <= end) {
    if (line[cross] == old) line[cross] = next;
    if (line[cross + 2] == old) line[cross + 2] = next;
   }
   if (line[0] > line[2] || line[1] > line[3] || (line[0] == line[2] && line[1] == line[3])) {
    throw new IllegalArgumentException("Kayıt çakışması.");
   }
  }
 }

 static boolean connected(List<int[]> records, int scale) {
  for (int[] record : records) {
   boolean vertical = record[0] == record[2];
   int axis = record[vertical ? 0 : 1];
   int along = vertical ? 1 : 0;
   if (!isSupported(records, vertical, axis, record[along], scale)
    || !isSupported(records, vertical, axis, record[along + 2], scale)) return false;
  }
  return true;
 }

 private static boolean isSupported(List<int[]> records, boolean vertical, int axis, int end, int scale) {
  if (end == 0 || end == scale) return true;
  int cross = vertical ? 0 : 1, along = 1 - cross;
  for (int[] support : records) {
   if (support[along] == end && support[along + 2] == end && support[cross] <= axis && support[cross + 2] >= axis) return true;
  }
  return false;
 }
}
