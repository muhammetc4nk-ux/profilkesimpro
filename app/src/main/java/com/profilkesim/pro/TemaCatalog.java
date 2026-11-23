package com.profilkesim.pro;

import java.util.Locale;

final class TemaCatalog {
 static final String DETAILS = "https://temaaluminyum.com.tr/wp-content/uploads/2016/10/isi-yalitimsiz-kapi-pencere-sistemi-katalogu.pdf";

 enum Series {
  C60("C60", "C60", 83,
   new Profile[]{new Profile("T.3101", 490, 600, 79), new Profile("T.3102", 900, 600, 79)},
   new Profile[]{new Profile("T.3101", 490, 600, 84), new Profile("T.3102", 900, 600, 79)},
   new Profile[]{new Profile("T.3105", 660, 600, 80)}),
  ALDOKS("Aldoks", "ALDOKS", 73,
   new Profile[]{new Profile("T.3001", 402, 455, 71), new Profile("T.3004", 585, 454, 71),
    new Profile("T.3007", 907, 455, 71)},
   new Profile[]{new Profile("T.3003", 590, 455, 73), new Profile("T.3006", 765, 454, 73),
    new Profile("T.3009", 1100, 455, 73)},
   new Profile[]{new Profile("T.3002", 590, 455, 72), new Profile("T.3005", 765, 454, 72),
    new Profile("T.3008", 1100, 454, 72)});

  final String label, url;
  final int detailPage;
  final Profile[] frames, mullions, sashes;

  Series(String label, String file, int detailPage, Profile[] frames, Profile[] mullions, Profile[] sashes) {
   this.label = label;
   url = "https://temaaluminyum.com.tr/wp-content/uploads/2022/03/"
    + file + "-SI%CC%87STEM-PROFI%CC%87LLERI%CC%87.pdf";
   this.detailPage = detailPage;
   this.frames = frames;
   this.mullions = mullions;
   this.sashes = sashes;
  }
 }

 static final class Profile {
  final String code;
  final int face, depth, page;

  Profile(String code, int face, int depth, int page) {
   this.code = code;
   this.face = face;
   this.depth = depth;
   this.page = page;
  }

  String label() {
   return code + " · " + ShopDimensions.mm(face) + " × " + ShopDimensions.mm(depth) + " mm";
  }
 }

 private TemaCatalog() {}

 static String normalizeCode(String text) {
  return text.trim().toUpperCase(Locale.ROOT).replaceFirst("^T[ .-]*(3[01][0-9]{2})$", "T.$1");
 }
}
