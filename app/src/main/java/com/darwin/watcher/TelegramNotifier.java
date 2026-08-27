package com.darwin.watcher;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public final class TelegramNotifier {
    private static final String TAG = "TelegramNotifier";
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    public interface Callback {
        void onResult(boolean success, String message);
    }

    private TelegramNotifier() { }

    public static boolean isNetworkAvailable(Context context) {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Network network = cm.getActiveNetwork();
                    if (network == null) return false;
                    NetworkCapabilities actNw = cm.getNetworkCapabilities(network);
                    return actNw != null && (
                        actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        actNw.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                        actNw.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                    );
                } else {
                    NetworkInfo netInfo = cm.getActiveNetworkInfo();
                    return netInfo != null && netInfo.isConnected();
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "isNetworkAvailable check error: " + e.getMessage());
        }
        return true;
    }

    public static void sendDone(Context context) {
        if (!Prefs.telegramEnabled(context)) {
            Log.d(TAG, "Telegram disabled in preferences.");
            return;
        }
        sendText(context, "✅ Darwin Watcher: Automation task finished successfully on *" + Prefs.targetLabel(context) + "*!", null);
    }

    public static void sendTest(Context context, Callback callback) {
        sendText(context, "🚀 *Darwin Watcher*: Test notification from your device!", callback);
    }

    public static void sendText(Context context, String text, Callback callback) {
        sendText(context, text, null, callback);
    }

    public static void sendText(Context context, String text, String replyMarkupJson, Callback callback) {
        String token = Prefs.telegramToken(context).trim();
        String chat = Prefs.telegramChat(context).trim();

        if (token.length() == 0 || chat.length() == 0) {
            String msg = "Telegram token or Chat ID is missing.";
            Log.w(TAG, msg);
            Prefs.setLastStatus(context, msg);
            if (callback != null) callback.onResult(false, msg);
            return;
        }

        new Thread(new Sender(context.getApplicationContext(), token, chat, text, replyMarkupJson, callback)).start();
    }

    public static void answerCallbackQuery(Context context, String callbackQueryId, String text) {
        String token = Prefs.telegramToken(context).trim();
        if (token.length() == 0 || callbackQueryId == null || callbackQueryId.length() == 0) return;
        new Thread(new CallbackAnswerer(token, callbackQueryId, text)).start();
    }

    public static void sendPhoto(Context context, byte[] photoBytes, String caption, Callback callback) {
        if (!Prefs.telegramEnabled(context)) return;
        String token = Prefs.telegramToken(context).trim();
        String chat = Prefs.telegramChat(context).trim();

        if (token.length() == 0 || chat.length() == 0) {
            String msg = "Telegram token or Chat ID is missing.";
            Log.w(TAG, msg);
            Prefs.setLastStatus(context, msg);
            if (callback != null) callback.onResult(false, msg);
            return;
        }

        new Thread(new PhotoSender(context.getApplicationContext(), token, chat, photoBytes, caption, callback)).start();
    }

    /** Reports the Telegram message_id so a live frame can be edited in place. */
    public interface MessageCallback {
        void onMessage(boolean ok, int messageId, String error);
    }

    /**
     * Posts a photo (messageId <= 0) or replaces the photo on an existing message
     * (messageId > 0). Editing keeps a live view to a single chat message instead of
     * flooding the chat with one image per refresh.
     *
     * Unlike sendPhoto() this is not gated on Prefs.telegramEnabled - it is only ever
     * reached from an explicit Telegram command, so the intent is unambiguous.
     */
    public static void sendOrEditFrame(Context context, int messageId, byte[] jpeg,
                                       String caption, String replyMarkupJson, MessageCallback cb) {
        String token = Prefs.telegramToken(context).trim();
        String chat = Prefs.telegramChat(context).trim();
        if (token.length() == 0 || chat.length() == 0) {
            if (cb != null) cb.onMessage(false, 0, "Telegram token or Chat ID is missing.");
            return;
        }
        if (jpeg == null || jpeg.length == 0) {
            if (cb != null) cb.onMessage(false, 0, "Screen capture failed.");
            return;
        }
        new Thread(new FrameSender(context.getApplicationContext(), token, chat, messageId,
                jpeg, caption, replyMarkupJson, cb)).start();
    }

    private static final class FrameSender implements Runnable {
        private final Context context;
        private final String token;
        private final String chat;
        private final int messageId;
        private final byte[] jpeg;
        private final String caption;
        private final String replyMarkupJson;
        private final MessageCallback cb;

        FrameSender(Context context, String token, String chat, int messageId, byte[] jpeg,
                    String caption, String replyMarkupJson, MessageCallback cb) {
            this.context = context;
            this.token = token;
            this.chat = chat;
            this.messageId = messageId;
            this.jpeg = jpeg;
            this.caption = caption;
            this.replyMarkupJson = replyMarkupJson;
            this.cb = cb;
        }

        @Override
        public void run() {
            String cleanToken = token.startsWith("bot") ? token.substring(3) : token;
            boolean edit = messageId > 0;
            HttpURLConnection connection = null;
            try {
                String boundary = "===" + System.currentTimeMillis() + "===";
                String lineEnd = "\r\n";
                String twoHyphens = "--";

                URL url = new URL("https://api.telegram.org/bot" + cleanToken
                        + (edit ? "/editMessageMedia" : "/sendPhoto"));
                connection = (HttpURLConnection) url.openConnection();
                connection.setDoInput(true);
                connection.setDoOutput(true);
                connection.setUseCaches(false);
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Connection", "Keep-Alive");
                connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                connection.setConnectTimeout(20000);
                connection.setReadTimeout(20000);

                OutputStream out = connection.getOutputStream();
                writeField(out, boundary, "chat_id", chat.trim());
                if (edit) {
                    writeField(out, boundary, "message_id", String.valueOf(messageId));
                    // editMessageMedia carries the caption inside the media object and
                    // refers to the uploaded part by name via attach://
                    writeField(out, boundary, "media",
                            "{\"type\":\"photo\",\"media\":\"attach://photo\",\"caption\":"
                                    + jsonString(caption) + "}");
                } else if (caption != null && caption.length() > 0) {
                    writeField(out, boundary, "caption", caption);
                }
                if (replyMarkupJson != null && replyMarkupJson.length() > 0) {
                    writeField(out, boundary, "reply_markup", replyMarkupJson);
                }

                out.write((twoHyphens + boundary + lineEnd).getBytes("UTF-8"));
                out.write(("Content-Disposition: form-data; name=\"photo\"; filename=\"frame.jpg\"" + lineEnd).getBytes("UTF-8"));
                out.write(("Content-Type: image/jpeg" + lineEnd + lineEnd).getBytes("UTF-8"));
                out.write(jpeg);
                out.write(lineEnd.getBytes("UTF-8"));
                out.write((twoHyphens + boundary + twoHyphens + lineEnd).getBytes("UTF-8"));
                out.flush();
                out.close();

                int code = connection.getResponseCode();
                String body = readStream(code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream());
                if (code >= 200 && code < 300) {
                    int id = parseMessageId(body);
                    report(true, id > 0 ? id : messageId, null);
                } else {
                    report(false, 0, "HTTP " + code + ": " + parseError(body));
                }
            } catch (Exception exc) {
                Log.w(TAG, "Live frame " + (edit ? "edit" : "send") + " failed: " + exc.getMessage());
                report(false, 0, exc.getClass().getSimpleName() + ": " + exc.getMessage());
            } finally {
                if (connection != null) connection.disconnect();
            }
        }

        private void report(boolean ok, int id, String error) {
            if (cb != null) cb.onMessage(ok, id, error);
        }

        private static void writeField(OutputStream out, String boundary, String name, String value) throws Exception {
            out.write(("--" + boundary + "\r\n").getBytes("UTF-8"));
            out.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n").getBytes("UTF-8"));
            out.write(((value == null ? "" : value) + "\r\n").getBytes("UTF-8"));
        }
    }

    /** Minimal JSON string escaping - captions are app-generated, not user input. */
    private static String jsonString(String s) {
        if (s == null) return "\"\"";
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' || c == '\\') sb.append('\\').append(c);
            else if (c == '\n') sb.append("\\n");
            else if (c == '\r') sb.append("\\r");
            else if (c == '\t') sb.append("\\t");
            else if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
            else sb.append(c);
        }
        return sb.append('"').toString();
    }

    static int parseMessageId(String json) {
        if (json == null) return 0;
        int idx = json.indexOf("\"message_id\":");
        if (idx < 0) return 0;
        int i = idx + 13;
        int start = i;
        while (i < json.length() && (Character.isDigit(json.charAt(i)) || (i == start && json.charAt(i) == '-'))) i++;
        try {
            return Integer.parseInt(json.substring(start, i));
        } catch (Exception e) {
            return 0;
        }
    }

    private static final class CallbackRunner implements Runnable {
        private final Callback callback;
        private final boolean success;
        private final String message;

        CallbackRunner(Callback callback, boolean success, String message) {
            this.callback = callback;
            this.success = success;
            this.message = message;
        }

        @Override
        public void run() {
            if (callback != null) {
                callback.onResult(success, message);
            }
        }
    }

    private static final class CallbackAnswerer implements Runnable {
        private final String token;
        private final String queryId;
        private final String text;

        CallbackAnswerer(String token, String queryId, String text) {
            this.token = token;
            this.queryId = queryId;
            this.text = text;
        }

        @Override
        public void run() {
            String cleanToken = token.startsWith("bot") ? token.substring(3) : token;
            HttpURLConnection conn = null;
            try {
                StringBuilder body = new StringBuilder();
                body.append("callback_query_id=").append(enc(queryId));
                if (text != null && text.length() > 0) {
                    body.append("&text=").append(enc(text));
                }
                URL url = new URL("https://api.telegram.org/bot" + cleanToken + "/answerCallbackQuery");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

                OutputStream out = conn.getOutputStream();
                out.write(body.toString().getBytes("UTF-8"));
                out.flush();
                out.close();

                conn.getResponseCode();
            } catch (Exception ignored) {
            } finally {
                if (conn != null) conn.disconnect();
            }
        }
    }

    private static final class Sender implements Runnable {
        private final Context context;
        private final String token;
        private final String chat;
        private final String text;
        private final String replyMarkup;
        private final Callback callback;

        Sender(Context context, String token, String chat, String text, String replyMarkup, Callback callback) {
            this.context = context;
            this.token = token;
            this.chat = chat;
            this.text = text;
            this.replyMarkup = replyMarkup;
            this.callback = callback;
        }

        @Override
        public void run() {
            String cleanToken = token.startsWith("bot") ? token.substring(3) : token;
            String cleanChat = chat.trim();
            int maxAttempts = 3;

            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                HttpURLConnection connection = null;
                try {
                    StringBuilder body = new StringBuilder();
                    body.append("chat_id=").append(enc(cleanChat));
                    body.append("&text=").append(enc(text));
                    body.append("&parse_mode=Markdown");
                    if (replyMarkup != null && replyMarkup.length() > 0) {
                        body.append("&reply_markup=").append(enc(replyMarkup));
                    }

                    URL url = new URL("https://api.telegram.org/bot" + cleanToken + "/sendMessage");
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("POST");
                    connection.setDoOutput(true);
                    connection.setConnectTimeout(15000);
                    connection.setReadTimeout(15000);
                    connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

                    OutputStream out = connection.getOutputStream();
                    out.write(body.toString().getBytes("UTF-8"));
                    out.flush();
                    out.close();

                    int code = connection.getResponseCode();
                    String responseBody = readStream(code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream());

                    if (code >= 200 && code < 300) {
                        final String successMsg = "Telegram message sent successfully!";
                        Prefs.setLastStatus(context, successMsg);
                        notifyMain(true, successMsg);
                        return;
                    } else if (code >= 400 && code < 500) {
                        String errorDetail = parseError(responseBody);
                        final String failMsg = "Telegram failed (HTTP " + code + "): " + errorDetail;
                        Log.e(TAG, failMsg + " -> Raw: " + responseBody);
                        Prefs.setLastStatus(context, failMsg);
                        notifyMain(false, failMsg);
                        return;
                    }
                } catch (Exception exc) {
                    Log.w(TAG, "Attempt " + attempt + " failed: " + exc.getMessage());
                    if (attempt < maxAttempts) {
                        try { Thread.sleep(2000); } catch (InterruptedException ignored) { }
                    } else {
                        final String failMsg = "Telegram error: " + exc.getClass().getSimpleName() + " (" + exc.getMessage() + ")";
                        Log.e(TAG, failMsg, exc);
                        Prefs.setLastStatus(context, failMsg);
                        notifyMain(false, failMsg);
                    }
                } finally {
                    if (connection != null) connection.disconnect();
                }
            }
        }

        private void notifyMain(boolean success, String message) {
            if (callback != null) {
                MAIN_HANDLER.post(new CallbackRunner(callback, success, message));
            }
        }
    }

    private static final class PhotoSender implements Runnable {
        private final Context context;
        private final String token;
        private final String chat;
        private final byte[] photoBytes;
        private final String caption;
        private final Callback callback;

        PhotoSender(Context context, String token, String chat, byte[] photoBytes, String caption, Callback callback) {
            this.context = context;
            this.token = token;
            this.chat = chat;
            this.photoBytes = photoBytes;
            this.caption = caption;
            this.callback = callback;
        }

        @Override
        public void run() {
            String cleanToken = token.startsWith("bot") ? token.substring(3) : token;
            String cleanChat = chat.trim();
            int maxAttempts = 3;

            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                HttpURLConnection connection = null;
                try {
                    String boundary = "===" + System.currentTimeMillis() + "===";
                    String lineEnd = "\r\n";
                    String twoHyphens = "--";

                    URL url = new URL("https://api.telegram.org/bot" + cleanToken + "/sendPhoto");
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setDoInput(true);
                    connection.setDoOutput(true);
                    connection.setUseCaches(false);
                    connection.setRequestMethod("POST");
                    connection.setRequestProperty("Connection", "Keep-Alive");
                    connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                    connection.setConnectTimeout(25000);
                    connection.setReadTimeout(25000);

                    OutputStream out = connection.getOutputStream();

                    // 1. chat_id field
                    out.write((twoHyphens + boundary + lineEnd).getBytes("UTF-8"));
                    out.write(("Content-Disposition: form-data; name=\"chat_id\"" + lineEnd + lineEnd).getBytes("UTF-8"));
                    out.write((cleanChat + lineEnd).getBytes("UTF-8"));

                    // 2. caption field
                    if (caption != null && caption.length() > 0) {
                        out.write((twoHyphens + boundary + lineEnd).getBytes("UTF-8"));
                        out.write(("Content-Disposition: form-data; name=\"caption\"" + lineEnd + lineEnd).getBytes("UTF-8"));
                        out.write((caption + lineEnd).getBytes("UTF-8"));
                    }

                    // 3. photo file
                    out.write((twoHyphens + boundary + lineEnd).getBytes("UTF-8"));
                    out.write(("Content-Disposition: form-data; name=\"photo\"; filename=\"screenshot.jpg\"" + lineEnd).getBytes("UTF-8"));
                    out.write(("Content-Type: image/jpeg" + lineEnd + lineEnd).getBytes("UTF-8"));
                    out.write(photoBytes);
                    out.write(lineEnd.getBytes("UTF-8"));

                    // End of multipart
                    out.write((twoHyphens + boundary + twoHyphens + lineEnd).getBytes("UTF-8"));
                    out.flush();
                    out.close();

                    int code = connection.getResponseCode();
                    String responseBody = readStream(code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream());

                    if (code >= 200 && code < 300) {
                        final String successMsg = "Telegram screenshot sent!";
                        Prefs.setLastStatus(context, successMsg);
                        notifyMain(true, successMsg);
                        return;
                    } else if (code >= 400 && code < 500) {
                        String errorDetail = parseError(responseBody);
                        final String failMsg = "Telegram photo failed (HTTP " + code + "): " + errorDetail;
                        Log.e(TAG, failMsg + " -> Raw: " + responseBody);
                        Prefs.setLastStatus(context, failMsg);
                        notifyMain(false, failMsg);
                        return;
                    }
                } catch (Exception exc) {
                    Log.w(TAG, "Attempt " + attempt + " failed: " + exc.getMessage());
                    if (attempt < maxAttempts) {
                        try { Thread.sleep(2000); } catch (InterruptedException ignored) { }
                    } else {
                        final String failMsg = "Telegram photo error: " + exc.getClass().getSimpleName() + " (" + exc.getMessage() + ")";
                        Log.e(TAG, failMsg, exc);
                        Prefs.setLastStatus(context, failMsg);
                        notifyMain(false, failMsg);
                    }
                } finally {
                    if (connection != null) connection.disconnect();
                }
            }
        }

        private void notifyMain(boolean success, String message) {
            if (callback != null) {
                MAIN_HANDLER.post(new CallbackRunner(callback, success, message));
            }
        }
    }

    private static String readStream(InputStream is) {
        if (is == null) return "";
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static String parseError(String json) {
        if (json == null || json.length() == 0) return "Unknown server response";
        int idx = json.indexOf("\"description\":\"");
        if (idx >= 0) {
            int end = json.indexOf("\"", idx + 15);
            if (end > idx + 15) {
                return json.substring(idx + 15, end);
            }
        }
        return json.length() > 80 ? json.substring(0, 80) : json;
    }

    private static String enc(String value) throws Exception {
        return URLEncoder.encode(value, "UTF-8");
    }
}
