package tv.noirstream;

import org.junit.Test;

import java.net.InetAddress;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;

public final class ProviderUrlPolicyTest {
    @Test
    public void blocksLocalAndSpecialUseHostNames() {
        assertTrue(ProviderUrlPolicy.isBlockedHostName("localhost"));
        assertTrue(ProviderUrlPolicy.isBlockedHostName("router.local"));
        assertTrue(ProviderUrlPolicy.isBlockedHostName("metadata.google.internal"));
        assertFalse(ProviderUrlPolicy.isBlockedHostName("catalog.example.org"));
    }

    @Test
    public void rejectsInvalidSchemesCredentialsAndLocalHostsBeforeFetching() {
        assertThrows(IllegalArgumentException.class,
                () -> ProviderUrlPolicy.requirePublicHttpUrl("file:///etc/passwd"));
        assertThrows(IllegalArgumentException.class,
                () -> ProviderUrlPolicy.requirePublicHttpUrl("https://user:pass@example.org/catalog.json"));
        assertThrows(IllegalArgumentException.class,
                () -> ProviderUrlPolicy.requirePublicHttpUrl("http://localhost/catalog.json"));
    }

    @Test
    public void acceptsPublicAddressesAndRejectsPrivateOrReservedRanges() throws Exception {
        assertTrue(ProviderUrlPolicy.isPublicAddress(InetAddress.getByName("1.1.1.1")));
        assertFalse(ProviderUrlPolicy.isPublicAddress(InetAddress.getByName("127.0.0.1")));
        assertFalse(ProviderUrlPolicy.isPublicAddress(InetAddress.getByName("10.0.0.5")));
        assertFalse(ProviderUrlPolicy.isPublicAddress(InetAddress.getByName("169.254.169.254")));
        assertFalse(ProviderUrlPolicy.isPublicAddress(InetAddress.getByName("192.168.1.10")));
        assertFalse(ProviderUrlPolicy.isPublicAddress(InetAddress.getByName("fc00::1")));
    }
}
