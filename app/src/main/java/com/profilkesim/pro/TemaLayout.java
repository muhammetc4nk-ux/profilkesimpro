package com.profilkesim.pro;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Resolves drawing centre lines into actual profile faces and clear openings. */
final class TemaLayout {
 static final int LEFT = 0, TOP = 1, RIGHT = 2, BOTTOM = 3;

 static final class Section {
  final String code;
  final int face;
  Section(String code, int face) {
   if (face <= 0) throw new IllegalArgumentException("Profil genişliği sıfırdan büyük olmalı.");
   this.code = code;
   this.face = face;
  }
 }

 static final class Frame {
  final Section left, top, right, bottom;
  Frame(Section left, Section top, Section right, Section bottom) {
   this.left = left;
   this.top = top;
   this.right = right;
   this.bottom = bottom;
  }
  Frame(Section section) { this(section, section, section, section); }
 }

 static final class Span {
  final int start, end;
  Span(int start, int end) { this.start = start; this.end = end; }
 }

 static final class Opening {
  final int left, top, right, bottom;
  final boolean door;
  Opening(int left, int top, int right, int bottom, boolean door) {
   if (left >= right || top >= bottom) throw new IllegalArgumentException("Bölme, seçilen profil genişliklerine sığmıyor.");
   this.left = left;
   this.top = top;
   this.right = right;
   this.bottom = bottom;
   this.door = door;
  }
  int width() { return right - left; }
  int height() { return bottom - top; }
 }

 static final class Record {
  final boolean vertical;
  final int axis, start, end;
  final Section section;
  Record(int x1, int y1, int x2, int y2, Section section) {
   vertical = x1 == x2;
   if ((vertical && y1 >= y2) || (!vertical && (y1 != y2 || x1 >= x2))) {
    throw new IllegalArgumentException("Orta kayıt çizgisini kontrol edin.");
   }
   axis = vertical ? x1 : y1;
   start = vertical ? y1 : x1;
   end = vertical ? y2 : x2;
   this.section = section;
  }
 }

 static final class Member {
  final Section section;
  final boolean vertical, frameStart, frameEnd, floorEnd;
  final int axis, start, end;
  Member(Section section, boolean vertical, int axis, int start, int end,
         boolean frameStart, boolean frameEnd, boolean floorEnd) {
   this.section = section;
   this.vertical = vertical;
   this.axis = axis;
   this.start = start;
   this.end = end;
   this.frameStart = frameStart;
   this.frameEnd = frameEnd;
   this.floorEnd = floorEnd;
  }
  int cutLength(int frameJoint, int mullionJoint) {
   return end - start + (frameStart ? frameJoint : mullionJoint)
    + (floorEnd ? 0 : frameEnd ? frameJoint : mullionJoint);
  }
 }

 final int width, height, scale, innerWidth, innerHeight, bottomFace;
 final Frame frame;
 final List<Opening> bays = new ArrayList<>();
 final List<Span> rails;
 final List<Record> records;

 TemaLayout(int width, int height, Frame frame, int scale, List<int[]> cells, List<Record> records,
            Set<Integer> floorDoors) {
  this.width = width;
  this.height = height;
  this.frame = frame;
  this.scale = scale;
  this.records = Collections.unmodifiableList(new ArrayList<>(records));
  if (scale <= 0 || cells.isEmpty()) throw new IllegalArgumentException("Çizimde en az bir bölme olmalı.");
  for (Record record : records) {
   if (record.axis <= 0 || record.axis >= scale || record.start < 0 || record.end > scale) {
    throw new IllegalArgumentException("Orta kayıt kasa sınırları içinde olmalı.");
   }
  }
  innerWidth = width - frame.left.face - frame.right.face;
  if (innerWidth <= 0 || height <= frame.top.face) throw new IllegalArgumentException("Profil payları dış ölçüyü aşıyor.");
  List<Span> gaps = new ArrayList<>();
  for (int i = 0; i < cells.size(); i++) {
   int[] cell = cells.get(i);
   if (cell[LEFT] < 0 || cell[TOP] < 0 || cell[RIGHT] > scale || cell[BOTTOM] > scale
     || cell[LEFT] >= cell[RIGHT] || cell[TOP] >= cell[BOTTOM]) {
    throw new IllegalArgumentException("Çizimde bölme sınırını kontrol edin.");
   }
   if (cell[BOTTOM] != scale || !floorDoors.contains(i + 1)) continue;
   int l = cell[LEFT] == 0 ? 0 : x(cell[LEFT]) - wallFace(cell, LEFT) / 2;
   int r = cell[RIGHT] == scale ? width : x(cell[RIGHT]) + afterCentre(wallFace(cell, RIGHT));
   gaps.add(new Span(l, r));
  }
  rails = bottomRails(width, gaps);
  bottomFace = rails.isEmpty() ? 0 : frame.bottom.face;
  innerHeight = height - frame.top.face - bottomFace;
  if (innerHeight <= 0) throw new IllegalArgumentException("Profil payları dış ölçüyü aşıyor.");
  for (int i = 0; i < cells.size(); i++) {
   int[] cell = cells.get(i);
   boolean door = cell[BOTTOM] == scale && floorDoors.contains(i + 1);
   bays.add(new Opening(x(cell[LEFT]) + afterCentre(wallFace(cell, LEFT)),
    y(cell[TOP]) + afterCentre(wallFace(cell, TOP)),
    x(cell[RIGHT]) - wallFace(cell, RIGHT) / 2,
    door ? height : y(cell[BOTTOM]) - wallFace(cell, BOTTOM) / 2, door));
  }
 }

