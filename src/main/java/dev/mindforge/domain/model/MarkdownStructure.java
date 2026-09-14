package dev.mindforge.domain.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The only parser of page bodies: what a fence, a heading, a section and a link are (T26).
 *
 * <ul>
 *   <li>A fence opens at a line of up to three spaces then three or more {@code `} or {@code ~}, and closes at a line
 *       (up to three spaces of indent) holding at least as many of the same character; unclosed, it runs to the end.
 *   <li>A heading is a line outside a fence starting at column 0 with 1–6 {@code #} and a space.
 *   <li>A section is a level-1 heading and everything up to the next one; its anchor is
 *       {@code Identifier.slugify(stripLinks(text))}, and when anchors repeat the first wins.
 *   <li>An inline link outside fences and code spans is internal when its destination is exactly
 *       {@code /<page path>.md} with an optional {@code #<anchor>}, external when it is {@code http(s)://}, and invalid
 *       otherwise; an autolink is external when it is {@code http(s)://} and invalid otherwise. Images and link
 *       reference definitions are invalid too. Nothing is normalised.
 * </ul>
 */
public final class MarkdownStructure {

    public record Section(String heading, String anchor, int start, int end) {}

    public enum LinkKind { INTERNAL, EXTERNAL, INVALID }

    /**
     * A link spanning {@code [start, end)}. {@code text} is null for an autolink or a reference definition;
     * {@code targetPath} and {@code fragment} are set only on an internal link.
     */
    public record Link(LinkKind kind, String text, String destination, String targetPath, String fragment,
                       int start, int end) {}

    /** A range {@code [start, end)} of a body. */
    public record Span(int start, int end) {}

    private record Heading(int level, String text, int start) {}

    private static final Pattern FENCE_OPEN = Pattern.compile(" {0,3}(`{3,}|~{3,}).*");
    private static final Pattern HEADING = Pattern.compile("(#{1,6}) (.*)");
    private static final Pattern CLOSING_HASHES = Pattern.compile("(?:^|\\s+)#+\\s*$");
    private static final Pattern REFERENCE_DEFINITION = Pattern.compile(" {0,3}\\[[^\\]\\n]+\\]:.*");
    /** One character of link text: an escape, a line break that does not end the paragraph, or anything but a bracket. */
    private static final String LINK_TEXT_CHAR = "(?:\\\\.|[^\\\\\\[\\]\\n]|\\n(?![ \\t]*\\n))";
    /** Link text may hold one level of balanced brackets, as CommonMark allows. */
    private static final Pattern INLINE_LINK = Pattern.compile(
        "\\[((?:" + LINK_TEXT_CHAR + "|\\[" + LINK_TEXT_CHAR + "*\\])*)\\]"
            + "\\(((?:[^()\\s]|\\([^()\\s]*\\))*)(?:[ \\t]+\"[^\"\\n]*\")?\\)");
    /** A URI autolink of any scheme, or an email autolink. */
    private static final Pattern AUTOLINK =
        Pattern.compile("<([A-Za-z][A-Za-z0-9+.\\-]{1,31}:[^\\s<>]*|[^\\s<>@]+@[^\\s<>@]+)>");
    private static final Pattern TAG = Pattern.compile("<[^\\s<>][^<>\\n]*>");
    /** Group 1 is the page path, group 4 the fragment. */
    private static final Pattern INTERNAL_DESTINATION = Pattern.compile(
        "/(" + PagePath.PATTERN.pattern() + ")\\.md(?:#(" + Identifier.PATTERN.pattern() + "))?");

    private MarkdownStructure() {}

    public static List<Section> sections(String body) {
        List<Heading> level1 = new Scan(body).headings.stream().filter(heading -> heading.level() == 1).toList();
        List<Section> sections = new ArrayList<>();
        for (int i = 0; i < level1.size(); i++) {
            Heading heading = level1.get(i);
            int end = i + 1 < level1.size() ? level1.get(i + 1).start() : body.length();
            sections.add(new Section(heading.text(), Identifier.slugify(stripLinks(heading.text())), heading.start(), end));
        }
        return sections;
    }

    /** The section a fragment or supersession names: the first with that anchor. */
    public static Optional<Section> section(String body, String anchor) {
        return sections(body).stream().filter(section -> section.anchor().equals(anchor)).findFirst();
    }

    /** Every link in the body, in order. */
    public static List<Link> links(String body) {
        return new Scan(body).links;
    }

    /** Replaces each inline link with its text; images, autolinks and code are left as they are. */
    public static String stripLinks(String text) {
        StringBuilder stripped = new StringBuilder();
        int position = 0;
        for (Link link : new Scan(text).links) {
            if (link.text() != null && text.charAt(link.start()) == '[') {
                stripped.append(text, position, link.start()).append(link.text());
                position = link.end();
            }
        }
        return stripped.append(text, position, text.length()).toString();
    }

    /**
     * The ranges a link insertion may wrap: everything outside links (inline and autolinks), code spans, fenced
     * blocks, heading lines of any level, reference definitions and tag-like {@code <…>} spans.
     */
    public static List<Span> eligibleSpans(String body) {
        boolean[] excluded = new Scan(body).excluded;
        List<Span> spans = new ArrayList<>();
        int start = -1;
        for (int i = 0; i <= body.length(); i++) {
            boolean eligible = i < body.length() && !excluded[i];
            if (eligible && start < 0) {
                start = i;
            } else if (!eligible && start >= 0) {
                spans.add(new Span(start, i));
                start = -1;
            }
        }
        return spans;
    }

    // ---------------------------------------------------------------------------
    // Scanning
    // ---------------------------------------------------------------------------

    private static final class Scan {

        private final String body;
        private final boolean[] fenced;
        private final boolean[] excluded;
        private final List<Heading> headings = new ArrayList<>();
        private final List<Link> links = new ArrayList<>();

        private Scan(String body) {
            this.body = body;
            fenced = new boolean[body.length()];
            excluded = new boolean[body.length()];
            scanLines();
            scanInline();
            links.sort(Comparator.comparingInt(Link::start));
        }

        private void scanLines() {
            char fenceChar = 0;
            int fenceLength = 0;
            int lineStart = 0;
            while (true) {
                int newline = body.indexOf('\n', lineStart);
                int lineEnd = newline < 0 ? body.length() : newline;
                int nextLine = newline < 0 ? lineEnd : newline + 1;
                String line = body.substring(lineStart, lineEnd);
                Matcher matcher;
                if (fenceChar != 0) {
                    if (closesFence(line, fenceChar, fenceLength)) {
                        fenceChar = 0;
                    }
                    mark(fenced, lineStart, nextLine);
                } else if ((matcher = FENCE_OPEN.matcher(line)).matches()) {
                    fenceChar = matcher.group(1).charAt(0);
                    fenceLength = matcher.group(1).length();
                    mark(fenced, lineStart, nextLine);
                } else if ((matcher = HEADING.matcher(line)).matches()) {
                    String text = CLOSING_HASHES.matcher(matcher.group(2)).replaceFirst("").trim();
                    headings.add(new Heading(matcher.group(1).length(), text, lineStart));
                    mark(excluded, lineStart, lineEnd);
                } else if (REFERENCE_DEFINITION.matcher(line).matches()) {
                    links.add(new Link(LinkKind.INVALID, null, line.trim(), null, null, lineStart, lineEnd));
                    mark(excluded, lineStart, lineEnd);
                }
                if (newline < 0) {
                    break;
                }
                lineStart = nextLine;
            }
            for (int i = 0; i < body.length(); i++) {
                excluded[i] |= fenced[i];
            }
        }

        private void scanInline() {
            int[] nextFenced = new int[body.length() + 1];
            nextFenced[body.length()] = body.length();
            for (int i = body.length() - 1; i >= 0; i--) {
                nextFenced[i] = fenced[i] ? i : nextFenced[i + 1];
            }
            int i = 0;
            while (i < body.length()) {
                if (fenced[i]) {
                    i++;
                    continue;
                }
                int limit = nextFenced[i];
                i = switch (body.charAt(i)) {
                    case '\\' -> i + 2;
                    case '`' -> codeSpan(i, limit);
                    case '!' -> image(i, limit);
                    case '[' -> inlineLink(i, limit);
                    case '<' -> angleBrackets(i, limit);
                    default -> i + 1;
                };
            }
        }

        /** A code span closes at the next backtick run of the same length within its paragraph. */
        private int codeSpan(int start, int limit) {
            int open = backtickRunEnd(start, limit);
            int i = open;
            while (i < limit && !body.startsWith("\n\n", i)) {
                if (body.charAt(i) != '`') {
                    i++;
                    continue;
                }
                int close = backtickRunEnd(i, limit);
                if (close - i == open - start) {
                    mark(excluded, start, close);
                    return close;
                }
                i = close;
            }
            return open;
        }

        private int image(int start, int limit) {
            Matcher matcher = INLINE_LINK.matcher(body).region(start + 1, limit);
            if (start + 1 >= limit || body.charAt(start + 1) != '[' || !matcher.lookingAt()) {
                return start + 1;
            }
            add(new Link(LinkKind.INVALID, matcher.group(1), matcher.group(2), null, null, start, matcher.end()));
            return matcher.end();
        }

        private int inlineLink(int start, int limit) {
            Matcher matcher = INLINE_LINK.matcher(body).region(start, limit);
            if (!matcher.lookingAt()) {
                return start + 1;
            }
            add(classify(matcher.group(1), matcher.group(2), start, matcher.end()));
            return matcher.end();
        }

        private int angleBrackets(int start, int limit) {
            Matcher autolink = AUTOLINK.matcher(body).region(start, limit);
            if (autolink.lookingAt()) {
                String destination = autolink.group(1);
                LinkKind kind = isHttp(destination) ? LinkKind.EXTERNAL : LinkKind.INVALID;
                add(new Link(kind, null, destination, null, null, start, autolink.end()));
                return autolink.end();
            }
            Matcher tag = TAG.matcher(body).region(start, limit);
            if (tag.lookingAt()) {
                mark(excluded, start, tag.end());
                return tag.end();
            }
            return start + 1;
        }

        private void add(Link link) {
            links.add(link);
            mark(excluded, link.start(), link.end());
        }

        private int backtickRunEnd(int start, int limit) {
            int end = start;
            while (end < limit && body.charAt(end) == '`') {
                end++;
            }
            return end;
        }

        private static Link classify(String text, String destination, int start, int end) {
            Matcher internal = INTERNAL_DESTINATION.matcher(destination);
            if (internal.matches() && PagePath.isValid(internal.group(1))) {
                return new Link(LinkKind.INTERNAL, text, destination, internal.group(1), internal.group(4), start, end);
            }
            LinkKind kind = isHttp(destination) ? LinkKind.EXTERNAL : LinkKind.INVALID;
            return new Link(kind, text, destination, null, null, start, end);
        }

        private static boolean isHttp(String destination) {
            return destination.startsWith("http://") || destination.startsWith("https://");
        }

        private static boolean closesFence(String line, char fenceChar, int fenceLength) {
            int indent = 0;
            while (indent < Math.min(3, line.length()) && line.charAt(indent) == ' ') {
                indent++;
            }
            int run = indent;
            while (run < line.length() && line.charAt(run) == fenceChar) {
                run++;
            }
            return run - indent >= fenceLength && line.substring(run).isBlank();
        }

        private static void mark(boolean[] flags, int start, int end) {
            for (int i = start; i < end; i++) {
                flags[i] = true;
            }
        }
    }
}
