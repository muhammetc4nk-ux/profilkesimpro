package com.profilkesim.pro;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

public final class TemaOpeningResizeTest {
 private static final int SCALE = 1_000_000;
 private static final TemaLayout.Section LEFT = new TemaLayout.Section("T.3001", 402);
 private static final TemaLayout.Section RIGHT = new TemaLayout.Section("T.3004", 585);
 private static final TemaLayout.Section RECORD = new TemaLayout.Section("T.3006", 765);
 private static final TemaLayout.Frame FRAME = new TemaLayout.Frame(LEFT, LEFT, RIGHT, RIGHT);

 @Test public void resizingTheOnlyOpeningChangesOutsideWidthAndHeightWithoutARecord() {
  TemaOpeningResize.Drawing before = drawing(Collections.emptySet());
  TemaOpeningResize.Drawing after = TemaOpeningResize.resize(before, 0, 18001, 13007, new int[]{100},
   model -> layout(model, Collections.emptySet()));
  assertEquals(18001 + LEFT.face + RIGHT.face, after.width);
  assertEquals(13007 + LEFT.face + RIGHT.face, after.height);
  assertEquals(18001, layout(after, Collections.emptySet()).bays.get(0).width());
  assertEquals(13007, layout(after, Collections.emptySet()).bays.get(0).height());
 }

 @Test public void glassSizeUsesTheSelectedSashAndKeepsTheDoorWithoutABottomSill() {
  TemaLayout.Section sash = new TemaLayout.Section("T.3002", 590);
  int allowance = TemaJoinery.glassAllowance(sash);
  Set<Integer> doors = Collections.singleton(1);
  assertEquals(1380, allowance);
  TemaOpeningResize.Drawing before = drawing(Collections.emptySet());
  TemaOpeningResize.Drawing result = TemaOpeningResize.resize(before, 0, 9000 + allowance, 19000 + allowance,
   new int[]{allowance}, model -> layout(model, doors));
  TemaLayout layout = layout(result, doors);
  assertEquals(19000 + allowance + LEFT.face, result.height);
  assertTrue(layout.rails.isEmpty());
  assertEquals(9000, layout.bays.get(0).width() - allowance);
  assertEquals(19000, layout.bays.get(0).height() - allowance);
 }

 @Test public void changingOneBayRepositionsTheRecordAndRecalculatesItsNeighbour() {
  TemaOpeningResize.Drawing before = drawing(Collections.singleton(SCALE / 2));
  TemaLayout original = layout(before, Collections.emptySet());
  int first = original.bays.get(0).width(), second = original.bays.get(1).width();
  TemaOpeningResize.Drawing result = TemaOpeningResize.resize(before, 0, first + 1307, original.bays.get(0).height(),
   new int[]{100, 100}, model -> layout(model, Collections.emptySet()));
  TemaLayout layout = layout(result, Collections.emptySet());
  assertEquals(before.width, result.width);
  assertEquals(first + 1307, layout.bays.get(0).width());
  assertEquals(second - 1307, layout.bays.get(1).width());
  assertEquals(Integer.valueOf(2), result.profiles.get("v" + result.columns.first()));
 }

 @Test public void balancesLocalHorizontalRecordsInIndependentColumns() {
  int middle = SCALE / 2;
  List<int[]> local = java.util.Arrays.asList(
   new int[]{0, 200000, middle, 200000, 2},
   new int[]{middle, 700000, SCALE, 700000, 2});
  TemaOpeningResize.Drawing original = new TemaOpeningResize.Drawing(20500, 17000, SCALE,
   Collections.singleton(middle), Collections.emptySet(), local, Collections.emptyMap());
  TemaLayout before = layout(original, Collections.emptySet());
  TemaOpeningResize.Drawing result = TemaHorizontalEqualize.apply(original, before, Collections.emptySet(), 2);
  TemaLayout after = layout(result, Collections.emptySet());
  assertNotSame(original, result);
  assertEquals(original.height, result.height);
  assertEquals(200000, original.local.get(0)[1]);
  assertEquals(700000, original.local.get(1)[1]);
  for (TemaLayout.Opening bay : after.bays) {
   assertTrue(Math.abs(after.bays.get(0).height() - bay.height()) <= 1);
  }
  TemaOpeningResize.Drawing protectedResult = TemaHorizontalEqualize.apply(original, before, Collections.singleton(1), 2);
  assertEquals(200000, protectedResult.local.get(0)[1]);
  assertNotEquals(700000, protectedResult.local.get(1)[1]);
 }

