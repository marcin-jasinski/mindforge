package dev.mindforge.infrastructure.security;

import java.io.UncheckedIOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.function.Predicate;

import dev.mindforge.infrastructure.security.EgressDeniedException.Reason;

/**
 * The SSRF control for outbound HTTP (ADR 0022). A URL is admitted only when its scheme is http or
 * https, its port is the default, 80 or 443, it has no userinfo, and its host is a name or a
 * canonical IP literal of a public unicast address. {@link #resolve} returns a host's addresses only
 * when every one is public unicast, so the client connects to exactly the checked addresses.
 */
public final class EgressPolicy {

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");
    private static final Set<Integer> ALLOWED_PORTS = Set.of(-1, 80, 443);

    private final Function<String, InetAddress[]> resolver;
    private final Predicate<InetAddress> addressAllowed;
    private final IntPredicate portAllowed;

    public EgressPolicy() {
        this(EgressPolicy::lookUp, EgressPolicy::isPublicUnicast, ALLOWED_PORTS::contains);
    }

    EgressPolicy(Function<String, InetAddress[]> resolver, Predicate<InetAddress> addressAllowed,
                 IntPredicate portAllowed) {
        this.resolver = resolver;
        this.addressAllowed = addressAllowed;
        this.portAllowed = portAllowed;
    }

