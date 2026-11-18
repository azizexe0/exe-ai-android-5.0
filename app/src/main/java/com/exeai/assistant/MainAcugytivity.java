package com.exeai.assistant;

import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final int SPEECH = 41;
    private static final int IMAGE_PICK = 42;
    private static final int CAMERA = 43;
    private static final int IMPORT_QA = 44;

    private static final int RED = Color.rgb(255, 45, 55);
    private LinearLayout chat;
    private EditText input;
    private ScrollView scroll;
    private DB db;
    private TextToSpeech tts;
    private SharedPreferences prefs;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    private boolean ttsReady = false;
    private boolean voiceEnabled = true;
    private String selectedVoiceName = "";
    private float speechRate = 1.0f;
    private float speechPitch = 1.0f;
    private String lastImageText = "";

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(9, 9, 9));
        getWindow().setNavigationBarColor(Color.rgb(9, 9, 9));

        prefs = getSharedPreferences("exe_ai_settings", MODE_PRIVATE);
        voiceEnabled = prefs.getBoolean("voice_enabled", true);
        selectedVoiceName = prefs.getString("voice_name", "");
        speechRate = prefs.getFloat("speech_rate", 1.0f);
        speechPitch = prefs.getFloat("speech_pitch", 1.0f);

        db = new DB(this);
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true;
                tts.setLanguage(new Locale("tr", "TR"));
                applySavedVoice();
            }
        });

        buildUI();
        addMessage("EXE AI 5.0 hazır. Yazım hatalarına toleranslı komut algılama, sohbet, sesli giriş, kamera OCR ve kalıcı hafıza kullanılabilir.\nGeliştiricim: Aziz EXE.", false);
    }

    private void buildUI() {
        LinearLayout base = new LinearLayout(this);
        base.setOrientation(LinearLayout.VERTICAL);
        base.setBackgroundColor(Color.rgb(10, 10, 10));

        TextView title = new TextView(this);
        title.setText("EXE AI 5.0");
        title.setTextColor(RED);
        title.setTextSize(26);
        title.setTypeface(null, 1);
        title.setPadding(18, 18, 12, 3);

        TextView sub = new TextView(this);
        sub.setText("PERSONAL AI ASSISTANT  •  RED / BLACK");
        sub.setTextColor(0xffaaaaaa);
        sub.setTextSize(10);
        sub.setPadding(20, 0, 12, 12);
        base.addView(title);
        base.addView(sub);

        scroll = new ScrollView(this);
        chat = new LinearLayout(this);
        chat.setOrientation(LinearLayout.VERTICAL);
        chat.setPadding(12, 10, 12, 10);
        scroll.addView(chat);
        base.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout tools = new LinearLayout(this);
        tools.setPadding(6, 4, 6, 4);
        addTool(tools, "🎙 Sesli", v -> listen());
        addTool(tools, "📷 Kamera", v -> openCamera());
        addTool(tools, "🖼 Görsel", v -> pickImage());
        addTool(tools, "⚙ Ayarlar", v -> showSettings());
        base.addView(tools);

        LinearLayout extraTools = new LinearLayout(this);
        extraTools.setPadding(6, 0, 6, 4);
        addTool(extraTools, "📚 Bilgi paketi", v -> importKnowledge());
        addTool(extraTools, "▶ YouTube", v -> launchApp("youtube"));
        addTool(extraTools, "⛏ Minecraft", v -> launchApp("minecraft"));
        addTool(extraTools, "⚙ Sistem", v -> openSettings());
        base.addView(extraTools);

        LinearLayout bottom = new LinearLayout(this);
        bottom.setPadding(8, 8, 8, 12);
        input = new EditText(this);
        input.setSingleLine(false);
        input.setMinLines(1);
        input.setMaxLines(4);
        input.setHint("EXE AI'ye yaz...");
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(0xff888888);
        input.setPadding(12, 10, 12, 10);
        input.setBackgroundTintList(android.content.res.ColorStateList.valueOf(RED));
        bottom.addView(input, new LinearLayout.LayoutParams(0, -2, 1));

        Button send = new Button(this);
        send.setText("GÖNDER");
        send.setTextColor(Color.WHITE);
        send.setAllCaps(false);
        send.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xffb00020));
        send.setOnClickListener(v -> send());
        bottom.addView(send);
        base.addView(bottom);

        setContentView(base);
    }

    private void addTool(LinearLayout row, String label, View.OnClickListener click) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(10);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setPadding(2, 2, 2, 2);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff242424));
        b.setOnClickListener(click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        lp.setMargins(2, 0, 2, 0);
        row.addView(b, lp);
    }

    private void addMessage(String message, boolean user) {
        TextView t = new TextView(this);
        t.setText((user ? "SEN  ›  " : "EXE  ›  ") + message);
        t.setTextColor(user ? 0xffffb5b5 : Color.WHITE);
        t.setTextSize(15);
        t.setPadding(14, 12, 14, 12);
        t.setBackgroundColor(user ? 0xff351316 : 0xff202020);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 5, 0, 5);
        chat.addView(t, lp);
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    private void send() {
        String q = input.getText().toString().trim();
        if (q.isEmpty()) return;
        input.setText("");
        addMessage(q, true);
        hideKeyboard();

        String learningQuery = getLearningQuery(q);
        if (learningQuery != null) {
            if (learningQuery.isEmpty()) {
                reply("Öğrenmek istediğin konuyu komuttan sonra yaz. Örnek: *** RAM nedir?");
                return;
            }
            addMessage("İnternette ücretsiz kaynak aranıyor...", false);
            worker.execute(() -> learnFromInternet(learningQuery));
            return;
        }

        String answer = answer(q);
        reply(answer);
    }

    // Supported formats: "*** konu", "### konu", and also "***konu" / "###konu".
    private String getLearningQuery(String q) {
        String trimmed = q.trim();
        if (trimmed.startsWith("***") || trimmed.startsWith("###")) {
            return trimmed.substring(3).trim();
        }
        String lower = trimmed.toLowerCase(new Locale("tr", "TR"));
        if (lower.startsWith("öğren:") || lower.startsWith("ogren:")) {
            return trimmed.substring(trimmed.indexOf(':') + 1).trim();
        }
        return null;
    }

    private void learnFromInternet(String query) {
        HttpURLConnection connection = null;
        try {
            String searchUrl = "https://tr.wikipedia.org/w/api.php?action=query&list=search&srsearch="
                    + URLEncoder.encode(query, "UTF-8")
                    + "&format=json&utf8=1&srlimit=1";
            JSONObject searchJson = new JSONObject(httpGet(searchUrl));
            JSONArray results = searchJson.getJSONObject("query").getJSONArray("search");
            if (results.length() == 0) {
                postReply("Bu konu için ücretsiz kaynaklarda uygun bir sonuç bulamadım. Başka kelimelerle deneyebilirsin.");
                return;
            }

            JSONObject first = results.getJSONObject(0);
            String title = first.getString("title");
            String page = URLEncoder.encode(title.replace(' ', '_'), "UTF-8").replace("+", "%20");
            String summaryUrl = "https://tr.wikipedia.org/api/rest_v1/page/summary/" + page;
            JSONObject summary = new JSONObject(httpGet(summaryUrl));

            String answer = summary.optString("extract", "").trim();
            String source = summary.optString("content_urls", "");
            String sourceUrl = "https://tr.wikipedia.org/wiki/" + page;

            if (answer.isEmpty()) {
                answer = first.optString("snippet", "").replaceAll("<[^>]*>", "").trim();
            }
            if (answer.isEmpty()) {
                postReply("Kaynak bulundu ama okunabilir bir açıklama alınamadı.");
                return;
            }

            db.saveLearned(query, answer, sourceUrl);
            postReply("Öğrendim ve kalıcı hafızama kaydettim:\n" + answer + "\n\nKaynak: " + sourceUrl);
        } catch (Exception e) {
            postReply("İnternetten öğrenemedim. Bağlantını kontrol edip tekrar dene. Hata: " + safeError(e));
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private String httpGet(String address) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(address).openConnection();
        c.setRequestMethod("GET");
        c.setConnectTimeout(12000);
        c.setReadTimeout(15000);
        c.setRequestProperty("User-Agent", "EXE-AI-Android/4.0 (educational assistant)");
        c.setRequestProperty("Accept", "application/json");
        try (InputStream in = c.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder out = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) out.append(line);
            return out.toString();
        } finally {
            c.disconnect();
        }
    }

    private String safeError(Exception e) {
        String m = e.getMessage();
        return m == null ? e.getClass().getSimpleName() : m;
    }

    private void postReply(String text) {
        mainHandler.post(() -> reply(text));
    }

    private void reply(String text) {
        addMessage(text, false);
        if (tts != null && ttsReady && voiceEnabled) {
            tts.setSpeechRate(speechRate);
            tts.setPitch(speechPitch);
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "exe_reply");
        }
    }

    private String answer(String q) {
        String s = TextUnderstanding.normalize(q);
        if (TextUnderstanding.containsIntent(s, "seni kim yapti", "seni kim kodladi", "sen kimsin", "ai sen kimsin"))
            return "Ben EXE AI'yım. Beni Aziz EXE kodladı!";
        if (TextUnderstanding.containsIntent(s, "adin ne", "ismin ne", "senin adin ne"))
            return "Benim adım EXE AI. Kişisel yapay zekâ asistanınım.";
        if (TextUnderstanding.containsIntent(s, "merhaba", "selam", "slm", "iyi gunler", "gunaydin"))
            return "Merhaba! Yazım hatası olsa da ne demek istediğini anlamaya çalışıyorum. Bugün ne yapalım?";
        if (TextUnderstanding.containsIntent(s, "nasilsin", "naber", "ne haber"))
            return "İyiyim, teşekkür ederim! Seninle sohbet etmeye hazırım. Sen nasılsın?";
        if (TextUnderstanding.containsIntent(s, "ne yapiyorsun", "napion", "napıyorsun"))
            return "Mesajını anlamaya ve yardımcı olmaya çalışıyorum. İstersen bir görev de verebilirsin.";
        if (TextUnderstanding.containsIntent(s, "tesekkur", "sagol", "eyvallah"))
            return "Rica ederim! Başka bir konuda da yardımcı olabilirim.";
        if (TextUnderstanding.containsIntent(s, "saat kac", "saat kacta"))
            return "Cihaz saati: " + new SimpleDateFormat("HH:mm", new Locale("tr", "TR")).format(new Date());

        if (TextUnderstanding.containsIntent(s, "komik", "espiri yap", "saka yap", "beni guldur")) {
            prefs.edit().putBoolean("comedy_mode", true).apply();
            return nextJoke();
        }
        if (prefs.getBoolean("comedy_mode", false) && TextUnderstanding.containsIntent(s, "devam", "baska", "daha")) return nextJoke();
        if (TextUnderstanding.containsIntent(s, "espiri modu kapat", "normal moda gec")) {
            prefs.edit().putBoolean("comedy_mode", false).apply();
            return "Espiri modunu kapattım. Normal sohbete döndüm.";
        }

        // Any installed app can be launched by its visible name, with approximate spelling.
        if (hasOpenVerb(s)) {
            String app = launchInstalledAppFromText(q);
            if (app != null) return app + " uygulamasını açtım.";
        }
        if (TextUnderstanding.containsIntent(s, "youtube")) { launchApp("youtube"); return "YouTube'u açmayı denedim."; }
        if (TextUnderstanding.containsIntent(s, "minecraft")) { launchApp("minecraft"); return "Minecraft'ı açmayı denedim."; }
        if (TextUnderstanding.containsIntent(s, "chrome")) { launchApp("chrome"); return "Chrome'u açmayı denedim."; }
        if (TextUnderstanding.containsIntent(s, "wifi", "wi fi")) {
            startActivity(new Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)); return "Wi-Fi ayarlarını açtım.";
        }
        if (TextUnderstanding.containsIntent(s, "bluetooth")) {
            startActivity(new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)); return "Bluetooth ayarlarını açtım.";
        }
        if (TextUnderstanding.containsIntent(s, "yardim", "help", "neler yapabilirsin"))
            return "Komutlar: *** konu — internetten öğren; Sesli — konuş; Kamera — fotoğraf OCR; Bilgi paketi — TSV içe aktar; uygulama adı + aç — uygulama başlat; *komik* — espiri modu; devam — yeni espiri.";

        String learned = db.findLearnedFuzzy(q);
        if (learned != null) return "Hafızamda kayıtlı yakın eşleşme:\n" + learned;
        String qa = db.knowledgeFuzzy(q);
        if (qa != null) return qa;
        if (!lastImageText.isEmpty() && TextUnderstanding.containsIntent(s, "resim", "fotograf", "gorsel", "kamerada"))
            return "Fotoğraftan okuyabildiğim yazılar:\n" + lastImageText;
        return "Mesajını aldım; yazım hatalarını düzelterek ve yakın soru eşleşmelerini arayarak anlamaya çalıştım, ancak güvenilir bir cevap bulamadım. Daha fazla bilgi için *** konu komutunu veya bilgi paketi içe aktarmayı kullanabilirsin.";
    }

    private boolean hasOpenVerb(String s) {
        return TextUnderstanding.containsIntent(s, "ac", "baslat", "calistir", "gir", "uygulamasini ac", "programi ac");
    }

    private String launchInstalledAppFromText(String original) {
        String[] words = TextUnderstanding.normalize(original).split("\\s+");
        Intent query = new Intent(Intent.ACTION_MAIN, null);
        query.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = getPackageManager().queryIntentActivities(query, 0);
        ResolveInfo best = null;
        int bestScore = Integer.MAX_VALUE;
        for (ResolveInfo info : apps) {
            CharSequence labelCs = info.loadLabel(getPackageManager());
            if (labelCs == null) continue;
            String label = TextUnderstanding.normalize(labelCs.toString());
            if (normalizedContainsAppAlias(words, label)) { best = info; bestScore = 0; break; }
            for (int i = 0; i < words.length; i++) {
                for (int n = 1; n <= 4 && i + n <= words.length; n++) {
                    StringBuilder phrase = new StringBuilder();
                    boolean commandWord = false;
                    for (int j = i; j < i + n; j++) {
                        String w = words[j];
                        if (TextUnderstanding.containsIntent(w, "ac", "baslat", "calistir", "uygulama", "programi", "gir", "lutfen")) { commandWord = true; break; }
                        if (phrase.length() > 0) phrase.append(' ');
                        phrase.append(w);
                    }
                    if (commandWord || phrase.length() == 0) continue;
                    String candidate = phrase.toString();
                    int distance = TextUnderstanding.distance(candidate, label);
                    if (label.contains(candidate) || candidate.contains(label)) distance = 0;
                    int allowed = Math.max(1, Math.min(2, label.length() / 6));
                    if (distance <= allowed && distance < bestScore) { bestScore = distance; best = info; }
                }
            }
        }
        if (best == null) return null;
        Intent launch = new Intent(Intent.ACTION_MAIN);
        launch.addCategory(Intent.CATEGORY_LAUNCHER);
        launch.setClassName(best.activityInfo.packageName, best.activityInfo.name);
        try { startActivity(launch); return best.loadLabel(getPackageManager()).toString(); }
        catch (Exception e) { return null; }
    }

    private boolean normalizedContainsAppAlias(String[] words, String label) {
        String joined = String.join(" ", words);
        if (joined.contains(label)) return true;
        if (label.contains("youtube") && (joined.contains(" yt ") || joined.startsWith("yt "))) return true;
        if (label.contains("minecraft") && (joined.contains(" mc ") || joined.startsWith("mc "))) return true;
        if ((label.contains("settings") || label.contains("ayarlar")) && joined.contains("ayarlar")) return true;
        return false;
    }

    private String nextJoke() {
        String[] jokes = {
                "Bilgisayar neden doktora gitmiş? Çünkü virüs kapmış! 😂",
                "Matematik kitabı neden üzgünmüş? Çünkü çok fazla problemi varmış! 😄",
                "Telefon neden gözlük takmış? Çünkü rehberindeki isimleri daha net görmek istemiş! 🤓",
                "Programcı neden denize girmemiş? Çünkü çok fazla bug varmış! 🐛",
                "Wi-Fi neden saklambaçta iyiymiş? Çünkü herkes onu arıyormuş! 😂"
        };
        int index = prefs.getInt("joke_index", 0);
        prefs.edit().putInt("joke_index", index + 1).apply();
        return jokes[index % jokes.length];
    }

    private void launchApp(String name) {
        String[] packages;
        if (name.equals("youtube")) packages = new String[]{"com.google.android.youtube"};
        else if (name.equals("minecraft")) packages = new String[]{"com.mojang.minecraftpe", "com.mojang.minecraf
