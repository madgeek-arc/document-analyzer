/*
 * Copyright 2025 OpenAIRE AMKE
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.openaire.documentanalyzer.extract.service;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;
import crawlercommons.sitemaps.*;
import eu.openaire.documentanalyzer.common.model.HtmlContent;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Comment;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;
import org.jsoup.select.NodeTraversor;
import org.jsoup.select.NodeVisitor;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class WebPageContentExtractor implements ContentExtractor, Closeable {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(WebPageContentExtractor.class);


    private final HttpUriReader contentReader;
    private final Playwright playwright;
    private final Browser browser;

    public WebPageContentExtractor() throws NoSuchAlgorithmException, KeyManagementException {
        this.contentReader = new HttpUriReader();
        this.playwright = Playwright.create();
        this.browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                .setHeadless(true)
                .setArgs(List.of("--no-sandbox", "--disable-setuid-sandbox")));
    }

    /**
     * Performs whole-site scraping using the sitemap file. If the sitemap does not exist, it scrapes only the
     * provided url.
     *
     * @param uri the uri to extract content from
     * @return the extracted content
     * @throws IOException
     */
    public HtmlContent extractWholeSite(URI uri) throws IOException {
        String baseUrl = uri.getScheme() + "://" + uri.getHost();
        URI sitemapUrl = URI.create(String.join("/", baseUrl, "sitemap.xml"));
        List<String> urls;

        try {
            urls = extractUrlsFromSitemap(sitemapUrl);
            urls = getOnlyHtmlPages(urls);
            // TODO: keep only english version of pages when it exists
        } catch (Exception e) {
            logger.debug(e.getMessage());
            logger.info("Sitemap not found. Proceeding with provided url.");
            urls = List.of(uri.toString());
        }

        // extract content from main site
        HtmlContent content = extractFromUrl(uri.toString());

        for (String url : urls) {
            try {
                // extract content from supplementary sites
                HtmlContent extra = extractFromUrl(url);
                content.addExtraContent(extra);
            } catch (Throwable throwable) {
                logger.error("Skipping page {}.", url, throwable);
            }
        }
        return content;
    }

    /**
     * Navigates to the given URL with Playwright, waits for the page to render
     * (including JS-driven content), then extracts and cleans the HTML.
     *
     * @param url the url to render and extract content
     * @return the extracted content
     * @throws IOException
     */
    @Retryable(retryFor = IOException.class, maxAttempts = 3, backoff = @Backoff(delay = 500, multiplier = 2))
    // Replace Retryable with RetryTemplate if I decide to implement robots.txt compliant scraping
    public HtmlContent extractFromUrl(String url) throws IOException {
//        try (BrowserContext context = browser.newContext(); Page page = context.newPage()) {
        try (BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .setExtraHTTPHeaders(Map.of(
                        "Accept-Language", "en-US,en;q=0.9",
                        "Accept", "text/html,application/xhtml+xml,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
                ))); Page page = context.newPage()) {
            page.navigate(url, new Page.NavigateOptions()
                    .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                    .setTimeout(100000));
            try {
                page.waitForLoadState(LoadState.NETWORKIDLE,
                        new Page.WaitForLoadStateOptions().setTimeout(60000));
            } catch (PlaywrightException e) {
                logger.debug("Network idle timeout for {}, proceeding with current state", url);
            }
            String renderedHtml = page.content();
            HtmlContent content = extractHtmlContent(renderedHtml);
            content.setUrl(url);
            return content;
        } catch (TimeoutError e) {
            throw new IOException("Navigation timeout for " + url);
        } catch (PlaywrightException e) {
            throw new IOException("Playwright failed to render page: " + url, e);
        }
    }

    /**
     * Loads raw HTML bytes into Playwright for rendering (executes inline scripts),
     * then extracts and cleans the result.
     */
    @Override
    public HtmlContent extract(byte[] data) throws IOException {
        String htmlData = new String(data, StandardCharsets.UTF_8);
        try (BrowserContext context = browser.newContext()) {
            Page page = context.newPage();
            page.setContent(htmlData);
            String renderedHtml = page.content();
            return extractHtmlContent(renderedHtml);
        } catch (PlaywrightException e) {
            logger.warn("Playwright rendering failed, falling back to raw HTML parsing", e);
            return extractHtmlContent(htmlData);
        }
    }

    @Override
    public void close() {
        browser.close();
        playwright.close();
    }

    private HtmlContent extractHtmlContent(String htmlData) {
        Document doc = clean(Jsoup.parse(htmlData));

        logger.debug("HTML: \n{}", doc.html().strip());

        String metadata = extractMetadata(doc);
        String text = extractText(doc);

        HtmlContent content = HtmlContent.of(doc.html(), text);
        content.setMetadata(metadata);
        return content;
    }

    private static String extractText(Document doc) {
        StringBuilder builder = new StringBuilder();

        doc.select("header").remove();
        doc.select("footer").remove();

        // Generate full text from paragraphs, headers, list items, etc.
        Elements elements = doc.select("br, p, h1, h2, h3, h4, h5, h6, li, blockquote");
        for (Element el : elements) {
            String text = el.text().trim();
            if (!text.isEmpty()) {
                builder.append(text);
            }
            if (el.tagName().equals("p") || el.tagName().matches("h[1-6]")) {
                builder.append("\n\n");
            } else {
                builder.append('\n');
            }
        }
        return builder.toString();
    }

    private static String extractMetadata(Document document) {
        StringBuilder metadataBuilder = new StringBuilder();

        Elements metaTags = document.select("meta");
        for (Element meta : metaTags) {
            String name = meta.hasAttr("name") ? meta.attr("name") :
                    meta.hasAttr("property") ? meta.attr("property") :
                            meta.hasAttr("http-equiv") ? meta.attr("http-equiv") : "unknown";

            String content = meta.attr("content");
            metadataBuilder.append(name).append(": ").append(content).append('\n');
        }
        metadataBuilder.append("Title").append(": ").append(document.title());

        return metadataBuilder.toString();
    }


    private static Document clean(Document doc) {
        // remove comments and webpage header
        NodeTraversor.traverse(new NodeVisitor() {
            @Override
            public void head(Node node, int depth) {
                if (node instanceof Comment) {
                    node.remove();
                }
            }

            @Override
            public void tail(Node node, int depth) {
                // do nothing
            }
        }, doc);

        doc.select("header").remove();
        // doc.select("footer").remove(); // Do not remove footer as it usually contains useful links
        doc.select("link[rel=stylesheet]").remove();
        doc.select("link[rel=*icon]").remove();
        doc.select("input").remove();
        doc.select("style").remove();
        doc.select("script, noscript").remove();
        doc.select("iframe, embed, object").remove();
        doc.select("img, image, svg").remove();
        doc.select("font").unwrap();
        doc.select("div, span, section").unwrap();

        for (Element el : doc.getAllElements()) {
            el.removeAttr("onload");
            el.removeAttr("onclick");
            el.removeAttr("onerror");
            el.removeAttr("onmouseover");
            el.removeAttr("onmouseout");
            el.removeAttr("img");
            el.removeAttr("style");
            el.removeAttr("class");
            el.removeAttr("width");
            el.removeAttr("height");
            el.removeAttr("border");
            el.removeAttr("cellpadding");
            el.removeAttr("cellspacing");
            el.removeAttr("align");
        }
        return doc;
    }

    /**
     * Reads a sitemap url and extracts all site urls.
     *
     * @param url the sitemap url
     * @return list of urls
     * @throws Exception if sitemap parsing fails
     */
    public List<String> extractUrlsFromSitemap(URI url) throws Exception {

        SiteMapParser parser = new SiteMapParser();
        List<String> urls = new ArrayList<>();

        UriReader.Data data = contentReader.read(url);

        AbstractSiteMap abstractSiteMap = parser.parseSiteMap(data.data(), data.uri().toURL());

        if (abstractSiteMap.isIndex()) {
            // sitemap index — points to multiple child sitemaps
            SiteMapIndex index = (SiteMapIndex) abstractSiteMap;

            for (AbstractSiteMap childSiteMap : index.getSitemaps()) {
                // recurse into each child sitemap
                urls.addAll(extractUrlsFromSitemap(childSiteMap.getUrl().toURI()));
            }
        } else {
            // regular sitemap — contains actual URLs
            SiteMap siteMap = (SiteMap) abstractSiteMap;

            for (SiteMapURL siteMapUrl : siteMap.getSiteMapUrls()) {
                urls.add(siteMapUrl.getUrl().toString());
            }
        }

        return urls;
    }

    /**
     * Accepts a list of urls and removes all urls resolving to known image, video, audio, archive, asset file types.
     *
     * @param urls list of urls
     * @return cleaned up list of urls (simple urls or document file types)
     */
    private List<String> getOnlyHtmlPages(List<String> urls) {
        List<String> pages = new ArrayList<>();
        Pattern pattern = Pattern.compile(
                "\\.(jpg|jpeg|png|gif|webp|svg|ico|bmp|tiff|" +  // images
                        "mp4|mov|avi|mkv|webm|wmv|flv|" +             // video
                        "mp3|wav|ogg|flac|aac|wma|" +                 // audio
//                        "pdf|doc|docx|xls|xlsx|ppt|pptx|" +           // documents
                        "zip|tar|gz|rar|7z|" +                        // archives
                        "css|js|woff|woff2|ttf|eot)$",                // assets
                Pattern.CASE_INSENSITIVE
        );
        for (String urlString : urls) {
            URI url = URI.create(urlString);
            if (!pattern.matcher(url.getPath()).find()) {
                pages.add(urlString);
            }
        }
        return pages;
    }
}