    /** Returns the normalised URI of an admitted URL; a host name is not resolved here. */
    public URI check(String url) {
        if (url == null) {
            throw new EgressDeniedException(Reason.SCHEME);
        }
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new EgressDeniedException(Reason.HOST);
        }
        String scheme = uri.getScheme();
        if (scheme == null || !ALLOWED_SCHEMES.contains(scheme.toLowerCase(Locale.ROOT))) {
            throw new EgressDeniedException(Reason.SCHEME);
        }
        String authority = uri.getRawAuthority();
        if (uri.getRawUserInfo() != null || (authority != null && authority.contains("@"))) {
            throw new EgressDeniedException(Reason.USERINFO);
        }
        if (authority == null) {
            throw new EgressDeniedException(Reason.HOST);
        }
        if (!portAllowed.test(uri.getPort())) {
            throw new EgressDeniedException(Reason.PORT);
        }
        checkHost(uri.getHost(), authority);
        return uri.normalize();
    }

    /** Returns the host's addresses when every one is allowed; otherwise refuses the host. */
    public List<InetAddress> resolve(String host) {
        if (host == null || host.isBlank()) {
            throw new EgressDeniedException(Reason.HOST);
        }
        InetAddress[] addresses;
        try {
            addresses = resolver.apply(host);
        } catch (UncheckedIOException e) {
            throw new EgressDeniedException(Reason.UNRESOLVABLE);
        }
        if (addresses == null || addresses.length == 0) {
            throw new EgressDeniedException(Reason.UNRESOLVABLE);
        }
        for (InetAddress address : addresses) {
            if (!addressAllowed.test(address)) {
                throw new EgressDeniedException(Reason.ADDRESS);
            }
        }
        return List.of(addresses);
    }

    /**
     * True only for a public unicast address. IPv4 excludes 0/8, 10/8, 100.64/10, 127/8, 169.254/16,
     * 172.16/12, 192.168/16 and 224/3; IPv6 must be global unicast (2000::/3). IPv4-mapped,
     * IPv4-compatible, NAT64 (64:ff9b::/96) and 6to4 (2002::/16) forms are checked by their IPv4.
     */
    public static boolean isPublicUnicast(InetAddress address) {
        if (address instanceof Inet4Address) {
            return isPublicIpv4(address.getAddress());
        }
        if (address instanceof Inet6Address) {
            return isPublicIpv6(address.getAddress());
        }
        return false;
    }

    private void checkHost(String host, String authority) {
        if (host != null && host.startsWith("[")) {
            if (!addressAllowed.test(ipv6Literal(host))) {
                throw new EgressDeniedException(Reason.ADDRESS);
            }
            return;
        }
        String name = host != null ? host : hostPart(authority);
        if (endsInNumber(name)) {
            InetAddress address = ipv4Literal(name);
            if (!addressAllowed.test(address)) {
                throw new EgressDeniedException(Reason.ADDRESS);
            }
            if (host == null || !name.equals(address.getHostAddress())) {
                throw new EgressDeniedException(Reason.HOST);
            }
            return;
        }
        if (host == null) {
            throw new EgressDeniedException(Reason.HOST);
        }
    }

    private static InetAddress ipv6Literal(String bracketed) {
        try {
            return InetAddress.getByName(bracketed);
        } catch (UnknownHostException e) {
            throw new EgressDeniedException(Reason.HOST);
        }
    }

    private static String hostPart(String authority) {
        int colon = authority.lastIndexOf(':');
        return colon >= 0 ? authority.substring(0, colon) : authority;
    }

    /** The WHATWG URL rule: a host whose last label is a number is an IPv4 address in any notation. */
    private static boolean endsInNumber(String host) {
        String trimmed = host.endsWith(".") ? host.substring(0, host.length() - 1) : host;
        String last = trimmed.substring(trimmed.lastIndexOf('.') + 1);
        if (last.startsWith("0x") || last.startsWith("0X")) {
            return last.substring(2).chars().allMatch(c -> Character.digit(c, 16) >= 0);
        }
        return !last.isEmpty() && last.chars().allMatch(c -> c >= '0' && c <= '9');
    }

    /** Parses an IPv4 host as a browser does: one to four parts, each decimal, octal or hex. */
    private static InetAddress ipv4Literal(String host) {
        String trimmed = host.endsWith(".") ? host.substring(0, host.length() - 1) : host;
        String[] parts = trimmed.split("\\.", -1);
        if (parts.length > 4) {
            throw new EgressDeniedException(Reason.HOST);
        }
        long value = 0;
        for (int i = 0; i < parts.length; i++) {
            long part = ipv4Part(parts[i]);
            boolean last = i == parts.length - 1;
            long limit = last ? (1L << (8 * (5 - parts.length))) - 1 : 255;
            if (part > limit) {
                throw new EgressDeniedException(Reason.HOST);
            }
            value = last ? (value << (8 * (5 - parts.length))) | part : (value << 8) | part;
        }
        byte[] bytes = {(byte) (value >>> 24), (byte) (value >>> 16), (byte) (value >>> 8), (byte) value};
        try {
            return InetAddress.getByAddress(bytes);
        } catch (UnknownHostException e) {
            throw new EgressDeniedException(Reason.HOST);
        }
    }

    private static long ipv4Part(String part) {
        String digits = part;
        int radix = 10;
        if (part.startsWith("0x") || part.startsWith("0X")) {
            digits = part.substring(2);
            radix = 16;
        } else if (part.length() > 1 && part.startsWith("0")) {
            digits = part.substring(1);
            radix = 8;
        }
        if (digits.isEmpty()) {
            if (radix == 16) {
                return 0;
            }
            throw new EgressDeniedException(Reason.HOST);
        }
        int base = radix;
        if (digits.length() > 15 || !digits.chars().allMatch(c -> Character.digit(c, base) >= 0)) {
            throw new EgressDeniedException(Reason.HOST);
        }
        return Long.parseLong(digits, base);
    }

    private static boolean isPublicIpv4(byte[] b) {
        int first = b[0] & 0xFF;
        int second = b[1] & 0xFF;
        return first != 0
            && first != 10
            && first != 127
            && first < 224
            && !(first == 100 && second >= 64 && second <= 127)
            && !(first == 169 && second == 254)
            && !(first == 172 && second >= 16 && second <= 31)
            && !(first == 192 && second == 168);
    }

    private static boolean isPublicIpv6(byte[] b) {
        if (allZero(b, 0, 10) && (b[10] & 0xFF) == 0xFF && (b[11] & 0xFF) == 0xFF) {
            return isPublicIpv4(Arrays.copyOfRange(b, 12, 16));
        }
        if (allZero(b, 0, 12)) {
            return isPublicIpv4(Arrays.copyOfRange(b, 12, 16));
        }
        if (b[0] == 0x00 && b[1] == 0x64 && (b[2] & 0xFF) == 0xFF && (b[3] & 0xFF) == 0x9B && allZero(b, 4, 12)) {
            return isPublicIpv4(Arrays.copyOfRange(b, 12, 16));
        }
        if (b[0] == 0x20 && b[1] == 0x02) {
            return isPublicIpv4(Arrays.copyOfRange(b, 2, 6));
        }
        return (b[0] & 0xE0) == 0x20;
    }

    private static boolean allZero(byte[] bytes, int from, int to) {
        for (int i = from; i < to; i++) {
            if (bytes[i] != 0) {
                return false;
            }
        }
        return true;
    }

    private static InetAddress[] lookUp(String host) {
        try {
            return InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new UncheckedIOException(e);
        }
    }
}
