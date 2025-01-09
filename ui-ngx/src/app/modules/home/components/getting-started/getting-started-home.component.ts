///
/// Copyright © 2016-2025 The Thingsboard Authors
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

import { Component, OnInit } from '@angular/core';
import { Observable, of } from 'rxjs';
import { InstructionsService } from '@core/http/instructions.service';
import { MatDialog } from '@angular/material/dialog';
import { EntityTableConfig } from '@home/models/entity/entities-table-config.models';
import { ClientCredentials, CredentialsType } from '@shared/models/credentials.model';
import { EntityType, entityTypeResources, entityTypeTranslations } from '@shared/models/entity-type.models';
import { ClientCredentialsComponent } from '@home/pages/client-credentials/client-credentials.component';
import { AddEntityDialogData } from '@home/models/entity/entity-component.models';
import { ClientCredentialsService } from '@core/http/client-credentials.service';
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
import { FlexModule } from '@angular/flex-layout/flex';
import { CardTitleButtonComponent } from '@shared/components/button/card-title-button.component';
import { MatStepper, MatStep, MatStepLabel } from '@angular/material/stepper';
import { NgFor, NgIf, AsyncPipe } from '@angular/common';
import { TbMarkdownComponent } from '@shared/components/markdown.component';
import { MatButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';

@Component({
    selector: 'tb-getting-started',
    templateUrl: './getting-started-home.component.html',
    styleUrls: ['./getting-started-home.component.scss'],
    providers: [
        {
            provide: STEPPER_GLOBAL_OPTIONS,
            useValue: { displayDefaultIndicatorType: false }
        }
    ],
    imports: [FlexModule, CardTitleButtonComponent, MatStepper, NgFor, MatStep, MatStepLabel, TbMarkdownComponent, NgIf, MatButton, MatIcon, AsyncPipe, TranslateModule]
})
export class GettingStartedHomeComponent implements OnInit {

  cardType = HomePageTitleType.GETTING_STARTED;
  steps: Observable<Array<any>> = of([]);
  stepsData: Array<any> = [];
  data: string;
  selectedStep = 0;

  configParams = this.configService.brokerConfig;

  constructor(private instructionsService: InstructionsService,
              private dialog: MatDialog,
              private clientCredentialsService: ClientCredentialsService,
              private translate: TranslateService,
              private store: Store<AppState>,
              private configService: ConfigService,
              private router: Router) {
  }

  ngOnInit() {
    const basicAuthEnabled = this.configParams.basicAuthEnabled;
    this.steps = this.instructionsService.setInstructionsList(basicAuthEnabled);
    this.steps.subscribe((res) => {
      this.stepsData = res;
    });
    if (basicAuthEnabled) {
      this.init('client-app');
    } else {
      this.init('enable-basic-auth');
    }
  }

  selectStep(event: any) {
    this.selectedStep = event.selectedIndex;
    this.getStep(this.stepsData[event.selectedIndex].id);
  }

  addClientCredentials(type: string) {
    const config = new EntityTableConfig<ClientCredentials>();
    config.entityType = EntityType.MQTT_CLIENT_CREDENTIALS;
    config.entityComponent = ClientCredentialsComponent;
    config.entityTranslations = entityTypeTranslations.get(EntityType.MQTT_CLIENT_CREDENTIALS);
    config.entityResources = entityTypeResources.get(EntityType.MQTT_CLIENT_CREDENTIALS);
    config.addDialogStyle = {width: 'fit-content'};
    if (type === 'dev') {
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
    } else if (type === 'app') {
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
      data: {
        entitiesTableConfig: config
      }
    }).afterClosed();
    $entity.subscribe(
      (entity) => {
        if (entity) {
          this.clientCredentialsService.saveClientCredentials(entity).subscribe(() => {
            this.store.dispatch(new ActionNotificationShow(
              {
                message: this.translate.instant('getting-started.credentials-added'),
                type: 'success',
                duration: 1500,
                verticalPosition: 'top',
                horizontalPosition: 'left'
              }));
          });
        }
      }
    );
  }

  openSessions() {
    this.router.navigate(['/sessions'], {queryParams: {
        connectedStatusList: [ConnectionState.CONNECTED, ConnectionState.DISCONNECTED],
        clientTypeList: [ClientType.APPLICATION],
        clientId: 'tbmq',
        openSession: true
      }});
  }

  private getStep(id: string) {
    this.instructionsService.getInstruction(id).subscribe(data =>
      this.data = data
    );
  }

  private init(id: string) {
    this.getStep(id);
  }
}
