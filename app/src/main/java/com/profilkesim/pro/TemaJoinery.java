package com.profilkesim.pro;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/** Rectangular joinery geometry. Dimensions are integer tenths of a millimetre. */
final class TemaJoinery {
 enum Corner { MITER, VERTICAL_FULL, HORIZONTAL_FULL }
 enum Continuous { VERTICAL, HORIZONTAL }

 static final class Order {
  int width, height, quantity;
  String frameCode, mullionCode, sashCode;
  int frameFace, mullionFace, sashFace;
  int vertical, horizontal, joint;
  Integer frameJoint, mullionJoint;
  Set<Integer> emptyCells = Collections.emptySet();
  int frameGlass, mullionGlass, sashGlass, sashGap;
  boolean glassSizesVerified;
  Bead fixedBead, sashBead;
  String adapterCode;
  Corner corner = Corner.MITER;
  Corner sashCorner = Corner.MITER;
  Continuous continuous = Continuous.VERTICAL;
  List<Integer> columnOpenings = Collections.emptyList(), rowOpenings = Collections.emptyList();
  Set<Integer> openingSashes = Collections.emptySet();
 }

 static final class Bead {
  final String code;
  final int face, edgeAdjustment;
  final Corner corner;

  Bead(String code, int face, int edgeAdjustment, Corner corner) {
   this.code = code;
   this.face = face;
   this.edgeAdjustment = edgeAdjustment;
   this.corner = corner;
  }
 }

 static final class Cut {
  final String code, role;
  final int length, angle;
  int quantity;

  Cut(String code, String role, int length, int angle, int quantity) {
   this.code = code;
   this.role = role;
   this.length = length;
   this.angle = angle;
   this.quantity = quantity;
  }
 }

 static final class Pane {
  final int number, width, height;
  final boolean sash;

  Pane(int number, int width, int height, boolean sash) {
   this.number = number;
   this.width = width;
   this.height = height;
   this.sash = sash;
  }
 }

 static final class Plan {
  final List<Cut> cuts = new ArrayList<>();
  final List<Pane> panes = new ArrayList<>();
  final List<Integer> columns, rows;
  BigDecimal glassArea = BigDecimal.ZERO;
  int mullionEnds;

  Plan(List<Integer> columns, List<Integer> rows) {
   this.columns = columns;
   this.rows = rows;
  }

  void addCut(String code, String role, int length, int angle, int quantity) {
   if (length <= 0) throw new IllegalArgumentException(role + " kesim boyu sıfırdan büyük olmalı.");
   for (Cut cut : cuts) {
    if (cut.code.equals(code) && cut.role.equals(role) && cut.angle == angle && cut.length == length) {
     cut.quantity += quantity;
     return;
    }
   }
   cuts.add(new Cut(code, role, length, angle, quantity));
  }
 }

 private TemaJoinery() {}

 static void addManualCuts(Plan plan, String input) {
  int lineNumber = 0;
  for (String line : input.split("\\r?\\n")) {
   lineNumber++;
   if (line.trim().isEmpty()) continue;
   String[] fields = line.split(";", -1);
   if (fields.length != 4 || fields[0].trim().isEmpty()) {
    throw new IllegalArgumentException("Ek kesim satırı " + lineNumber + " için kod;ölçü;adet;açı girin.");
   }
   int length = ShopDimensions.tenths(fields[1], "Ek kesim boyu");
   int quantity, angle;
   try {
    quantity = Integer.parseInt(fields[2].trim());
    angle = Integer.parseInt(fields[3].trim());
   } catch (NumberFormatException error) {
    throw new IllegalArgumentException("Ek kesim satırı " + lineNumber + " için adet ve açıyı kontrol edin.");
   }
   if (quantity < 1 || quantity > 1000 || (angle != 45 && angle != 90)) {
    throw new IllegalArgumentException("Ek kesim adedi 1–1000, açı 45° veya 90° olmalı.");
   }
   plan.addCut(TemaCatalog.normalizeCode(fields[0]), "Ek katalog kesimi", length, angle, quantity);
  }
 }

 static Plan calculate(Order o) {
  if (o.width <= 0 || o.height <= 0 || o.frameFace <= 0 || o.quantity <= 0
    || o.vertical < 0 || o.horizontal < 0 || o.vertical > 12 || o.horizontal > 12) {
   throw new IllegalArgumentException("Kasa dış ölçülerini, profil yüzünü, adedi ve kayıt sayısını kontrol edin.");
  }
  boolean hasMullion = o.vertical + o.horizontal > 0;
  if (hasMullion && o.mullionFace <= 0) throw new IllegalArgumentException("Orta kayıt yüz ölçüsü sıfırdan büyük olmalı.");
  if (!o.openingSashes.isEmpty() && o.sashFace <= 0) throw new IllegalArgumentException("Kanat yüz ölçüsü sıfırdan büyük olmalı.");
  int innerWidth = o.width - 2 * o.frameFace;
  int innerHeight = o.height - 2 * o.frameFace;
  List<Integer> columns = openings(innerWidth - o.vertical * o.mullionFace, o.vertical + 1, o.columnOpenings, "Yatay");
  List<Integer> rows = openings(innerHeight - o.horizontal * o.mullionFace, o.horizontal + 1, o.rowOpenings, "Dikey");
  Plan plan = new Plan(columns, rows);
  addRectangle(plan, o.frameCode, "Kasa", o.width, o.height, o.frameFace, o.corner, o.quantity);
  if (hasMullion) addMullions(o, plan, innerWidth, innerHeight);
  for (int index : o.openingSashes) {
   if (index < 1 || index > columns.size() * rows.size()) {
    throw new IllegalArgumentException("Kanat hücresi 1–" + (columns.size() * rows.size()) + " aralığında olmalı.");
   }
  }
  for (int y = 0; y < rows.size(); y++) {
   for (int x = 0; x < columns.size(); x++) addPane(o, plan, x, y);
  }
  return plan;
 }

