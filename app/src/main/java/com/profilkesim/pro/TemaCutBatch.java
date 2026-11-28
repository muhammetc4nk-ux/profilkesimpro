package com.profilkesim.pro;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Packs each profile independently, retaining the originating position on every cut. */
final class TemaCutBatch {
 static final int MAX_PIECES = 3000;

 static final class Line {
  final String key, label, ends;
  final int length, quantity;

  Line(String key, String label, int length, int quantity, String ends) {
   this.key = key;
   this.label = label;
   this.length = length;
   this.quantity = quantity;
   this.ends = ends;
  }
 }

 static final class Piece {
  final String position, ends;
  final int length;

  Piece(String position, int length, String ends) {
   this.position = position;
   this.length = length;
   this.ends = ends;
  }
 }

 static final class Bar {
  final CutPacking.Bar layout;
  final List<Piece> pieces;

  Bar(CutPacking.Bar layout, List<Piece> pieces) {
   this.layout = layout;
   this.pieces = pieces;
  }
 }

 static final class Profile {
  final String key, label;
  final List<Bar> bars = new ArrayList<>();

  Profile(String key, String label) {
   this.key = key;
   this.label = label;
  }
 }

 static final class Result {
  final Map<String, Profile> profiles = new LinkedHashMap<>();
  int barCount, pieceCount;
  long net, remaining;
 }

 static Result packPlan(String name, TemaJoinery.Plan plan, int stock, int kerf) {
  List<Line> lines = new ArrayList<>();
  for (TemaJoinery.Cut cut : plan.cuts) {
   String ends = cut.angle + "°" + (cut.angle == cut.endAngle ? "" : "/" + cut.endAngle + "°");
   lines.add(new Line(cut.code, cut.code, cut.length, cut.quantity, ends));
  }
  return pack(Collections.singletonMap(name, lines), stock, kerf);
 }

 static Result pack(Map<String, List<Line>> positions, int stock, int kerf) {
  if (stock <= 0 || kerf < 0) throw new IllegalArgumentException("Stok boyunu ve testere payını kontrol edin.");
  Result result = new Result();
  Map<String, List<Piece>> byProfile = new LinkedHashMap<>();
  for (Map.Entry<String, List<Line>> position : positions.entrySet()) {
   for (Line line : position.getValue()) {
    if (line.quantity < 0 || line.quantity > MAX_PIECES || line.length < 0 || line.key.isEmpty()) {
     throw new IllegalArgumentException(position.getKey() + " · " + line.label + " satırını kontrol edin.");
    }
    if (line.length == 0 || line.quantity == 0) continue;
    if (line.length > stock) {
     throw new IllegalArgumentException(position.getKey() + " · " + line.label + " · "
      + ShopDimensions.mm(line.length) + " mm kesim, " + ShopDimensions.mm(stock) + " mm stoğa sığmıyor.");
    }
    result.pieceCount += line.quantity;
    if (result.pieceCount > MAX_PIECES) {
     throw new IllegalArgumentException("Bir stok planına en fazla " + MAX_PIECES + " parça yerleştirilebilir.");
    }
    if (!result.profiles.containsKey(line.key)) result.profiles.put(line.key, new Profile(line.key, line.label));
    List<Piece> pieces = byProfile.computeIfAbsent(line.key, key -> new ArrayList<>());
    Piece piece = new Piece(position.getKey(), line.length, line.ends);
    for (int i = 0; i < line.quantity; i++) pieces.add(piece);
    result.net += (long) line.length * line.quantity;
   }
  }
  if (result.pieceCount == 0) throw new IllegalArgumentException("Stok planı için en az bir pozda kesim olmalı.");

  for (Map.Entry<String, List<Piece>> entry : byProfile.entrySet()) {
   List<Integer> lengths = new ArrayList<>();
   Map<Integer, ArrayDeque<Piece>> byLength = new LinkedHashMap<>();
   for (Piece piece : entry.getValue()) {
    lengths.add(piece.length);
    byLength.computeIfAbsent(piece.length, size -> new ArrayDeque<>()).addLast(piece);
   }
   Profile profile = result.profiles.get(entry.getKey());
   for (CutPacking.Bar bar : CutPacking.pack(lengths, Collections.singletonList(stock), kerf)) {
    List<Piece> cuts = new ArrayList<>(bar.pieces.size());
    for (int length : bar.pieces) cuts.add(byLength.get(length).removeFirst());
    profile.bars.add(new Bar(bar, cuts));
    result.barCount++;
    result.remaining += bar.remaining();
   }
  }
  return result;
 }
}
