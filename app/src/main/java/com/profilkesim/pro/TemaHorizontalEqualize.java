package com.profilkesim.pro;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Equalizes clear vertical gaps between horizontal records, strip by strip. */
final class TemaHorizontalEqualize {
 private TemaHorizontalEqualize() {}

 static TemaOpeningResize.Drawing apply(TemaOpeningResize.Drawing drawing, TemaLayout layout,
                                        Set<Integer> protectedBays, int defaultProfile) {
  List<int[]> cells = drawing.cells();
  List<TemaLayout.Record> records = layout.records;
  Set<Integer> edges = new TreeSet<>();
  for (int[] cell : cells) { edges.add(cell[0]); edges.add(cell[2]); }
  List<List<Integer>> strips = new ArrayList<>();
  List<Integer> stripEnds = new ArrayList<>();
  List<Integer> firstStrip = new ArrayList<>();
  boolean[] fixed = new boolean[records.size()];
  int[] positions = new int[records.size()];
  Arrays.fill(positions, -1);
  for (int i = 0; i < records.size(); i++) firstStrip.add(-1);
  for (int x : edges) {
   if (x == drawing.scale) break;
   List<Integer> strip = new ArrayList<>();
   int end = layout.innerHeight;
   for (int i = 0; i < records.size(); i++) {
    TemaLayout.Record record = records.get(i);
    if (!record.vertical && record.start <= x && x < record.end) strip.add(i);
   }
   if (strip.isEmpty()) continue;
   for (int i = 0; i < cells.size(); i++) {
    int[] cell = cells.get(i);
    if (cell[0] > x || x >= cell[2]) continue;
    if (layout.bays.get(i).door) end = layout.height - layout.frame.top.face;
    if (protectedBays.contains(i + 1)) {
     for (int index : strip) {
      int axis = records.get(index).axis;
      if (cell[1] <= axis && axis <= cell[3]) fixed[index] = true;
     }
    }
   }
   strip.sort(Comparator.comparingInt(index -> records.get(index).axis));
   for (int index : strip) {
    int previous = firstStrip.get(index);
    if (previous < 0) firstStrip.set(index, strips.size());
    else if (!strips.get(previous).equals(strip) || stripEnds.get(previous) != end) fixed[index] = true;
   }
   strips.add(strip);
   stripEnds.add(end);
  }

  for (int i = 0; i < strips.size(); i++) {
   List<Integer> strip = strips.get(i);
   int start = 0, availableStart = 0;
   for (int j = 0; j <= strip.size(); j++) {
    if (j < strip.size() && !fixed[strip.get(j)]) continue;
    TemaLayout.Record boundary = j == strip.size() ? null : records.get(strip.get(j));
    int end = boundary == null ? stripEnds.get(i)
     : layout.y(boundary.axis) - layout.frame.top.face - boundary.section.face / 2;
    int clear = end - availableStart;
    for (int k = start; k < j; k++) clear -= records.get(strip.get(k)).section.face;
    if (j > start && clear < j - start + 1) throw new IllegalArgumentException("Yatay kayıtlar arasında net boşluk yetersiz.");
    int profileWidth = 0;
    for (int k = start; k < j; k++) {
     int index = strip.get(k), face = records.get(index).section.face;
     int center = availableStart + (int)Math.round((k - start + 1) * (double)clear / (j - start + 1))
      + profileWidth + face / 2;
     int at = (int)Math.round(center * (double)drawing.scale / layout.innerHeight);
     if (at <= 0 || at >= drawing.scale || (positions[index] >= 0 && positions[index] != at)) {
      throw new IllegalArgumentException("Yatay kayıtlar bu çizimde aynı anda eşitlenemiyor.");
     }
     positions[index] = at;
     profileWidth += face;
    }
    if (boundary != null) {
     availableStart = end + boundary.section.face;
     start = j + 1;
    }
   }
  }

  TemaOpeningResize.Drawing result = drawing.copy();
  result.rows.clear();
  for (int row : drawing.rows) result.profiles.remove("h" + row);
  boolean moved = false;
  for (int i = 0; i < records.size(); i++) {
   TemaLayout.Record record = records.get(i);
   if (record.vertical) continue;
   int next = positions[i] < 0 ? record.axis : positions[i];
   moved |= next != record.axis;
   if (i < result.local.size()) {
    int[] line = result.local.get(i);
    line[1] = line[3] = next;
   } else {
    if (!result.rows.add(next)) throw new IllegalArgumentException("Yatay kayıtlar çakışıyor.");
    Integer profile = drawing.profiles.get("h" + record.axis);
    if (profile != null) result.profiles.put("h" + next, profile);
   }
  }
  if (!moved) return drawing;
  for (int i = 0; i < drawing.local.size(); i++) {
   int[] original = drawing.local.get(i), line = result.local.get(i);
   if (original[0] != original[2]) continue;
   for (int side = 1; side <= 3; side += 2) {
    for (int r = 0; r < records.size(); r++) {
     TemaLayout.Record record = records.get(r);
     if (record.vertical || record.axis != original[side] || original[0] < record.start || original[0] > record.end) continue;
     int next = positions[r] < 0 ? record.axis : positions[r];
     if (line[side] != original[side] && line[side] != next) throw new IllegalArgumentException("Yatay kayıt bağlantıları ayrılamıyor.");
     line[side] = next;
    }
   }
   if (line[1] >= line[3]) throw new IllegalArgumentException("Yatay kayıtlar çakışıyor.");
  }
  if (!TemaRecordPlacement.connected(result.records(defaultProfile), drawing.scale) || result.cells().size() != cells.size()) {
   throw new IllegalArgumentException("Yatay kayıt bağlantıları korunamadı.");
  }
  return result;
 }
}
