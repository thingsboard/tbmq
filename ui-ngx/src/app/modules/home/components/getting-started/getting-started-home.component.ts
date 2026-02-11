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

import { Component, Input, OnInit } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { EntityTableConfig } from '@home/models/entity/entities-table-config.models';
import { ClientCredentials, CredentialsType } from '@shared/models/credentials.model';
import { EntityType, entityTypeResources, entityTypeTranslations } from '@shared/models/entity-type.models';
import { ClientCredentialsComponent } from '@home/pages/client-credentials/client-credentials.component';
import { AddEntityDialogData } from '@home/models/entity/entity-component.models';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { HomePageTitleType } from '@shared/models/home-page.model';
import { ClientType } from '@shared/models/client.model';
import { ActionNotificationShow } from '@core/notification/notification.actions';
import { TranslateService, TranslateModule } from '@ngx-translate/core';
import { STEPPER_GLOBAL_OPTIONS } from '@angular/cdk/stepper';
import {
  ClientCredentialsWizardDialogComponent
} from '@home/components/wizard/client-credentials-wizard-dialog.component';
import { Router } from '@angular/router';
import { ConnectionState } from '@shared/models/session.model';
import { ConfigService } from '@core/http/config.service';
import { CardTitleButtonComponent } from '@shared/components/button/card-title-button.component';
import { MatStepper, MatStep, MatStepLabel } from '@angular/material/stepper';
import { TbMarkdownComponent } from '@shared/components/markdown.component';
import { MatButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { coerceBoolean } from '@shared/decorators/coercion';
import { clientIdRandom } from '@shared/models/ws-client.model';
import {
  GettingStartedStep,
  GettingStartedButtonAction,
  GettingStartedStepId,
  getGettingStartedSteps,
  gettingStartedStepCommands
} from '@shared/models/getting-started.model';

@Component({
    selector: 'tb-getting-started-home',
    templateUrl: './getting-started-home.component.html',
    styleUrls: ['./getting-started-home.component.scss'],
    providers: [
        {
            provide: STEPPER_GLOBAL_OPTIONS,
            useValue: { displayDefaultIndicatorType: false }
        }
    ],
    imports: [CardTitleButtonComponent, MatStepper, MatStep, MatStepLabel, TbMarkdownComponent, MatButton, MatIcon, TranslateModule]
})
export class GettingStartedHomeComponent implements OnInit {

  @Input()
  @coerceBoolean()
  hideTitle = true;

  cardType = HomePageTitleType.GETTING_STARTED;
  steps: GettingStartedStep[] = [];
  selectedStep = 0;
  configParams = this.configService.brokerConfig;

  private randomClientId = clientIdRandom();

  constructor(private dialog: MatDialog,
              private translate: TranslateService,
              private store: Store<AppState>,
              private configService: ConfigService,
              private router: Router) {
  }

  ngOnInit() {
    const basicAuthEnabled = this.configParams.basicAuthEnabled;
    this.steps = getGettingStartedSteps(basicAuthEnabled);
  }

  selectStep(event: any) {
    this.selectedStep = event.selectedIndex;
  }

  getStepCommand(stepId: GettingStartedStepId): string {
    const mqttHost = '{:mqttHost}';
    const mqttPort = '{:mqttPort}';

    switch (stepId) {
      case GettingStartedStepId.SUBSCRIBE:
        return gettingStartedStepCommands[GettingStartedStepId.SUBSCRIBE](this.randomClientId, mqttHost, mqttPort);
      case GettingStartedStepId.PUBLISH:
        return gettingStartedStepCommands[GettingStartedStepId.PUBLISH](mqttHost, mqttPort);
      default:
        return '';
    }
  }

  handleButtonAction(action: GettingStartedButtonAction) {
    switch (action) {
      case GettingStartedButtonAction.ADD_APP_CREDENTIALS:
        this.addClientCredentials(action);
        break;
      case GettingStartedButtonAction.ADD_DEVICE_CREDENTIALS:
        this.addClientCredentials(action);
        break;
      case GettingStartedButtonAction.OPEN_SESSIONS:
        this.openSessions();
        break;
    }
  }

  addClientCredentials(stepType: GettingStartedButtonAction) {
    const config = new EntityTableConfig<ClientCredentials>();
    config.entityType = EntityType.MQTT_CLIENT_CREDENTIALS;
    config.entityComponent = ClientCredentialsComponent;
    config.entityTranslations = entityTypeTranslations.get(EntityType.MQTT_CLIENT_CREDENTIALS);
    config.entityResources = entityTypeResources.get(EntityType.MQTT_CLIENT_CREDENTIALS);
    config.addDialogStyle = {width: 'fit-content'};
    if (stepType === GettingStartedButtonAction.ADD_DEVICE_CREDENTIALS) {
      config.demoData = {
        name: 'TBMQ Device Demo',
        clientType: ClientType.DEVICE,
        credentialsType: CredentialsType.MQTT_BASIC,
        credentialsValue: JSON.stringify({
          userName: 'tbmq_dev',
          password: 'tbmq_dev',
          authRules: {
            pubAuthRulePatterns: ['.*'],
            subAuthRulePatterns: ['.*']
          }
        })
      };
    } else if (stepType === GettingStartedButtonAction.ADD_APP_CREDENTIALS) {
      config.demoData = {
        name: 'TBMQ Application Demo',
        clientType: ClientType.APPLICATION,
        credentialsType: CredentialsType.MQTT_BASIC,
        credentialsValue: JSON.stringify({
          userName: 'tbmq_app',
          password: 'tbmq_app',
          authRules: {
            pubAuthRulePatterns: ['.*'],
            subAuthRulePatterns: ['.*']
          }
        })
      };
    }
    const $entity = this.dialog.open<ClientCredentialsWizardDialogComponent, AddEntityDialogData<ClientCredentials>,
      ClientCredentials>(ClientCredentialsWizardDialogComponent, {
      disableClose: true,
      panelClass: ['tb-dialog', 'tb-fullscreen-dialog'],
      autoFocus: false,
      data: {
        entitiesTableConfig: config
      }
    }).afterClosed();
    $entity.subscribe(
      (entity) => {
        if (entity) {
          this.store.dispatch(new ActionNotificationShow(
            {
              message: this.translate.instant('getting-started.credentials-added'),
              type: 'success',
              duration: 1500,
              verticalPosition: 'top',
              horizontalPosition: 'left'
            }
          ));
        }
      }
    );
  }

  private openSessions() {
    const targetRoute = '/sessions';
    const queryParams = {
      connectedStatusList: [ConnectionState.CONNECTED, ConnectionState.DISCONNECTED],
      clientTypeList: [ClientType.APPLICATION],
      clientId: this.randomClientId,
      openSession: true
    };
    if (this.router.url.startsWith(targetRoute)) {
      this.router.navigateByUrl('/', { skipLocationChange: true }).then(() => {
        this.router.navigate([targetRoute], { queryParams });
      });
    } else {
      this.router.navigate([targetRoute], { queryParams });
    }
  }
}
