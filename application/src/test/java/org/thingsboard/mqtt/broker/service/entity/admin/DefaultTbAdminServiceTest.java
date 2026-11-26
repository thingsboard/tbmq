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
package org.thingsboard.mqtt.broker.service.entity.admin;

import org.junit.jupiter.api.Test;
import org.thingsboard.mqtt.broker.common.data.User;
import org.thingsboard.mqtt.broker.common.util.edition.TbmqEdition;
import org.thingsboard.mqtt.broker.dao.settings.AdminSettingsService;
import org.thingsboard.mqtt.broker.dao.user.UserService;
import org.thingsboard.mqtt.broker.dto.AdminDto;
import org.thingsboard.mqtt.broker.service.mail.MailService;
import org.thingsboard.mqtt.broker.service.security.system.SystemSecurityService;
import org.thingsboard.mqtt.broker.service.system.SystemSettingsNotificationService;
import org.thingsboard.mqtt.broker.service.user.AdminService;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultTbAdminServiceTest {

    private final AdminService adminService = mock(AdminService.class);
    private final UserService userService = mock(UserService.class);

    @Test
    void liteMarksAnAddedAdminOnTheDefaultPassword() throws Exception {
        UUID id = savedUserId();

        service(TbmqEdition.LITE).save(AdminDto.fromEmail("new@test.org"), null);
        service(TbmqEdition.LITE).save(admin(null, "sysadmin"), null);

        verify(userService, times(2)).markPasswordChangeRequired(id);
    }

    @Test
    void liteDoesNotMarkAnAddedAdminWithItsOwnPassword() throws Exception {
        savedUserId();

        service(TbmqEdition.LITE).save(admin(null, "Str0ng-pass"), null);

        verify(userService, never()).markPasswordChangeRequired(any());
    }

    @Test
    void liteDoesNotMarkAnEditedAdmin() throws Exception {
        savedUserId();

        service(TbmqEdition.LITE).save(admin(UUID.randomUUID(), null), null);

        verify(userService, never()).markPasswordChangeRequired(any());
    }

    @Test
    void clusterDoesNotMarkAnAddedAdmin() throws Exception {
        savedUserId();

        service(TbmqEdition.CLUSTER).save(AdminDto.fromEmail("new@test.org"), null);

        verify(userService, never()).markPasswordChangeRequired(any());
    }

    private DefaultTbAdminService service(TbmqEdition edition) {
        return new DefaultTbAdminService(adminService, userService, mock(AdminSettingsService.class), mock(MailService.class),
                mock(SystemSettingsNotificationService.class), mock(SystemSecurityService.class), edition);
    }

    private UUID savedUserId() throws Exception {
        User user = new User();
        user.setId(UUID.randomUUID());
        when(adminService.createAdmin(any(), anyBoolean())).thenReturn(user);
        return user.getId();
    }

    private static AdminDto admin(UUID id, String password) {
        AdminDto dto = AdminDto.fromEmail("new@test.org");
        dto.setId(id);
        dto.setPassword(password);
        return dto;
    }

}
