package com.profilkesim.pro;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;

/** Rectangular joinery geometry. Dimensions are integer tenths of a millimetre. */
final class TemaJoinery {
 static final int SASH_GAP = 5 * ShopDimensions.PER_MM;
 static final int GLASS_DEDUCTION = 10 * ShopDimensions.PER_MM;
 private static final int ALDOKS_BEAD_END = 17 * ShopDimensions.PER_MM;

 enum Corner { MITER, VERTICAL_FULL, HORIZONTAL_FULL }
 enum Continuous { VERTICAL, HORIZONTAL }
 enum ProfileRole { FRAME, MULLION, SASH }

 static final class Order {
  TemaLayout layout;
  Map<Integer,TemaLayout.Section> sashProfiles = Collections.emptyMap();
  Map<Integer,TemaLayout.Section> skirtProfiles = Collections.emptyMap();
  boolean removeDoorSills;
  boolean aldoksDrawing, aldoksBeads;
  int width, height, quantity;
  String frameCode, mullionCode, sashCode;
  int frameFace, mullionFace, sashFace;
  int vertical, horizontal, joint;
  Integer frameJoint, mullionJoint;
  Set<Integer> emptyCells = Collections.emptySet();
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
  final int number, width, height, insideWidth, insideHeight;
  final TemaLayout.Section sash, skirt;
  final String adapterCode;

  Pane(int number, int insideWidth, int insideHeight, TemaLayout.Section sash, TemaLayout.Section skirt, String adapterCode) {
   this.number = number;
   this.insideWidth = insideWidth;
   this.insideHeight = insideHeight;
   this.width = insideWidth - GLASS_DEDUCTION;
   this.height = insideHeight - GLASS_DEDUCTION;
   this.sash = sash;
   this.skirt = skirt;
   this.adapterCode = adapterCode;
  }
 }

 static final class ProfileMark {
  final String code;
  final int face, length, axis, start, end;
  final boolean vertical;
  final ProfileRole role;

  ProfileMark(TemaLayout.Section section, int length, int axis, int start, int end, boolean vertical, ProfileRole role) {
   this.code = section.code;
   this.face = section.face;
   this.length = length;
   this.axis = axis;
   this.start = start;
   this.end = end;
   this.vertical = vertical;
   this.role = role;
  }

  String allowanceText() {
   return "Pay " + ShopDimensions.mm(face) + " mm";
  }

 }

 static final class Plan {
  final List<Cut> cuts = new ArrayList<>();
  final List<Pane> panes = new ArrayList<>();
  final List<ProfileMark> profileMarks = new ArrayList<>();
  final List<Integer> columns, rows;
  final TemaLayout layout;
  BigDecimal glassArea = BigDecimal.ZERO;
  int mullionEnds, frameCorners;

  Plan(List<Integer> columns, List<Integer> rows, TemaLayout layout) {
   this.columns = columns;
   this.rows = rows;
   this.layout = layout;
  }

  Map<String, List<Cut>> cutsByProfile() {
   Map<String, List<Cut>> grouped = new LinkedHashMap<>();
   for (Cut cut : cuts) {
    List<Cut> parts = grouped.get(cut.code);
    if (parts == null) {
     parts = new ArrayList<>();
     grouped.put(cut.code, parts);
    }
    parts.add(cut);
   }
   return grouped;
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

 static int glassAllowance(TemaLayout.Section sash) {
  return GLASS_DEDUCTION + (sash == null ? 0 : 2 * (SASH_GAP + sash.face));
 }

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
   int length = member.cutLength(frameJoint, mullionJoint);
   plan.addCut(member.section.code, member.vertical ? "Dikey orta kayıt" : "Yatay orta kayıt",
    length, 90, o.quantity);
   plan.profileMarks.add(new ProfileMark(member.section, length, member.axis, member.start,
    member.end, member.vertical, ProfileRole.MULLION));
   plan.mullionEnds += (member.floorEnd ? 1 : 2) * o.quantity;
  }
  for (int index = 0; index < plan.layout.bays.size(); index++) {
   int number = index + 1;
   if (o.emptyCells.contains(number)) continue;
   TemaLayout.Opening bay = plan.layout.bays.get(index);
   addPaneDimensions(o, plan, number, bay, o.openingSashes.contains(number));
  }
  if (o.aldoksBeads) plan.cuts.sort((a, b) -> Integer.compare(beadCutSequence(a), beadCutSequence(b)));
  return plan;
 }

