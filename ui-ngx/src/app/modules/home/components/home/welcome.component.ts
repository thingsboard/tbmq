///
/// Copyright © 2016-2026 The Thingsboard Authors
///
/// Licensed under the Apache License, Version 2.0 (the "License");
/// you may not use this file except in compliance with the License.
/// You may obtain a copy of the License at
///
///     http://www.apache.org/licenses/LICENSE-2.0
///
/// Unless required by applicable law or agreed to in writing, software
/// distributed under the License is distributed on an "AS IS" BASIS,
/// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
/// See the License for the specific language governing permissions and
/// limitations under the License.
///

import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Router } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { MatIcon } from '@angular/material/icon';
import { EntityTableConfig } from '@home/models/entity/entities-table-config.models';
import { ClientCredentials } from '@shared/models/credentials.model';
import { EntityType, entityTypeResources, entityTypeTranslations } from '@shared/models/entity-type.models';
import { ClientCredentialsComponent } from '@home/pages/client-credentials/client-credentials.component';
import { AddEntityDialogData } from '@home/models/entity/entity-component.models';
import { ClientCredentialsWizardDialogComponent } from '@home/components/wizard/client-credentials-wizard-dialog.component';
import { TranslateModule } from '@ngx-translate/core';

@Component({
  selector: 'tb-home-welcome',
  templateUrl: './welcome.component.html',
  styleUrls: ['./welcome.component.scss'],
  imports: [MatIcon, TranslateModule],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class WelcomeComponent {

  guideVisited = !!localStorage.getItem('tbmq_welcome_guide_visited');
  credentialsVisited = true; //!!localStorage.getItem('tbmq_welcome_credentials_visited');

  constructor(
    private router: Router,
    private dialog: MatDialog
  ) {}

  openGettingStartedGuide() {
    localStorage.setItem('tbmq_welcome_guide_visited', 'true');
    this.guideVisited = true;
    this.router.navigate(['/getting-started']);
  }

  createCredentials() {
    // localStorage.setItem('tbmq_welcome_credentials_visited', 'true');
    this.credentialsVisited = true;
    this.router.navigate(['authentication', 'client-credentials']);
    const config = new EntityTableConfig<ClientCredentials>();
    config.entityType = EntityType.MQTT_CLIENT_CREDENTIALS;
    config.entityComponent = ClientCredentialsComponent;
    config.entityTranslations = entityTypeTranslations.get(EntityType.MQTT_CLIENT_CREDENTIALS);
    config.entityResources = entityTypeResources.get(EntityType.MQTT_CLIENT_CREDENTIALS);
    config.addDialogStyle = {width: 'fit-content'};
    this.dialog.open<ClientCredentialsWizardDialogComponent, AddEntityDialogData<ClientCredentials>,
      ClientCredentials>(ClientCredentialsWizardDialogComponent, {
      disableClose: true,
      panelClass: ['tb-dialog', 'tb-fullscreen-dialog'],
      autoFocus: false,
      data: {
        entitiesTableConfig: config
      }
    });
  }
}
