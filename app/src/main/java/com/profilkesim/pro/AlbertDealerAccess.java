package com.profilkesim.pro;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

final class AlbertDealerAccess {
 private static final byte[] PASSWORD_HASH = {
  -51, -122, 26, 60, -80, -122, 38, -92, 40, 81, -92, -33, -112, -4, 17, 113, 109, -88, 84, -3, -74, -38, 38, -91, -12, 9, 117, 91, 6, 48, 66, 48
 };

 private AlbertDealerAccess() {}

 static boolean accepts(CharSequence input) {
  try {
   byte[] digest = MessageDigest.getInstance("SHA-256").digest(input.toString().getBytes(StandardCharsets.UTF_8));
   return MessageDigest.isEqual(PASSWORD_HASH, digest);
  } catch (NoSuchAlgorithmException error) {
   throw new IllegalStateException("SHA-256 unavailable", error);
  }
 }
}