 static Plan gridPlan(Order o) {
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
  plan.addCut(code, role + " yatay", horizontalLength(width, face, corner), angle, 2 * quantity);
  plan.addCut(code, role + " dikey", verticalLength(height, face, corner), angle, 2 * quantity);
 }

 private static int horizontalLength(int width, int face, Corner corner) {
  return corner == Corner.VERTICAL_FULL ? width - 2 * face : width;
 }

 private static int verticalLength(int height, int face, Corner corner) {
  return corner == Corner.HORIZONTAL_FULL ? height - 2 * face : height;
 }

 static Corner frameCorner(TemaLayout.Frame frame, Corner configured, boolean top, boolean left) {
  TemaLayout.Section rail = top ? frame.top : frame.bottom;
  TemaLayout.Section jamb = left ? frame.left : frame.right;
  if (isAngleProfile(rail)) return Corner.HORIZONTAL_FULL;
  if (isAngleProfile(jamb)) return Corner.VERTICAL_FULL;
  if (top && isSkirt(rail)) return Corner.VERTICAL_FULL;
  if (isSkirt(jamb)) return Corner.HORIZONTAL_FULL;
  return configured;
 }

 private static boolean isAngleProfile(TemaLayout.Section section) {
  return "T.3011".equals(section.code);
 }

 private static boolean isSkirt(TemaLayout.Section section) {
  return "T.3010".equals(section.code) || "T.3109".equals(section.code);
 }

 private static int angle(Corner corner) { return corner == Corner.MITER ? 45 : 90; }

 private static void addFrame(Order o, Plan plan) {
  TemaLayout.Frame f = plan.layout.frame;
  Corner topLeft = frameCorner(f, o.corner, true, true), topRight = frameCorner(f, o.corner, true, false);
  Corner bottomLeft = frameCorner(f, o.corner, false, true), bottomRight = frameCorner(f, o.corner, false, false);
  int width = o.width - (topLeft == Corner.VERTICAL_FULL ? f.left.face : 0)
   - (topRight == Corner.VERTICAL_FULL ? f.right.face : 0);
  plan.addCut(f.top.code, "Kasa yatay", width, angle(topLeft), angle(topRight), o.quantity);
  plan.profileMarks.add(new ProfileMark(f.top, width, f.top.face / 2, 0, o.width, false, ProfileRole.FRAME));
  boolean leftSill = false, rightSill = false;
  for (TemaLayout.Span span : plan.layout.rails) {
   boolean left = span.start == 0, right = span.end == o.width;
   leftSill |= left;
   rightSill |= right;
   int length = span.end - span.start - (left && bottomLeft == Corner.VERTICAL_FULL ? f.left.face : 0)
    - (right && bottomRight == Corner.VERTICAL_FULL ? f.right.face : 0);
   plan.addCut(f.bottom.code, left && right ? "Kasa yatay" : "Kasa alt yatay", length,
    left ? angle(bottomLeft) : 90, right ? angle(bottomRight) : 90, o.quantity);
   plan.profileMarks.add(new ProfileMark(f.bottom, length, o.height - f.bottom.face / 2,
    span.start, span.end, false, ProfileRole.FRAME));
  }
  plan.frameCorners = (2 + (leftSill ? 1 : 0) + (rightSill ? 1 : 0)) * o.quantity;
  int leftLength = o.height - (topLeft == Corner.HORIZONTAL_FULL ? f.top.face : 0)
   - (leftSill && bottomLeft == Corner.HORIZONTAL_FULL ? f.bottom.face : 0);
  int rightLength = o.height - (topRight == Corner.HORIZONTAL_FULL ? f.top.face : 0)
   - (rightSill && bottomRight == Corner.HORIZONTAL_FULL ? f.bottom.face : 0);
  plan.addCut(f.left.code, "Kasa dikey", leftLength, angle(topLeft), leftSill ? angle(bottomLeft) : 90, o.quantity);
  plan.addCut(f.right.code, "Kasa dikey", rightLength, angle(topRight), rightSill ? angle(bottomRight) : 90, o.quantity);
  plan.profileMarks.add(new ProfileMark(f.left, leftLength, f.left.face / 2, 0, o.height, true, ProfileRole.FRAME));
  plan.profileMarks.add(new ProfileMark(f.right, rightLength, o.width - f.right.face / 2, 0, o.height,
   true, ProfileRole.FRAME));
 }

