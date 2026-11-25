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
package org.thingsboard.mqtt.broker.service.mqtt.client.credentials;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.data.BrokerConstants;
import org.thingsboard.mqtt.broker.common.data.client.credentials.BasicMqttCredentials;
import org.thingsboard.mqtt.broker.common.data.client.credentials.PubSubAuthorizationRules;
import org.thingsboard.mqtt.broker.common.data.importing.csv.BulkImportColumnType;
import org.thingsboard.mqtt.broker.common.data.security.ClientCredentialsType;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.common.data.util.StringUtils;
import org.thingsboard.mqtt.broker.common.util.MqttClientCredentialsUtil;
import org.thingsboard.mqtt.broker.dao.client.MqttClientCredentialsService;
import org.thingsboard.mqtt.broker.exception.DataValidationException;
import org.thingsboard.mqtt.broker.util.CsvUtils;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Writes the MQTT basic credentials as the CSV the bulk import reads, so an export imported into another broker
 * recreates them. The system WebSocket credentials are left out: every broker creates its own.
 * <p>
 * The import splits a rule list on the auth rules delimiter and trims every pattern, and the UI import dialog splits
 * the file on line breaks before it reads the CSV. A pattern with the delimiter, a pattern with leading or trailing
 * whitespace and a name with a line break would not come back unchanged, so the export refuses them, naming the
 * credentials. The line breaks of a description are written as spaces.
 */
@Service
@RequiredArgsConstructor
public class MqttClientCredentialsCsvExportService {

    private static final List<String> HEADER = List.of(
            BulkImportColumnType.NAME.name(),
            BulkImportColumnType.DESCRIPTION.name(),
            BulkImportColumnType.CLIENT_TYPE.name(),
            BulkImportColumnType.CLIENT_ID.name(),
            BulkImportColumnType.USERNAME.name(),
            BulkImportColumnType.PASSWORD.name(),
            BulkImportColumnType.PASSWORD_HASH.name(),
            BulkImportColumnType.SUB_AUTH_RULE_PATTERNS.name(),
            BulkImportColumnType.PUB_AUTH_RULE_PATTERNS.name());
    private static final Pattern LINE_BREAK = Pattern.compile("\r\n|[\r\n]");
    private static final String DESCRIPTION_FIELD = "description";

    private final MqttClientCredentialsService credentialsService;

    /**
     * Checks every credentials and collects the rows, so a refusal comes before anything is written.
     *
     * @throws DataValidationException for credentials that would not come back unchanged from the import
     */
    public Export prepare(boolean includePasswordHashes, char authRulesDelimiter) {
        List<MqttClientCredentials> credentials = credentialsService.findByCredentialsType(ClientCredentialsType.MQTT_BASIC).stream()
                .filter(c -> !BrokerConstants.WS_SYSTEM_MQTT_CLIENT_CREDENTIALS_NAME.equals(c.getName()))
                .sorted(Comparator.comparing(MqttClientCredentials::getName))
                .toList();
        List<List<String>> rows = new ArrayList<>(credentials.size() + 1);
        rows.add(HEADER);
        for (MqttClientCredentials c : credentials) {
            rows.add(toRow(c, includePasswordHashes, authRulesDelimiter));
        }
        return new Export(rows);
    }

    public void export(boolean includePasswordHashes, char authRulesDelimiter, Writer out) throws IOException {
        prepare(includePasswordHashes, authRulesDelimiter).writeTo(out);
    }

    private List<String> toRow(MqttClientCredentials credentials, boolean includePasswordHashes, char delimiter) {
        String name = credentials.getName();
        checkNoLineBreak(name, name, "the name");
        BasicMqttCredentials basic = MqttClientCredentialsUtil.getMqttCredentials(credentials, BasicMqttCredentials.class);
        checkNoLineBreak(name, basic.getClientId(), "the client id");
        checkNoLineBreak(name, basic.getUserName(), "the username");
        PubSubAuthorizationRules rules = basic.getAuthRules();
        return List.of(
                name,
                description(credentials),
                credentials.getClientType() == null ? "" : credentials.getClientType().name(),
                nullToEmpty(basic.getClientId()),
                nullToEmpty(basic.getUserName()),
                "",
                includePasswordHashes ? nullToEmpty(basic.getPassword()) : "",
                joinPatterns(name, rules == null ? null : rules.getSubAuthRulePatterns(), delimiter),
                joinPatterns(name, rules == null ? null : rules.getPubAuthRulePatterns(), delimiter));
    }

    private String joinPatterns(String name, List<String> patterns, char delimiter) {
        if (patterns == null) {
            return "";
        }
        for (String pattern : patterns) {
            checkNoLineBreak(name, pattern, "an auth rule pattern");
            if (pattern.indexOf(delimiter) >= 0) {
                throw new DataValidationException(String.format(
                        "Credentials '%s' have an auth rule pattern containing '%s': export with another auth rules delimiter", name, delimiter));
            }
            if (pattern.isEmpty()) {
                throw new DataValidationException(String.format(
                        "Credentials '%s' have an empty auth rule pattern, which the import drops", name));
            }
            if (!pattern.equals(pattern.trim())) {
                throw new DataValidationException(String.format(
                        "Credentials '%s' have an auth rule pattern with leading or trailing spaces, which the import removes", name));
            }
        }
        return String.join(String.valueOf(delimiter), patterns);
    }

    private static void checkNoLineBreak(String credentialsName, String value, String what) {
        if (value != null && (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0)) {
            throw new DataValidationException(String.format("Credentials '%s' have a line break in %s", credentialsName, what));
        }
    }

    private String description(MqttClientCredentials credentials) {
        JsonNode additionalInfo = credentials.getAdditionalInfo();
        if (additionalInfo == null || !additionalInfo.hasNonNull(DESCRIPTION_FIELD)) {
            return "";
        }
        return LINE_BREAK.matcher(additionalInfo.get(DESCRIPTION_FIELD).asText()).replaceAll(" ");
    }

    private static String nullToEmpty(String value) {
        return StringUtils.isEmpty(value) ? "" : value;
    }

    /**
     * The checked rows of an export, the header first.
     */
    public record Export(List<List<String>> rows) {

        public void writeTo(Writer out) throws IOException {
            CsvUtils.writeCsv(out, rows);
        }

    }

}
