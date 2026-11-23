package com.profilkesim.pro;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

final class ShopDimensions {
 static final int PER_MM = 10;
 private static final BigDecimal LIMIT = BigDecimal.valueOf(100_000);

 private ShopDimensions() {}

 static int tenths(String text, String label) {
  try {
   String value = text.trim().replace(',', '.');
   if (!value.matches("[+-]?[0-9]+(?:\\.[0-9]{1,2})?")) throw new NumberFormatException();
   BigDecimal mm = new BigDecimal(value);
   if (mm.abs().compareTo(LIMIT) > 0) throw new NumberFormatException();
   return mm.movePointRight(1).intValueExact();
  } catch (ArithmeticException | NumberFormatException e) {
   throw new IllegalArgumentException(label + " için 0,1 mm hassasiyetinde bir değer girin.");
  }
 }

 static String mm(int tenths) {
  return BigDecimal.valueOf(tenths, 1).stripTrailingZeros().toPlainString().replace('.', ',');
 }

 static List<Integer> stocks(String text) {
  String[] values = text.trim().split("[;\\n]+");
  List<Integer> stocks = new ArrayList<>();
  for (String value : values) stocks.add(tenths(value, "Stok boyu"));
  return stocks;
 }

 static BigDecimal area(int width, int height, int quantity) {
  return BigDecimal.valueOf(width).multiply(BigDecimal.valueOf(height))
   .multiply(BigDecimal.valueOf(quantity)).movePointLeft(8);
 }

 static String squareMeters(BigDecimal area) {
  return area.setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString().replace('.', ',');
 }
}
