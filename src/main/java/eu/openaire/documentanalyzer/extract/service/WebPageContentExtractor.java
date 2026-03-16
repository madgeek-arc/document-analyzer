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
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.regex.Pattern;

public class WebPageContentExtractor implements ContentExtractor, Closeable {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(WebPageContentExtractor.class);
    private static final Pattern WEBSITE_PATTERN = Pattern.compile(
            "\\.(jpg|jpeg|png|gif|webp|svg|ico|bmp|tiff|tif|" +   // images
                    "mp4|mov|avi|mkv|webm|wmv|flv|" +                   // video
                    "mp3|wav|ogg|flac|aac|wma|" +                       // audio
                    "pdf|doc|docx|xls|xlsx|ppt|pptx|" +                 // documents
                    "zip|tar|gz|rar|7z|" +                              // archives
                    "css|js|woff|woff2|ttf|eot)$",                      // assets
            Pattern.CASE_INSENSITIVE
    );

    private static final int MAX_SUPPLEMENTARY_PAGES = 50;
    private static final int MIN_PATH_MATCHES = 2;
    private static final List<String> ATTRS_TO_REMOVE = List.of(
            "onload", "onclick", "onerror", "onmouseover", "onmouseout",
            "img", "style", "class", "width", "height", "border", "cellpadding", "cellspacing", "align"
    );


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
        Set<String> urls;

        try {
            urls = extractUrlsFromSitemap(sitemapUrl);
            urls = getOnlyHtmlPages(urls);
        } catch (Exception e) {
            logger.debug(e.getMessage());
            logger.info("Sitemap not found. Proceeding with provided url.");
            urls = new LinkedHashSet<>();
        }

        if (urls.size() > 2 * MAX_SUPPLEMENTARY_PAGES) { // when sitemap contains too many pages
            urls = filterUrlsByPathRelevance(uri, urls); // filter out "irrelevant" pages
        }

        // extract content from main site
        HtmlContent content = extractFromUrl(uri.toString());

        if (urls.size() > 2 * MAX_SUPPLEMENTARY_PAGES) {
            logger.warn("Too many supplementary pages provided... Skipping scraping.");
        } else {
            content = enrichContentUsingSupplementaryUrls(content, uri, urls);
        }

        return content;
    }

    private HtmlContent enrichContentUsingSupplementaryUrls(HtmlContent content, URI uri, Set<String> urls) {
        Set<String> uniqueUrls = new LinkedHashSet<>();

        // enrich content with supplementary sites
        for (String url : urls) {
            try {
                // keep only English version of pages when it exists
                uniqueUrls.add(contentReader.detectEnglishHtmlVersion(URI.create(url)));
            } catch (Exception e) {
                logger.warn("Skipping site: {}", url, e);
            }
        }

        if (uniqueUrls.size() > MAX_SUPPLEMENTARY_PAGES) {
            uniqueUrls = filterUrlsByPathRelevance(uri, uniqueUrls);
        }

        for (String url : uniqueUrls) {
            try {
                // extract content from supplementary sites
                HtmlContent extra = extractFromUrl(url);
                content.addExtraContent(extra);
            } catch (Exception throwable) {
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
        try (BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .setExtraHTTPHeaders(Map.of(
                        "Accept-Language", "en-US,en;q=0.9",
                        "Accept", "text/html,application/xhtml+xml,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
                ))); Page page = context.newPage()) {
            page.navigate(url, new Page.NavigateOptions()
                    .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                    .setTimeout(60000));
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
            ATTRS_TO_REMOVE.forEach(el::removeAttr);
        }
        return doc;
    }

    /**
     * Reads a sitemap url and extracts all site urls.
     *
     * @param url the sitemap url
     * @return set of urls (insertion-ordered, no duplicates)
     * @throws IOException            if the sitemap cannot be read
     * @throws URISyntaxException     if a child sitemap URL is malformed
     * @throws UnknownFormatException if the sitemap format is not recognized
     */
    public Set<String> extractUrlsFromSitemap(URI url) throws IOException, URISyntaxException, UnknownFormatException {
        SiteMapParser parser = new SiteMapParser();
        Set<String> urls = new LinkedHashSet<>();

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
     * Filters a set of URLs to keep only those most relevant to the given request URI,
     * based on progressive path-prefix matching. Starting from the deepest path segment,
     * it broadens by one level at a time until at least {@value MIN_PATH_MATCHES} matching
     * URLs are found. If no level yields enough matches, all URLs are returned unchanged.
     *
     * <p>Example: for {@code https://example.org/en/data/policies?filter=x}, the method
     * first tries to match URLs whose path starts with {@code /en/data/policies}, then
     * {@code /en/data}, then {@code /en}, stopping at the first level with enough results.
     *
     * @param requestUri the original URI whose path drives the relevance filtering
     * @param urls       the candidate URL set to filter
     * @return a filtered set of relevant URLs, or the original set if no filtering applies
     */
    static Set<String> filterUrlsByPathRelevance(URI requestUri, Set<String> urls) {
        String requestPath = requestUri.getPath();
        if (requestPath == null || requestPath.isEmpty() || requestPath.equals("/")) {
            return urls;
        }

        String[] segments = requestPath.split("/");
        // segments[0] is "" (the part before the leading slash); useful segments start at index 1

        for (int depth = segments.length; depth > 1; depth--) {
            String prefix = String.join("/", Arrays.copyOfRange(segments, 0, depth));
            Set<String> matching = new LinkedHashSet<>();
            for (String url : urls) {
                try {
                    String urlPath = URI.create(url).getPath();
                    if (urlPath != null && urlPath.startsWith(prefix)) {
                        matching.add(url);
                    }
                } catch (IllegalArgumentException e) {
                    logger.debug("Skipping malformed URL during path filtering: {}", url);
                }
            }
            if (matching.size() >= MIN_PATH_MATCHES) {
                logger.info("Filtered {} URLs to {} using path prefix '{}'", urls.size(), matching.size(), prefix);
                return matching;
            }
        }

        logger.info("No path-relevant subset found; keeping all {} URLs", urls.size());
        return urls;
    }

    /**
     * Accepts a collection of urls and removes all urls resolving to known image, video, audio, archive, asset file types.
     *
     * @param urls collection of urls
     * @return cleaned up set of urls (simple urls or document file types), insertion-ordered with no duplicates
     */
    private Set<String> getOnlyHtmlPages(Collection<String> urls) {
        Set<String> pages = new LinkedHashSet<>();
        for (String urlString : urls) {
            try {
                URI url = URI.create(urlString);
                if (!WEBSITE_PATTERN.matcher(url.getPath()).find()) {
                    pages.add(urlString);
                }
            } catch (IllegalArgumentException e) {
                logger.debug("Skipping malformed URL: {}", urlString);
            }
        }
        return pages;
    }
}
