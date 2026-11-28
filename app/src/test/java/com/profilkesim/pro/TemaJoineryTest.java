package com.profilkesim.pro;

import static org.junit.Assert.*;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public final class TemaJoineryTest {
 @Test public void c60FrameAndContinuousRecordsProduceGlassCutSizes() {
  TemaJoinery.Order order = order(20000, 15000, 490);
  order.quantity = 2;
  order.vertical = 1;
  order.horizontal = 1;
  order.mullionFace = 490;
  order.joint = 10;
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
  assertEquals(9165, plan.panes.get(0).width);
  assertEquals(6665, plan.panes.get(0).height);
  assertEquals(0, plan.glassArea.compareTo(ShopDimensions.area(9165, 6665, 8)));
 }

 @Test public void aldoksStraightFrameAndSashUseDifferentCutReferences() {
  TemaJoinery.Order order = order(12000, 10000, 402);
  order.corner = TemaJoinery.Corner.HORIZONTAL_FULL;
  order.openingSashes = Collections.singleton(1);
  order.sashFace = 590;
  TemaJoinery.Plan plan = TemaJoinery.calculate(order);
  assertEquals(12000, plan.cuts.get(0).length);
  assertEquals(9196, plan.cuts.get(1).length);
  assertEquals(90, plan.cuts.get(1).angle);
  assertEquals(11096, plan.cuts.get(2).length);
  assertEquals(45, plan.cuts.get(2).angle);
  assertEquals(9816, plan.panes.get(0).width);
  assertEquals(7816, plan.panes.get(0).height);
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
  assertEquals(8500, plan.panes.get(3).width);
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

 @Test public void fixedAndOpeningBeadsUseTheirOwnSeatsAndQuantities() {
  TemaJoinery.Order order = order(20000, 15000, 490);
  order.quantity = 2;
  order.vertical = 1;
  order.mullionFace = 490;
  order.sashFace = 660;
  order.openingSashes = Collections.singleton(2);
  order.adapterCode = "T.3107";
  order.fixedBead = new TemaJoinery.Bead("T.3112", 220, 0, TemaJoinery.Corner.MITER);
  order.sashBead = new TemaJoinery.Bead("T.3111", 170, 5, TemaJoinery.Corner.HORIZONTAL_FULL);
  TemaJoinery.Plan plan = TemaJoinery.calculate(order);
  assertCut(plan, "T.3112", "Sabit cam çıtası yatay", 9265, 45, 4);
  assertCut(plan, "T.3112", "Sabit cam çıtası dikey", 14020, 45, 4);
  assertCut(plan, "T.3107", "Kanat adaptörü yatay", 9265, 45, 4);
  assertCut(plan, "T.3111", "Kanat cam çıtası yatay", 7855, 90, 4);
  assertCut(plan, "T.3111", "Kanat cam çıtası dikey", 12270, 90, 4);
  assertEquals(4, plan.mullionEnds);
  assertEquals(9165, plan.panes.get(0).width);
  assertEquals(13920, plan.panes.get(0).height);
  assertEquals(7745, plan.panes.get(1).width);
  assertEquals(12500, plan.panes.get(1).height);
 }

 @Test public void readsLabelledDrawingPairsAndRecordCountsWithoutInferringLayout() {
  DrawingDimensions drawing = DrawingDimensions.read("Dış ölçüler: 2000 × 1500 mm\nDikey orta kayıt adedi: 2\nYatay kayıt: 0\nSabit bölme: 1\nCam boyu 700 mm");
  assertEquals(20000, drawing.width);
  assertEquals(15000, drawing.height);
  assertEquals(2, drawing.vertical);
  assertEquals(0, drawing.horizontal);
  assertEquals(1, drawing.fixed);
  assertEquals(-1, DrawingDimensions.read("Kasa dış ölçüsü 1900 x 1500\nDış en 2000 mm").width);
  assertEquals(-1, DrawingDimensions.read("Kanat 1200 × 900 mm\nCam eni 700 mm").width);
 }

 @Test public void aldoksUsesEachBoundarySectionAndTheSelectedSashWidth() {
  TemaLayout.Section narrow = new TemaLayout.Section("T.3001", 402);
  TemaLayout.Frame frame = new TemaLayout.Frame(narrow, new TemaLayout.Section("T.3004", 585),
   new TemaLayout.Section("T.3007", 907), narrow);
  List<int[]> bays = Arrays.asList(new int[]{0,0,500,1000}, new int[]{500,0,1000,400},
   new int[]{500,400,1000,1000});
  List<TemaLayout.Record> records = Arrays.asList(
   new TemaLayout.Record(500,0,500,1000,new TemaLayout.Section("T.3006",765)),
   new TemaLayout.Record(500,400,1000,400,new TemaLayout.Section("T.3003",590)));
  TemaJoinery.Order order = order(30000,23000,402);
  order.layout = new TemaLayout(order.width,order.height,frame,1000,bays,records,Collections.singleton(3));
  order.openingSashes = Collections.singleton(3);
  order.sashFace = 590;
  order.sashProfiles = Collections.singletonMap(3,new TemaLayout.Section("T.3008",1100));
  order.frameJoint = 10;
  order.mullionJoint = 20;
  TemaJoinery.Plan plan = TemaJoinery.calculate(order);
  assertEquals(13964,plan.layout.bays.get(0).width());
  assertEquals(13962,plan.layout.bays.get(2).width());
  assertEquals(13315,plan.layout.bays.get(2).height());
  assertEquals(11562,plan.panes.get(2).width);
  assertEquals(10915,plan.panes.get(2).height);
  TemaJoinery.ProfileMark sill = plan.profileMarks.get(1);
  assertEquals("T.3001", sill.code);
  assertEquals(402, sill.face);
  assertEquals(585, plan.profileMarks.get(0).face);
  assertEquals(14366, sill.length);
  assertEquals(0, sill.start);
  assertEquals(14366, sill.end);
  assertFalse(sill.vertical);
  assertEquals(4, plan.profileMarks.stream().filter(mark -> mark.role == TemaJoinery.ProfileRole.SASH).count());
  TemaJoinery.ProfileMark sash = plan.profileMarks.stream()
   .filter(mark -> mark.role == TemaJoinery.ProfileRole.SASH && mark.vertical).findFirst().get();
  TemaLayout.Opening door = plan.layout.bays.get(2);
  assertEquals("T.3008", sash.code);
  assertEquals(1100, sash.face);
  assertEquals(13215, sash.length);
  assertEquals(50, sash.start - door.top);
  assertEquals(50, door.bottom - sash.end);
  assertEquals(50, sash.axis - 550 - door.left);
  assertEquals(550, plan.panes.get(2).sash.face / 2);
  assertEquals(1,plan.layout.rails.size());
  assertCut(plan,"T.3001","Kasa alt yatay",14366,45,90,1);
  assertCut(plan,"T.3007","Kasa dikey",23000,45,90,1);
  // The sill butts against the floor-reaching mullion.
  assertCut(plan,"T.3006","Dikey orta kayıt",22415,90,1);
  assertCut(plan,"T.3003","Yatay orta kayıt",13992,90,1);
  assertCut(plan,"T.3008","Kanat dikey",13215,45,2);
  records.set(0,new TemaLayout.Record(500,0,500,1000,new TemaLayout.Section("T.3003",590)));
  order.layout = new TemaLayout(order.width,order.height,frame,1000,bays,records,Collections.singleton(3));
  TemaJoinery.Plan narrowerRecord = TemaJoinery.calculate(order);
  assertEquals(14050,narrowerRecord.layout.bays.get(2).width());
  assertEquals(13315,narrowerRecord.layout.bays.get(2).height());
  assertCut(narrowerRecord,"T.3003","Yatay orta kayıt",14080,90,1);
 }

 @Test public void c60KeepsTheSillOnlyUnderTheFixedBayAndRetainsUpperRecords() {
  TemaJoinery.Order order = order(20000,22000,490);
  order.frameCode = "T.3101";
  order.mullionCode = "T.3102";
  order.vertical = 1;
  order.horizontal = 1;
  order.mullionFace = 900;
  order.sashFace = 660;
  order.openingSashes = new java.util.HashSet<>(Arrays.asList(2,4));
  order.removeDoorSills = true;
  TemaJoinery.Plan plan = TemaJoinery.calculate(order);
  assertFalse(plan.layout.bays.get(1).door);
  assertTrue(plan.layout.bays.get(3).door);
  assertEquals(490,plan.layout.bays.get(3).height()-plan.layout.bays.get(2).height());
  assertEquals(1,plan.layout.rails.size());
  assertCut(plan,"T.3101","Kasa alt yatay",9550,45,90,1);
  // The sill joins the side of the mullion, which reaches the floor.
  assertCut(plan,"T.3102","Dikey orta kayıt",21510,90,1);
  assertCut(plan,"T.3102","Yatay orta kayıt",9060,90,2);
 }

 @Test public void fullWidthDoorsHaveNoLowerFrameOrAdapter() {
  TemaJoinery.Order order = order(10000,22000,490);
  order.sashFace = 660;
  order.sashCode = "T.3105";
  order.adapterCode = "T.3107";
  order.openingSashes = Collections.singleton(1);
  order.removeDoorSills = true;
  TemaJoinery.Plan plan = TemaJoinery.calculate(order);
  assertEquals(21510,plan.layout.bays.get(0).height());
  assertTrue(plan.layout.rails.isEmpty());
  assertEquals(2,plan.frameCorners);
  assertCut(plan,"KASA","Kasa yatay",10000,45,1);
  assertCut(plan,"KASA","Kasa dikey",22000,45,90,2);
  assertCut(plan,"T.3107","Kanat adaptörü yatay",9020,45,1);
  assertCut(plan,"T.3107","Kanat adaptörü dikey",21510,45,90,2);
 }

 @Test public void glassNeedsPositiveDimensionsAfterTenMillimeterDeduction() {
  TemaJoinery.Order order = order(6000, 1100, 500);
  assertThrows(IllegalArgumentException.class, () -> TemaJoinery.calculate(order));
  order.height = 1101;
  TemaJoinery.Plan plan = TemaJoinery.calculate(order);
  assertEquals(4900, plan.panes.get(0).width);
  assertEquals(1, plan.panes.get(0).height);
  order.width = 1100;
  assertThrows(IllegalArgumentException.class, () -> TemaJoinery.calculate(order));
 }

 @Test public void changingTheGridKeepsWholeBayAssignmentsAndTheirProfileOverrides() {
  int[] fixed = {0, 0, 500, 1000};
  int[] door = {500, 0, 1000, 1000};
  List<int[]> before = Arrays.asList(fixed, door);
  List<int[]> after = Arrays.asList(new int[]{0, 0, 500, 500}, new int[]{0, 500, 500, 1000}, door.clone());
  java.util.Set<Integer> openings = new java.util.LinkedHashSet<>(Collections.singletonList(2));
  java.util.Set<Integer> left = new java.util.LinkedHashSet<>(openings);
  java.util.Set<Integer> empty = new java.util.LinkedHashSet<>(Collections.singletonList(1));
  java.util.Map<String, Integer> profiles = new java.util.LinkedHashMap<>();
  profiles.put("v500", 1);
  profiles.put("s2", 3);

  TemaWorkshop.retainBays(before, after, openings, left, empty, profiles);

  assertEquals(Collections.singleton(3), openings);
  assertEquals(Collections.singleton(3), left);
  assertTrue(empty.isEmpty());
  assertEquals(Integer.valueOf(1), profiles.get("v500"));
  assertEquals(Integer.valueOf(3), profiles.get("s3"));
  assertFalse(profiles.containsKey("s2"));
 }

 @Test public void bayMeasurementsKeepTheClearFrameOpeningSeparateFromGlassSizes() {
  TemaJoinery.Order order = order(10000, 22000, 490);
  order.sashFace = 660;
  order.openingSashes = Collections.singleton(1);
  order.removeDoorSills = true;
  TemaJoinery.Plan plan = TemaJoinery.calculate(order);
  TemaLayout.Opening bay = plan.layout.bays.get(0);
  TemaJoinery.Pane pane = plan.panes.get(0);

  assertEquals("Bölme 1 · Kapı", TemaBayDimensions.caption(1, TemaBayDimensions.kind(bay, true, false)));
  assertEquals("902 × 2151 mm", TemaBayDimensions.size(bay.width(), bay.height()));
  assertEquals("750 × 1999 mm", TemaBayDimensions.size(pane.width, pane.height));
  assertEquals("Boş kasa", TemaBayDimensions.kind(bay, true, true));
 }

 @Test public void groupedCutRowsKeepDifferentEndsSeparateAndPreserveQuantities() {
  TemaJoinery.Plan plan = new TemaJoinery.Plan(Collections.emptyList(), Collections.emptyList(), null);
  plan.addCut("T.3001", "Kasa yatay", 12005, 45, 45, 2);
  plan.addCut("T.3004", "Kasa dikey", 10800, 90, 90, 1);
  plan.addCut("T.3001", "Kasa yatay", 12005, 45, 90, 1);
  plan.addCut("T.3001", "Kasa yatay", 12005, 45, 45, 1);

  java.util.Map<String, List<TemaJoinery.Cut>> grouped = plan.cutsByProfile();
  assertEquals(Arrays.asList("T.3001", "T.3004"), new java.util.ArrayList<>(grouped.keySet()));
  List<TemaJoinery.Cut> narrow = grouped.get("T.3001");
  assertEquals(2, narrow.size());
  assertEquals(12005, narrow.get(0).length);
  assertEquals(3, narrow.get(0).quantity);
  assertEquals(45, narrow.get(0).endAngle);
  assertEquals(45, narrow.get(1).angle);
  assertEquals(90, narrow.get(1).endAngle);
  assertEquals(1, narrow.get(1).quantity);
  assertTrue(TemaCatalog.Series.ALDOKS.find("T.3001").image != TemaCatalog.Series.ALDOKS.find("T.3004").image);
 }

 @Test public void clearOpeningUsesTheLargestActualProfileAlongASegmentedBoundary() {
  TemaLayout.Section frame = new TemaLayout.Section("T.3101", 490);
  TemaLayout.Section narrow = new TemaLayout.Section("T.3103", 207);
  TemaLayout.Section wide = new TemaLayout.Section("T.3102", 900);
  TemaLayout.Section cross = new TemaLayout.Section("T.3101", 490);
  List<int[]> cells = Arrays.asList(new int[]{0, 0, 500, 1000}, new int[]{500, 0, 1000, 500},
   new int[]{500, 500, 1000, 1000});
  List<TemaLayout.Record> records = Arrays.asList(new TemaLayout.Record(500, 0, 500, 500, narrow),
   new TemaLayout.Record(500, 500, 500, 1000, wide), new TemaLayout.Record(500, 500, 1000, 500, cross));
  TemaLayout layout = new TemaLayout(21000, 23000, new TemaLayout.Frame(frame), 1000, cells, records, Collections.emptySet());

  assertEquals(layout.x(500) - 450 - frame.face, layout.bays.get(0).width());
  assertEquals(21000 - frame.face - layout.x(500) - 104, layout.bays.get(1).width());
  assertEquals(21000 - frame.face - layout.x(500) - 450, layout.bays.get(2).width());
 }

 @Test public void recordEndAccountsForEverySectionTouchingTheFaceAtTheJoint() {
  TemaLayout.Section frame = new TemaLayout.Section("T.3101", 490);
  TemaLayout.Section narrow = new TemaLayout.Section("T.3103", 207);
  TemaLayout.Section wide = new TemaLayout.Section("T.3102", 900);
  TemaLayout.Section cross = new TemaLayout.Section("T.3006", 765);
  List<int[]> cells = Arrays.asList(new int[]{0, 0, 500, 490}, new int[]{0, 490, 500, 1000},
   new int[]{500, 0, 1000, 1000});
  List<TemaLayout.Record> records = Arrays.asList(new TemaLayout.Record(500, 0, 500, 500, narrow),
   new TemaLayout.Record(500, 500, 500, 1000, wide), new TemaLayout.Record(0, 490, 500, 490, cross));
  TemaLayout layout = new TemaLayout(21000, 23000, new TemaLayout.Frame(frame), 1000, cells, records, Collections.emptySet());
  TemaLayout.Member cut = layout.members(TemaJoinery.Continuous.VERTICAL).get(2);

  assertEquals("T.3006", cut.section.code);
  assertEquals(layout.x(500) - 450, cut.end);
  assertEquals(layout.x(500) - frame.face - 450 + 18 - 7, cut.cutLength(18, -7));
 }

 private static void assertCut(TemaJoinery.Plan plan, String code, String role, int length, int angle, int count) {
  assertCut(plan,code,role,length,angle,angle,count);
 }

 private static void assertCut(TemaJoinery.Plan plan, String code, String role, int length, int angle, int endAngle, int count) {
  for (TemaJoinery.Cut cut : plan.cuts) {
   if (!cut.code.equals(code) || !cut.role.equals(role) || cut.length != length) continue;
   assertEquals(length, cut.length);
   assertEquals(angle, cut.angle);
   assertEquals(endAngle, cut.endAngle);
   assertEquals(count, cut.quantity);
   return;
  }
  fail(role + " cut missing");
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
