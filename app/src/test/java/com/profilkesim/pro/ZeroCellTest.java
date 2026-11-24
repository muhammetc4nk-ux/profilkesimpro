package com.profilkesim.pro;

import static org.junit.Assert.*;

import java.util.Arrays;
import org.junit.Test;

public final class ZeroCellTest {
 @Test public void recognizesAnIsolatedZeroButNotAnEmptyCellOrOpenLetter() {
  int[][] image = blank();
  assertFalse(matches(image, false));
  oval(image, 40, 27);
  assertTrue(matches(image, false));
  for (int y = 22; y < 28; y++) for (int x = 43; x < 48; x++) image[y][x] = 0xffffffff;
  assertFalse(matches(image, false));
 }

 @Test public void readsZeroSubrowsWithoutTreatingOtherDigitsAsZero() {
  int[][] image = blank();
  oval(image, 40, 22);
  oval(image, 40, 68);
  Arrays.fill(image[45], 0xff202020);
  assertTrue(matches(image, true));
  assertFalse(matches(image, false));
  for (int y = 51; y < 74; y++) image[y][58] = 0xff202020;
  assertFalse(matches(image, true));
 }

 @Test public void rejectsEightAndRejectsZeroBesideAnUnclearDigit() {
  int[][] image = blank();
  oval(image, 40, 28);
  for (int x = 34; x <= 46; x++) image[28][x] = 0xff202020;
  assertFalse(matches(image, false));
  image = blank();
  oval(image, 36, 28);
  for (int y = 22; y < 32; y++) image[y][48] = 0xff202020;
  assertFalse(matches(image, false));
 }

 private static boolean matches(int[][] image, boolean stacked) {
  return ZeroCell.matches((y, row) -> System.arraycopy(image[y], 0, row, 0, 80),
   80, new CutSheet.Bounds(0, 0, 80, 90), stacked);
 }

 private static int[][] blank() {
  int[][] image = new int[90][80];
  for (int[] row : image) Arrays.fill(row, 0xffffffff);
  return image;
 }

 private static void oval(int[][] image, int centerX, int centerY) {
  for (int y = centerY - 8; y <= centerY + 8; y++) {
   for (int x = centerX - 6; x <= centerX + 6; x++) {
    double outer = Math.pow((x - centerX) / 6.0, 2) + Math.pow((y - centerY) / 8.0, 2);
    double inner = Math.pow((x - centerX) / 3.2, 2) + Math.pow((y - centerY) / 5.2, 2);
    if (outer <= 1 && inner >= 1) image[y][x] = 0xff202020;
   }
  }
 }
}
