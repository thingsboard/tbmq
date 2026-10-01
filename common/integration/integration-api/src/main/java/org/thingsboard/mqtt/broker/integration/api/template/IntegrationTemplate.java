/**
 * Copyright © 2016-2026 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thingsboard.mqtt.broker.integration.api.template;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A string with {@code ${name}} placeholders filled from the integration JSON body, i.e. the ObjectNode built by
 * {@code AbstractIntegration.constructBody} or {@code constructLifecycleEventBody}. The template is parsed once into
 * literal and placeholder segments, so {@link #resolve} does no regex work per record.
 * <p>
 * If any placeholder has no value (missing, JSON null or empty string), the whole template resolves to empty: a key
 * such as {@code dev-${username}} gives no key at all rather than {@code dev-}.
 */
public final class IntegrationTemplate {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}]*)}");

    /**
     * Top-level body fields a placeholder may name: every scalar field of the message body plus the fields common to all
     * lifecycle event types, because one template serves both (per-type event fields such as {@code reason} are not
     * included). IntegrationTemplateEnvelopeParityTest pins this set against the real body builders.
     */
    static final Set<String> ROOTS = Set.of("topicName", "clientId", "username", "qos", "retain", "ts", "eventType",
            "tbmqNode", "tbmqIeNode", "clientCertCn", "sessionId", "ipAddress");
    /** Body objects whose entries are addressed as {@code ${root.key}}. */
    static final Set<String> NESTED = Set.of("props", "metadata");

    private static final String ALLOWED = ROOTS.stream().sorted().map(root -> "${" + root + "}")
            .collect(Collectors.joining(", ")) + ", ${props.<key>}, ${metadata.<key>}";

    private final String template;
    private final List<String> literals;       // placeholders.size() + 1 entries
    private final List<String[]> placeholders; // each {root} or {root, key}

    private IntegrationTemplate(String template, List<String> literals, List<String[]> placeholders) {
        this.template = template;
        this.literals = literals;
        this.placeholders = placeholders;
    }

    /**
     * @param label the field being configured, as the user sees it (e.g. "Key", "Header 'x'"); every error starts with it
     * @throws IllegalArgumentException on an unknown, empty or unclosed placeholder
     */
    public static IntegrationTemplate parse(String label, String template) {
        String text = template == null ? "" : template;
        List<String> literals = new ArrayList<>();
        List<String[]> placeholders = new ArrayList<>();
        Matcher matcher = PLACEHOLDER.matcher(text);
        int last = 0;
        while (matcher.find()) {
            literals.add(text.substring(last, matcher.start()));
            placeholders.add(parsePlaceholder(label, matcher.group(1).trim()));
            last = matcher.end();
        }
        String tail = text.substring(last);
        if (tail.contains("${")) {
            throw new IllegalArgumentException(label + ": unclosed placeholder in '" + text + "'");
        }
        literals.add(tail);
        return new IntegrationTemplate(text, literals, placeholders);
    }

    public static void validate(String label, String template) {
        parse(label, template);
    }

    /** A template sent verbatim, placeholder syntax included. */
    public static IntegrationTemplate literal(String template) {
        String text = template == null ? "" : template;
        return new IntegrationTemplate(text, List.of(text), List.of());
    }

    private static String[] parsePlaceholder(String label, String name) {
        if (name.isEmpty()) {
            throw new IllegalArgumentException(label + ": empty placeholder '${}'");
        }
        int dot = name.indexOf('.');
        String root = dot < 0 ? name : name.substring(0, dot);
        if (NESTED.contains(root)) {
            String key = dot < 0 ? "" : name.substring(dot + 1);
            if (key.isEmpty()) {
                throw new IllegalArgumentException(label + ": placeholder '${" + name + "}' needs a key, e.g. ${" + root + ".<key>}");
            }
            if (key.contains(".")) {
                throw unknown(label, name);
            }
            return new String[]{root, key};
        }
        if (dot >= 0 || !ROOTS.contains(name)) {
            throw unknown(label, name);
        }
        return new String[]{name};
    }

    private static IllegalArgumentException unknown(String label, String name) {
        return new IllegalArgumentException(label + ": unknown placeholder '${" + name + "}'. Allowed: " + ALLOWED);
    }

    public boolean hasPlaceholders() {
        return !placeholders.isEmpty();
    }

    /**
     * @param body the integration JSON body; may be null only when {@link #hasPlaceholders()} is false
     * @return the filled template, or empty when any placeholder has no value
     */
    public Optional<String> resolve(ObjectNode body) {
        if (placeholders.isEmpty()) {
            return Optional.of(template);
        }
        StringBuilder sb = new StringBuilder(template.length() + 32);
        for (int i = 0; i < placeholders.size(); i++) {
            sb.append(literals.get(i));
            String value = valueOf(body, placeholders.get(i));
            if (value == null) {
                return Optional.empty();
            }
            sb.append(value);
        }
        sb.append(literals.get(placeholders.size()));
        return Optional.of(sb.toString());
    }

    private static String valueOf(ObjectNode body, String[] placeholder) {
        JsonNode node = body.get(placeholder[0]);
        if (placeholder.length == 2) {
            node = node != null && node.isObject() ? node.get(placeholder[1]) : null;
        }
        if (node == null || node.isNull() || node.isContainerNode()) {
            return null;
        }
        String value = node.isTextual() ? node.asText() : node.toString();
        return value.isEmpty() ? null : value;
    }
}
