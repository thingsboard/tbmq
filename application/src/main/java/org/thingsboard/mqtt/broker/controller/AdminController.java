/**
 * Copyright © 2016-2025 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.controller;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.thingsboard.mqtt.broker.common.data.AdminSettings;
import org.thingsboard.mqtt.broker.common.data.User;
import org.thingsboard.mqtt.broker.common.data.dto.WebSocketConnectionDto;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardErrorCode;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardException;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.security.model.SecuritySettings;
import org.thingsboard.mqtt.broker.dao.settings.AdminSettingsService;
import org.thingsboard.mqtt.broker.dao.ws.WebSocketConnectionService;
import org.thingsboard.mqtt.broker.dto.AdminDto;
import org.thingsboard.mqtt.broker.service.mail.MailService;
import org.thingsboard.mqtt.broker.service.user.AdminService;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.thingsboard.mqtt.broker.controller.ControllerConstants.USER_ID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
public class AdminController extends BaseController {

    private final AdminService adminService;
    private final AdminSettingsService adminSettingsService;
    private final MailService mailService;
    private final WebSocketConnectionService webSocketConnectionService;

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @PostMapping
    public User saveAdmin(@RequestBody AdminDto adminDto) throws ThingsboardException {
        return adminService.createAdmin(adminDto, true);
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @GetMapping(value = "/settings/{key}")
    public AdminSettings getAdminSettings(@PathVariable("key") String key) throws ThingsboardException {
        checkParameter("key", key);
        AdminSettings adminSettings = checkNotNull(adminSettingsService.findAdminSettingsByKey(key), "No Administration settings found for key: " + key);
        if (adminSettings.getKey().equals("mail")) {
            ((ObjectNode) adminSettings.getJsonValue()).remove("password");
        }
        return adminSettings;
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @PostMapping(value = "/settings")
    public AdminSettings saveAdminSettings(@RequestBody AdminSettings adminSettings) throws ThingsboardException {
        adminSettings = checkNotNull(adminSettingsService.saveAdminSettings(adminSettings));
        if (adminSettings.getKey().equals("mail")) {
            mailService.updateMailConfiguration();
            ((ObjectNode) adminSettings.getJsonValue()).remove("password");
        }
        return adminSettings;
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @PostMapping(value = "/settings/testMail")
    public void sendTestMail(@RequestBody AdminSettings adminSettings) throws ThingsboardException {
        if (adminSettings.getKey().equals("mail")) {
            if (!adminSettings.getJsonValue().has("password")) {
                AdminSettings mailSettings = checkNotNull(adminSettingsService.findAdminSettingsByKey("mail"));
                ((ObjectNode) adminSettings.getJsonValue()).put("password", mailSettings.getJsonValue().get("password").asText());
            }
            String email = getCurrentUser().getEmail();
            try {
                mailService.sendTestMail(adminSettings.getJsonValue(), email);
            } catch (ThingsboardException e) {
                String error = e.getMessage();
                if (e.getCause() != null) {
                    error += ": " + e.getCause().getMessage(); // showing actual underlying error for testing purposes
                }
                throw new ThingsboardException(error, e.getErrorCode());
            }
        }
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @DeleteMapping(value = "/{userId}")
    public void deleteAdmin(@PathVariable("userId") String strUserId) throws ThingsboardException {
        checkParameter("userId", strUserId);
        UUID userId = toUUID(strUserId);
        if (getCurrentUser().getId().equals(userId)) {
            throw new ThingsboardException("It is not allowed to delete its own user!", ThingsboardErrorCode.PERMISSION_DENIED);
        }
        checkUserId(userId);

        List<WebSocketConnectionDto> webSocketConnections = getWebSocketConnections(userId);
        webSocketConnections.forEach(wsConn -> clientSessionCleanUpService.disconnectClientSession(wsConn.getClientId()));

        userService.deleteUser(userId);
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @GetMapping(value = "", params = {"pageSize", "page"})
    public PageData<User> getAdmins(@RequestParam int pageSize,
                                    @RequestParam int page,
                                    @RequestParam(required = false) String textSearch,
                                    @RequestParam(required = false) String sortProperty,
                                    @RequestParam(required = false) String sortOrder) throws ThingsboardException {
        PageLink pageLink = createPageLink(pageSize, page, textSearch, sortProperty, sortOrder);
        return checkNotNull(userService.findUsers(pageLink));
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @GetMapping(value = "/user/{userId}")
    public User getAdminById(@PathVariable(USER_ID) String strUserId) throws ThingsboardException {
        checkParameter(USER_ID, strUserId);
        UUID userId = toUUID(strUserId);
        return checkUserId(userId);
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @PostMapping(value = "/user")
    @Deprecated(since = "2.2.0", forRemoval = true)
    public User saveAdminUser(@RequestBody User user) throws ThingsboardException {
        return checkNotNull(userService.saveUser(user));
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @PostMapping(value = "/securitySettings")
    public SecuritySettings saveSecuritySettings(@RequestBody SecuritySettings securitySettings) throws ThingsboardException {
        return checkNotNull(systemSecurityService.saveSecuritySettings(securitySettings));
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @GetMapping(value = "/securitySettings")
    public SecuritySettings getSecuritySettings() throws ThingsboardException {
        return checkNotNull(systemSecurityService.getSecuritySettings());
    }

    private List<WebSocketConnectionDto> getWebSocketConnections(UUID userId) {
        List<WebSocketConnectionDto> webSocketConnections = new ArrayList<>();
        PageLink pageLink = new PageLink(100);
        PageData<WebSocketConnectionDto> pageData;
        do {
            pageData = webSocketConnectionService.getWebSocketConnections(userId, pageLink);
            webSocketConnections.addAll(pageData.getData());
            if (pageData.hasNext()) {
                pageLink = pageLink.nextPageLink();
            }
        } while (pageData.hasNext());
        return webSocketConnections;
    }
}
