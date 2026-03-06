package com.cz.fitnessdiary.service;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 轻量联网搜索服务（免 API Key）。
 * 用于在“联网搜索”开启时提供可用的实时上下文，降低幻觉风险。
 */
public final class WebSearchService {

    private static final String SEARCH_ENDPOINT = "https://api.duckduckgo.com/?format=json&no_html=1&skip_disambig=1&q=";
    private static final int CONNECT_TIMEOUT_MS = 8000;
    private static final int READ_TIMEOUT_MS = 10000;
    private static final int MAX_ITEMS = 4;

    private WebSearchService() {
    }

    public static String searchSummary(String query) {
        if (query == null || query.trim().isEmpty()) {
            return "";
        }

        HttpURLConnection connection = null;
        BufferedReader reader = null;
        try {
            String encoded = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.name());
            URL url = new URL(SEARCH_ENDPOINT + encoded);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "FitnessDiary/1.0");

            int code = connection.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                return "";
            }

            reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder body = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line);
            }

            JSONObject json = new JSONObject(body.toString());
            List<String> lines = new ArrayList<>();

            String abstractText = json.optString("AbstractText", "").trim();
            String abstractUrl = json.optString("AbstractURL", "").trim();
            if (!abstractText.isEmpty()) {
                lines.add("1) " + trim(abstractText, 90) + (abstractUrl.isEmpty() ? "" : " [" + abstractUrl + "]"));
            }

            JSONArray related = json.optJSONArray("RelatedTopics");
            if (related != null) {
                appendRelatedTopics(lines, related);
            }

            if (lines.isEmpty()) {
                return "";
            }

            StringBuilder summary = new StringBuilder();
            for (int i = 0; i < Math.min(lines.size(), MAX_ITEMS); i++) {
                if (i > 0) {
                    summary.append('\n');
                }
                summary.append(lines.get(i));
            }
            return summary.toString();
        } catch (Exception ignored) {
            return "";
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (Exception ignored) {
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static void appendRelatedTopics(List<String> lines, JSONArray related) {
        for (int i = 0; i < related.length() && lines.size() < MAX_ITEMS; i++) {
            JSONObject item = related.optJSONObject(i);
            if (item == null) {
                continue;
            }

            if (item.has("Topics")) {
                JSONArray nested = item.optJSONArray("Topics");
                if (nested != null) {
                    appendRelatedTopics(lines, nested);
                }
                continue;
            }

            String text = item.optString("Text", "").trim();
            String url = item.optString("FirstURL", "").trim();
            if (text.isEmpty()) {
                continue;
            }

            int index = lines.size() + 1;
            lines.add(index + ") " + trim(text, 90) + (url.isEmpty() ? "" : " [" + url + "]"));
        }
    }

    private static String trim(String s, int max) {
        if (s == null || s.length() <= max) {
            return s == null ? "" : s;
        }
        return s.substring(0, max).trim() + "...";
    }
}
