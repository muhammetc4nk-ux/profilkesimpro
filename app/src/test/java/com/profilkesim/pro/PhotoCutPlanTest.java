package com.profilkesim.pro;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public final class PhotoCutPlanTest {
 @Test public void partsFromSeparatePhotosFitOnTheSameStockBar() {
  Map<Integer, List<MainActivity.CutRow>> photos = new LinkedHashMap<>();
  photos.put(1, Arrays.asList(row("K100", "Üst kasa", "4200", "1")));
  photos.put(2, Arrays.asList(row("K100", "Üst kasa", "1700", "1")));

  List<MainActivity.ProfileCutPlan> plans = plans(photos);
  assertEquals(1, plans.size());
  List<MainActivity.PackedStockBar> bars = plans.get(0).pack(3);
  assertEquals(1, bars.size());
  assertEquals(Arrays.asList(4200, 1700), bars.get(0).pieces);
  assertEquals(94, plans.get(0).stock - bars.get(0).used);
 }

 @Test public void groupsCodeFreeNamesAndKeepsDistinctCodesSeparate() {
  Map<Integer, List<MainActivity.CutRow>> photos = new LinkedHashMap<>();
  photos.put(1, Arrays.asList(row("", "Üst kasa", "2100", "1"), row("K100", "Üst kasa", "3000", "1")));
  photos.put(2, Arrays.asList(row("", "UST  KASA", "2100", "2"), row("K200", "Üst kasa", "2200", "1")));

  List<MainActivity.ProfileCutPlan> plans = plans(photos);
  assertEquals(3, plans.size());
  assertEquals(Arrays.asList(2100, 2100, 2100), plans.get(0).pieces);
  assertEquals(Arrays.asList(3000), plans.get(1).pieces);
  assertEquals(Arrays.asList(2200), plans.get(2).pieces);
 }

 @Test public void rejectsMissingQuantitiesInAnyPhoto() {
  Map<Integer, List<MainActivity.CutRow>> photos = new LinkedHashMap<>();
  photos.put(1, Arrays.asList(row("K100", "Üst kasa", "2600", "1")));
  photos.put(2, Arrays.asList(row("K100", "Üst kasa", "2600", "")));

  IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> plans(photos));
  assertTrue(error.getMessage().contains("Fotoğraf 2 kesim adedi"));
 }

 private static List<MainActivity.ProfileCutPlan> plans(Map<Integer, List<MainActivity.CutRow>> photos) {
  Map<String, String> stocks = new HashMap<>();
  for (List<MainActivity.CutRow> rows : photos.values()) {
   for (MainActivity.CutRow row : rows) stocks.put(MainActivity.profileStockKey(row), "6000");
  }
  return new ArrayList<>(MainActivity.combinePhotoCuts(photos, stocks, 3));
 }

 private static MainActivity.CutRow row(String code, String name, String size, String quantity) {
  MainActivity.CutRow row = new MainActivity.CutRow(code, name, -1, -1, null);
  row.draft = new String[]{size, quantity};
  return row;
 }
}
