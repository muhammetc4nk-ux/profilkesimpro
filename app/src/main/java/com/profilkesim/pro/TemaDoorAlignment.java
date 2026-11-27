package com.profilkesim.pro;

import java.util.ArrayList;
import java.util.List;

final class TemaDoorAlignment {
 static final class Result {
  final TemaOpeningResize.Drawing drawing;
  final int moved;

  Result(TemaOpeningResize.Drawing drawing, int moved) {
   this.drawing = drawing;
   this.moved = moved;
  }
 }

 private TemaDoorAlignment() {}

 static List<Integer> references(TemaOpeningResize.Drawing drawing, TemaLayout layout) {
  List<int[]> cells = drawing.cells();
  List<Integer> result = new ArrayList<>();
  for (int i = 0; i < cells.size(); i++) {
   if (layout.bays.get(i).door && lintel(cells.get(i), layout.records) != null) result.add(i);
  }
  return result;
 }

 static Result align(TemaOpeningResize.Drawing original, int doorIndex, int[] requiredOpenings,
                     TemaOpeningResize.Resolver resolver) {
  TemaLayout before = resolver.layout(original);
  List<int[]> cells = original.cells();
  int[] doorCell = cells.get(doorIndex);
  TemaLayout.Opening door = before.bays.get(doorIndex);
  if (!door.door || lintel(doorCell, before.records) == null) {
   throw new IllegalArgumentException("Önce kapının üstüne yatay orta kayıt ekleyin.");
  }
  List<TemaLayout.Record> doorLintels = new ArrayList<>();
  for (int index : references(original, before)) doorLintels.add(lintel(cells.get(index), before.records));

  TemaOpeningResize.Drawing result = original.copy();
  int moved = 0;
  for (int i = 0; i < original.local.size(); i++) {
   TemaLayout.Record record = before.records.get(i);
   if (!movable(record, doorCell, doorLintels)) continue;
   int lowerFace = record.section.face - record.section.face / 2;
   int current = before.y(record.axis) + lowerFace;
   if (current == door.top || !isNearestInSpan(before, record, i, door.top, original.local.size(), doorCell, doorLintels)) continue;
   int target = (int)Math.round((door.top - lowerFace - before.frame.top.face) * (double)original.scale / before.innerHeight);
   if (target <= 0 || target >= original.scale || crosses(before, record, target)) {
    throw new IllegalArgumentException("Kayıt, kapı hizasında komşu profile denk geliyor.");
   }
   int[] line = result.local.get(i);
   TemaRecordPlacement.moveEndpoints(result.local, line, 1, line[1], target, line[0], line[2]);
   moved++;
  }
  if (moved == 0) return new Result(original, 0);
  if (!TemaRecordPlacement.connected(result.records(0), result.scale)) {
   throw new IllegalArgumentException("Kapı hizasında bir kayıt bağlantısı açıkta kalıyor.");
  }
  TemaLayout after = resolver.layout(result);
  if (before.bays.size() != after.bays.size()) {
   throw new IllegalArgumentException("Kapı hizasında bölmeler çakışıyor.");
  }
  for (int i = 0; i < before.bays.size(); i++) {
   TemaLayout.Opening prior = before.bays.get(i), bay = after.bays.get(i);
   if (prior.door && (!bay.door || prior.left != bay.left || prior.right != bay.right
    || prior.top != bay.top || prior.bottom != bay.bottom)) {
    throw new IllegalArgumentException("Kapı ölçüsü sabit kalacak şekilde hizalama yapılamıyor.");
   }
   if (bay.width() <= requiredOpenings[i] || bay.height() <= requiredOpenings[i]) {
    throw new IllegalArgumentException("Hizalamadan sonra bir bölmenin cam açıklığı yetersiz kalıyor.");
   }
  }
  return new Result(result, moved);
 }

 private static TemaLayout.Record lintel(int[] cell, List<TemaLayout.Record> records) {
  if (cell[TemaLayout.TOP] == 0) return null;
  for (TemaLayout.Record record : records) {
   if (!record.vertical && record.axis == cell[TemaLayout.TOP]
    && record.start <= cell[TemaLayout.LEFT] && record.end >= cell[TemaLayout.RIGHT]) return record;
  }
  return null;
 }

 private static boolean overlaps(TemaLayout.Record record, int start, int end) {
  return record.start < end && start < record.end;
 }

 private static boolean movable(TemaLayout.Record record, int[] door, List<TemaLayout.Record> lintels) {
  return !record.vertical && !lintels.contains(record) && !overlaps(record, door[TemaLayout.LEFT], door[TemaLayout.RIGHT]);
 }

 private static int distance(TemaLayout layout, TemaLayout.Record record, int target) {
  return Math.abs(layout.y(record.axis) + record.section.face - record.section.face / 2 - target);
 }

 private static boolean isNearestInSpan(TemaLayout layout, TemaLayout.Record record, int index, int target,
                                         int localCount, int[] door, List<TemaLayout.Record> lintels) {
  int distance = distance(layout, record, target);
  for (int i = 0; i < layout.records.size(); i++) {
   TemaLayout.Record other = layout.records.get(i);
   if (i == index || other.vertical || !overlaps(other, record.start, record.end)) continue;
   int otherDistance = distance(layout, other, target);
   if (otherDistance == 0) return false;
   if (i < localCount && movable(other, door, lintels)
    && (otherDistance < distance || (otherDistance == distance && i < index))) return false;
  }
  return true;
 }

 private static boolean crosses(TemaLayout layout, TemaLayout.Record record, int target) {
  for (TemaLayout.Record other : layout.records) {
   if (other == record || other.vertical || !overlaps(other, record.start, record.end)) continue;
   if (other.axis >= Math.min(target, record.axis) && other.axis <= Math.max(target, record.axis)) return true;
  }
  return false;
 }
}
