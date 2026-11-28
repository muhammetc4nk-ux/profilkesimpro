package com.profilkesim.pro;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;
import java.util.Set;

final class TemaDiagramView extends View {
 private final TemaDiagramRenderer renderer;

 TemaDiagramView(Context context, TemaJoinery.Order order, TemaJoinery.Plan plan,
                  TemaCatalog.Series series, Set<Integer> leftCells) {
  super(context);
  renderer = new TemaDiagramRenderer(order, plan, series, leftCells, getResources().getDisplayMetrics().density, false);
  setContentDescription("Profil adları, bölme iç ölçüleri ve cam kesimleri olan doğrama çizimi, " + plan.layout.bays.size() + " bölme");
 }

 @Override protected void onDraw(Canvas canvas) {
  renderer.draw(canvas, getWidth(), getHeight());
 }
}
