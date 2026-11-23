package com.profilkesim.pro;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public final class CutSheetTest {
 @Test public void usesOnlyTheCuttingTableAndRetainsCodeFreePreview() {
  List<CutSheet.Word> form = Arrays.asList(
   word("Balkon eni", 190, 30), word("5420", 500, 30), word("12", 620, 30),
   word("Kesit", 96, 95), word("Profil adı", 226, 95), word("Profil kodu", 370, 95),
   word("Kesim ölçüsü", 500, 95), word("Adet", 620, 95), word("Stok boyu", 735, 95),
   word("Yan dikme", 220, 135), word("B15083", 370, 135), word("1250", 500, 135), word("2", 620, 135),
   word("6000", 735, 135),
   word("42×36 Tek ray", 220, 175), word("850", 500, 175), word("4", 620, 175),
   word("Genel toplam", 220, 215), word("5900", 500, 215), word("6", 620, 215),
   word("Balkon yüksekliği", 226, 250), word("2700", 500, 250), word("8", 620, 250));
  List<CutSheet.Row> rows = CutSheet.read(form, 800, 300);
  assertEquals(2, rows.size());
  assertEquals("B15083", rows.get(0).code);
  assertEquals("Yan dikme", rows.get(0).name);
  assertEquals(1250, rows.get(0).length);
  assertEquals(2, rows.get(0).quantity);
  CutSheet.Row codeFree = rows.get(1);
  assertEquals("", codeFree.code);
  assertEquals("42×36 Tek ray", codeFree.name);
  assertEquals(850, codeFree.length);
  assertEquals(4, codeFree.quantity);
  assertNotNull(codeFree.section);
  assertTrue(codeFree.section.right < codeFree.identity.left);
  assertTrue(codeFree.section.top >= rows.get(0).section.bottom);
 }

 @Test public void leavesUncertainAndConflictingNumbersEmptyWithoutBorrowingOtherCells() {
  List<CutSheet.Word> form = new ArrayList<>(header());
  form.addAll(Arrays.asList(
   word("Ray", 210, 110), word("12O0", 500, 110), word("0", 620, 110), word("6000", 735, 110),
   word("Kasa", 210, 145), word("0", 500, 145), word("6000", 735, 145),
   word("Kapak", 210, 180), word("800", 480, 180), word("900", 520, 180),
   new CutSheet.Word("3", new CutSheet.Bounds(616, 174, 624, 186), .35f),
   word("Kanat", 210, 215), word("900 mm", 500, 215), word("3 adet", 620, 215),
   word("Profil", 210, 250), word("1850", 500, 250), word("7", 735, 250)));
  List<CutSheet.Row> rows = CutSheet.read(form, 800, 300);
  assertEquals(5, rows.size());
  assertEquals(-1, rows.get(0).length);
  assertEquals(0, rows.get(0).quantity);
  assertEquals(0, rows.get(1).length);
  assertEquals(-1, rows.get(1).quantity);
  assertEquals(-1, rows.get(2).length);
  assertEquals(-1, rows.get(2).quantity);
  assertEquals(900, rows.get(3).length);
  assertEquals(3, rows.get(3).quantity);
  assertEquals(-1, rows.get(4).quantity);
 }

 @Test public void neverTreatsSystemMeasurementsOrUnheadedNumbersAsACutList() {
  List<CutSheet.Word> systemTable = Arrays.asList(
   word("Açıklama", 225, 70), word("Sistem ölçüsü", 500, 70), word("Adet", 620, 70),
   word("P 4", 225, 105), word("3600", 500, 105), word("2", 620, 105),
   word("Kasa profili B15082 1450 x 2", 255, 140));
  assertTrue(CutSheet.read(systemTable, 800, 300).isEmpty());
 }

 @Test public void sameParserCanReadTwoPhotosWithoutCarryingNumbersBetweenThem() {
  List<CutSheet.Word> first = new ArrayList<>(header());
  first.addAll(Arrays.asList(word("Kasa B15082", 225, 110), word("1100", 500, 110), word("2", 620, 110)));
  List<CutSheet.Word> second = new ArrayList<>(header());
  second.addAll(Arrays.asList(word("Kasa B15082", 225, 110), word("5", 620, 110)));
  CutSheet.Row rowOne = CutSheet.read(first, 800, 300).get(0);
  CutSheet.Row rowTwo = CutSheet.read(second, 800, 300).get(0);
  assertEquals("B15082", rowOne.code);
  assertEquals("Kasa", rowOne.name);
  assertEquals(1100, rowOne.length);
  assertEquals(2, rowOne.quantity);
  assertEquals(-1, rowTwo.length);
  assertEquals(5, rowTwo.quantity);
  assertNotNull(rowTwo.section);
 }

 private static List<CutSheet.Word> header() {
  return Arrays.asList(word("Profil adı", 232, 70), word("Kesim ölçüsü", 500, 70),
   word("Adet", 620, 70), word("Stok boyu", 735, 70));
 }

 private static CutSheet.Word word(String text, int x, int y) {
  int half = text.length() * 3;
  return new CutSheet.Word(text, new CutSheet.Bounds(x - half, y - 6, x + half, y + 6), .99f);
 }
}
