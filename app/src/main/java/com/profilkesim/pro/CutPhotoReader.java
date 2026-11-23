package com.profilkesim.pro;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.Log;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

final class CutPhotoReader {
 private static final String TAG = "ProfilKesimOCR";
 private static final Paint FILTER = new Paint(Paint.FILTER_BITMAP_FLAG);

 static final class Result {
  final List<MainActivity.CutRow> rows;
  final Bitmap original, preview;
  final List<RectF> highlights;

  Result(List<MainActivity.CutRow> rows, Bitmap original, Bitmap preview, List<RectF> highlights) {
   this.rows = rows;
   this.original = original;
   this.preview = preview;
   this.highlights = highlights;
  }
 }

 private static final class Candidate {
  final List<CutSheet.Row> rows;
  final TableOcrRegion region;

  Candidate(List<CutSheet.Row> rows, TableOcrRegion region) {
   this.rows = rows;
   this.region = region;
  }

  int score() {
   int score = 0;
   for (CutSheet.Row row : rows) score += 3 + (row.length > 0 ? 1 : 0) + (row.quantity >= 0 ? 1 : 0);
   return score;
  }

  boolean complete() {
   if (rows.isEmpty()) return false;
   for (CutSheet.Row row : rows) if (row.length < 0 || row.quantity < 0) return false;
   return true;
  }
 }

 private static final class Words {
  final List<CutSheet.Word> elements = new ArrayList<>();
  final List<Float> tilt = new ArrayList<>();

  float angle() {
   if (tilt.size() < 4) return 0;
   Collections.sort(tilt);
   float median = tilt.get(tilt.size() / 2);
   int agreeing = 0;
   for (float angle : tilt) if (Math.abs(angle - median) < 3) agreeing++;
   return agreeing * 3 >= tilt.size() * 2 && Math.abs(median) >= 1.1f && Math.abs(median) <= 16 ? median : 0;
  }
 }

 private CutPhotoReader() {}

 static Result scan(Bitmap original, InputImage file, RectF crop, TextRecognizer recognizer) throws InterruptedException {
  Bitmap source = original == null || crop == null ? original : crop(original, crop);
  Bitmap thumb = thumbnail(original, 840);
  if (source == null) {
   Words words = words(file, 0, 0, 1, recognizer);
   int height = 0;
   for (CutSheet.Word word : words.elements) height = Math.max(height, word.box.bottom + 1);
   return result(new Candidate(CutSheet.read(words.elements, 0, height), null), null, null, recognizer);
  }
  for (int turn = 0; turn < 4; turn++) {
   Bitmap oriented = turn == 0 ? source : rotate(source, turn * 90);
   CutTableGrid.Pixels pixels = pixels(oriented);
   TableOcrRegion region = TableOcrRegion.find(pixels, oriented.getWidth(), oriented.getHeight());
   Candidate isolated = isolated(oriented, region, recognizer);
   Words raw = words(InputImage.fromBitmap(oriented, 0), 0, 0, 1, recognizer);
   Candidate clean = candidate(raw, oriented);
   Candidate best = prefer(isolated, clean);
   float skew = raw.angle();
   if (skew != 0 && !best.complete()) {
    Bitmap straight = rotate(oriented, -skew);
    Candidate leveled = candidate(words(InputImage.fromBitmap(straight, 0), 0, 0, 1, recognizer), straight);
    if (leveled.score() == 0) {
     TableOcrRegion levelRegion = TableOcrRegion.find(pixels(straight), straight.getWidth(), straight.getHeight());
     leveled = isolated(straight, levelRegion, recognizer);
    }
    if (leveled.score() > best.score()) return result(leveled, thumb, straight, recognizer);
   }
   if (best.score() > 0) return result(best, thumb, oriented, recognizer);
   if (turn == 0 || turn == 2) {
    Bitmap highContrast = contrast(oriented);
    Candidate enhanced = candidate(words(InputImage.fromBitmap(highContrast, 0), 0, 0, 1, recognizer), oriented);
    highContrast.recycle();
    if (enhanced.score() > 0) return result(enhanced, thumb, oriented, recognizer);
   }
  }
  return new Result(Collections.emptyList(), thumb, thumb, Collections.emptyList());
 }

