package com.profilkesim.pro;

import java.util.Locale;

final class TemaCatalog {
 static final String DETAILS = "https://temaaluminyum.com.tr/wp-content/uploads/2016/10/isi-yalitimsiz-kapi-pencere-sistemi-katalogu.pdf";

 // The dimensions and images below come from the manufacturer’s section sheets.
 // The glass, gasket and connector clearances are assembly dimensions.
 private static final Profile C60_STANDARD = profile(3101, "Standart kutu", 490, 600, 14, 79, R.drawable.tema_3101);
 private static final Profile C60_WIDE = profile(3102, "Geniş kutu", 900, 600, 14, 79, R.drawable.tema_3102);
 private static final Profile C60_SLIM = profile(3103, "İnce çift kanallı", 207, 600, 14, 79, R.drawable.tema_3103);
 private static final Profile C60_ONE_SIDE = profile(3104, "Geniş tek kanallı", 1100, 600, 16, 79, R.drawable.tema_3104);
 private static final Profile C60_SASH = profile(3105, "Standart kanat", 660, 600, 14, 80, R.drawable.tema_3105);
 static final Profile C60_ADAPTER = profile(3107, "Kanat adaptörü", 220, 559, 0, 80, R.drawable.tema_3107);

 enum Series {
  C60("C60", "C60", 0xff4ff3d2,
   new Profile[]{C60_STANDARD, C60_WIDE, C60_ONE_SIDE},
   new Profile[]{C60_STANDARD, C60_WIDE, C60_SLIM},
   new Profile[]{C60_SASH},
   new Profile[]{profile(3112, "Cam çıtası · eğimli", 220, 165, 0, 82, R.drawable.tema_3112),
    profile(3111, "Cam çıtası · düz", 170, 235, 0, 82, R.drawable.tema_3111)}),
  ALDOKS("Aldoks", "ALDOKS", 0xffffb86d,
   new Profile[]{profile(3001, "Dar kasa", 420, 455, 11, 71, R.drawable.tema_3001),
    profile(3004, "Geniş kasa", 585, 454, 11, 71, R.drawable.tema_3004),
    profile(3007, "Ekstra geniş kasa", 907, 455, 15, 71, R.drawable.tema_3007)},
   new Profile[]{profile(3003, "Standart kayıt", 590, 455, 11, 73, R.drawable.tema_3003),
    profile(3006, "Geniş kayıt", 430, 454, 11, 73, R.drawable.tema_3006),
    profile(3009, "Ekstra geniş kayıt", 1100, 455, 17, 73, R.drawable.tema_3009)},
   new Profile[]{profile(3002, "Standart kanat", 590, 455, 11, 72, R.drawable.tema_3002),
    profile(3005, "Geniş kanat", 765, 454, 11, 72, R.drawable.tema_3005),
    profile(3008, "Ekstra geniş kanat", 1100, 454, 14, 72, R.drawable.tema_3008)},
   new Profile[]{profile(3020, "Cam çıtası · geniş", 170, 291, 0, 74, R.drawable.tema_3020),
    profile(3021, "Cam çıtası · dar", 170, 140, 0, 74, R.drawable.tema_3021)});

  final String label, url;
  final int accent;
  final Profile[] frames, mullions, sashes, beads;

  Series(String label, String file, int accent, Profile[] frames, Profile[] mullions, Profile[] sashes, Profile[] beads) {
   this.label = label;
   this.accent = accent;
   url = "https://temaaluminyum.com.tr/wp-content/uploads/2022/03/"
    + file + "-SI%CC%87STEM-PROFI%CC%87LLERI%CC%87.pdf";
   this.frames = frames;
   this.mullions = mullions;
   this.sashes = sashes;
   this.beads = beads;
  }

  String nameOf(String code) {
   Profile profile = find(code);
   return profile == null ? "Özel profil" : profile.name;
  }

  String displayOf(String code) {
   Profile profile = find(code);
   return profile == null ? "Özel profil" : profile.label();
  }

  boolean isBead(String code) {
   for (Profile bead : beads) if (bead.code.equals(code)) return true;
   return false;
  }

  boolean isBead(String code, String name) {
   return isBead(code) || (" " + CutSheet.identity(name) + " ").matches(".* (CITA|CITASI|CITALAR|CITALARI) .*");
  }

  Profile find(String code) {
   if (this == C60 && C60_ADAPTER.code.equals(code)) return C60_ADAPTER;
   for (Profile[] group : new Profile[][]{frames, mullions, sashes, beads}) {
    for (Profile profile : group) if (profile.code.equals(code)) return profile;
   }
   return null;
  }
 }

 static final class Profile {
  final String code, name;
  final int face, depth, wall, page, image;

  private Profile(String code, String name, int face, int depth, int wall, int page, int image) {
   this.code = code;
   this.name = name;
   this.face = face;
   this.depth = depth;
   this.wall = wall;
   this.page = page;
   this.image = image;
  }

  String label() { return name + " · " + ShopDimensions.mm(face) + " mm"; }

  String dimensions() {
   String section = ShopDimensions.mm(face) + " × " + ShopDimensions.mm(depth) + " mm";
   return wall == 0 ? section + " katalog ölçüsü" : section + " · et " + ShopDimensions.mm(wall) + " mm";
  }
 }

 private TemaCatalog() {}

 private static Profile profile(int code, String name, int face, int depth, int wall, int page, int image) {
  return new Profile("T." + code, name, face, depth, wall, page, image);
 }

 static String normalizeCode(String text) {
  return text.trim().toUpperCase(Locale.ROOT).replaceFirst("^T[ .-]*(3[01][0-9]{2})$", "T.$1");
 }
}
