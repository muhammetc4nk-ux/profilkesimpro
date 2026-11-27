package com.profilkesim.pro;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.TreeSet;

final class TemaOpeningResize {
 interface Resolver { TemaLayout layout(Drawing drawing); }
 private interface Coordinate { int at(int old); }

 static final class Drawing {
  final int scale;
  final TreeSet<Integer> columns, rows;
  final List<int[]> local;
  final Map<String, Integer> profiles;
  int width, height;

  Drawing(int width, int height, int scale, Set<Integer> columns, Set<Integer> rows,
          List<int[]> local, Map<String, Integer> profiles) {
   this.width = width;
   this.height = height;
   this.scale = scale;
   this.columns = new TreeSet<>(columns);
   this.rows = new TreeSet<>(rows);
   this.local = new ArrayList<>(local.size());
   for (int[] line : local) this.local.add(line.clone());
   this.profiles = new LinkedHashMap<>(profiles);
  }

  Drawing copy() { return new Drawing(width, height, scale, columns, rows, local, profiles); }

  List<int[]> cells() { return TemaRecordPlacement.bays(columns, rows, local, scale); }

  List<int[]> records(int defaultProfile) {
   List<int[]> all = new ArrayList<>(local);
   for (int x : columns) all.add(new int[]{x, 0, x, scale, profiles.getOrDefault("v" + x, defaultProfile)});
   for (int y : rows) all.add(new int[]{0, y, scale, y, profiles.getOrDefault("h" + y, defaultProfile)});
   return all;
  }
 }

 private TemaOpeningResize() {}

 static Drawing resize(Drawing drawing, int index, int width, int height, int[] deductions, Resolver resolver) {
  if (width <= deductions[index] || height <= deductions[index]) {
   throw new IllegalArgumentException("Seçilen cam ve profil payları için açıklık yetersiz.");
  }
  Drawing wider = fitAxis(drawing, index, 0, width, deductions, resolver);
  return fitAxis(wider, index, 1, height, deductions, resolver);
 }

 private static Drawing fitAxis(Drawing drawing, int index, int axis, int desired, int[] deductions, Resolver resolver) {
  TemaLayout layout = resolver.layout(drawing);
  int delta = desired - size(layout.bays.get(index), axis);
  if (delta == 0) return drawing;

  List<int[]> cells = drawing.cells();
  int[] cell = cells.get(index);
  int inner = axis == 0 ? layout.innerWidth : layout.innerHeight;
  for (int edge = axis + 2; edge >= axis; edge -= 2) {
   int old = cell[edge];
   if (old == 0 || old == drawing.scale) continue;
   int position = physical(old, inner, drawing.scale) + (edge == axis ? -delta : delta);
   if (position <= 0 || position >= inner) continue;
   int next = (int)Math.round((double)position * drawing.scale / inner);
   Drawing attempt = drawing.copy();
   try {
    moveBoundary(attempt, axis, old, next, cell);
    if (fits(attempt, index, axis, desired, deductions, resolver)) return attempt;
   } catch (IllegalArgumentException ignored) {
    // Try the other side or move the outside frame.
   }
  }

  Drawing attempt = drawing.copy();
  int start = physical(cell[axis], inner, drawing.scale);
  int end = physical(cell[axis + 2], inner, drawing.scale);
  int newInner = inner + delta;
  if (newInner > 0 && end > start && end - start + delta > 0) {
   try {
    remapAxis(attempt, axis, at -> {
     if (at == 0 || at == drawing.scale) return at;
     int distance = physical(at, inner, drawing.scale);
     double weight = Math.max(0d, Math.min(1d, (distance - start) / (double)(end - start)));
     return (int)Math.round((distance + delta * weight) * drawing.scale / newInner);
    });
    if (axis == 0) attempt.width += delta;
    else attempt.height += delta;
    if (fits(attempt, index, axis, desired, deductions, resolver)) return attempt;
   } catch (IllegalArgumentException ignored) {
    // Overlapping records and undersized neighbouring bays are invalid.
   }
  }
  throw new IllegalArgumentException((axis == 0 ? "En" : "Boy")
   + " bu değere ayarlanamıyor. İstenen ölçüde komşu bölme veya profil bağlantısı sığmıyor.");
 }