 private static Candidate prefer(Candidate first, Candidate second) {
  if (!first.rows.isEmpty() && first.rows.size() == second.rows.size()) {
   List<CutSheet.Row> merged = new ArrayList<>();
   for (int i = 0; i < first.rows.size(); i++) {
    CutSheet.Row a = first.rows.get(i), b = second.rows.get(i);
    if ((!a.code.isEmpty() && !b.code.isEmpty() && !a.code.equals(b.code))
      || Math.abs(a.identity.y() - b.identity.y()) > Math.max(a.identity.height(), b.identity.height())
      || !(CutSheet.identity(a.name).equals(CutSheet.identity(b.name)) || (!a.code.isEmpty() && a.code.equals(b.code)))) {
     return first.score() >= second.score() ? first : second;
    }
    CutSheet.Row row = new CutSheet.Row(a.name, a.code.isEmpty() ? b.code : a.code,
     agreement(a.length, b.length), agreement(a.quantity, b.quantity), a.identity);
    row.section = a.section == null ? b.section : a.section;
    row.lengthCell = a.lengthCell;
    row.quantityCell = a.quantityCell;
    if (a.length >= 0 && b.length >= 0 && a.length != b.length) row.lengthCell = null;
    if (a.quantity >= 0 && b.quantity >= 0 && a.quantity != b.quantity) row.quantityCell = null;
    merged.add(row);
   }
   return new Candidate(merged, first.region);
  }
  return first.score() >= second.score() ? first : second;
 }

 private static int agreement(int a, int b) { return a < 0 ? b : b < 0 || a == b ? a : -1; }

 private static Candidate candidate(Words words, Bitmap image) {
  return new Candidate(CutSheet.read(words.elements, image.getWidth(), image.getHeight(), pixels(image)), null);
 }

 private static Candidate isolated(Bitmap image, TableOcrRegion region, TextRecognizer recognizer) throws InterruptedException {
  if (region == null) return new Candidate(Collections.emptyList(), null);
  List<CutSheet.Word> all = new ArrayList<>();
  CutTableGrid.Pixels pixels = pixels(image);
  for (int column = 0; column < 3; column++) {
   CutSheet.Bounds box = new CutSheet.Bounds(region.columnEdge(column), region.box.top,
    region.columnEdge(column + 1), region.box.bottom);
   all.addAll(extract(image, pixels, region, box, 1.35f, recognizer));
  }
  return new Candidate(CutSheet.read(all, image.getWidth(), image.getHeight(), pixels, region), region);
 }

 private static List<CutSheet.Word> extract(Bitmap original, CutTableGrid.Pixels pixels, TableOcrRegion region,
                                            CutSheet.Bounds box, float scale, TextRecognizer recognizer) throws InterruptedException {
  int width = box.right - box.left, height = box.height();
  int padding = 18;
  Bitmap prepared = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
  if (region == null) {
   new Canvas(prepared).drawBitmap(original, -box.left, -box.top, FILTER);
  } else {
   int[] row = new int[original.getWidth()];
   for (int y = box.top; y < box.bottom; y++) {
    pixels.readRow(y, row);
    region.prepareRow(y, row);
    prepared.setPixels(row, box.left, row.length, 0, y - box.top, width, 1);
   }
  }
  Bitmap padded = Bitmap.createBitmap(Math.round(width * scale) + 2 * padding,
   Math.round(height * scale) + 2 * padding, Bitmap.Config.ARGB_8888);
  Canvas canvas = new Canvas(padded);
  canvas.drawColor(Color.WHITE);
  canvas.drawBitmap(prepared, null, new RectF(padding, padding, padding + width * scale, padding + height * scale), FILTER);
  prepared.recycle();
  try {
   return words(InputImage.fromBitmap(padded, 0), box.left - padding / scale, box.top - padding / scale, scale, recognizer).elements;
  } finally {
   padded.recycle();
  }
 }

 private static Words words(InputImage image, float x, float y, float scale, TextRecognizer recognizer) throws InterruptedException {
  Words words = new Words();
  try {
   Text recognized = Tasks.await(recognizer.process(image));
   for (Text.TextBlock block : recognized.getTextBlocks()) for (Text.Line line : block.getLines()) {
    android.graphics.Point[] corners = line.getCornerPoints();
    if (Math.abs(line.getAngle()) > 22 || (corners != null && corners.length >= 2 && corners[1].x <= corners[0].x)) continue;
    if (line.getText().length() >= 5) words.tilt.add(line.getAngle());
    for (Text.Element word : line.getElements()) {
     android.graphics.Rect b = word.getBoundingBox();
     if (b == null || b.isEmpty()) continue;
     words.elements.add(new CutSheet.Word(word.getText(), new CutSheet.Bounds(
      Math.round(x + b.left / scale), Math.round(y + b.top / scale),
      Math.round(x + b.right / scale), Math.round(y + b.bottom / scale)), word.getConfidence()));
    }
   }
  } catch (ExecutionException | RuntimeException error) {
   Log.w(TAG, "Text recognition pass failed", error);
  }
  return words;
 }

