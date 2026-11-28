package com.profilkesim.pro;

import static org.junit.Assert.*;

import org.junit.Test;

public final class WorkshopIdentityTest {
 @Test public void albertGateAcceptsOnlyTheRequestedPassword() {
  assertTrue(AlbertDealerAccess.accepts("alb12"));
  assertFalse(AlbertDealerAccess.accepts("ALB12"));
  assertFalse(AlbertDealerAccess.accepts("alb12 "));
  assertFalse(AlbertDealerAccess.accepts(""));
 }

 @Test public void networkTimeAdvancesMonotonicallyAndReturnsToThePhoneTimeOffline() {
  long server = 1_795_816_800_000L;
  long local = server + 60_000L;
  WorkshopClock.Reading online = WorkshopClock.select(true, server, 1400, 6100, local);
  assertTrue(online.online);
  assertEquals(server + 4700, online.millis);
  WorkshopClock.Reading offline = WorkshopClock.select(false, server, 1400, 6100, local);
  assertFalse(offline.online);
  assertEquals(local, offline.millis);
  assertEquals(local, WorkshopClock.select(true, server, 1400, 800_000, local).millis);
 }
}
