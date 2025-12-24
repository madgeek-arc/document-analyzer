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

import eu.openaire.documentanalyzer.common.model.HtmlContent;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Comment;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;
import org.jsoup.select.NodeTraversor;
import org.jsoup.select.NodeVisitor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class WebPageContentExtractor implements ContentExtractor {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(WebPageContentExtractor.class);

    @Override
    public HtmlContent extract(byte[] data) throws IOException {
        String htmlData = new String(data, StandardCharsets.UTF_8);
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
