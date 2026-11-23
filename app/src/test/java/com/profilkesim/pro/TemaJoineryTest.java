package com.profilkesim.pro;

import static org.junit.Assert.*;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public final class TemaJoineryTest {
 @Test public void c60FrameAndContinuousRecordsProduceVerifiedGlassSizes() {
  TemaJoinery.Order order = order(20000, 15000, 490);
  order.quantity = 2;
  order.vertical = 1;
  order.horizontal = 1;
  order.mullionFace = 490;
  order.joint = 10;
  order.frameGlass = 10;
  order.mullionGlass = 20;
  TemaJoinery.Plan plan = TemaJoinery.calculate(order);

  assertEquals(Arrays.asList(9265, 9265), plan.columns);
  assertEquals(Arrays.asList(6765, 6765), plan.rows);
  assertEquals(20000, plan.cuts.get(0).length);
  assertEquals(45, plan.cuts.get(0).angle);
  assertEquals(14040, plan.cuts.get(2).length);
  assertEquals(9285, plan.cuts.get(3).length);
  assertEquals(90, plan.cuts.get(2).angle);
  assertEquals(12, plan.mullionEnds);
  assertEquals(4, plan.panes.size());
  assertEquals(9295, plan.panes.get(0).width);
  assertEquals(6795, plan.panes.get(0).height);
  assertEquals(0, plan.glassArea.compareTo(ShopDimensions.area(9295, 6795, 8)));
 }

 @Test public void aldoksStraightFrameAndSashUseDifferentCutReferences() {
  TemaJoinery.Order order = order(12000, 10000, 402);
  order.corner = TemaJoinery.Corner.HORIZONTAL_FULL;
  order.openingSashes = Collections.singleton(1);
  order.sashFace = 590;
  order.sashGap = 30;
  order.sashGlass = 80;
  TemaJoinery.Plan plan = TemaJoinery.calculate(order);
  assertEquals(12000, plan.cuts.get(0).length);
  assertEquals(9196, plan.cuts.get(1).length);
  assertEquals(90, plan.cuts.get(1).angle);
  assertEquals(11136, plan.cuts.get(2).length);
  assertEquals(45, plan.cuts.get(2).angle);
  assertEquals(10116, plan.panes.get(0).width);
  assertEquals(8116, plan.panes.get(0).height);
 }

 @Test public void customOpeningsAndHorizontalPriorityUseCorrectSpans() {
  TemaJoinery.Order order = order(18000, 14000, 500);
  order.vertical = 1;
  order.horizontal = 1;
  order.mullionFace = 400;
  order.continuous = TemaJoinery.Continuous.HORIZONTAL;
  order.columnOpenings = Arrays.asList(8000, 8600);
  order.rowOpenings = Arrays.asList(6000, 6600);
  TemaJoinery.Plan plan = TemaJoinery.calculate(order);
  assertEquals(17000, plan.cuts.get(2).length);
  assertEquals(6000, plan.cuts.get(3).length);
  assertEquals(6600, plan.cuts.get(4).length);
  assertEquals(6, plan.mullionEnds);
  assertEquals(8600, plan.panes.get(3).width);
  order.columnOpenings = Arrays.asList(8000, 8700);
  assertThrows(IllegalArgumentException.class, () -> TemaJoinery.calculate(order));
 }

 @Test public void packingSupportsMixedStockAndKerfOnlyBetweenPieces() {
  List<CutPacking.Bar> stock = CutPacking.pack(Arrays.asList(7000, 2998, 2998), Arrays.asList(6000, 7000), 4);
  assertEquals(2, stock.size());
  assertEquals(7000, stock.get(0).stock);
  assertEquals(7000, stock.get(0).used);
  assertEquals(6000, stock.get(1).stock);
  assertEquals(6000, stock.get(1).used);
  assertEquals(0, stock.get(1).remaining());
  assertThrows(IllegalArgumentException.class,
   () -> CutPacking.pack(Collections.singletonList(7001), Arrays.asList(6000, 7000), 4));
 }

 @Test public void smallOrdersFindAPackingThatGreedyPlacementWouldMiss() {
  List<CutPacking.Bar> bars = CutPacking.pack(Arrays.asList(10, 9, 8, 6, 4, 3), Collections.singletonList(20), 0);
  assertEquals(2, bars.size());
  for (CutPacking.Bar bar : bars) assertEquals(0, bar.remaining());
 }

 @Test public void manualCatalogCutsKeepDecimalsAndAngle() {
  TemaJoinery.Plan plan = TemaJoinery.calculate(order(18000, 14000, 490));
  TemaJoinery.addManualCuts(plan, "t3101;850,5;2;45\nT.3101;850,5;1;45\nT.3101;850,5;1;90");
  assertEquals(4, plan.cuts.size());
  assertEquals(8505, plan.cuts.get(2).length);
  assertEquals(3, plan.cuts.get(2).quantity);
  assertEquals(45, plan.cuts.get(2).angle);
  assertEquals(90, plan.cuts.get(3).angle);
  assertThrows(IllegalArgumentException.class, () -> TemaJoinery.addManualCuts(plan, "T.3101;850;2;60"));
 }

 @Test public void decimalAreaAndDrawingMeasurementsKeepUnitsAndRejectConflicts() {
  assertEquals(2859, ShopDimensions.tenths("285,9", "En"));
  assertEquals(0, new BigDecimal("3.6036").compareTo(ShopDimensions.area(12000, 10010, 3)));
  DrawingDimensions read = DrawingDimensions.read("Profil eni 49 mm\nDış en: 285,9 cm\nKasa yüksekliği 2.10 m\n4500 × 1000");
  assertEquals(28590, read.width);
  assertEquals(21000, read.height);
  assertEquals(-1, DrawingDimensions.read("Dış en 2850\nDış en 3000").width);
  DrawingDimensions scaled = DrawingDimensions.scaled(490, 49, 2200, 1400);
  assertEquals(22000, scaled.width);
  assertEquals(14000, scaled.height);
  assertThrows(IllegalArgumentException.class, () -> DrawingDimensions.scaled(490, 0, 2200, 1400));
 }

 private static TemaJoinery.Order order(int width, int height, int face) {
  TemaJoinery.Order order = new TemaJoinery.Order();
  order.width = width;
  order.height = height;
  order.frameFace = face;
  order.quantity = 1;
  order.frameCode = "KASA";
  order.mullionCode = "KAYIT";
  order.sashCode = "KANAT";
  return order;
 }
}
