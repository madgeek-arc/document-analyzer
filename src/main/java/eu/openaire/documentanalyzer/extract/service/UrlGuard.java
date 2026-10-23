/*
 * Copyright 2026 OpenAIRE AMKE
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

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

/**
 * Decides whether a URL may be fetched. Only {@code http} and {@code https} URLs are allowed and, unless
 * private addresses are explicitly allowed, the host must not resolve to a loopback, private, link-local,
 * carrier-grade NAT, multicast or wildcard address.
 *
 * <p>The check resolves the host name independently of the HTTP client, so a host whose DNS answer changes
 * between the check and the request (DNS rebinding) is not covered.
 */
public class UrlGuard {

    private final boolean allowPrivateAddresses;

    public UrlGuard(boolean allowPrivateAddresses) {
        this.allowPrivateAddresses = allowPrivateAddresses;
    }

    /**
     * @param uri the URL about to be fetched
     * @throws BlockedUrlException if the URL must not be fetched
     */
    public void check(URI uri) throws BlockedUrlException {
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new BlockedUrlException("Only http and https URLs are allowed: " + uri);
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new BlockedUrlException("URL has no host: " + uri);
        }
        if (allowPrivateAddresses) {
            return;
        }
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            return; // the request itself will fail to resolve the host
        }
        for (InetAddress address : addresses) {
            if (isNonPublic(address)) {
                throw new BlockedUrlException("URL resolves to a non-public address: " + uri);
            }
        }
    }

    /**
     * Returns whether a request the browser is about to make may proceed. Requests that don't go over
     * {@code http}/{@code https} (for example {@code data:} or {@code blob:}) don't reach the network and
     * are allowed.
     */
    public boolean isAllowedRequest(String url) {
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                return true;
            }
            check(uri);
            return true;
        } catch (BlockedUrlException | IllegalArgumentException e) {
            return false;
        }
    }

    static boolean isNonPublic(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            int first = bytes[0] & 0xff;
            int second = bytes[1] & 0xff;
            return first == 0 || (first == 100 && second >= 64 && second <= 127);
        }
        return (bytes[0] & 0xfe) == 0xfc; // IPv6 unique local fc00::/7
    }
}
