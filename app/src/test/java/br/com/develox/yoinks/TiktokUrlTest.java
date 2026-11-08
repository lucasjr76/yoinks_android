// SPDX-License-Identifier: GPL-3.0-only
package br.com.develox.yoinks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

public class TiktokUrlTest {
    @Test public void photoPostBecomesVideoUrl() {
        assertEquals("https://www.tiktok.com/@curisgabriel2026/video/7704478371723398421",
                MainActivity.tiktokPhotoAsVideo(
                        "https://www.tiktok.com/@curisgabriel2026/photo/7704478371723398421?_r=1&_t=ZS-9BBT6xHdHDo"));
        assertEquals("https://www.tiktok.com/@a.b/video/1",
                MainActivity.tiktokPhotoAsVideo("https://m.tiktok.com/@a.b/photo/1"));
    }

    @Test public void otherUrlsAreLeftAlone() {
        assertNull(MainActivity.tiktokPhotoAsVideo("https://www.tiktok.com/@a/video/1"));
        assertNull(MainActivity.tiktokPhotoAsVideo("https://www.youtube.com/watch?v=x"));
        assertNull(MainActivity.tiktokPhotoAsVideo("https://evil.com/?u=https://www.tiktok.com/@a/photo/1"));
    }

    @Test public void readsImageUrlsFromPageJson() {
        String html = "<html><script id=\"__UNIVERSAL_DATA_FOR_REHYDRATION__\" type=\"application/json\">"
                + "{\"__DEFAULT_SCOPE__\":{\"webapp.video-detail\":{\"itemInfo\":{\"itemStruct\":"
                + "{\"imagePost\":{\"images\":["
                + "{\"imageURL\":{\"urlList\":[\"https:\\u002F\\u002Fcdn.example\\u002F1.jpeg\",\"https://alt\"]}},"
                + "{\"imageURL\":{\"urlList\":[\"http://insecure\"]}},"
                + "{\"imageURL\":{\"urlList\":[\"https://cdn.example/2.jpeg\"]}}"
                + "]}}}}}}</script></html>";
        assertEquals(List.of("https://cdn.example/1.jpeg", "https://cdn.example/2.jpeg"),
                MainActivity.tiktokImageUrls(html));
    }

    @Test public void readsThreadsVideoOfTheRequestedPost() {
        String html = "<script type=\"application/json\" data-sjs>{\"x\":1}</script>"
                + "<script type=\"application/json\" data-sjs>{\"require\":[[\"a\",{\"result\":{\"data\":"
                + "{\"media\":{\"code\":\"POST1\",\"video_versions\":null,\"text_post_app_info\":"
                + "{\"linked_inline_media\":{\"media_type\":2,\"video_versions\":["
                + "{\"type\":102,\"width\":320,\"url\":\"https:\\/\\/cdn\\/small.mp4\"},"
                + "{\"type\":101,\"width\":720,\"url\":\"https:\\/\\/cdn\\/big.mp4\"}]}}},"
                + "\"reply\":{\"code\":\"OTHER\",\"video_versions\":[{\"width\":999,\"url\":\"https://cdn/other.mp4\"}]}"
                + "}}}]]}</script>";
        assertEquals(List.of("https://cdn/big.mp4"), MainActivity.threadsVideoUrls(html, "POST1"));
        assertTrue(MainActivity.threadsVideoUrls(html, "MISSING").isEmpty());
        assertTrue(MainActivity.threadsVideoUrls("<html></html>", "POST1").isEmpty());
    }

    @Test public void comparesReleaseTagWithAppVersion() {
        assertTrue(MainActivity.isNewer("v0.4.0", "0.3.0"));
        assertTrue(MainActivity.isNewer("v0.3.1", "0.3.0"));
        assertTrue(MainActivity.isNewer("v1.0", "0.9.9"));
        assertTrue(MainActivity.isNewer("v0.10.0", "0.9.0"));
        assertFalse(MainActivity.isNewer("v0.3.0", "0.3.0"));
        assertFalse(MainActivity.isNewer("v0.2.1", "0.3.0"));
        assertFalse(MainActivity.isNewer("nightly", "0.3.0"));
        // yt-dlp date versions
        assertTrue(MainActivity.isNewer("2026.11.05", "2026.10.26"));
        assertFalse(MainActivity.isNewer("2026.10.26", "2026.10.26"));
    }

    @Test public void missingOrBrokenJsonGivesNoImages() {
        assertTrue(MainActivity.tiktokImageUrls("<html></html>").isEmpty());
        assertTrue(MainActivity.tiktokImageUrls(
                "<script id=\"__UNIVERSAL_DATA_FOR_REHYDRATION__\">{oops</script>").isEmpty());
        assertTrue(MainActivity.tiktokImageUrls(
                "<script id=\"__UNIVERSAL_DATA_FOR_REHYDRATION__\">{\"__DEFAULT_SCOPE__\":{}}</script>").isEmpty());
    }
}