 private static Result result(Candidate candidate, Bitmap original, Bitmap image, TextRecognizer recognizer) throws InterruptedException {
  List<MainActivity.CutRow> rows = new ArrayList<>();
  List<RectF> highlights = new ArrayList<>();
  CutTableGrid.Pixels pixels = image == null ? null : pixels(image);
  for (CutSheet.Row row : candidate.rows) {
   Bitmap icon = null;
   if (valid(row.section, image)) {
    icon = thumbnail(Bitmap.createBitmap(image, row.section.left, row.section.top,
     row.section.right - row.section.left, row.section.height()), 300);
    highlights.add(normalized(row.section, image));
   }
   int length = row.length < 0 ? retry(image, pixels, candidate.region, row.lengthCell, false, recognizer) : row.length;
   int count = row.quantity < 0 ? retry(image, pixels, candidate.region, row.quantityCell, true, recognizer) : row.quantity;
   if (valid(row.lengthCell, image)) highlights.add(normalized(row.lengthCell, image));
   if (valid(row.quantityCell, image)) highlights.add(normalized(row.quantityCell, image));
   rows.add(new MainActivity.CutRow(row.code, row.name, length, count, icon));
  }
  return new Result(rows, original, thumbnail(image, 840), highlights);
 }

 private static int retry(Bitmap image, CutTableGrid.Pixels pixels, TableOcrRegion region, CutSheet.Bounds box,
                          boolean quantity, TextRecognizer recognizer) throws InterruptedException {
  if (!valid(box, image)) return -1;
  float scale = Math.max(1.5f, Math.min(4f, 105f / box.height()));
  int raw = CutSheet.number(extract(image, pixels, null, box, scale, recognizer), quantity);
  if (region == null) return raw;
  int clean = CutSheet.number(extract(image, pixels, region, box, scale, recognizer), quantity);
  return agreement(raw, clean);
 }

 private static boolean valid(CutSheet.Bounds box, Bitmap image) {
  return box != null && image != null && box.left >= 0 && box.top >= 0 && box.right <= image.getWidth()
   && box.bottom <= image.getHeight() && box.height() > 1 && box.right - box.left > 1;
 }

 private static RectF normalized(CutSheet.Bounds box, Bitmap image) {
  float width = image.getWidth(), height = image.getHeight();
  return new RectF(box.left / width, box.top / height, box.right / width, box.bottom / height);
 }

 private static CutTableGrid.Pixels pixels(Bitmap image) {
  int width = image.getWidth();
  return (y, row) -> image.getPixels(row, 0, width, 0, y, width, 1);
 }

 private static Bitmap contrast(Bitmap image) {
  Bitmap enhanced = Bitmap.createBitmap(image.getWidth(), image.getHeight(), Bitmap.Config.ARGB_8888);
  android.graphics.ColorMatrix matrix = new android.graphics.ColorMatrix();
  matrix.setSaturation(0);
  matrix.postConcat(new android.graphics.ColorMatrix(new float[]{1.7f,0,0,0,-77, 0,1.7f,0,0,-77, 0,0,1.7f,0,-77, 0,0,0,1,0}));
  Paint contrast = new Paint(FILTER);
  contrast.setColorFilter(new android.graphics.ColorMatrixColorFilter(matrix));
  new Canvas(enhanced).drawBitmap(image, 0, 0, contrast);
  return enhanced;
 }

 private static Bitmap rotate(Bitmap image, float angle) {
  Matrix rotation = new Matrix();
  rotation.setRotate(angle);
  return Bitmap.createBitmap(image, 0, 0, image.getWidth(), image.getHeight(), rotation, true);
 }

 static Bitmap thumbnail(Bitmap image, int longest) {
  if (image == null) return null;
  float scale = Math.min(1f, (float) longest / Math.max(image.getWidth(), image.getHeight()));
  return Bitmap.createScaledBitmap(image, Math.max(1, Math.round(image.getWidth() * scale)),
   Math.max(1, Math.round(image.getHeight() * scale)), true);
 }

 private static Bitmap crop(Bitmap image, RectF region) {
  int x = Math.max(0, Math.round(region.left * image.getWidth()));
  int y = Math.max(0, Math.round(region.top * image.getHeight()));
  int width = Math.min(image.getWidth(), Math.round(region.right * image.getWidth())) - x;
  int height = Math.min(image.getHeight(), Math.round(region.bottom * image.getHeight())) - y;
  if (width < 24 || height < 24) throw new IllegalArgumentException("Kesim tablosu alanı çok küçük.");
  return Bitmap.createBitmap(image, x, y, width, height);
 }
}
