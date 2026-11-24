package com.profilkesim.pro;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public final class DrawingSketchTest {
 @Test public void readsBarePairAndKeepsItsSharedUnit() {
  DrawingDimensions dimensions = DrawingDimensions.readSketch("245 × 153 cm", Collections.singletonList(
   label("245 × 153 cm", 196, 72, 274, 90)), null);
  assertEquals(24500, dimensions.width);
  assertEquals(15300, dimensions.height);
 }

 @Test public void assignsUnlabelledNumbersToTheOutsideEdgesOfASketch() {
  int width = 500, height = 650;
  int[][] image = new int[height][width];
  for (int y = 0; y < height; y++) {
   Arrays.fill(image[y], 0xffffffff);
   if (y == 180 || y == 460) Arrays.fill(image[y], 95, 409, 0xff1c3657);
   if (y >= 180 && y <= 460) {
    for (int x : new int[]{95, 251, 408}) image[y][x] = 0xff1c3657;
   }
  }
  CutSheet.Bounds frame = DrawingFrame.find((y, row) -> System.arraycopy(image[y], 0, row, 0, width), width, height);
  assertNotNull(frame);
  assertTrue(frame.right - frame.left > 300);
  DrawingDimensions dimensions = DrawingDimensions.readSketch("2000\n1500\n4200", Arrays.asList(
   label("2000", 215, 140, 290, 157), label("1500", 426, 305, 465, 325), label("4200", 62, 54, 111, 76),
   label("625", 215, 300, 256, 318)), frame);
  assertEquals(20000, dimensions.width);
  assertEquals(15000, dimensions.height);
 }

 @Test public void treatsSeparatePanelNumbersAndConflictingMeasurementsAsUncertain() {
  CutSheet.Bounds frame = new CutSheet.Bounds(100, 180, 410, 460);
  DrawingDimensions panels = DrawingDimensions.readSketch("", Arrays.asList(
   label("500", 163, 137, 196, 155), label("500", 306, 137, 339, 155),
   label("1500", 425, 305, 461, 325)), frame);
  assertEquals(-1, panels.width);
  assertEquals(15000, panels.height);
  DrawingDimensions labelled = DrawingDimensions.read("En 2400 mm\nEn 2100 mm");
  assertEquals(-1, labelled.withFallback(new DrawingDimensions(24000, 15000)).width);
  assertFalse(DrawingDimensions.readSketch("Cam 1000 × 1500", Collections.singletonList(
   label("Cam 1000 × 1500", 160, 144, 340, 157)), frame).hasValues());
 }

 private static CutSheet.Word label(String text, int l, int t, int r, int b) {
  return new CutSheet.Word(text, new CutSheet.Bounds(l, t, r, b), .95f);
 }
}