 private static boolean fits(Drawing attempt, int index, int axis, int desired, int[] deductions, Resolver resolver) {
  if (attempt.width > ShopDimensions.MAX_TENTHS || attempt.height > ShopDimensions.MAX_TENTHS
   || !TemaRecordPlacement.connected(attempt.records(0), attempt.scale)) return false;
  TemaLayout layout = resolver.layout(attempt);
  if (layout.bays.size() != deductions.length || size(layout.bays.get(index), axis) != desired) return false;
  for (int i = 0; i < deductions.length; i++) if (size(layout.bays.get(i), axis) <= deductions[i]) return false;
  return true;
 }

 private static void moveBoundary(Drawing drawing, int axis, int old, int next, int[] cell) {
  if (next <= 0 || next >= drawing.scale) throw new IllegalArgumentException("Kayıt kasa sınırını aşıyor.");
  TreeSet<Integer> lines = axis == 0 ? drawing.columns : drawing.rows;
  if (lines.contains(old)) {
   Integer previous = lines.lower(old), after = lines.higher(old);
   if ((previous != null && next <= previous) || (after != null && next >= after)) {
    throw new IllegalArgumentException("Kayıt komşu bölmeyi aşıyor.");
   }
   lines.remove(old);
   lines.add(next);
   moveProfile(drawing.profiles, axis, old, next);
   TemaRecordPlacement.moveEndpoints(drawing.local, null, axis, old, next, 0, drawing.scale);
   return;
  }

  int along = 1 - axis;
  for (int[] line : drawing.local) {
   if (line[axis] != old || line[axis + 2] != old || line[along] > cell[along] || line[along + 2] < cell[along + 2]) continue;
   if ((next <= cell[axis] && old == cell[axis + 2]) || (next >= cell[axis + 2] && old == cell[axis])) {
    throw new IllegalArgumentException("Kayıt karşı kenarı aşıyor.");
   }
   TemaRecordPlacement.moveEndpoints(drawing.local, line, axis, old, next, line[along], line[along + 2]);
   return;
  }
  throw new IllegalArgumentException("Bölme sınırı bulunamadı.");
 }

 private static void remapAxis(Drawing drawing, int axis, Coordinate coordinate) {
  TreeSet<Integer> positions = axis == 0 ? drawing.columns : drawing.rows;
  TreeSet<Integer> mapped = new TreeSet<>();
  Map<String, Integer> profiles = new LinkedHashMap<>(drawing.profiles);
  String prefix = axis == 0 ? "v" : "h";
  drawing.profiles.keySet().removeAll(gridKeys(prefix, positions));
  for (int at : positions) {
   int next = coordinate.at(at);
   if (next <= 0 || next >= drawing.scale || !mapped.add(next)) {
    throw new IllegalArgumentException("Kayıtlar çakışıyor.");
   }
   Integer profile = profiles.get(prefix + at);
   if (profile != null) drawing.profiles.put(prefix + next, profile);
  }
  positions.clear();
  positions.addAll(mapped);
  for (int[] line : drawing.local) {
   line[axis] = coordinate.at(line[axis]);
   line[axis + 2] = coordinate.at(line[axis + 2]);
  }
 }

 private static List<String> gridKeys(String prefix, Set<Integer> positions) {
  List<String> keys = new ArrayList<>();
  for (int at : positions) keys.add(prefix + at);
  return keys;
 }

 private static void moveProfile(Map<String, Integer> profiles, int axis, int old, int next) {
  String prefix = axis == 0 ? "v" : "h";
  Integer value = profiles.remove(prefix + old);
  if (value != null) profiles.put(prefix + next, value);
 }

 static int[] outside(TemaLayout.Frame frame, List<int[]> cells, Set<Integer> sashes, int scale, int width, int height) {
  if (width <= 0 || height <= 0) throw new IllegalArgumentException("Kasa iç açıklığı sıfırdan büyük olmalı.");
  boolean sill = false;
  for (int i = 0; i < cells.size(); i++) {
   if (cells.get(i)[TemaLayout.BOTTOM] == scale && !sashes.contains(i + 1)) {
    sill = true;
    break;
   }
  }
  int outsideWidth = width + frame.left.face + frame.right.face;
  int outsideHeight = height + frame.top.face + (sill ? frame.bottom.face : 0);
  if (outsideWidth > ShopDimensions.MAX_TENTHS || outsideHeight > ShopDimensions.MAX_TENTHS) {
   throw new IllegalArgumentException("Kasa dış ölçüsü 100.000 mm’yi aşamaz.");
  }
  return new int[]{outsideWidth, outsideHeight};
 }

 private static int physical(int at, int span, int scale) { return (int)Math.round((double)at * span / scale); }
 private static int size(TemaLayout.Opening bay, int axis) { return axis == 0 ? bay.width() : bay.height(); }
}
