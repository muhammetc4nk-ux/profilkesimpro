package com.profilkesim.pro;

import android.os.Bundle;
import android.os.Parcel;
import android.util.Base64;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

final class TemaPosition {
 final String id;
 String name;
 String source = "";
 Bundle drawing;
 List<TemaCutBatch.Line> cuts = Collections.emptyList();

 TemaPosition(String name) { this(java.util.UUID.randomUUID().toString(), name); }

 private TemaPosition(String id, String name) {
  this.id = id;
  this.name = name;
 }

 JSONObject toJson() throws JSONException {
  JSONObject saved = new JSONObject().put("id", id).put("name", name).put("source", source);
  if (drawing != null) {
   Parcel parcel = Parcel.obtain();
   try {
    parcel.writeBundle(drawing);
    saved.put("drawing", Base64.encodeToString(parcel.marshall(), Base64.NO_WRAP));
   } finally {
    parcel.recycle();
   }
  }
  JSONArray lines = new JSONArray();
  for (TemaCutBatch.Line cut : cuts) {
   lines.put(new JSONArray().put(cut.key).put(cut.label).put(cut.length).put(cut.quantity).put(cut.ends));
  }
  return saved.put("cuts", lines);
 }

 static TemaPosition fromJson(JSONObject saved, ClassLoader loader) throws JSONException {
  TemaPosition position = new TemaPosition(saved.getString("id"), saved.getString("name"));
  position.source = saved.optString("source");
  if (saved.has("drawing")) {
   byte[] bytes = Base64.decode(saved.getString("drawing"), Base64.DEFAULT);
   Parcel parcel = Parcel.obtain();
   try {
    parcel.unmarshall(bytes, 0, bytes.length);
    parcel.setDataPosition(0);
    position.drawing = parcel.readBundle(loader);
   } finally {
    parcel.recycle();
   }
  }
  JSONArray rows = saved.getJSONArray("cuts");
  List<TemaCutBatch.Line> cuts = new ArrayList<>(rows.length());
  for (int i = 0; i < rows.length(); i++) {
   JSONArray row = rows.getJSONArray(i);
   cuts.add(new TemaCutBatch.Line(row.getString(0), row.getString(1), row.getInt(2), row.getInt(3), row.getString(4)));
  }
  position.cuts = cuts;
  return position;
 }
}
