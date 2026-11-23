
package com.profilkesim.pro;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
    LinearLayout root, body;
    int navy = 0xff173447;
    int teal = 0xff087e89;

    int dp(int n) {
        return (int)(n * getResources().getDisplayMetrics().density);
    }

    GradientDrawable box(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(12));
        return d;
    }

    TextView text(String s, int size) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(navy);
        t.setPadding(dp(12), dp(12), dp(12), dp(12));
        return t;
    }

    void page(String title) {
        root = new LinearLayout(this);
        root.setOrientation(1);
        root.setBackground(new GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            new int[]{0xffc3dbe0, 0xfff3f7f7, 0xffa9cbd0}
        ));

        int imageId = getResources().getIdentifier(
            "cambalkon", "drawable", getPackageName()
        );
        if (imageId != 0) root.setBackgroundResource(imageId);

        setContentView(root);

        TextView brand = text("muhammet can . K", 12);
        brand.setTextColor(Color.WHITE);
        brand.setGravity(Gravity.CENTER);
        brand.setBackgroundColor(navy);
        root.addView(brand, new LinearLayout.LayoutParams(-1, dp(44)));

        ScrollView scroll = new ScrollView(this);
        root.addView(scroll);

        body = new LinearLayout(this);
        body.setOrientation(1);
        body.setPadding(dp(14), dp(12), dp(14), dp(20));
        scroll.addView(body);

        TextView h = text(title, 23);
        h.setTypeface(null, Typeface.BOLD);
        h.setBackground(box(0xf2ffffff));
        body.addView(h);
    }

    void note(String s) {
        TextView t = text(s, 14);
        t.setBackground(box(0xeeffffff));
        body.addView(t);
    }

    void button(String s, Runnable r) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setBackground(box(teal));

        LinearLayout.LayoutParams p =
            new LinearLayout.LayoutParams(-1, dp(54));
        p.setMargins(0, dp(6), 0, dp(6));
        body.addView(b, p);
        b.setOnClickListener(v -> r.run());
    }

    EditText input(String hint, String val) {
        EditText e = new EditText(this);
        e.setText(val);
        e.setHint(hint);
        e.setTextSize(17);
        e.setPadding(dp(12), dp(12), dp(12), dp(12));
        e.setBackground(box(0xf8ffffff));

        LinearLayout.LayoutParams p =
            new LinearLayout.LayoutParams(-1, dp(55));
        p.setMargins(0, dp(6), 0, dp(6));
        body.addView(e, p);
        return e;
    }

    int val(EditText e) {
        return Integer.parseInt(e.getText().toString().trim());
    }

    void result(String s) {
        TextView t = text(s, 16);
        t.setTextIsSelectable(true);
        t.setBackground(box(0xf8ffffff));
        body.addView(t);
    }

    void bad() {
        Toast.makeText(this, "Ölçü veya adet hatalı", Toast.LENGTH_LONG).show();
    }

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);
        home();
    }

    @Override
    public void onBackPressed() {
        home();
    }

    void home() {
        page("Profil Kesim Pro");
        note("Cam balkon ve doğrama hesaplama • Ölçüler mm");

        button("Profil kesim optimizasyonu", () -> opt());
        button("Cam alanı ve adet hesabı", () -> glass());
        button("Kanat eşit bölme hesabı", () -> panels());

        for (String s : new String[]{
            "Tiara Twin", "Statü", "Sürme", "Giyotin",
            "Cam tavan", "PVC", "Sineklik", "Duşakabin",
            "ASAŞ RST116 Hebeschiebe"
        }) {
            button(s, () -> system(s));
        }
    }

    void opt() {
        page("Profil kesim optimizasyonu");
        note("Kesimler: Her satır 1250x3 biçiminde. Testere payı parça başına eklenir. Azalan ilk uygun yerleştirme yöntemi kullanılır.");

        EditText stock = input("Stok boyu", "6000");
        EditText kerf = input("Testere payı", "3");
        EditText cuts = input("Ölçü x adet", "1250x3\n850x4");

        cuts.setSingleLine(false);
        cuts.setMinLines(4);
        cuts.getLayoutParams().height = dp(140);

        button("Kesim planı oluştur", () -> {
            try {
                int length = val(stock);
                int saw = val(kerf);
                if (length <= 0 || saw < 0) throw new Exception();

                ArrayList<Integer> pieces = new ArrayList<>();

                for (String line : cuts.getText().toString().split("[\\n,;]+")) {
                    String[] a = line.trim()
                        .toLowerCase(Locale.ROOT)
                        .replace(" ", "")
                        .split("[x×*]");

                    if (a[0].isEmpty()) continue;

                    int size = Integer.parseInt(a[0]);
                    int qty = a.length > 1 ? Integer.parseInt(a[1]) : 1;

                    if (size <= 0 || qty < 1 || qty > 1000 ||
                        size + saw > length || pieces.size() + qty > 3000) {
                        throw new Exception();
                    }

                    for (int i = 0; i < qty; i++) pieces.add(size);
                }

                if (pieces.isEmpty()) throw new Exception();

                Collections.sort(pieces, Collections.reverseOrder());

                ArrayList<ArrayList<Integer>> bins = new ArrayList<>();
                ArrayList<Integer> used = new ArrayList<>();
                long total = 0;

                for (int size : pieces) {
                    total += size;
                    int target = -1;

                    for (int i = 0; i < used.size(); i++) {
                        if (used.get(i) + size + saw <= length) {
                            target = i;
                            break;
                        }
                    }

                    if (target < 0) {
                        target = bins.size();
                        bins.add(new ArrayList<Integer>());
                        used.add(0);
                    }

                    bins.get(target).add(size);
                    used.set(target, used.get(target) + size + saw);
                }

                StringBuilder out = new StringBuilder(
                    "Gerekli profil: " + bins.size() + " boy\n" +
                    "Parça: " + pieces.size() + " adet\n" +
                    "Net kesim: " + total + " mm\n" +
                    "Kalan: " + ((long) bins.size() * length -
                        total - (long) pieces.size() * saw) + " mm\n\n"
                );

                for (int i = 0; i < bins.size(); i++) {
                    out.append("Boy ").append(i + 1)
                        .append(": ").append(bins.get(i))
                        .append("\nKalan: ")
                        .append(length - used.get(i))
                        .append(" mm\n\n");
                }

                result(out.toString());

            } catch (Exception ex) {
                bad();
            }
        });

        button("Ana menü", () -> home());
    }

    void glass() {
        page("Cam alan hesabı");

        EditText w = input("En mm", "870");
        EditText h = input("Boy mm", "1265");
        EditText n = input("Adet", "1");

        button("Hesapla", () -> {
            try {
                int a = val(w), b = val(h), c = val(n);
                if (a <= 0 || b <= 0 || c <= 0) throw new Exception();

                result(String.format(Locale.US,
                    "Tek cam: %.3f m²\nToplam: %.3f m²",
                    a * b / 1000000.0,
                    a * b * c / 1000000.0));

            } catch (Exception e) {
                bad();
            }
        });

        button("Ana menü", () -> home());
    }

    void panels() {
        page("Kanat bölme hesabı");
        note("Sistem payları hariç teorik eşit bölme.");

        EditText w = input("Açıklık eni mm", "4100");
        EditText n = input("Kanat adedi", "6");
        EditText ded = input("Toplam düşüm mm", "0");

        button("Hesapla", () -> {
            try {
                int a = val(w), b = val(n), c = val(ded);
                if (a <= 0 || b <= 0 || c < 0 || c >= a)
                    throw new Exception();

                result(String.format(Locale.US,
                    "Teorik kanat eni: %.1f mm",
                    (a - c) / (double) b));

            } catch (Exception e) {
                bad();
            }
        });

        button("Ana menü", () -> home());
    }

    void system(String s) {
        page(s);
        note("Ön ölçülendirme. Gerçek profil/cam kesim payları için üretici kataloğu gereklidir.");

        EditText w = input("Açıklık eni mm", "3000");
        EditText h = input("Açıklık boyu mm", "2200");
        EditText n = input("Bölüm adedi", "4");

        button("Ön hesapla", () -> {
            try {
                int a = val(w), b = val(h), c = val(n);
                if (a <= 0 || b <= 0 || c <= 0)
                    throw new Exception();

                result(String.format(Locale.US,
                    "Sistem: %s\nAlan: %.2f m²\n" +
                    "Teorik bölüm eni: %.1f mm\n\n" +
                    "Bunlar nihai imalat kesim ölçüsü değildir.",
                    s, a * b / 1000000.0, a / (double) c));

            } catch (Exception e) {
                bad();
            }
        });

        button("Ana menü", () -> home());
    }
}

