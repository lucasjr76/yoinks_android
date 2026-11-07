// SPDX-License-Identifier: GPL-3.0-only
package br.com.develox.yoinks;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.yausername.ffmpeg.FFmpeg;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MainActivity extends Activity {
    // yt-dlp's TikTok extractor only accepts /video/ URLs; photo posts use the same id.
    private static final Pattern TIKTOK_PHOTO = Pattern.compile(
            "^https?://(?:www\\.|m\\.)?tiktok\\.com/(@[\\w.-]*)/photo/(\\d+)",
            Pattern.CASE_INSENSITIVE);
    // ponytail: scrapes TikTok's page JSON, not yt-dlp; breaks if TikTok changes this structure.
    private static final Pattern TIKTOK_DATA = Pattern.compile(
            "<script[^>]+id=\"__UNIVERSAL_DATA_FOR_REHYDRATION__\"[^>]*>(.*?)</script>",
            Pattern.DOTALL);
    // The mobile page omits the post JSON; the desktop page includes it.
    private static final String DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36";
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private EditText link;
    private Button video;
    private Button audio;
    private TextView status;
    private ProgressBar progress;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);

        link = findViewById(R.id.link);
        video = findViewById(R.id.video);
        audio = findViewById(R.id.audio);
        status = findViewById(R.id.status);
        progress = findViewById(R.id.progress);

        findViewById(R.id.paste).setOnClickListener(view -> pasteLink());
        link.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable text) {
                String url = extractUrl(text.toString());
                video.setText(url != null && tiktokPhotoAsVideo(url) != null
                        ? "Baixar fotos (JPG)" : "Baixar vídeo MP4");
            }
        });
        video.setOnClickListener(view -> startDownload(false));
        audio.setOnClickListener(view -> startDownload(true));
        if (state == null) acceptShare(getIntent());
    }

    private void pasteLink() {
        ClipboardManager clipboard = getSystemService(ClipboardManager.class);
        ClipData clip = clipboard.getPrimaryClip();
        CharSequence text = clip == null || clip.getItemCount() == 0
                ? null : clip.getItemAt(0).getText();
        if (!fillLink(text)) {
            link.setError("Copie um link HTTP ou HTTPS antes de colar.");
        }
    }

    private boolean fillLink(CharSequence text) {
        String url = text == null ? null : extractUrl(text.toString());
        if (url == null) return false;
        link.setError(null);
        link.setText(url);
        link.setSelection(url.length());
        return true;
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        acceptShare(intent);
    }

    private void acceptShare(Intent intent) {
        if (!Intent.ACTION_SEND.equals(intent.getAction())) return;
        if (!fillLink(intent.getCharSequenceExtra(Intent.EXTRA_TEXT))) {
            link.setError("O texto compartilhado não contém um link HTTP ou HTTPS.");
        }
    }

    private static String extractUrl(String text) {
        Matcher matches = Patterns.WEB_URL.matcher(text);
        while (matches.find()) {
            String candidate = matches.group();
            Uri uri = Uri.parse(candidate);
            String scheme = uri.getScheme();
            if (("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))
                    && uri.getHost() != null && uri.getUserInfo() == null) return candidate;
        }
        return null;
    }

    /** Rewrites a TikTok photo post URL to the /video/ form yt-dlp accepts, or returns null. */
    static String tiktokPhotoAsVideo(String url) {
        Matcher match = TIKTOK_PHOTO.matcher(url);
        return match.find()
                ? "https://www.tiktok.com/" + match.group(1) + "/video/" + match.group(2) : null;
    }

    private void startDownload(boolean extractAudio) {
        String typed = extractUrl(link.getText().toString());
        if (typed == null) {
            link.setError("Informe um link HTTP ou HTTPS.");
            return;
        }
        String photo = tiktokPhotoAsVideo(typed);
        String url = photo != null ? photo : typed;
        boolean photos = photo != null && !extractAudio;
        video.setEnabled(false);
        audio.setEnabled(false);
        progress.setVisibility(View.VISIBLE);
        progress.setProgress(0);
        progress.setIndeterminate(true);
        status.setTextColor(getColor(R.color.muted));
        status.setText("Preparando download…");
        worker.execute(() -> {
            String result;
            boolean failed = false;
            try {
                result = photos ? downloadPhotos(url)
                        : "Salvo em Downloads/Yoinks/" + download(url, extractAudio);
            } catch (Exception error) {
                failed = true;
                result = "Falha no download.\n" + error.getMessage();
            }
            String message = result;
            boolean showError = failed;
            runOnUiThread(() -> {
                progress.setIndeterminate(false);
                progress.setVisibility(View.GONE);
                status.setTextColor(getColor(showError ? R.color.error : R.color.mint));
                status.setText(message);
                video.setEnabled(true);
                audio.setEnabled(true);
            });
        });
    }

    private String download(String url, boolean extractAudio) throws Exception {
        YoutubeDL engine = YoutubeDL.getInstance();
        engine.init(getApplicationContext());
        FFmpeg.getInstance().init(getApplicationContext());
        File temp = Files.createTempDirectory(getCacheDir().toPath(), "yoinks-").toFile();
        try {
            YoutubeDLRequest request = new YoutubeDLRequest(url);
            request.addOption("--no-playlist");
            request.addOption("--no-mtime");
            request.addOption("--socket-timeout", "20");
            request.addOption("--trim-filenames", "140");
            request.addOption("-o", new File(temp, "%(title)s [%(id)s].%(ext)s").getPath());
            if (extractAudio) {
                request.addOption("-f", "bestaudio/best");
                request.addOption("-x");
                request.addOption("--audio-format", "mp3");
            } else {
                request.addOption("-f", "bv[ext=mp4][vcodec^=avc1]+ba[ext=m4a]/b[ext=mp4]");
                request.addOption("--merge-output-format", "mp4");
            }
            runOnUiThread(() -> status.setText("Baixando…"));
            engine.execute(request, null, (percent, eta, line) -> {
                if (percent >= 0) runOnUiThread(() -> {
                    progress.setIndeterminate(false);
                    progress.setProgress(Math.min(100, Math.round(percent)));
                });
                return kotlin.Unit.INSTANCE;
            });
            String extension = extractAudio ? ".mp3" : ".mp4";
            File[] results = temp.listFiles(file -> file.isFile() && file.length() > 0
                    && file.getName().endsWith(extension));
            if (results == null || results.length != 1) {
                throw new IOException("O conversor não retornou um arquivo " + extension + ".");
            }
            runOnUiThread(() -> {
                progress.setIndeterminate(true);
                status.setText("Salvando em Downloads…");
            });
            return publish(results[0], extractAudio ? "audio/mpeg" : "video/mp4");
        } finally {
            File[] files = temp.listFiles();
            if (files != null) for (File file : files) file.delete();
            temp.delete();
        }
    }

    /** Image URLs of a TikTok photo post, read from the desktop page HTML. Empty if not found. */
    static List<String> tiktokImageUrls(String html) {
        List<String> urls = new ArrayList<>();
        Matcher script = TIKTOK_DATA.matcher(html);
        if (!script.find()) return urls;
        try {
            JSONObject node = new JSONObject(script.group(1));
            for (String key : new String[]{"__DEFAULT_SCOPE__", "webapp.video-detail",
                    "itemInfo", "itemStruct", "imagePost"}) {
                node = node.optJSONObject(key);
                if (node == null) return urls;
            }
            JSONArray images = node.optJSONArray("images");
            for (int i = 0; images != null && i < images.length(); i++) {
                JSONObject image = images.optJSONObject(i);
                JSONObject source = image == null ? null : image.optJSONObject("imageURL");
                JSONArray list = source == null ? null : source.optJSONArray("urlList");
                String url = list == null ? "" : list.optString(0);
                if (url.startsWith("https://")) urls.add(url);
            }
        } catch (JSONException ignored) {
            // Unexpected page format: treated as "no photos found".
        }
        return urls;
    }

    /** Downloads url into target and returns the response Content-Type. */
    private static String fetch(String url, File target) throws IOException {
        HttpURLConnection http = (HttpURLConnection) new URL(url).openConnection();
        try {
            http.setConnectTimeout(20_000);
            http.setReadTimeout(20_000);
            http.setRequestProperty("User-Agent", DESKTOP_UA);
            http.setRequestProperty("Referer", "https://www.tiktok.com/");
            int code = http.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new IOException("O TikTok respondeu HTTP " + code + ".");
            }
            try (InputStream input = http.getInputStream()) {
                Files.copy(input, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return http.getContentType();
        } finally {
            http.disconnect();
        }
    }

    private String downloadPhotos(String url) throws IOException {
        String id = url.substring(url.lastIndexOf('/') + 1);
        File temp = Files.createTempDirectory(getCacheDir().toPath(), "yoinks-").toFile();
        try {
            File page = new File(temp, "page.html");
            fetch(url, page);
            List<String> images = tiktokImageUrls(
                    new String(Files.readAllBytes(page.toPath()), StandardCharsets.UTF_8));
            if (images.isEmpty()) {
                throw new IOException("Não foi possível encontrar as fotos deste post. "
                        + "O TikTok pode ter mudado a página.");
            }
            List<String> saved = new ArrayList<>();
            for (int i = 0; i < images.size(); i++) {
                int done = i;
                runOnUiThread(() -> {
                    progress.setIndeterminate(false);
                    progress.setProgress(done * 100 / images.size());
                    status.setText("Baixando foto " + (done + 1) + " de " + images.size() + "…");
                });
                File image = new File(temp, "image");
                String type = fetch(images.get(i), image);
                boolean webp = type != null && type.startsWith("image/webp");
                File named = new File(temp, String.format(Locale.ROOT, "TikTok [%s] %02d.%s",
                        id, i + 1, webp ? "webp" : "jpg"));
                if (!image.renameTo(named)) throw new IOException("Não foi possível preparar a foto.");
                saved.add(publish(named, webp ? "image/webp" : "image/jpeg"));
                named.delete();
            }
            return saved.size() + (saved.size() == 1 ? " foto salva" : " fotos salvas")
                    + " em Downloads/Yoinks/\n" + String.join("\n", saved);
        } finally {
            File[] files = temp.listFiles();
            if (files != null) for (File file : files) file.delete();
            temp.delete();
        }
    }

    private String publish(File file, String mime) throws IOException {
        ContentValues metadata = new ContentValues();
        metadata.put(MediaStore.Downloads.DISPLAY_NAME, file.getName());
        metadata.put(MediaStore.Downloads.MIME_TYPE, mime);
        metadata.put(MediaStore.Downloads.RELATIVE_PATH, "Download/Yoinks");
        metadata.put(MediaStore.Downloads.IS_PENDING, 1);
        Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, metadata);
        if (uri == null) throw new IOException("Não foi possível criar o arquivo em Downloads.");
        try {
            try (OutputStream output = getContentResolver().openOutputStream(uri)) {
                if (output == null) throw new IOException("Não foi possível abrir o arquivo de destino.");
                Files.copy(file.toPath(), output);
            }
            String savedName = file.getName();
            try (var rows = getContentResolver().query(uri,
                    new String[]{MediaStore.Downloads.DISPLAY_NAME}, null, null, null)) {
                if (rows != null && rows.moveToFirst()) savedName = rows.getString(0);
            }
            ContentValues done = new ContentValues();
            done.put(MediaStore.Downloads.IS_PENDING, 0);
            if (getContentResolver().update(uri, done, null, null) != 1) {
                throw new IOException("Não foi possível finalizar o arquivo.");
            }
            return savedName;
        } catch (IOException | RuntimeException error) {
            getContentResolver().delete(uri, null, null);
            throw error;
        }
    }

    @Override protected void onDestroy() {
        worker.shutdown();
        super.onDestroy();
    }
}
