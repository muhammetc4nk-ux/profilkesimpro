package com.profilkesim.pro;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

public final class TemaDoorAlignmentTest {
 private static final int SCALE = 1_000_000;
 private static final TemaLayout.Section STANDARD = new TemaLayout.Section("T.3003", 590);
 private static final TemaLayout.Section WIDE = new TemaLayout.Section("T.3006", 765);
 private static final TemaLayout.Frame FRAME = new TemaLayout.Frame(new TemaLayout.Section("T.3001", 402));

 @Test public void alignsTheNearestOtherRecordWithItsOwnFaceAndMovesItsDependentEnd() {
  List<int[]> local = Arrays.asList(new int[]{0, 400000, 500000, 400000, 0},
   new int[]{500000, 510000, SCALE, 510000, 1},
   new int[]{500000, 180000, SCALE, 180000, 0},
   new int[]{740000, 510000, 740000, SCALE, 0});
  TemaOpeningResize.Drawing source = drawing(Collections.singleton(500000), local);
  Set<Integer> doors = Collections.singleton(3);
  TemaOpeningResize.Resolver resolver = model -> layout(model, doors);
  TemaLayout before = resolver.layout(source);

  assertEquals(Collections.singletonList(2), TemaDoorAlignment.references(source, before));
  TemaDoorAlignment.Result alignment = TemaDoorAlignment.align(source, 2, deductions(before), resolver);
  TemaLayout after = resolver.layout(alignment.drawing);

  assertEquals(1, alignment.moved);
  assertArrayEquals(local.get(0), alignment.drawing.local.get(0));
  assertArrayEquals(local.get(2), alignment.drawing.local.get(2));
  assertEquals(before.bays.get(2).top, after.bays.get(2).top);
  assertEquals(before.bays.get(2).height(), after.bays.get(2).height());
  assertEquals(765, after.records.get(1).section.face);
  int alignedAxis = alignment.drawing.local.get(1)[1];
  assertEquals(alignedAxis, alignment.drawing.local.get(3)[1]);
  assertEquals(before.bays.get(2).top, after.y(alignedAxis) + 383);
  assertEquals(510000, source.local.get(1)[1]);
 }

 @Test public void keepsAllDoorLintelsWhenChoosingBetweenTwoDoors() {
  Set<Integer> columns = new LinkedHashSet<>(Arrays.asList(330000, 670000));
  List<int[]> local = Arrays.asList(new int[]{0, 430000, 330000, 430000, 0},
   new int[]{330000, 485000, 670000, 485000, 0}, new int[]{670000, 540000, SCALE, 540000, 0});
  TemaOpeningResize.Drawing source = drawing(columns, local);
  Set<Integer> doors = new LinkedHashSet<>(Arrays.asList(4, 6));
  TemaOpeningResize.Resolver resolver = model -> layout(model, doors);
  TemaLayout before = resolver.layout(source);

  assertEquals(Arrays.asList(3, 5), TemaDoorAlignment.references(source, before));
  TemaDoorAlignment.Result alignment = TemaDoorAlignment.align(source, 5, deductions(before), resolver);
  TemaLayout after = resolver.layout(alignment.drawing);

  assertEquals(1, alignment.moved);
  assertArrayEquals(local.get(0), alignment.drawing.local.get(0));
  assertArrayEquals(local.get(2), alignment.drawing.local.get(2));
  assertEquals(before.bays.get(3).top, after.bays.get(3).top);
  assertEquals(before.bays.get(5).top, after.bays.get(5).top);
  assertEquals(before.bays.get(5).top, after.bays.get(4).top);
 }

 @Test public void stockAndDrawingDisplayUseNamesAndCanIdentifyBeadsWithoutACode() {
  assertEquals("Dar kasa · 42 mm", TemaCatalog.Series.ALDOKS.displayOf("T.3001"));
  assertEquals("Standart kayıt · 42 mm", TemaCatalog.Series.ALDOKS.displayOf("T.3003"));
  assertEquals("Geniş kasa · 58,5 mm", TemaCatalog.Series.ALDOKS.displayOf("T.3004"));
  assertEquals("Özel profil", TemaCatalog.Series.C60.nameOf("M123"));
  assertTrue(TemaCatalog.Series.ALDOKS.isBead("T.3021"));
  assertTrue(TemaCatalog.Series.C60.isBead("", "Cam çıtası"));
  assertFalse(TemaCatalog.Series.C60.isBead("T.3101", "Standart kutu"));
 }

 private static int[] deductions(TemaLayout layout) {
  int[] result = new int[layout.bays.size()];
  Arrays.fill(result, TemaJoinery.GLASS_DEDUCTION);
  return result;
 }

 private static TemaOpeningResize.Drawing drawing(Set<Integer> columns, List<int[]> local) {
  return new TemaOpeningResize.Drawing(30000, 26000, SCALE, columns, Collections.emptySet(), local, Collections.emptyMap());
 }

 private static TemaLayout layout(TemaOpeningResize.Drawing model, Set<Integer> doors) {
  List<TemaLayout.Record> records = new ArrayList<>();
  for (int[] line : model.records(0)) {
   records.add(new TemaLayout.Record(line[0], line[1], line[2], line[3], line[4] == 0 ? STANDARD : WIDE));
  }
  return new TemaLayout(model.width, model.height, FRAME, SCALE, model.cells(), records, doors);
 }
}
