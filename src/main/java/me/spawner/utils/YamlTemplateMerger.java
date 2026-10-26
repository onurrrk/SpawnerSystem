package me.spawner.utils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class YamlTemplateMerger {

    private static final String CURATED_SECTION = "loot";

    private static final class Node {
        final String key;
        final int indent;
        final List<String> pre = new ArrayList<>();
        final List<String> self = new ArrayList<>();
        final List<Node> children = new ArrayList<>();
        boolean section;

        Node(String key, int indent) {
            this.key = key;
            this.indent = indent;
        }
    }

    private static final class KeyLine {
        final String key;
        final String rest;

        KeyLine(String key, String rest) {
            this.key = key;
            this.rest = rest;
        }
    }

    private static final class Parsed {
        final List<Node> nodes = new ArrayList<>();
        final List<String> tail = new ArrayList<>();
    }

    private YamlTemplateMerger() {
    }

    public static List<String> merge(List<String> templateLines, List<String> userLines) {
        Parsed template = parse(templateLines);
        Parsed user = parse(userLines);

        List<String> out = new ArrayList<>();
        mergeNodes(template.nodes, user.nodes, out, template.nodes.isEmpty() ? 0 : template.nodes.get(0).indent);
        out.addAll(template.tail);
        return out;
    }

    private static void mergeNodes(List<Node> templateNodes, List<Node> userNodes, List<String> out, int targetIndent) {
        Map<String, Node> userByKey = new LinkedHashMap<>();
        for (Node node : userNodes) {
            userByKey.putIfAbsent(node.key, node);
        }
        Set<String> used = new HashSet<>();

        for (Node t : templateNodes) {
            Node u = userByKey.get(t.key);
            out.addAll(t.pre);

            if (u == null) {
                emit(t, 0, out);
                continue;
            }
            used.add(t.key);
            int delta = t.indent - u.indent;

            if (t.section && u.section) {
                if (t.key.equals(CURATED_SECTION)) {
                    emit(u, delta, out);
                    continue;
                }
                out.addAll(t.self);
                int childTarget = t.children.isEmpty()
                        ? (u.children.isEmpty() ? t.indent + 2 : u.children.get(0).indent + delta)
                        : t.children.get(0).indent;
                mergeNodes(t.children, u.children, out, childTarget);
            } else if (t.section && isEmptyHeader(u)) {
                emit(t, 0, out);
            } else if (!t.section && !u.section) {
                for (String line : u.self) {
                    out.add(shift(line, delta));
                }
            } else {
                emit(u, delta, out);
            }
        }

        for (Node u : userNodes) {
            if (used.contains(u.key)) continue;
            if (userByKey.get(u.key) != u) continue;
            int delta = targetIndent - u.indent;
            for (String line : u.pre) {
                out.add(shift(line, delta));
            }
            emit(u, delta, out);
        }
    }

    private static boolean isEmptyHeader(Node node) {
        if (node.section || node.self.size() != 1 || !node.children.isEmpty()) {
            return false;
        }
        KeyLine keyLine = parseKeyLine(node.self.get(0));
        return keyLine != null && keyLine.rest.isEmpty();
    }

    private static void emit(Node node, int delta, List<String> out) {
        for (String line : node.self) {
            out.add(shift(line, delta));
        }
        for (Node child : node.children) {
            for (String line : child.pre) {
                out.add(shift(line, delta));
            }
            emit(child, delta, out);
        }
    }

    private static String shift(String line, int delta) {
        if (delta == 0 || line.trim().isEmpty()) {
            return line;
        }
        if (delta > 0) {
            return " ".repeat(delta) + line;
        }
        int remove = 0;
        while (remove < -delta && remove < line.length() && line.charAt(remove) == ' ') {
            remove++;
        }
        return line.substring(remove);
    }

    private static Parsed parse(List<String> lines) {
        Parsed parsed = new Parsed();
        int topIndent = 0;
        for (String line : lines) {
            if (parseKeyLine(line) != null) {
                topIndent = indentOf(line);
                break;
            }
        }
        int end = parseBlock(lines, 0, topIndent, parsed.nodes);
        for (int i = end; i < lines.size(); i++) {
            parsed.tail.add(lines.get(i));
        }
        return parsed;
    }

    private static int parseBlock(List<String> lines, int start, int indent, List<Node> out) {
        int n = lines.size();
        int i = start;
        List<String> pending = new ArrayList<>();
        int pendingStart = -1;

        while (i < n) {
            String line = lines.get(i);
            if (isBlankOrComment(line)) {
                if (pending.isEmpty()) pendingStart = i;
                pending.add(line);
                i++;
                continue;
            }

            int lineIndent = indentOf(line);
            if (lineIndent < indent) {
                break;
            }

            KeyLine keyLine = parseKeyLine(line);
            if (keyLine == null || lineIndent > indent) {
                if (!out.isEmpty()) {
                    Node last = out.get(out.size() - 1);
                    last.self.addAll(pending);
                    last.self.add(line);
                    pending.clear();
                }
                i++;
                continue;
            }

            Node node = new Node(keyLine.key, indent);
            node.pre.addAll(pending);
            pending.clear();
            node.self.add(line);
            i++;

            if (keyLine.rest.isEmpty()) {
                int j = i;
                while (j < n && isBlankOrComment(lines.get(j))) j++;
                if (j < n) {
                    String next = lines.get(j);
                    int nextIndent = indentOf(next);
                    boolean nextIsList = next.trim().startsWith("-");
                    if (nextIsList && nextIndent >= indent) {
                        i = consumeList(lines, i, indent, node);
                    } else if (nextIndent > indent && parseKeyLine(next) != null) {
                        node.section = true;
                        i = parseBlock(lines, i, nextIndent, node.children);
                    }
                }
            } else if (keyLine.rest.startsWith("|") || keyLine.rest.startsWith(">")) {
                while (i < n && (lines.get(i).trim().isEmpty() || indentOf(lines.get(i)) > indent)) {
                    node.self.add(lines.get(i));
                    i++;
                }
            } else {
                while (i < n && !lines.get(i).trim().isEmpty() && indentOf(lines.get(i)) > indent) {
                    node.self.add(lines.get(i));
                    i++;
                }
            }
            out.add(node);
            pendingStart = -1;
        }

        if (!pending.isEmpty() && pendingStart >= 0) {
            return pendingStart;
        }
        return i;
    }

    private static int consumeList(List<String> lines, int start, int indent, Node node) {
        int n = lines.size();
        int i = start;
        while (i < n) {
            String line = lines.get(i);
            if (isBlankOrComment(line)) {
                int k = i;
                while (k < n && isBlankOrComment(lines.get(k))) k++;
                if (k < n && continuesList(lines.get(k), indent)) {
                    for (int x = i; x < k; x++) node.self.add(lines.get(x));
                    i = k;
                    continue;
                }
                break;
            }
            if (continuesList(line, indent)) {
                node.self.add(line);
                i++;
            } else {
                break;
            }
        }
        return i;
    }

    private static boolean continuesList(String line, int indent) {
        int lineIndent = indentOf(line);
        return lineIndent > indent || (lineIndent == indent && line.trim().startsWith("-"));
    }

    private static boolean isBlankOrComment(String line) {
        String trimmed = line.trim();
        return trimmed.isEmpty() || trimmed.startsWith("#");
    }

    private static int indentOf(String line) {
        int count = 0;
        while (count < line.length() && line.charAt(count) == ' ') {
            count++;
        }
        return count;
    }

    private static KeyLine parseKeyLine(String line) {
        String t = line.trim();
        if (t.isEmpty() || t.startsWith("#") || t.equals("-") || t.startsWith("- ")) {
            return null;
        }

        String key;
        int colon;
        char first = t.charAt(0);
        if (first == '"' || first == '\'') {
            int end = 1;
            while (end < t.length()) {
                char c = t.charAt(end);
                if (first == '"' && c == '\\') {
                    end += 2;
                    continue;
                }
                if (c == first) {
                    if (first == '\'' && end + 1 < t.length() && t.charAt(end + 1) == '\'') {
                        end += 2;
                        continue;
                    }
                    break;
                }
                end++;
            }
            if (end >= t.length()) return null;
            colon = end + 1;
            while (colon < t.length() && t.charAt(colon) == ' ') colon++;
            if (colon >= t.length() || t.charAt(colon) != ':') return null;
            key = t.substring(1, end);
        } else {
            colon = -1;
            for (int i = 0; i < t.length(); i++) {
                if (t.charAt(i) == ':' && (i + 1 == t.length() || t.charAt(i + 1) == ' ')) {
                    colon = i;
                    break;
                }
            }
            if (colon <= 0) return null;
            key = t.substring(0, colon).trim();
        }

        String rest = t.substring(colon + 1).trim();
        if (rest.startsWith("#")) {
            rest = "";
        }
        return new KeyLine(key, rest);
    }
}