 @Test public void equalizesSelectedNetOpeningsWithoutResizingTheFrame() {
  TemaOpeningResize.Drawing before = drawing(Collections.singleton(SCALE / 3));
  Set<Integer> doors = new java.util.LinkedHashSet<>(java.util.Arrays.asList(1, 2));
  TemaOpeningResize.Drawing after = TemaOpeningResize.equalize(before, java.util.Arrays.asList(0, 1),
   new int[]{1380, 1730}, model -> layout(model, doors));
  TemaLayout equal = layout(after, doors);
  assertEquals(before.width, after.width);
  assertEquals(before.height, after.height);
  assertEquals(equal.bays.get(0).width(), equal.bays.get(1).width());
  assertEquals(equal.bays.get(0).height(), equal.bays.get(1).height());
  assertTrue(equal.rails.isEmpty());
  assertEquals(Collections.singleton(SCALE / 3), before.columns);
 }

 @Test public void extendsTheFrameWhenTheAdjacentBayWouldOtherwiseBeTooNarrow() {
  TemaOpeningResize.Drawing before = drawing(Collections.singleton(SCALE / 2));
  TemaLayout original = layout(before, Collections.emptySet());
  int first = original.bays.get(0).width(), second = original.bays.get(1).width();
  int delta = second - 100;
  TemaOpeningResize.Drawing result = TemaOpeningResize.resize(before, 0, first + delta, original.bays.get(0).height(),
   new int[]{100, 100}, model -> layout(model, Collections.emptySet()));
  TemaLayout layout = layout(result, Collections.emptySet());
  assertEquals(before.width + delta, result.width);
  assertEquals(first + delta, layout.bays.get(0).width());
  assertEquals(second, layout.bays.get(1).width());
  assertEquals(Collections.singleton(SCALE / 2), before.columns);
 }

 @Test public void movingALocalRecordChangesOnlyItsColumn() {
  int middle = SCALE / 2;
  List<int[]> horizontal = java.util.Arrays.asList(
   new int[]{0, middle, middle, middle, 2}, new int[]{middle, middle, SCALE, middle, 2});
  TemaOpeningResize.Drawing before = new TemaOpeningResize.Drawing(20500, 17000, SCALE,
   Collections.singleton(middle), Collections.emptySet(), horizontal, Collections.emptyMap());
  TemaLayout previous = layout(before, Collections.emptySet());
  TemaOpeningResize.Drawing result = TemaOpeningResize.resize(before, 0, previous.bays.get(0).width(),
   previous.bays.get(0).height() - 1207, new int[]{100, 100, 100, 100}, model -> layout(model, Collections.emptySet()));
  TemaLayout after = layout(result, Collections.emptySet());
  assertEquals(before.height, result.height);
  assertEquals(previous.bays.get(0).height() - 1207, after.bays.get(0).height());
  assertEquals(previous.bays.get(1).height(), after.bays.get(1).height());
  assertArrayEquals(horizontal.get(1), result.local.get(1));
 }

 @Test public void overallInsideSizeAccountsForDifferentProfilesAndOnlyExistingSills() {
  TemaOpeningResize.Drawing model = drawing(Collections.singleton(SCALE / 2));
  List<int[]> cells = model.cells();
  assertArrayEquals(new int[]{20000 + 987, 18000 + 987},
   TemaOpeningResize.outside(FRAME, cells, Collections.singleton(2), SCALE, 20000, 18000));
  assertArrayEquals(new int[]{20000 + 987, 18000 + 402},
   TemaOpeningResize.outside(FRAME, cells, new java.util.LinkedHashSet<>(java.util.Arrays.asList(1, 2)), SCALE, 20000, 18000));
 }

 private static TemaOpeningResize.Drawing drawing(Set<Integer> columns) {
  Map<String, Integer> profiles = Collections.singletonMap("v" + SCALE / 2, 2);
  return new TemaOpeningResize.Drawing(20500, 17000, SCALE, columns, Collections.emptySet(), Collections.emptyList(), profiles);
 }

 private static TemaLayout layout(TemaOpeningResize.Drawing model, Set<Integer> doors) {
  List<TemaLayout.Record> records = new ArrayList<>();
  for (int[] record : model.records(2)) {
   records.add(new TemaLayout.Record(record[0], record[1], record[2], record[3], RECORD));
  }
  return new TemaLayout(model.width, model.height, FRAME, SCALE, model.cells(), records, doors);
 }
}
