package com.profilkesim.pro;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;
import org.junit.Test;

public final class TemaRecordPlacementTest {
 @Test public void horizontalRecordEndsAtSelectedBayAndHasItsOwnCutAndFace() {
  int scale = 1000;
  TreeSet<Integer> xs = new TreeSet<>(Collections.singleton(500)), ys = new TreeSet<>();
  List<int[]> before = TemaRecordPlacement.bays(xs, ys, Collections.emptyList(), scale);
  TemaRecordPlacement.Start start = TemaRecordPlacement.start(before, false, 4, 430, 10);
  assertNotNull(start);
  assertEquals(0, start.support);
  assertNull(TemaRecordPlacement.finish(before, start, 1000, 430, 18, 10, 2));
  int[] segment = TemaRecordPlacement.finish(before, start, 504, 438, 18, 10, 2);
  assertArrayEquals(new int[]{0, 430, 500, 430, 2}, segment);

  List<int[]> cells = TemaRecordPlacement.bays(xs, ys, Collections.singletonList(segment), scale);
  assertEquals(3, cells.size());
  assertArrayEquals(before.get(1), cells.get(1));
  TemaLayout.Section frame = new TemaLayout.Section("T.3001", 402);
  TemaLayout.Section upright = new TemaLayout.Section("T.3003", 590);
  TemaLayout.Section added = new TemaLayout.Section("T.3006", 765);
  List<TemaLayout.Record> records = Arrays.asList(
   new TemaLayout.Record(500, 0, 500, scale, upright),
   new TemaLayout.Record(segment[0], segment[1], segment[2], segment[3], added));
  TemaLayout layout = new TemaLayout(20000, 16000, new TemaLayout.Frame(frame), scale, cells, records, Collections.emptySet());
  TemaJoinery.Order order = new TemaJoinery.Order();
  order.layout = layout;
  order.width = layout.width;
  order.height = layout.height;
  order.quantity = 1;
  order.frameCode = frame.code;
  order.frameFace = frame.face;
  TemaJoinery.Plan plan = TemaJoinery.calculate(order);
  TemaJoinery.ProfileMark mark = plan.profileMarks.stream().filter(m -> m.code.equals(added.code)).findFirst().get();
  assertEquals(765, mark.face);
  assertEquals("Pay 76,5 mm", mark.allowanceText());
  assertEquals(layout.bays.get(0).width(), mark.length);
  assertEquals(layout.height - 2 * frame.face, layout.bays.get(1).height());
  assertEquals(mark.length, plan.cuts.stream().filter(cut -> cut.code.equals(added.code)).findFirst().get().length);
 }

 @Test public void sharedSupportOffersBothSidesButFinishesExactlyOneAndCannotBeRemovedWhileNeeded() {
  int scale = 1000;
  List<int[]> cells = TemaRecordPlacement.bays(new TreeSet<>(Collections.singleton(500)),
   new TreeSet<>(), Collections.emptyList(), scale);
  TemaRecordPlacement.Start start = TemaRecordPlacement.start(cells, false, 506, 470, 12);
  assertNotNull(start);
  assertEquals(Arrays.asList(0, 1000), TemaRecordPlacement.oppositeSupports(cells, start));
  assertNull(TemaRecordPlacement.finish(cells, start, 1010, 401, 20, 12, 0));
  int[] right = TemaRecordPlacement.finish(cells, start, 996, 465, 20, 12, 0);
  assertArrayEquals(new int[]{500, 470, 1000, 470, 0}, right);
  int[] support = {500, 0, 500, 1000, 1};
  assertTrue(TemaRecordPlacement.connected(Arrays.asList(support, right), scale));
  assertFalse(TemaRecordPlacement.connected(Collections.singletonList(right), scale));
  assertNull(TemaRecordPlacement.nearest(Collections.singletonList(right), 240, 470, 15, 15));
  assertSame(right, TemaRecordPlacement.nearest(Collections.singletonList(right), 620, 473, 15, 15));
 }

 @Test public void movingLocalRecordLeavesAnotherRecordOnSameAxisInPlace() {
  int[] selected = {500, 0, 500, 500, 0};
  int[] other = {500, 500, 500, 1000, 1};
  int[] dependent = {500, 350, 1000, 350, 0};
  TemaRecordPlacement.moveEndpoints(Arrays.asList(selected, other, dependent), selected, 0, 500, 580, 0, 500);
  assertArrayEquals(new int[]{580, 0, 580, 500, 0}, selected);
  assertArrayEquals(new int[]{500, 500, 500, 1000, 1}, other);
  assertArrayEquals(new int[]{580, 350, 1000, 350, 0}, dependent);
 }
}
