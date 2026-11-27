package com.profilkesim.pro;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public final class TemaCutBatchTest {
 @Test public void sameProfileSharesBarsAcrossDrawnAndScannedPositions() {
  Map<String, List<TemaCutBatch.Line>> positions = new LinkedHashMap<>();
  positions.put("P01", Arrays.asList(
   new TemaCutBatch.Line("T.3101", "T.3101", 30000, 1, "45°/90°"),
   new TemaCutBatch.Line("T.3105", "T.3105", 15000, 1, "45°")));
  positions.put("P02", Arrays.asList(
   new TemaCutBatch.Line("T.3101", "T.3101 Kasa", 29960, 1, ""),
   new TemaCutBatch.Line("T.3101", "T.3101 Kasa", 500, 0, ""),
   new TemaCutBatch.Line("T.3101", "T.3101 Kasa", 0, 1, "")));

  TemaCutBatch.Result result = TemaCutBatch.pack(positions, 60000, 40);
  assertEquals(2, result.profiles.size());
  assertEquals(2, result.barCount);
  assertEquals(3, result.pieceCount);
  assertEquals(74960, result.net);
  assertEquals(45000, result.remaining);

  TemaCutBatch.Bar bar = result.profiles.get("T.3101").bars.get(0);
  assertEquals(60000, bar.layout.used);
  assertEquals(0, bar.layout.remaining());
  assertEquals("P01", bar.pieces.get(0).position);
  assertEquals("45°/90°", bar.pieces.get(0).ends);
  assertEquals("P02", bar.pieces.get(1).position);
  assertEquals("", bar.pieces.get(1).ends);
  assertEquals(2, TemaCutBatch.pack(positions, 59999, 40).profiles.get("T.3101").bars.size());
 }

 @Test public void equalLengthsRetainTheirPositionsAndAngleInstructions() {
  Map<String, List<TemaCutBatch.Line>> positions = new LinkedHashMap<>();
  positions.put("Batı", Collections.singletonList(new TemaCutBatch.Line("T.3101", "Kasa", 29980, 1, "90°")));
  positions.put("Doğu", Collections.singletonList(new TemaCutBatch.Line("T.3101", "Kasa", 29980, 1, "45°")));

  List<TemaCutBatch.Piece> cuts = TemaCutBatch.pack(positions, 60000, 40).profiles.get("T.3101").bars.get(0).pieces;
  assertEquals("Batı", cuts.get(0).position);
  assertEquals("90°", cuts.get(0).ends);
  assertEquals("Doğu", cuts.get(1).position);
  assertEquals("45°", cuts.get(1).ends);
  IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
   () -> TemaCutBatch.pack(positions, 29000, 40));
  assertTrue(error.getMessage().contains("Batı"));
  assertTrue(error.getMessage().contains("2900 mm"));
 }
}