 static List<Span> bottomRails(int width, List<Span> gaps) {
  Collections.sort(gaps, (a, b) -> Integer.compare(a.start, b.start));
  List<Span> rails = new ArrayList<>();
  int start = 0;
  for (Span gap : gaps) {
   if (gap.start > start) rails.add(new Span(start, gap.start));
   start = Math.max(start, gap.end);
  }
  if (start < width) rails.add(new Span(start, width));
  return rails;
 }

 int x(int at) { return frame.left.face + (int) Math.round((double) innerWidth * at / scale); }
 int y(int at) { return frame.top.face + (int) Math.round((double) innerHeight * at / scale); }

 List<Member> members(TemaJoinery.Continuous continuous) {
  List<Member> members = new ArrayList<>();
  for (Record record : records) {
   TreeSet<Integer> ends = new TreeSet<>();
   if (record.vertical != (continuous == TemaJoinery.Continuous.VERTICAL)) {
    for (Record other : records) {
     if (other.vertical == record.vertical || other.start >= record.axis || other.end <= record.axis
       || other.axis <= record.start || other.axis >= record.end) continue;
     ends.add(other.axis);
    }
   }
   ends.add(record.end);
   int start = record.start;
   int fixed = record.vertical ? x(record.axis) : y(record.axis);
   for (int end : ends) {
    boolean floor = record.vertical && end == scale && !hasSillAt(fixed);
    int first = coordinate(record, start) + afterCentre(contactFace(record, start));
    int last = floor ? height : coordinate(record, end) - contactFace(record, end) / 2;
    members.add(new Member(record.section, record.vertical, fixed, first, last, start == 0, end == scale, floor));
    start = end;
   }
  }
  return members;
 }

 private boolean hasSillAt(int x) {
  for (Span rail : rails) if (x >= rail.start && x <= rail.end) return true;
  return false;
 }

 private int coordinate(Record record, int at) { return record.vertical ? y(at) : x(at); }

 private int contactFace(Record piece, int at) {
  if (at == 0 || at == scale) return 0;
  int axis = piece.vertical ? x(piece.axis) : y(piece.axis);
  int low = axis - piece.section.face / 2, high = axis + afterCentre(piece.section.face);
  int face = 0;
  for (Record support : records) {
   if (support.vertical == piece.vertical || support.axis != at) continue;
   int start = coordinate(support, support.start), end = coordinate(support, support.end);
   if (start < high && end > low) face = Math.max(face, support.section.face);
  }
  return face;
 }

 private int wallFace(int[] cell, int edge) {
  if (cell[edge] == 0 || cell[edge] == scale) return 0;
  boolean vertical = edge == LEFT || edge == RIGHT;
  int start = cell[vertical ? TOP : LEFT], end = cell[vertical ? BOTTOM : RIGHT];
  int face = 0;
  for (Record boundary : records) {
   if (boundary.vertical == vertical && boundary.axis == cell[edge] && boundary.start < end && boundary.end > start) {
    face = Math.max(face, boundary.section.face);
   }
  }
  return face;
 }

 private static int afterCentre(int face) { return face - face / 2; }
}
