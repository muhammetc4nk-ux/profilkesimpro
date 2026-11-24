package com.profilkesim.pro;

/** Verifies a zero-shaped glyph inside a cell whose meaning is already numeric. */
final class ZeroCell {
 private ZeroCell() {}

 static boolean matches(CutTableGrid.Pixels pixels, int imageWidth, CutSheet.Bounds cell, boolean stacked) {
  int width = cell.right - cell.left - 4, height = cell.height();
  if (width < 8 || height < 10 || (long) width * height > 250_000) return false;
  int[] row = new int[imageWidth], histogram = new int[256];
  int[] levels = new int[width * height];
  for (int y = 0; y < height; y++) {
   pixels.readRow(cell.top + y, row);
   for (int x = 0; x < width; x++) {
    int color = row[cell.left + 2 + x];
    int gray = (3 * ((color >> 16) & 255) + 6 * ((color >> 8) & 255) + (color & 255)) / 10;
    levels[y * width + x] = gray;
    histogram[gray]++;
   }
  }
  int pale = 255, seen = 0;
  while (pale > 0 && (seen += histogram[pale]) < levels.length / 5) pale--;
  int threshold = Math.min(174, pale - 36);
  if (threshold < 42) return false;
  byte[] ink = new byte[levels.length];
  boolean[] divider = new boolean[height];
  for (int y = 0; y < height; y++) {
   int count = 0;
   for (int x = 0; x < width; x++) if (levels[y * width + x] <= threshold) count++;
   divider[y] = count * 5 >= width * 4;
  }
  for (int y = 0; y < height; y++) {
   if (divider[y] || (y > 0 && divider[y - 1]) || (y + 1 < height && divider[y + 1])) continue;
   for (int x = 0; x < width; x++) if (levels[y * width + x] <= threshold) ink[y * width + x] = 1;
  }

  int zeros = 0, priorX = -1, priorBottom = -1;
  int[] queue = new int[ink.length];
  for (int i = 0; i < ink.length; i++) {
   if (ink[i] != 1) continue;
   int count = flood(ink, width, height, i, (byte) 1, (byte) 2, queue);
   if (count < 5) continue;
   int left = width, right = -1, top = height, bottom = -1;
   for (int j = 0; j < count; j++) {
    int x = queue[j] % width, y = queue[j] / width;
    left = Math.min(left, x);
    right = Math.max(right, x);
    top = Math.min(top, y);
    bottom = Math.max(bottom, y);
   }
   int w = right - left + 1, h = bottom - top + 1, center = (left + right) / 2;
   if (left == 0 || right == width - 1 || top == 0 || bottom == height - 1
     || w < 4 || h < 6 || h > height * .78f || w < h * .38f || w > h * 1.10f
     || center < width * .19f || center > width * .81f
     || count < .25f * w * h || count > .82f * w * h
     || !hasCentralHole(ink, width, left, top, w, h)) return false;
   if (zeros > 0 && (!stacked || top <= priorBottom + 2 || Math.abs(priorX - center) > Math.max(3, w / 3))) return false;
   priorX = center;
   priorBottom = bottom;
   zeros++;
  }
  return zeros > 0;
 }

 private static boolean hasCentralHole(byte[] ink, int stride, int left, int top, int width, int height) {
  byte[] glyph = new byte[width * height];
  for (int y = 0; y < height; y++) {
   for (int x = 0; x < width; x++) {
    glyph[y * width + x] = ink[(top + y) * stride + left + x];
   }
  }
  int[] queue = new int[glyph.length];
  for (int y = 0; y < height; y++) {
   for (int x = 0; x < width; x++) {
    int i = y * width + x;
    if ((x == 0 || y == 0 || x == width - 1 || y == height - 1) && glyph[i] == 0) {
     flood(glyph, width, height, i, (byte) 0, (byte) 3, queue);
    }
   }
  }
  int center = (height / 2) * width + width / 2;
  if (glyph[center] != 0) return false;
  int count = flood(glyph, width, height, center, (byte) 0, (byte) 4, queue);
  if (count < Math.max(4, glyph.length / 10)) return false;
  int minY = height, maxY = 0;
  for (int i = 0; i < count; i++) {
   int y = queue[i] / width;
   minY = Math.min(minY, y);
   maxY = Math.max(maxY, y);
  }
  if (maxY - minY < height * .43f || Math.abs(minY - (height - maxY - 1)) > height * .26f) return false;
  for (byte pixel : glyph) if (pixel == 0) return false;
  return true;
 }

 private static int flood(byte[] pixels, int width, int height, int start, byte from, byte to, int[] queue) {
  int end = 0;
  queue[end++] = start;
  pixels[start] = to;
  for (int i = 0; i < end; i++) {
   int at = queue[i], x = at % width, y = at / width;
   if (x > 0) end = enqueue(pixels, at - 1, from, to, queue, end);
   if (x + 1 < width) end = enqueue(pixels, at + 1, from, to, queue, end);
   if (y > 0) end = enqueue(pixels, at - width, from, to, queue, end);
   if (y + 1 < height) end = enqueue(pixels, at + width, from, to, queue, end);
  }
  return end;
 }

 private static int enqueue(byte[] pixels, int index, byte from, byte to, int[] queue, int end) {
  if (pixels[index] != from) return end;
  pixels[index] = to;
  queue[end] = index;
  return end + 1;
 }
}
