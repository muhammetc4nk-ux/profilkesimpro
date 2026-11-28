package com.profilkesim.pro;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.SystemClock;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

final class WorkshopClock {
 private static final String TIME_URL = "https://www.google.com/generate_204";
 private static final long RESYNC_MS = TimeUnit.MINUTES.toMillis(5);
 private static final long MAX_SAMPLE_AGE_MS = TimeUnit.MINUTES.toMillis(12);
 private static final long RETRY_MS = TimeUnit.SECONDS.toMillis(30);
 private static final Locale TURKISH = new Locale("tr", "TR");

 static final class Reading {
  final long millis;
  final boolean online;

  Reading(long millis, boolean online) {
   this.millis = millis;
   this.online = online;
  }
 }

 private static final class Sample {
  final Network network;
  final long millis, elapsed;

  Sample(Network network, long millis, long elapsed) {
   this.network = network;
   this.millis = millis;
   this.elapsed = elapsed;
  }
 }

 private final ConnectivityManager connectivity;
 private volatile Sample sample;
 private Network attemptedNetwork;
 private long nextAttempt;
 private CountDownLatch pending;

 WorkshopClock(Context context) {
  connectivity = (ConnectivityManager)context.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
 }

 Reading read() {
  Network network = network();
  Sample anchor = sample;
  boolean connected = anchor != null && network != null && network.equals(anchor.network);
  long elapsed = SystemClock.elapsedRealtime();
  return select(connected, anchor == null ? 0 : anchor.millis, anchor == null ? 0 : anchor.elapsed,
   elapsed, System.currentTimeMillis());
 }

 void refresh() { synchronize(network()); }

 long forDocument() {
  CountDownLatch refresh = synchronize(network());
  Reading now = read();
  if (now.online || refresh == null) return now.millis;
  try {
   refresh.await(3200, TimeUnit.MILLISECONDS);
  } catch (InterruptedException error) {
   Thread.currentThread().interrupt();
  }
  return read().millis;
 }

 static String format(long millis, String pattern) {
  return new SimpleDateFormat(pattern, TURKISH).format(new Date(millis));
 }

 static Reading select(boolean connected, long server, long anchorElapsed, long nowElapsed, long device) {
  long elapsed = nowElapsed - anchorElapsed;
  if (connected && server > 0 && elapsed >= 0 && elapsed < MAX_SAMPLE_AGE_MS) {
   return new Reading(server + elapsed, true);
  }
  return new Reading(device, false);
 }

 private Network network() {
  if (connectivity == null) return null;
  Network current = connectivity.getActiveNetwork();
  NetworkCapabilities capabilities = current == null ? null : connectivity.getNetworkCapabilities(current);
  return capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ? current : null;
 }

 private synchronized CountDownLatch synchronize(Network current) {
  if (current == null) {
   sample = null;
   attemptedNetwork = null;
   return null;
  }
  if (pending != null) return pending;
  if (current.equals(attemptedNetwork) && SystemClock.elapsedRealtime() < nextAttempt) return null;
  attemptedNetwork = current;
  nextAttempt = SystemClock.elapsedRealtime() + RETRY_MS;
  CountDownLatch request = new CountDownLatch(1);
  pending = request;
  new Thread(() -> fetch(current, request), "workshop-clock").start();
  return request;
 }

 private void fetch(Network current, CountDownLatch request) {
  HttpURLConnection connection = null;
  try {
   long start = SystemClock.elapsedRealtime();
   connection = (HttpURLConnection)current.openConnection(new URL(TIME_URL));
   connection.setConnectTimeout(2300);
   connection.setReadTimeout(2200);
   connection.setInstanceFollowRedirects(false);
   connection.setUseCaches(false);
   connection.setRequestProperty("Cache-Control", "no-cache");
   if (connection.getResponseCode() != HttpURLConnection.HTTP_NO_CONTENT) return;
   long received = SystemClock.elapsedRealtime();
   long date = connection.getHeaderFieldDate("Date", 0);
   if (date < 1609459200000L || !current.equals(network())) return;
   synchronized (this) {
    sample = new Sample(current, date + (received - start) / 2, received);
    nextAttempt = received + RESYNC_MS;
   }
  } catch (IOException | RuntimeException ignored) {
   // The wall clock remains available during a failed or offline refresh.
  } finally {
   if (connection != null) connection.disconnect();
   synchronized (this) {
    pending = null;
    request.countDown();
   }
  }
 }
}
