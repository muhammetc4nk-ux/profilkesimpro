package com.profilkesim.pro;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class CutDocumentPages {
 static final class Page {
  final Uri uri;
  final String label;

  Page(Uri uri, String label) {
   this.uri = uri;
   this.label = label;
  }
 }

 static List<Page> read(Context context, Uri source) throws IOException {
  ContentResolver resolver = context.getContentResolver();
  String label = documentName(resolver, source);
  if (!"application/pdf".equalsIgnoreCase(resolver.getType(source)) && !label.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
   return java.util.Collections.singletonList(new Page(source, null));
  }
  File document = File.createTempFile("cut_document_", ".pdf", context.getCacheDir());
  List<Page> pages = new ArrayList<>();
  try {
   try (InputStream input = resolver.openInputStream(source); FileOutputStream output = new FileOutputStream(document)) {
    if (input == null) throw new IOException("PDF içeriği açılamadı.");
    byte[] buffer = new byte[16_384];
    for (int read; (read = input.read(buffer)) != -1;) output.write(buffer, 0, read);
   }
   try (ParcelFileDescriptor descriptor = ParcelFileDescriptor.open(document, ParcelFileDescriptor.MODE_READ_ONLY);
        PdfRenderer pdf = new PdfRenderer(descriptor)) {
    for (int i = 0; i < pdf.getPageCount(); i++) {
     try (PdfRenderer.Page page = pdf.openPage(i)) {
      pages.add(new Page(render(context, page), label + " · Sayfa " + (i + 1)));
     }
    }
   }
   if (pages.isEmpty()) throw new IOException("PDF boş.");
   return pages;
  } catch (IOException | RuntimeException error) {
   for (Page page : pages) deleteRendered(context, page.uri);
   throw error;
  } finally {
   document.delete();
  }
 }

 private static Uri render(Context context, PdfRenderer.Page page) throws IOException {
  float scale = 3000f / Math.max(page.getWidth(), page.getHeight());
  Bitmap image = Bitmap.createBitmap(Math.max(1, Math.round(page.getWidth() * scale)),
   Math.max(1, Math.round(page.getHeight() * scale)), Bitmap.Config.ARGB_8888);
  File target = null;
  try {
   image.eraseColor(Color.WHITE);
   page.render(image, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
   target = File.createTempFile("cut_pdf_page_", ".jpg", context.getCacheDir());
   try (FileOutputStream output = new FileOutputStream(target)) {
    if (!image.compress(Bitmap.CompressFormat.JPEG, 95, output)) throw new IOException("PDF sayfası kaydedilemedi.");
   }
   return Uri.fromFile(target);
  } catch (IOException | RuntimeException error) {
   if (target != null) target.delete();
   throw error;
  } finally {
   image.recycle();
  }
 }

 static void deleteRendered(Context context, Uri uri) {
  if (uri == null || !"file".equals(uri.getScheme()) || uri.getPath() == null) return;
  File path = new File(uri.getPath());
  if (context.getCacheDir().equals(path.getParentFile()) && path.getName().startsWith("cut_pdf_page_")) path.delete();
 }

 private static String documentName(ContentResolver resolver, Uri source) {
  String last = source.getLastPathSegment();
  if (!"content".equals(source.getScheme())) return last == null ? "Kesim PDF’i" : last;
  try (android.database.Cursor cursor = resolver.query(source, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
   if (cursor != null && cursor.moveToFirst() && cursor.getString(0) != null) return cursor.getString(0);
  } catch (IllegalArgumentException | SecurityException ignored) { }
  return last == null ? "Kesim PDF’i" : last;
 }
}
