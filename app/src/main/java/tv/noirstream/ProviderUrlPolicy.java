package tv.noirstream;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;

/** Network policy for URLs fetched by NOIR as remote provider/add-on manifests. */
final class ProviderUrlPolicy {
    private ProviderUrlPolicy() { }

    static void requirePublicHttpUrl(String address) throws Exception {
        if (address == null || address.length() > 2048) {
            throw new IllegalArgumentException("Provider URL is empty or too long.");
        }
        URI uri;
        try {
            uri = URI.create(address.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Enter a valid HTTPS provider URL.");
        }
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (!(("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))
                && host != null && uri.getUserInfo() == null)) {
            throw new IllegalArgumentException("Provider manifests require an HTTP(S) URL without embedded credentials.");
        }
        if (isBlockedHostName(host)) {
            throw new IllegalArgumentException("Local and private-network provider URLs are not allowed.");
        }
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException exception) {
            throw new IllegalArgumentException("The provider host could not be resolved.");
        }
        if (addresses.length == 0) throw new IllegalArgumentException("The provider host has no address.");
        for (InetAddress resolved : addresses) {
            if (!isPublicAddress(resolved)) {
                throw new IllegalArgumentException("The provider host resolves to a local or private-network address.");
            }
        }
    }

    static boolean isBlockedHostName(String host) {
        if (host == null) return true;
        String value = host.toLowerCase(Locale.ROOT);
        if (value.startsWith("[") && value.endsWith("]")) value = value.substring(1, value.length() - 1);
        return "localhost".equals(value) || value.endsWith(".localhost")
                || value.endsWith(".local") || value.endsWith(".internal")
                || "metadata.google.internal".equals(value);
    }

    static boolean isPublicAddress(InetAddress address) {
        if (address == null || address.isAnyLocalAddress() || address.isLoopbackAddress()
                || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                || address.isMulticastAddress()) return false;
        byte[] bytes = address.getAddress();
        if (bytes.length == 16) {
            int first = bytes[0] & 0xff;
            // Permit only IPv6 global-unicast (2000::/3); this also excludes
            // unique-local, link-local, mapped, documentation, and transition ranges.
            if ((first & 0xe0) != 0x20) return false;
            if (first == 0x20 && (bytes[1] & 0xff) == 0x01
                    && (bytes[2] & 0xff) == 0x0d && (bytes[3] & 0xff) == 0xb8) return false;
            return true;
        }
        if (bytes.length != 4) return false;
        int a = bytes[0] & 0xff, b = bytes[1] & 0xff, c = bytes[2] & 0xff;
        if (a == 0 || a == 10 || a == 127 || a >= 224) return false;
        if (a == 100 && b >= 64 && b <= 127) return false; // shared address space
        if (a == 169 && b == 254) return false;
        if (a == 172 && b >= 16 && b <= 31) return false;
        if (a == 192 && (b == 168 || b == 0 || (b == 2 && c == 0))) return false;
        if (a == 198 && (b == 18 || b == 19 || (b == 51 && c == 100))) return false;
        if (a == 203 && b == 0 && c == 113) return false;
        return true;
    }
}
