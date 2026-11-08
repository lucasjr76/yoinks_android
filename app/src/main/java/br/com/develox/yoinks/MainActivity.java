// SPDX-License-Identifier: GPL-3.0-only
package br.com.develox.yoinks;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.yausername.ffmpeg.FFmpeg;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

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
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Callable;
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
    private static final String AUTHOR_URL = "https://github.com/lucasjr76/";
    private static final String REPO_URL = "https://github.com/lucasjr76/yoinks_android";
    private static final String LATEST_RELEASE_API =
            "https://api.github.com/repos/lucasjr76/yoinks_android/releases/latest";
    private static final Pattern VERSION = Pattern.compile("(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?");
    private static final String TIKTOK_REFERER = "https://www.tiktok.com/";
    // yt-dlp has no Threads extractor. With these browser headers the post page embeds its
    // media JSON; the video's direct MP4 URL is then handed to yt-dlp like any other link.
    // ponytail: scrapes Threads' page JSON; breaks if Meta changes it. Drop once yt-dlp
    // ships a Threads extractor (PR yt-dlp/yt-dlp#9852).
    private static final Pattern THREADS = Pattern.compile(
            "^https?://(?:www\\.)?threads\\.(?:com|net)/", Pattern.CASE_INSENSITIVE);
    private static final Pattern THREADS_CODE = Pattern.compile("/post/([\\w-]+)");
    private static final Pattern THREADS_OG_URL = Pattern.compile(
            "property=\"og:url\"\\s+content=\"[^\"]*/post/([\\w-]+)");
    private static final Pattern JSON_SCRIPT = Pattern.compile(
            "<script type=\"application/json\"[^>]*>(.*?)</script>", Pattern.DOTALL);
    private static final String[] BROWSER_PAGE = {
            "Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Accept-Language", "en-us,en;q=0.5",
            "Sec-Fetch-Mode", "navigate"};
    // Version of app/src/main/res/raw/ytdlp; bump it together with that file.
    private static final String BUNDLED_YTDLP = "2026.10.26";
    private static boolean engineChecked;
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
        findViewById(R.id.menu).setOnClickListener(this::showMenu);
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
        runJob("Preparando download…", () -> photos ? downloadPhotos(url)
                : "Salvo em Downloads/Yoinks/" + download(url, extractAudio),
                "Falha no download.\n", "\n\nSe o site mudou, toque em ⋮ › Atualizar motor de "
                        + "download e tente de novo.");
    }

    /** Runs job on the worker with both buttons disabled and shows its result in the status. */
    private void runJob(String preparing, Callable<String> job, String errorTitle, String errorHint) {
        video.setEnabled(false);
        audio.setEnabled(false);
        progress.setVisibility(View.VISIBLE);
        progress.setProgress(0);
        progress.setIndeterminate(true);
        status.setTextColor(getColor(R.color.muted));
        status.setText(preparing);
        worker.execute(() -> {
            String result;
            boolean failed = false;
            try {
                result = job.call();
            } catch (Exception error) {
                failed = true;
                result = errorTitle + error.getMessage() + errorHint;
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

    /**
     * Prepares yt-dlp. The library copies the APK's yt-dlp only when none is installed, so an
     * app update would keep an older engine; replace it when the APK carries a newer one.
     * Runs on the worker only, before the first init of this process.
     */
    private YoutubeDL engine() throws Exception {
        YoutubeDL engine = YoutubeDL.getInstance();
        if (!engineChecked) {
            SharedPreferences prefs = getSharedPreferences("yoinks", MODE_PRIVATE);
            String installed = newestOf(prefs.getString("bundledEngine", null),
                    engine.version(getApplicationContext()));
            if (installed == null || isNewer(BUNDLED_YTDLP, installed)) {
                File dir = new File(getNoBackupFilesDir(), "youtubedl-android/yt-dlp");
                File[] files = dir.listFiles();
                if (files != null) for (File file : files) file.delete();
                dir.delete();
                prefs.edit().putString("bundledEngine", BUNDLED_YTDLP).apply();
            }
            engineChecked = true;
        }
        engine.init(getApplicationContext());
        return engine;
    }

    private static String newestOf(String a, String b) {
        if (a == null) return b;
        if (b == null) return a;
        return isNewer(b, a) ? b : a;
    }

    /** yt-dlp version in use: the one installed by an engine update, else the APK's. */
    private String engineVersion() {
        String updated = YoutubeDL.getInstance().version(getApplicationContext());
        return updated != null && isNewer(updated, BUNDLED_YTDLP) ? updated : BUNDLED_YTDLP;
    }

    private void updateEngine() {
        runJob("Atualizando o motor de download…", () -> {
            YoutubeDL.UpdateStatus result = engine().updateYoutubeDL(
                    getApplicationContext(), YoutubeDL.UpdateChannel._STABLE);
            return result == YoutubeDL.UpdateStatus.DONE
                    ? "Motor atualizado para o yt-dlp " + engineVersion() + "."
                    : "O motor já está na versão mais recente (yt-dlp " + engineVersion() + ").";
        }, "Não foi possível atualizar o motor.\n", "");
    }

    private String download(String url, boolean extractAudio) throws Exception {
        YoutubeDL engine = engine();
        FFmpeg.getInstance().init(getApplicationContext());
        File temp = Files.createTempDirectory(getCacheDir().toPath(), "yoinks-").toFile();
        try {
            String name = "%(title)s [%(id)s].%(ext)s";
            if (THREADS.matcher(url).find()) {
                runOnUiThread(() -> status.setText("Lendo o post do Threads…"));
                File page = new File(temp, "page.html");
                fetch(url, page, BROWSER_PAGE);
                String html = new String(Files.readAllBytes(page.toPath()), StandardCharsets.UTF_8);
                page.delete();
                Matcher og = THREADS_OG_URL.matcher(html);
                Matcher typed = THREADS_CODE.matcher(url);
                String code = og.find() ? og.group(1) : typed.find() ? typed.group(1) : null;
                List<String> videos = code == null ? new ArrayList<>() : threadsVideoUrls(html, code);
                if (videos.isEmpty()) {
                    throw new IOException("Não foi encontrado vídeo neste post do Threads. "
                            + "Confira se o post é público e tem vídeo.");
                }
                // ponytail: first video only; loop over videos if carousel posts matter.
                url = videos.get(0);
                name = "Threads [" + code + "].%(ext)s";
            }
            YoutubeDLRequest request = new YoutubeDLRequest(url);
            request.addOption("--no-playlist");
            request.addOption("--no-mtime");
            request.addOption("--socket-timeout", "20");
            request.addOption("--trim-filenames", "140");
            request.addOption("-o", new File(temp, name).getPath());
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

    /** Direct MP4 URLs of the videos in Threads post code, read from its page HTML. */
    static List<String> threadsVideoUrls(String html, String code) {
        Set<String> urls = new LinkedHashSet<>();
        Matcher script = JSON_SCRIPT.matcher(html);
        while (script.find() && urls.isEmpty()) {
            String json = script.group(1);
            if (!json.contains("\"code\":\"" + code + "\"")) continue;
            try {
                JSONObject post = findPost(new JSONTokener(json).nextValue(), code);
                if (post != null) collectVideos(post, urls);
            } catch (JSONException ignored) {
                // Not the data we look for; try the next script.
            }
        }
        return new ArrayList<>(urls);
    }

    private static JSONObject findPost(Object node, String code) {
        if (node instanceof JSONObject) {
            JSONObject object = (JSONObject) node;
            if (code.equals(object.optString("code"))) return object;
            for (Iterator<String> keys = object.keys(); keys.hasNext(); ) {
                JSONObject found = findPost(object.opt(keys.next()), code);
                if (found != null) return found;
            }
        } else if (node instanceof JSONArray) {
            JSONArray array = (JSONArray) node;
            for (int i = 0; i < array.length(); i++) {
                JSONObject found = findPost(array.opt(i), code);
                if (found != null) return found;
            }
        }
        return null;
    }

    /** Adds the widest version of every video under node (post, carousel or inline media). */
    private static void collectVideos(Object node, Set<String> urls) {
        if (node instanceof JSONObject) {
            JSONObject object = (JSONObject) node;
            JSONArray versions = object.optJSONArray("video_versions");
            String best = null;
            int bestWidth = -1;
            for (int i = 0; versions != null && i < versions.length(); i++) {
                JSONObject version = versions.optJSONObject(i);
                String url = version == null ? "" : version.optString("url");
                if (url.startsWith("https://") && version.optInt("width") > bestWidth) {
                    best = url;
                    bestWidth = version.optInt("width");
                }
            }
            if (best != null) urls.add(best);
            for (Iterator<String> keys = object.keys(); keys.hasNext(); ) {
                String key = keys.next();
                if (!key.equals("video_versions")) collectVideos(object.opt(key), urls);
            }
        } else if (node instanceof JSONArray) {
            JSONArray array = (JSONArray) node;
            for (int i = 0; i < array.length(); i++) collectVideos(array.opt(i), urls);
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

    /**
     * Downloads url into target, following redirects, and returns the response Content-Type.
     * headers holds extra request header name/value pairs.
     */
    private static String fetch(String url, File target, String... headers) throws IOException {
        HttpURLConnection http = (HttpURLConnection) new URL(url).openConnection();
        try {
            http.setConnectTimeout(20_000);
            http.setReadTimeout(20_000);
            http.setRequestProperty("User-Agent", DESKTOP_UA);
            for (int i = 0; i + 1 < headers.length; i += 2) {
                http.setRequestProperty(headers[i], headers[i + 1]);
            }
            int code = http.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new IOException("O servidor respondeu HTTP " + code + ".");
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
            fetch(url, page, "Referer", TIKTOK_REFERER);
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
                String type = fetch(images.get(i), image, "Referer", TIKTOK_REFERER);
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

    private void showMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, 1, 0, "Verificar atualizações");
        menu.getMenu().add(0, 3, 1, "Atualizar motor de download");
        menu.getMenu().add(0, 2, 2, "Sobre");
        // Engine updates replace yt-dlp's files; never while a download runs.
        menu.getMenu().findItem(3).setEnabled(video.isEnabled());
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) checkForUpdates();
            else if (item.getItemId() == 3) updateEngine();
            else showAbout();
            return true;
        });
        menu.show();
    }

    /** Only notifies: looks up the latest GitHub release and links to its page. */
    private void checkForUpdates() {
        Toast.makeText(this, "Verificando atualizações…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            String tag = null;
            String page = REPO_URL + "/releases/latest";
            try {
                File json = File.createTempFile("release", ".json", getCacheDir());
                try {
                    fetch(LATEST_RELEASE_API, json);
                    JSONObject release = new JSONObject(
                            new String(Files.readAllBytes(json.toPath()), StandardCharsets.UTF_8));
                    tag = release.getString("tag_name");
                    String html = release.optString("html_url");
                    if (html.startsWith(REPO_URL + "/")) page = html;
                } finally {
                    json.delete();
                }
            } catch (IOException | JSONException error) {
                tag = null;
            }
            String latest = tag;
            String url = page;
            runOnUiThread(() -> showUpdateResult(latest, url));
        }).start();
    }

    private void showUpdateResult(String latest, String url) {
        if (isFinishing() || isDestroyed()) return;
        String current = BuildConfig.VERSION_NAME;
        AlertDialog.Builder dialog = new AlertDialog.Builder(this).setTitle("Atualizações");
        if (latest == null) {
            dialog.setMessage("Não foi possível verificar agora. Confira a conexão e tente de novo.");
        } else if (isNewer(latest, current)) {
            dialog.setMessage("A versão " + latest + " está disponível no GitHub.\n"
                    + "Você usa a versão " + current + ".");
            dialog.setPositiveButton("Ver no GitHub", (d, which) -> openLink(url));
        } else {
            dialog.setMessage("Você já usa a versão mais recente (" + current + ").");
        }
        dialog.setNegativeButton("Fechar", null).show();
    }

    /** True when release tag latest (e.g. "v0.4.0") is newer than version current. */
    static boolean isNewer(String latest, String current) {
        int[] a = versionParts(latest);
        int[] b = versionParts(current);
        for (int i = 0; i < 3; i++) if (a[i] != b[i]) return a[i] > b[i];
        return false;
    }

    private static int[] versionParts(String version) {
        int[] parts = new int[3];
        Matcher match = VERSION.matcher(version);
        if (match.find()) {
            for (int i = 0; i < 3; i++) {
                String part = match.group(i + 1);
                parts[i] = part == null ? 0 : Integer.parseInt(part);
            }
        }
        return parts;
    }

    private void showAbout() {
        SpannableString text = new SpannableString("Versão " + BuildConfig.VERSION_NAME
                + "\n\nCriado por lucasjr76\n" + AUTHOR_URL
                + "\n\nCódigo-fonte, novidades e downloads:\n" + REPO_URL
                + "\n\nIniciativa baseada no Yoinks de Pablo Stanley:\n"
                + "https://github.com/pablostanley/yoinks"
                + "\n\nMotor de download: yt-dlp " + engineVersion()
                + "\n\nSoftware livre sob GPL-3.0. Usa yt-dlp, FFmpeg e youtubedl-android.");
        Linkify.addLinks(text, Linkify.WEB_URLS);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Yoinks Android")
                .setMessage(text)
                .setPositiveButton("Abrir no GitHub", (d, which) -> openLink(REPO_URL))
                .setNegativeButton("Fechar", null)
                .show();
        TextView message = dialog.findViewById(android.R.id.message);
        if (message != null) message.setMovementMethod(LinkMovementMethod.getInstance());
    }

    private void openLink(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException error) {
            Toast.makeText(this, url, Toast.LENGTH_LONG).show();
        }
    }

    @Override protected void onDestroy() {
        worker.shutdown();
        super.onDestroy();
    }
}
