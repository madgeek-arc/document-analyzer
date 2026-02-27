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
import eu.openaire.documentanalyzer.common.model.HtmlContent;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Comment;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;
import org.jsoup.select.NodeTraversor;
import org.jsoup.select.NodeVisitor;

import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class WebPageContentExtractor implements ContentExtractor, Closeable {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(WebPageContentExtractor.class);

    private final Playwright playwright;
    private final Browser browser;

    public WebPageContentExtractor() {
        this.playwright = Playwright.create();
        this.browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                .setHeadless(true)
                .setArgs(List.of("--no-sandbox", "--disable-setuid-sandbox")));
    }

    /**
     * Navigates to the given URL with Playwright, waits for the page to render
     * (including JS-driven content), then extracts and cleans the HTML.
     */
    public HtmlContent extractFromUrl(String url) throws IOException {
        try (BrowserContext context = browser.newContext()) {
            Page page = context.newPage();
            page.navigate(url);
            try {
                page.waitForLoadState(LoadState.NETWORKIDLE,
                        new Page.WaitForLoadStateOptions().setTimeout(5000));
            } catch (PlaywrightException e) {
                logger.debug("Network idle timeout for {}, proceeding with current state", url);
            }
            String renderedHtml = page.content();
            return processHtml(renderedHtml);
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
            return processHtml(renderedHtml);
        } catch (PlaywrightException e) {
            logger.warn("Playwright rendering failed, falling back to raw HTML parsing", e);
            return processHtml(htmlData);
        }
    }

    @Override
    public void close() {
        browser.close();
        playwright.close();
    }

    private HtmlContent processHtml(String htmlData) {
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
//        doc.select("footer").remove();
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
}