 private static List<Integer> openings(int net, int count, List<Integer> entered, String axis) {
  if (net < count) throw new IllegalArgumentException(axis + " boşluk kasa ve kayıt ölçülerine sığmıyor.");
  if (!entered.isEmpty()) {
   long sum = 0;
   for (int opening : entered) {
    if (opening <= 0) throw new IllegalArgumentException(axis + " açıklıklar sıfırdan büyük olmalı.");
    sum += opening;
   }
   if (entered.size() != count || sum != net) {
    throw new IllegalArgumentException(axis + " eksende " + count + " açıklık girin. Toplamları "
     + ShopDimensions.mm(net) + " mm olmalı.");
   }
   return new ArrayList<>(entered);
  }
  List<Integer> values = new ArrayList<>();
  for (int i = 0; i < count; i++) values.add(net / count + (i < net % count ? 1 : 0));
  return values;
 }

 private static void addRectangle(Plan plan, String code, String role, int width, int height,
                                  int face, Corner corner, int quantity) {
  int angle = corner == Corner.MITER ? 45 : 90;
  int horizontal = corner == Corner.VERTICAL_FULL ? width - 2 * face : width;
  int vertical = corner == Corner.HORIZONTAL_FULL ? height - 2 * face : height;
  plan.addCut(code, role + " yatay", horizontal, angle, 2 * quantity);
  plan.addCut(code, role + " dikey", vertical, angle, 2 * quantity);
 }

 private static void addMullions(Order o, Plan plan, int innerWidth, int innerHeight) {
  int f = o.frameJoint == null ? o.joint : o.frameJoint;
  int m = o.mullionJoint == null ? o.joint : o.mullionJoint;
  int adjustment = 2 * f;
  if (o.continuous == Continuous.VERTICAL) {
   if (o.vertical > 0) plan.addCut(o.mullionCode, "Dikey orta kayıt", innerHeight + adjustment, 90, o.vertical * o.quantity);
   if (o.horizontal > 0) for (int i=0; i<plan.columns.size(); i++) {
    int width=plan.columns.get(i);
    plan.addCut(o.mullionCode, "Yatay orta kayıt", width + (i==0?f:m) + (i==plan.columns.size()-1?f:m), 90, o.horizontal * o.quantity);
   }
   plan.mullionEnds = 2 * (o.vertical + o.horizontal * plan.columns.size()) * o.quantity;
  } else {
   if (o.horizontal > 0) plan.addCut(o.mullionCode, "Yatay orta kayıt", innerWidth + adjustment, 90, o.horizontal * o.quantity);
   if (o.vertical > 0) for (int i=0; i<plan.rows.size(); i++) {
    int height=plan.rows.get(i);
    plan.addCut(o.mullionCode, "Dikey orta kayıt", height + (i==0?f:m) + (i==plan.rows.size()-1?f:m), 90, o.vertical * o.quantity);
   }
   plan.mullionEnds = 2 * (o.horizontal + o.vertical * plan.rows.size()) * o.quantity;
  }
 }

 private static void addPane(Order o, Plan plan, int x, int y) {
  int number = y * plan.columns.size() + x + 1;
  int width = plan.columns.get(x), height = plan.rows.get(y);
  if (o.emptyCells.contains(number)) return;
  boolean sash = o.openingSashes.contains(number);
  if (sash) {
   if (o.adapterCode != null) {
    addRectangle(plan, o.adapterCode, "Kanat adaptörü", width, height, 0, Corner.MITER, o.quantity);
   }
   width -= 2 * o.sashGap;
   height -= 2 * o.sashGap;
   if (width <= 2 * o.sashFace || height <= 2 * o.sashFace) {
    throw new IllegalArgumentException(number + ". hücrede kanat profili cam açıklığına sığmıyor.");
   }
   addRectangle(plan, o.sashCode, "Kanat", width, height, o.sashFace, o.sashCorner, o.quantity);
   addBead(plan, o.sashBead, "Kanat cam çıtası", width - 2 * o.sashFace, height - 2 * o.sashFace, o.quantity);
   width += 2 * (o.sashGlass - o.sashFace);
   height += 2 * (o.sashGlass - o.sashFace);
  } else {
   addBead(plan, o.fixedBead, "Sabit cam çıtası", width, height, o.quantity);
   width += (x == 0 ? o.frameGlass : o.mullionGlass)
    + (x == plan.columns.size() - 1 ? o.frameGlass : o.mullionGlass);
   height += (y == 0 ? o.frameGlass : o.mullionGlass)
    + (y == plan.rows.size() - 1 ? o.frameGlass : o.mullionGlass);
  }
  if (width <= 0 || height <= 0) throw new IllegalArgumentException(number + ". hücrenin net cam ölçüsünü kontrol edin.");
  plan.panes.add(new Pane(number, width, height, sash));
  plan.glassArea = plan.glassArea.add(ShopDimensions.area(width, height, o.quantity));
 }

 private static void addBead(Plan plan, Bead bead, String role, int width, int height, int quantity) {
  if (bead == null) return;
  // Long points are measured on the seating line, independently of glass overlap.
  width += 2 * bead.edgeAdjustment;
  height += 2 * bead.edgeAdjustment;
  if (width <= 2 * bead.face || height <= 2 * bead.face) {
   throw new IllegalArgumentException(role + " seçilen açıklığa sığmıyor. Çıta oturma ölçüsünü kontrol edin.");
  }
  addRectangle(plan, bead.code, role, width, height, bead.face, bead.corner, quantity);
 }
}
