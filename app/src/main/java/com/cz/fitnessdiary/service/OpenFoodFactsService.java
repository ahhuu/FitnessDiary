package com.cz.fitnessdiary.service;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Open Food Facts API integration for barcode-based food lookup.
 * API docs: https://world.openfoodfacts.org/files/api-documentation.html
 */
public class OpenFoodFactsService {

    private static final String TAG = "OpenFoodFactsService";
    private static final String API_BASE = "https://world.openfoodfacts.org/api/v0/product/";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public interface LookupCallback {
        void onSuccess(FoodResult result);
        void onNotFound();
        void onError(String message);
    }

    public static class FoodResult {
        public final String name;
        public final float caloriesPer100g;
        public final float proteinPer100g;
        public final float carbsPer100g;
        public final float fatPer100g;
        public final String servingUnit;
        public final int weightPerUnit;

        public FoodResult(String name, float caloriesPer100g, float proteinPer100g,
                float carbsPer100g, float fatPer100g, String servingUnit, int weightPerUnit) {
            this.name = name;
            this.caloriesPer100g = caloriesPer100g;
            this.proteinPer100g = proteinPer100g;
            this.carbsPer100g = carbsPer100g;
            this.fatPer100g = fatPer100g;
            this.servingUnit = servingUnit;
            this.weightPerUnit = weightPerUnit;
        }
    }

    public void lookupByBarcode(String barcode, LookupCallback callback) {
        executor.execute(() -> {
            HttpURLConnection conn = null;
            try {
                String urlStr = API_BASE + URLEncoder.encode(barcode, "UTF-8")
                        + ".json?fields=product_name,nutriments,quantity";
                URL url = new URL(urlStr);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setRequestProperty("User-Agent", "FitnessDiary/1.6 (Android)");

                int code = conn.getResponseCode();
                if (code != 200) {
                    callback.onError("服务器返回错误: " + code);
                    return;
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();

                JSONObject root = new JSONObject(sb.toString());
                int status = root.optInt("status", 0);
                if (status != 1) {
                    callback.onNotFound();
                    return;
                }

                JSONObject product = root.getJSONObject("product");
                String name = product.optString("product_name", "未知食物");
                JSONObject nutriments = product.optJSONObject("nutriments");
                if (nutriments == null) {
                    callback.onNotFound();
                    return;
                }

                float energy100g = (float) nutriments.optDouble("energy-kcal_100g", 0);
                float protein100g = (float) nutriments.optDouble("proteins_100g", 0);
                float carbs100g = (float) nutriments.optDouble("carbohydrates_100g", 0);
                float fat100g = (float) nutriments.optDouble("fat_100g", 0);

                String quantity = product.optString("quantity", null);
                int weightPerUnit = 100;
                if (quantity != null && !quantity.isEmpty()) {
                    String q = quantity.replaceAll("[^0-9]", "");
                    if (!q.isEmpty()) {
                        try {
                            weightPerUnit = Integer.parseInt(q);
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }

                FoodResult result = new FoodResult(name, energy100g, protein100g, carbs100g, fat100g,
                        weightPerUnit > 0 ? "份" : "g", Math.max(weightPerUnit, 100));
                callback.onSuccess(result);

            } catch (Exception e) {
                Log.e(TAG, "Barcode lookup failed", e);
                callback.onError("查询失败: " + e.getMessage());
            } finally {
                if (conn != null)
                    conn.disconnect();
            }
        });
    }

    public void shutdown() {
        executor.shutdown();
    }
}
