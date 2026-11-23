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
  assertEquals(12500, rows.get(0).length);
  assertEquals(2, rows.get(0).quantity);
  CutSheet.Row codeFree = rows.get(1);
  assertEquals("", codeFree.code);
  assertEquals("42×36 Tek ray", codeFree.name);
  assertEquals(8500, codeFree.length);
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
  assertEquals(9000, rows.get(3).length);
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
  assertEquals(11000, rowOne.length);
  assertEquals(2, rowOne.quantity);
  assertEquals(-1, rowTwo.length);
  assertEquals(5, rowTwo.quantity);
  assertNotNull(rowTwo.section);
 }

 @Test public void keepsSideBySideSystemTextOutOfTheRuledTable() {
  int width = 900, height = 600;
  int[][] photo = new int[height][width];
  for (int y = 0; y < height; y++) {
   Arrays.fill(photo[y], 0xffffffff);
   if (y >= 150 && y < 200) Arrays.fill(photo[y], 460, 885, 0xff153650);
   if (y >= 200 && y <= 500) {
    for (int x : new int[]{460, 675, 810, 884}) photo[y][x] = 0xff25364a;
    if (y % 50 == 0) Arrays.fill(photo[y], 460, 885, 0xff25364a);
    if (y % 50 >= 12 && y % 50 <= 35) {
     Arrays.fill(photo[y], 478, 526, 0xff666666);
     Arrays.fill(photo[y], 553, 615, 0xff000000);
    }
   }
  }
  CutTableGrid.Pixels pixels = (y, colors) -> System.arraycopy(photo[y], 0, colors, 0, width);
  TableOcrRegion region = TableOcrRegion.find(pixels, width, height);
  assertNotNull(region);
  List<CutSheet.Word> words = Arrays.asList(
   word("Cam Balkon", 220, 175), word("Profil adı", 580, 178),
   word("Kesim ölçüsü", 739, 170), word("Adet", 851, 178), word("(mm)", 739, 187),
   word("Sistem ölçüsü", 185, 225), word("4820", 282, 225), word("12", 339, 225),
   word("Üst kasa", 581, 225), word("3160", 767, 225), word("2", 836, 225),
   word("Yan dikme", 581, 275), word("2090", 747, 275), word("1", 851, 275),
   word("İspanyolet profili", 600, 325), word("1930", 744, 325),
   word("RAL 7016 Antrasit", 585, 527), word("6000", 745, 527), word("4", 851, 527));
  List<CutSheet.Row> rows = CutSheet.read(words, width, height, pixels, region);
  assertEquals(3, rows.size());
  assertEquals("Üst kasa", rows.get(0).name);
  assertEquals(31600, rows.get(0).length);
  assertEquals(2, rows.get(0).quantity);
  assertEquals("", rows.get(0).code);
  assertNotNull(rows.get(0).section);
  assertTrue(rows.get(0).section.left > 460);
  assertTrue(rows.get(0).section.right <= rows.get(0).identity.left);
  assertEquals(-1, rows.get(2).quantity);
  assertNotNull(rows.get(2).quantityCell);
  assertTrue(rows.get(2).quantityCell.left > 810);
  assertTrue(rows.get(2).quantityCell.top > 300);
 }

 @Test public void doesNotTreatUpsideDownLabelsAsAHeaderOrMetadataAsAProfile() {
  List<CutSheet.Word> rotated = Arrays.asList(
   word("Adet", 126, 90), word("Ölçü", 330, 90), word("Profil", 650, 90),
   word("Antrasit 7016 RAL", 640, 125), word("6000", 330, 125), word("4", 126, 125));
  assertTrue(CutSheet.read(rotated, 800, 300).isEmpty());
  List<CutSheet.Word> metadata = new ArrayList<>(header());
  metadata.addAll(Arrays.asList(word("Antrasit 7016 RAL", 225, 110), word("6000", 500, 110), word("4", 620, 110)));
  assertTrue(CutSheet.read(metadata, 800, 300).isEmpty());
 }

 @Test public void readsSplitHeadersWithCodeOnlyAndDecimalUnits() {
  List<CutSheet.Word> sheet = Arrays.asList(
   word("Profil", 230, 61), word("Kesim", 500, 61), word("Miktar", 620, 72),
   word("kodu", 230, 80), word("boyu", 500, 80),
   word("T.3001", 235, 119), word("125,05 cm", 500, 119), word("2", 620, 119),
   word("Toplam", 235, 150), word("2501", 500, 150), word("3", 620, 150));
  List<CutSheet.Row> rows = CutSheet.read(sheet, 800, 185);
  assertEquals(1, rows.size());
  assertEquals("T.3001", rows.get(0).code);
  assertEquals(12505, rows.get(0).length);
  assertEquals(2, rows.get(0).quantity);
 }

 @Test public void keepsFractionalMillimetresAndFlagsUncertainCharacters() {
  assertEquals(8505, CutSheet.number(Arrays.asList(word("850,5", 500, 150), word("mm", 550, 150)), false));
  assertEquals(-1, CutSheet.number(Arrays.asList(word("8S0,5", 500, 150)), false));
  assertEquals(-1, CutSheet.number(Arrays.asList(word("1,5", 620, 150)), true));
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