 static int[] glassDeductions(TemaLayout.Section sash, TemaLayout.Section skirt, Corner corner,
                              boolean aldoksBeads, boolean aldoksDrawing) {
  if (!aldoksBeads) {
   int allowance = glassAllowance(sash);
   return new int[]{allowance, allowance + (skirt == null ? 0 : skirt.face - sash.face)};
  }
  int[] beads = aldoksBeadDimensions(0, 0, sash, skirt, corner, aldoksDrawing);
  return new int[]{GLASS_DEDUCTION - beads[0], GLASS_DEDUCTION - beads[1]};
 }

 private static int[] aldoksBeadDimensions(int width, int height, TemaLayout.Section sash, TemaLayout.Section skirt,
                                           Corner corner, boolean drawing) {
  if (sash == null) return new int[]{width - ShopDimensions.PER_MM, height - 2 * ALDOKS_BEAD_END};
  int cutWidth = width + (drawing ? 14 * ShopDimensions.PER_MM : -2 * SASH_GAP);
  int cutHeight = height + (drawing ? -8 * ShopDimensions.PER_MM : -2 * SASH_GAP);
  int cheeks = 2 * aldoksBeadCheek(sash);
  int bottomExtra = skirt == null ? 0 : skirt.face - sash.face;
  return new int[]{horizontalLength(cutWidth, sash.face, corner) - cheeks,
   verticalLength(cutHeight, sash.face, corner) - cheeks - bottomExtra - 2 * ALDOKS_BEAD_END};
 }

