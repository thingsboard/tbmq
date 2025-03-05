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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
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

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
public class AdminController extends BaseController {

    public static final String USER_ID = "userId";

    private final AdminService adminService;
    private final AdminSettingsService adminSettingsService;
    private final MailService mailService;
    private final WebSocketConnectionService webSocketConnectionService;

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @RequestMapping(value = "", method = RequestMethod.POST)
    @ResponseBody
    public User saveAdmin(@RequestBody AdminDto adminDto) throws ThingsboardException {
        try {
            checkNotNull(adminDto);
            return adminService.createAdmin(adminDto, true);
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @RequestMapping(value = "/settings/{key}", method = RequestMethod.GET)
    @ResponseBody
    public AdminSettings getAdminSettings(@PathVariable("key") String key) throws ThingsboardException {
        try {
            checkParameter("key", key);
            AdminSettings adminSettings = checkNotNull(adminSettingsService.findAdminSettingsByKey(key), "No Administration settings found for key: " + key);
            if (adminSettings.getKey().equals("mail")) {
                ((ObjectNode) adminSettings.getJsonValue()).remove("password");
            }
            return adminSettings;
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @RequestMapping(value = "/settings", method = RequestMethod.POST)
    @ResponseBody
    public AdminSettings saveAdminSettings(@RequestBody AdminSettings adminSettings) throws ThingsboardException {
        try {
            checkNotNull(adminSettings);
            adminSettings = checkNotNull(adminSettingsService.saveAdminSettings(adminSettings));
            if (adminSettings.getKey().equals("mail")) {
                mailService.updateMailConfiguration();
                ((ObjectNode) adminSettings.getJsonValue()).remove("password");
            }
            return adminSettings;
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @RequestMapping(value = "/settings/testMail", method = RequestMethod.POST)
    public void sendTestMail(@RequestBody AdminSettings adminSettings) throws ThingsboardException {
        try {
            adminSettings = checkNotNull(adminSettings);
            if (adminSettings.getKey().equals("mail")) {
                if (!adminSettings.getJsonValue().has("password")) {
                    AdminSettings mailSettings = checkNotNull(adminSettingsService.findAdminSettingsByKey("mail"));
                    ((ObjectNode) adminSettings.getJsonValue()).put("password", mailSettings.getJsonValue().get("password").asText());
                }
                String email = getCurrentUser().getEmail();
                mailService.sendTestMail(adminSettings.getJsonValue(), email);
            }
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @RequestMapping(value = "/{userId}", method = RequestMethod.DELETE)
    @ResponseBody
    public void deleteAdmin(@PathVariable("userId") String strUserId) throws ThingsboardException {
        checkParameter("userId", strUserId);
        try {
            UUID userId = toUUID(strUserId);
            if (getCurrentUser().getId().equals(userId)) {
                throw new ThingsboardException("It is not allowed to delete its own user!", ThingsboardErrorCode.PERMISSION_DENIED);
            }
            checkUserId(userId);

            List<WebSocketConnectionDto> webSocketConnections = getWebSocketConnections(userId);
            webSocketConnections.forEach(wsConn -> clientSessionCleanUpService.disconnectClientSession(wsConn.getClientId()));

            userService.deleteUser(userId);
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @RequestMapping(value = "", params = {"pageSize", "page"}, method = RequestMethod.GET)
    @ResponseBody
    public PageData<User> getAdmins(@RequestParam int pageSize,
                                    @RequestParam int page,
                                    @RequestParam(required = false) String textSearch,
                                    @RequestParam(required = false) String sortProperty,
                                    @RequestParam(required = false) String sortOrder) throws ThingsboardException {
        try {
            PageLink pageLink = createPageLink(pageSize, page, textSearch, sortProperty, sortOrder);
            return checkNotNull(userService.findUsers(pageLink));
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @RequestMapping(value = "/user/{userId}", method = RequestMethod.GET)
    @ResponseBody
    public User getAdminById(@PathVariable(USER_ID) String strUserId) throws ThingsboardException {
        checkParameter(USER_ID, strUserId);
        try {
            UUID userId = toUUID(strUserId);
            return checkUserId(userId);
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN')")
    @RequestMapping(value = "/user", method = RequestMethod.POST)
    @ResponseBody
    public User saveAdminUser(@RequestBody User user) throws ThingsboardException {
        try {
            checkNotNull(user);
            return checkNotNull(userService.saveUser(user));
        } catch (Exception e) {
            throw handleException(e);
        }
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @RequestMapping(value = "/securitySettings", method = RequestMethod.POST)
    @ResponseBody
    public SecuritySettings saveSecuritySettings(@RequestBody SecuritySettings securitySettings) throws ThingsboardException {
        return checkNotNull(systemSecurityService.saveSecuritySettings(securitySettings));
    }

    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @RequestMapping(value = "/securitySettings", method = RequestMethod.GET)
    @ResponseBody
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
