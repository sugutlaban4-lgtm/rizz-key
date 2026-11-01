package com.laban.rizzkey;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * The part that makes this behave like Wrizz: an actual language model.
 * Without a key it returns an error and the app falls back to the written lines.
 */
final class AiClient {

    private static final String ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=";

    interface Callback {
        void done(String[] lines, String error);
    }

    private AiClient() {
    }

    static String apiKey(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("rizz", Context.MODE_PRIVATE);
        return prefs.getString("ai_key", "").trim();
    }

    static void saveKey(Context context, String key) {
        context.getSharedPreferences("rizz", Context.MODE_PRIVATE)
                .edit().putString("ai_key", key.trim()).apply();
    }

    static String buildPrompt(String stage, String tone, String chat) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You help someone reply in a chat. Situation: ").append(stage)
                .append(". Tone: ").append(tone).append(". ");
        if (chat == null || chat.trim().isEmpty()) {
            prompt.append("There is no earlier conversation, so write an opening message. ");
        } else {
            prompt.append("This is what was said: \"").append(chat.trim()).append("\". ");
        }
        prompt.append("Write three short replies, each one or two sentences, ")
                .append("natural for WhatsApp in Kenya, no numbering, no quotes, ")
                .append("no explanation. Put each reply on its own line.");
        return prompt.toString();
    }

    static void ask(String key, String prompt, Callback callback) {
        if (key == null || key.trim().isEmpty()) {
            callback.done(null, "Add your AI key in the Rizz key app first");
            return;
        }
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                JSONObject part = new JSONObject().put("text", prompt);
                JSONArray parts = new JSONArray().put(part);
                JSONObject content = new JSONObject().put("parts", parts);
                JSONArray contents = new JSONArray().put(content);
                JSONObject body = new JSONObject().put("contents", contents);

                URL url = new URL(ENDPOINT + key.trim());
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(30000);
                connection.setDoOutput(true);

                try (OutputStream out = connection.getOutputStream()) {
                    out.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }

                int code = connection.getResponseCode();
                InputStream stream = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
                String raw = readAll(stream);

                if (code >= 400) {
                    callback.done(null, explain(code, raw));
                    return;
                }

                JSONObject reply = new JSONObject(raw);
                JSONArray candidates = reply.optJSONArray("candidates");
                if (candidates == null || candidates.length() == 0) {
                    callback.done(null, "The model sent nothing back");
                    return;
                }
                JSONObject first = candidates.getJSONObject(0);
                String text = first.getJSONObject("content").getJSONArray("parts")
                        .getJSONObject(0).getString("text");

                String[] lines = clean(text);
                if (lines.length == 0) {
                    callback.done(null, "The model sent nothing usable");
                } else {
                    callback.done(lines, null);
                }
            } catch (Exception error) {
                callback.done(null, "No connection, or the key is wrong");
            } finally {
                if (connection != null) connection.disconnect();
            }
        }).start();
    }

    private static String explain(int code, String raw) {
        if (code == 400 || code == 403) return "That key is not accepted. Check it in the Rizz key app";
        if (code == 429) return "Too many asks just now. Wait a minute";
        if (raw != null && raw.contains("API key not valid")) return "That key is not accepted";
        return "The model answered with an error (" + code + ")";
    }

    private static String[] clean(String text) {
        if (text == null) return new String[0];
        String body = text.replace("\r", "");
        body = body.replaceAll("(?m)^\\s*(\\d+[.)]|[-*])\\s*", "");
        String[] raw = body.split("\n");
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String line : raw) {
            String trimmed = line.trim();
            if (trimmed.length() < 3) continue;
            if (trimmed.startsWith("```")) continue;
            out.add(trimmed);
            if (out.size() == 3) break;
        }
        return out.toArray(new String[0]);
    }

    private static String readAll(InputStream stream) {
        if (stream == null) return "";
        StringBuilder text = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) text.append(line);
        } catch (Exception ignored) {
        }
        return text.toString();
    }
}