 private static void addPaneDimensions(Order o, Plan plan, int number, TemaLayout.Opening bay,
                                        boolean opening) {
  int width = bay.width(), height = bay.height();
  TemaLayout.Section sash = null;
  TemaLayout.Section skirt = opening ? o.skirtProfiles.get(number) : null;
  Bead bead = opening ? o.sashBead : o.fixedBead;
  if (opening) {
   if (o.adapterCode != null) {
    if (bay.door) {
     plan.addCut(o.adapterCode, "Kanat adaptörü yatay", width, 45, o.quantity);
     plan.addCut(o.adapterCode, "Kanat adaptörü dikey", height, 45, 90, 2 * o.quantity);
    } else addRectangle(plan, o.adapterCode, "Kanat adaptörü", width, height, 0, Corner.MITER, o.quantity);
   }
   sash = o.sashProfiles.get(number);
   if (sash == null) sash = new TemaLayout.Section(o.sashCode, o.sashFace);
   int cutWidth = width + (o.aldoksDrawing ? 14 * ShopDimensions.PER_MM : -2 * SASH_GAP);
   int cutHeight = height + (o.aldoksDrawing ? -8 * ShopDimensions.PER_MM : -2 * SASH_GAP);
   width -= 2 * SASH_GAP;
   height -= 2 * SASH_GAP;
   int bottomFace = skirt == null ? sash.face : skirt.face;
   if (width <= 2 * sash.face || height <= sash.face + bottomFace) {
    throw new IllegalArgumentException(number + ". hücrede kanat profili cam açıklığına sığmıyor.");
   }
   if (skirt == null) {
    addRectangle(plan, sash.code, "Kanat", cutWidth, cutHeight, sash.face, o.sashCorner, o.quantity);
   } else {
    int angle = o.sashCorner == Corner.MITER ? 45 : 90;
    int rail = horizontalLength(cutWidth, sash.face, o.sashCorner);
    int jamb = cutHeight - (o.sashCorner == Corner.HORIZONTAL_FULL ? sash.face + bottomFace : 0);
    plan.addCut(sash.code, "Kanat yatay", rail, angle, o.quantity);
    plan.addCut(sash.code, "Kanat dikey", jamb, angle, 2 * o.quantity);
    plan.addCut(skirt.code, "Kanat alt etek profili", rail, angle, o.quantity);
   }
   int left = bay.left + SASH_GAP, top = bay.top + SASH_GAP;
   int halfFace = sash.face / 2;
   int horizontal = horizontalLength(width, sash.face, o.sashCorner);
   int vertical = height - (o.sashCorner == Corner.HORIZONTAL_FULL ? sash.face + bottomFace : 0);
   plan.profileMarks.add(new ProfileMark(sash, horizontal, top + halfFace, left, left + width,
    false, ProfileRole.SASH));
   plan.profileMarks.add(new ProfileMark(skirt == null ? sash : skirt, horizontal, top + height - bottomFace / 2, left, left + width,
    false, ProfileRole.SASH));
   plan.profileMarks.add(new ProfileMark(sash, vertical, left + halfFace, top, top + height,
    true, ProfileRole.SASH));
   plan.profileMarks.add(new ProfileMark(sash, vertical, left + width - halfFace, top, top + height,
    true, ProfileRole.SASH));
   width -= 2 * sash.face;
   height -= sash.face + bottomFace;
  }
  int[] beads = o.aldoksBeads ? aldoksBeadDimensions(bay.width(), bay.height(), sash, skirt, o.sashCorner, o.aldoksDrawing) : null;
  Pane pane = new Pane(number, beads == null ? width : beads[0], beads == null ? height : beads[1],
   sash, skirt, opening ? o.adapterCode : null);
  if (pane.width <= 0 || pane.height <= 0) {
   throw new IllegalArgumentException(number + ". bölmede cam için iç en ve boydan 10 mm düşülemiyor.");
  }
  String beadRole = opening ? "Kanat cam çıtası" : "Sabit cam çıtası";
  if (beads != null) addAldoksBead(plan, bead, beadRole, beads[0], beads[1], o.quantity);
  else addBead(plan, bead, beadRole, width, height, o.quantity);
  plan.panes.add(pane);
  plan.glassArea = plan.glassArea.add(ShopDimensions.area(pane.width, pane.height, o.quantity));
 }

 private static int aldoksBeadCheek(TemaLayout.Section sash) {
  switch (sash.code) {
   case "T.3002": return 420;
   case "T.3005": return 595;
   case "T.3008": return 930;
   default: return Math.max(0, sash.face - ALDOKS_BEAD_END);
  }
 }

 private static int beadCutSequence(Cut cut) {
  if (cut.role.endsWith("cam çıtası yatay")) return 1;
  if (cut.role.endsWith("cam çıtası dikey")) return 2;
  return 0;
 }

 private static void addAldoksBead(Plan plan, Bead bead, String role, int width, int height, int quantity) {
  if (bead == null) return;
  if (width <= 0 || height <= 0) {
   throw new IllegalArgumentException(role + " seçilen açıklığa sığmıyor.");
  }
  plan.addCut(bead.code, role + " yatay", width, 90, 2 * quantity);
  plan.addCut(bead.code, role + " dikey", height, 90, 2 * quantity);
 }

 private static void addBead(Plan plan, Bead bead, String role, int width, int height, int quantity) {
  if (bead == null) return;
  // Long points are measured on the bead seating line.
  width += 2 * bead.edgeAdjustment;
  height += 2 * bead.edgeAdjustment;
  if (width <= 2 * bead.face || height <= 2 * bead.face) {
   throw new IllegalArgumentException(role + " seçilen açıklığa sığmıyor. Çıta oturma ölçüsünü kontrol edin.");
  }
  addRectangle(plan, bead.code, role, width, height, bead.face, bead.corner, quantity);
 }
}
