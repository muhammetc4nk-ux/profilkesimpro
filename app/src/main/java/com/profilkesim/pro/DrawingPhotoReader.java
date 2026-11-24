package com.profilkesim.pro;

import android.graphics.Bitmap;
import android.graphics.Rect;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

final class DrawingPhotoReader {
 private DrawingPhotoReader() {}

 static DrawingDimensions scan(Bitmap original, TextRecognizer recognizer) throws InterruptedException, ExecutionException {
  Bitmap input = CutPhotoReader.thumbnail(original, 2100);
  try {
   return scanImage(input, recognizer);
  } finally {
   if (input != original) input.recycle();
  }
 }

 private static DrawingDimensions scanImage(Bitmap original, TextRecognizer recognizer) throws InterruptedException, ExecutionException {
  Scan first = read(original, recognizer, 0, original.getWidth(), original.getHeight());
  DrawingDimensions labelled = DrawingDimensions.read(first.text);
  if (complete(labelled)) return labelled;
  Bitmap enhanced = CutPhotoReader.localContrast(original);
  try {
   CutSheet.Bounds outline = frame(enhanced);
   DrawingDimensions result = DrawingDimensions.readSketch(first.text, first.lines, outline);
   if (complete(result)) return result;
   Scan second = read(enhanced, recognizer, 0, enhanced.getWidth(), enhanced.getHeight());
   result = result.withFallback(DrawingDimensions.readSketch(second.text, second.lines, outline));
   if (complete(result)) return result;
   if (outline == null && Math.abs(first.angle()) >= 2) {
    Bitmap flat = CutPhotoReader.rotate(enhanced, -first.angle());
    try {
     CutSheet.Bounds flattened = frame(flat);
     if (flattened != null) return result.withFallback(scanAligned(flat, flattened, recognizer));
    } finally {
     if (flat != enhanced) flat.recycle();
    }
   }
   return outline != null && result.height < 0
    ? result.withFallback(readSideNumbers(enhanced, outline, recognizer)) : result;
  } finally {
   enhanced.recycle();
  }
 }

 private static DrawingDimensions scanAligned(Bitmap image, CutSheet.Bounds outline, TextRecognizer recognizer)
  throws ExecutionException, InterruptedException {
  Scan scan = read(image, recognizer, 0, image.getWidth(), image.getHeight());
  DrawingDimensions dimensions = DrawingDimensions.readSketch(scan.text, scan.lines, outline);
  return dimensions.height < 0 ? dimensions.withFallback(readSideNumbers(image, outline, recognizer)) : dimensions;
 }

 private static DrawingDimensions readSideNumbers(Bitmap image, CutSheet.Bounds frame, TextRecognizer recognizer)
  throws ExecutionException, InterruptedException {
  List<CutSheet.Word> lines = new ArrayList<>();
  int margin = Math.max(30, (frame.right - frame.left) / 4);
  for (int side : new int[]{-1, 1}) {
   int left = side < 0 ? Math.max(0, frame.left - margin) : frame.right;
   int right = side < 0 ? frame.left : Math.min(image.getWidth(), frame.right + margin);
   if (right - left < 16) continue;
   Bitmap strip = Bitmap.createBitmap(image, left, frame.top, right - left, frame.height());
   try {
    for (int direction : new int[]{90, -90}) {
     Bitmap turned = CutPhotoReader.rotate(strip, direction);
     try {
      Scan sideScan = read(turned, recognizer, direction, strip.getWidth(), strip.getHeight());
      for (CutSheet.Word word : sideScan.lines) {
       CutSheet.Bounds box = word.box;
       lines.add(new CutSheet.Word(word.text, new CutSheet.Bounds(
        box.left + left, box.top + frame.top, box.right + left, box.bottom + frame.top), word.confidence));
      }
     } finally {
      if (turned != strip) turned.recycle();
     }
    }
   } finally {
    if (strip != image) strip.recycle();
   }
  }
  DrawingDimensions side = DrawingDimensions.readSketch("", lines, frame);
  return new DrawingDimensions(-1, side.height);
 }

 private static CutSheet.Bounds frame(Bitmap image) {
  Bitmap small = CutPhotoReader.thumbnail(image, 805);
  try {
   int width = small.getWidth(), height = small.getHeight();
   CutSheet.Bounds box = DrawingFrame.find((y, row) -> small.getPixels(row, 0, width, 0, y, width, 1), width, height);
   if (box == null) return null;
   return new CutSheet.Bounds(box.left * image.getWidth() / width, box.top * image.getHeight() / height,
    box.right * image.getWidth() / width, box.bottom * image.getHeight() / height);
  } finally {
   if (small != image) small.recycle();
  }
 }

 private static boolean complete(DrawingDimensions dimensions) { return dimensions.width > 0 && dimensions.height > 0; }

 private static Scan read(Bitmap image, TextRecognizer recognizer, int rotation, int width, int height)
  throws InterruptedException, ExecutionException {
  Text text = Tasks.await(recognizer.process(InputImage.fromBitmap(image, 0)));
  Scan result = new Scan(text.getText());
  for (Text.TextBlock block : text.getTextBlocks()) for (Text.Line line : block.getLines()) {
   Rect bounds = line.getBoundingBox();
   android.graphics.Point[] corners = line.getCornerPoints();
   if (bounds == null || bounds.isEmpty() || Math.abs(line.getAngle()) > 18
     || (corners != null && corners.length >= 2 && corners[1].x <= corners[0].x)) continue;
   float confidence = 1;
   for (Text.Element item : line.getElements()) confidence = Math.min(confidence, item.getConfidence());
   if (rotation == 0 && line.getText().length() >= 3 && confidence >= .55f) result.angles.add(line.getAngle());
   CutSheet.Bounds box;
   if (rotation == 90) {
    box = new CutSheet.Bounds(bounds.top, height - bounds.right, bounds.bottom, height - bounds.left);
   } else if (rotation == -90) {
    box = new CutSheet.Bounds(width - bounds.bottom, bounds.left, width - bounds.top, bounds.right);
   } else box = new CutSheet.Bounds(bounds.left, bounds.top, bounds.right, bounds.bottom);
   result.lines.add(new CutSheet.Word(line.getText(), box, confidence));
  }
  return result;
 }

 private static final class Scan {
  final String text;
  final List<CutSheet.Word> lines = new ArrayList<>();
  final List<Float> angles = new ArrayList<>();
  Scan(String text) { this.text = text; }
  float angle() {
   if (angles.size() < 2) return 0;
   Collections.sort(angles);
   return angles.get(angles.size() / 2);
  }
 }
}
