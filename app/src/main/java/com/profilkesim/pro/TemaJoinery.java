package com.profilkesim.pro;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;

/** Rectangular joinery geometry. Dimensions are integer tenths of a millimetre. */
final class TemaJoinery {
 enum Corner { MITER, VERTICAL_FULL, HORIZONTAL_FULL }
 enum Continuous { VERTICAL, HORIZONTAL }

 static final class Order {
  TemaLayout layout;
  Map<Integer,TemaLayout.Section> sashProfiles = Collections.emptyMap();
  boolean removeDoorSills;
  int width, height, quantity;
  String frameCode, mullionCode, sashCode;
  int frameFace, mullionFace, sashFace;
  int vertical, horizontal, joint;
  Integer frameJoint, mullionJoint;
  Set<Integer> emptyCells = Collections.emptySet();
  int frameGlass, mullionGlass, sashGlass, sashGap;
  java.util.Map<Integer,Integer> sashGaps=new java.util.HashMap<>();
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
  final int length, angle, endAngle;
  int quantity;

  Cut(String code, String role, int length, int angle, int endAngle, int quantity) {
   this.code = code;
   this.role = role;
   this.length = length;
   this.angle = angle;
   this.endAngle = endAngle;
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
  final TemaLayout layout;
  BigDecimal glassArea = BigDecimal.ZERO;
  int mullionEnds, frameCorners;

  Plan(List<Integer> columns, List<Integer> rows, TemaLayout layout) {
   this.columns = columns;
   this.rows = rows;
   this.layout = layout;
  }

  void addCut(String code, String role, int length, int angle, int quantity) {
   addCut(code, role, length, angle, angle, quantity);
  }

  void addCut(String code, String role, int length, int angle, int endAngle, int quantity) {
   if (length <= 0) throw new IllegalArgumentException(role + " kesim boyu sıfırdan büyük olmalı.");
   for (Cut cut : cuts) {
    if (cut.code.equals(code) && cut.role.equals(role) && cut.angle == angle && cut.endAngle == endAngle && cut.length == length) {
     cut.quantity += quantity;
     return;
    }
   }
   cuts.add(new Cut(code, role, length, angle, endAngle, quantity));
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
  Plan plan = o.layout != null ? new Plan(Collections.singletonList(o.layout.innerWidth),
   Collections.singletonList(o.layout.innerHeight), o.layout) : gridPlan(o);
  for (int index : o.openingSashes) {
   if (index < 1 || index > plan.layout.bays.size()) {
    throw new IllegalArgumentException("Kanat hücresi 1–" + plan.layout.bays.size() + " aralığında olmalı.");
   }
  }
  addFrame(o, plan);
  int frameJoint = o.frameJoint == null ? o.joint : o.frameJoint;
  int mullionJoint = o.mullionJoint == null ? o.joint : o.mullionJoint;
  for (TemaLayout.Member member : plan.layout.members(o.continuous)) {
   plan.addCut(member.section.code, member.vertical ? "Dikey orta kayıt" : "Yatay orta kayıt",
    member.cutLength(frameJoint, mullionJoint), 90, o.quantity);
   plan.mullionEnds += (member.floorEnd ? 1 : 2) * o.quantity;
  }
  for (int index = 0; index < plan.layout.bays.size(); index++) {
   int number = index + 1;
   if (o.emptyCells.contains(number)) continue;
   TemaLayout.Opening bay = plan.layout.bays.get(index);
   TemaLayout.Frame frame = plan.layout.frame;
   int glassX = (bay.left == frame.left.face ? o.frameGlass : o.mullionGlass)
    + (bay.right == o.width - frame.right.face ? o.frameGlass : o.mullionGlass);
   int glassY = (bay.top == frame.top.face ? o.frameGlass : o.mullionGlass)
    + (bay.bottom == o.height - plan.layout.bottomFace ? o.frameGlass : o.mullionGlass);
   addPaneDimensions(o, plan, number, bay, o.openingSashes.contains(number), glassX, glassY);
  }
  return plan;
 }

 private static Plan gridPlan(Order o) {
  int columnCount = o.vertical + 1, rowCount = o.horizontal + 1;
  Set<Integer> doors = new LinkedHashSet<>();
  if (o.removeDoorSills) {
   for (int i = columnCount * (rowCount - 1) + 1; i <= columnCount * rowCount; i++) {
    if (o.openingSashes.contains(i)) doors.add(i);
   }
  }
  int width = o.width - 2 * o.frameFace;
  int height = o.height - o.frameFace - (doors.size() == columnCount ? 0 : o.frameFace);
  List<Integer> columns = openings(width - o.vertical * o.mullionFace, columnCount, o.columnOpenings, "Yatay");
  List<Integer> rows = openings(height - o.horizontal * o.mullionFace, rowCount, o.rowOpenings, "Dikey");
  int scale = 1_000_000;
  List<Integer> xs = gridCentres(columns, o.mullionFace, width, scale);
  List<Integer> ys = gridCentres(rows, o.mullionFace, height, scale);
  List<int[]> bays = new ArrayList<>();
  for (int y = 0; y < rowCount; y++) {
   for (int x = 0; x < columnCount; x++) bays.add(new int[]{xs.get(x), ys.get(y), xs.get(x + 1), ys.get(y + 1)});
  }
  List<TemaLayout.Record> records = new ArrayList<>();
  if (o.vertical + o.horizontal > 0) {
   TemaLayout.Section profile = new TemaLayout.Section(o.mullionCode, o.mullionFace);
   if (o.continuous == Continuous.VERTICAL) addGridRecords(records, xs, true, profile, scale);
   addGridRecords(records, ys, false, profile, scale);
   if (o.continuous == Continuous.HORIZONTAL) addGridRecords(records, xs, true, profile, scale);
  }
  TemaLayout.Frame frame = new TemaLayout.Frame(new TemaLayout.Section(o.frameCode, o.frameFace));
  return new Plan(columns, rows, new TemaLayout(o.width, o.height, frame, scale, bays, records, doors));
 }

 private static List<Integer> gridCentres(List<Integer> openings, int face, int span, int scale) {
  List<Integer> centres = new ArrayList<>();
  centres.add(0);
  int position = face / 2;
  for (int i = 0; i + 1 < openings.size(); i++) {
   position += openings.get(i);
   centres.add((int)Math.round((double)position * scale / span));
   position += face;
  }
  centres.add(scale);
  return centres;
 }

 private static void addGridRecords(List<TemaLayout.Record> out, List<Integer> centres, boolean vertical,
                                    TemaLayout.Section profile, int scale) {
  for (int i = 1; i + 1 < centres.size(); i++) {
   int at = centres.get(i);
   out.add(vertical ? new TemaLayout.Record(at, 0, at, scale, profile)
    : new TemaLayout.Record(0, at, scale, at, profile));
  }
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

 private static void addFrame(Order o, Plan plan) {
  TemaLayout.Frame f = plan.layout.frame;
  int angle = o.corner == Corner.MITER ? 45 : 90;
  boolean verticalFull = o.corner == Corner.VERTICAL_FULL, horizontalFull = o.corner == Corner.HORIZONTAL_FULL;
  int width = o.width - (verticalFull ? f.left.face + f.right.face : 0);
  plan.addCut(f.top.code, "Kasa yatay", width, angle, o.quantity);
  boolean leftSill = false, rightSill = false;
  for (TemaLayout.Span span : plan.layout.rails) {
   boolean left = span.start == 0, right = span.end == o.width;
   leftSill |= left;
   rightSill |= right;
   int length = span.end - span.start - (verticalFull && left ? f.left.face : 0)
    - (verticalFull && right ? f.right.face : 0);
   plan.addCut(f.bottom.code, left && right ? "Kasa yatay" : "Kasa alt yatay", length,
    left ? angle : 90, right ? angle : 90, o.quantity);
  }
  int jamb = o.height - (horizontalFull ? f.top.face : 0);
  plan.frameCorners = (2 + (leftSill ? 1 : 0) + (rightSill ? 1 : 0)) * o.quantity;
  plan.addCut(f.left.code, "Kasa dikey", jamb - (horizontalFull && leftSill ? f.bottom.face : 0),
   angle, leftSill ? angle : 90, o.quantity);
  plan.addCut(f.right.code, "Kasa dikey", jamb - (horizontalFull && rightSill ? f.bottom.face : 0),
   angle, rightSill ? angle : 90, o.quantity);
 }

 private static void addPaneDimensions(Order o, Plan plan, int number, TemaLayout.Opening bay,
                                        boolean sash, int glassX, int glassY) {
  int width = bay.width(), height = bay.height();
  if (sash) {
   if (o.adapterCode != null) {
    if (bay.door) {
     plan.addCut(o.adapterCode, "Kanat adaptörü yatay", width, 45, o.quantity);
     plan.addCut(o.adapterCode, "Kanat adaptörü dikey", height, 45, 90, 2 * o.quantity);
    } else addRectangle(plan, o.adapterCode, "Kanat adaptörü", width, height, 0, Corner.MITER, o.quantity);
   }
   TemaLayout.Section profile = o.sashProfiles.get(number);
   int face = profile == null ? o.sashFace : profile.face;
   String code = profile == null ? o.sashCode : profile.code;
   int gap=o.sashGaps.getOrDefault(number,o.sashGap);
   width -= 2 * gap;
   height -= 2 * gap;
   if (width <= 2 * face || height <= 2 * face) {
    throw new IllegalArgumentException(number + ". hücrede kanat profili cam açıklığına sığmıyor.");
   }
   addRectangle(plan, code, "Kanat", width, height, face, o.sashCorner, o.quantity);
   addBead(plan, o.sashBead, "Kanat cam çıtası", width - 2 * face, height - 2 * face, o.quantity);
   width += 2 * (o.sashGlass - face);
   height += 2 * (o.sashGlass - face);
  } else {
   addBead(plan, o.fixedBead, "Sabit cam çıtası", width, height, o.quantity);
   width += glassX;
   height += glassY;
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
