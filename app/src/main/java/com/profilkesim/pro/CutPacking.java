package com.profilkesim.pro;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

/** Exact small-batch packing and decreasing best-fit for larger lists. */
final class CutPacking {
 static final class Bar {
  final List<Integer> pieces = new ArrayList<>();
  int stock, used;

  Bar(int stock) { this.stock = stock; }

  int remaining() { return stock - used; }
 }

 private CutPacking() {}

 static List<Bar> pack(List<Integer> pieces, List<Integer> stockLengths, int kerf) {
  List<Integer> stocks = new ArrayList<>(new TreeSet<>(stockLengths));
  if (kerf < 0 || stocks.isEmpty() || stocks.get(0) <= 0) {
   throw new IllegalArgumentException("Stok boyunu ve testere payını kontrol edin.");
  }
  int largest = stocks.get(stocks.size() - 1);
  List<Integer> sorted = new ArrayList<>(pieces);
  Collections.sort(sorted, Collections.reverseOrder());
  for (int piece : sorted) {
   if (piece <= 0 || piece > largest) throw new IllegalArgumentException("Bir kesim stok boyuna sığmıyor.");
  }
  if (sorted.size() <= 13) return exact(sorted, stocks, kerf);
  List<Bar> best = null;
  long leastWaste = Long.MAX_VALUE;
  for (int preferred : stocks) {
   List<Bar> trial = place(sorted, stocks, preferred, kerf);
   long waste = 0;
   for (Bar bar : trial) waste += bar.remaining();
   if (best == null || waste < leastWaste || (waste == leastWaste && trial.size() < best.size())) {
    best = trial;
    leastWaste = waste;
   }
  }
  return best;
 }

 private static List<Bar> exact(List<Integer> pieces, List<Integer> stocks, int kerf) {
  int states = 1 << pieces.size();
  long[] used = new long[states], waste = new long[states];
  int[] stock = new int[states], count = new int[states], choice = new int[states];
  int max = stocks.get(stocks.size() - 1);
  for (int mask = 1; mask < states; mask++) {
   int lower = mask & (mask - 1);
   used[mask] = used[lower] + pieces.get(Integer.numberOfTrailingZeros(mask)) + (lower == 0 ? 0 : kerf);
   if (used[mask] <= max) stock[mask] = fitting(stocks, (int) used[mask]);
  }
  for (int mask = 1; mask < states; mask++) {
   int first = mask & -mask;
   int rest = mask ^ first;
   waste[mask] = Long.MAX_VALUE;
   for (int others = rest; ; others = (others - 1) & rest) {
    int bar = first | others;
    if (stock[bar] != 0) {
     int left = mask ^ bar;
     long candidate = stock[bar] - used[bar] + waste[left];
     if (candidate < waste[mask] || (candidate == waste[mask] && count[left] + 1 < count[mask])) {
      waste[mask] = candidate;
      count[mask] = count[left] + 1;
      choice[mask] = bar;
     }
    }
    if (others == 0) break;
   }
  }
  List<Bar> result = new ArrayList<>();
  for (int mask = states - 1; mask != 0; mask ^= choice[mask]) {
   int group = choice[mask];
   Bar bar = new Bar(stock[group]);
   bar.used = (int) used[group];
   for (int i = 0; i < pieces.size(); i++) if ((group & (1 << i)) != 0) bar.pieces.add(pieces.get(i));
   result.add(bar);
  }
  return result;
 }

 private static List<Bar> place(List<Integer> pieces, List<Integer> stocks, int preferred, int kerf) {
  List<Bar> bars = new ArrayList<>();
  for (int piece : pieces) {
   Bar target = null;
   long gap = Long.MAX_VALUE;
   for (Bar bar : bars) {
    long remainder = (long) bar.stock - bar.used - kerf - piece;
    if (remainder >= 0 && remainder < gap) {
     gap = remainder;
     target = bar;
    }
   }
   if (target == null) {
    target = new Bar(fitting(stocks, Math.max(piece, preferred)));
    bars.add(target);
   } else {
    target.used += kerf;
   }
   target.used += piece;
   target.pieces.add(piece);
  }
  for (Bar bar : bars) bar.stock = fitting(stocks, bar.used);
  return bars;
 }

 private static int fitting(List<Integer> stocks, int needed) {
  for (int stock : stocks) if (stock >= needed) return stock;
  throw new IllegalArgumentException("Kesim stok boyuna sığmıyor.");
 }
}
