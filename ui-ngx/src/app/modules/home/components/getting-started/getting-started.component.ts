///
/// Copyright © 2016-2023 The Thingsboard Authors
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

import { AfterViewInit, Component } from '@angular/core';
import { Observable, of } from 'rxjs';
import { InstructionsService } from '@core/http/instructions.service';
import { MatDialog } from '@angular/material/dialog';
import { EntityTableConfig } from '@home/models/entity/entities-table-config.models';
import { ClientCredentials, CredentialsType } from '@shared/models/credentials.model';
import { EntityType, entityTypeResources, entityTypeTranslations } from '@shared/models/entity-type.models';
import { ClientCredentialsComponent } from '@home/pages/client-credentials/client-credentials.component';
import { AddEntityDialogComponent } from '@home/components/entity/add-entity-dialog.component';
import { AddEntityDialogData } from '@home/models/entity/entity-component.models';
import { ClientCredentialsService } from '@core/http/client-credentials.service';
import { BrokerConfig, ConfigParams } from '@shared/models/config.model';
import { select, Store } from '@ngrx/store';
import { selectUserDetails } from '@core/auth/auth.selectors';
import { map } from 'rxjs/operators';
import { AppState } from '@core/core.state';
import { HomePageTitleType } from '@shared/models/home-page.model';
import { ClientType } from '@shared/models/client.model';
import { ActionNotificationShow } from '@core/notification/notification.actions';
import { TranslateService } from '@ngx-translate/core';
import { STEPPER_GLOBAL_OPTIONS } from '@angular/cdk/stepper';
import {
  ClientCredentialsWizardDialogComponent
} from "@home/components/wizard/client-credentials-wizard-dialog.component";

@Component({
  selector: 'tb-getting-started',
  templateUrl: './getting-started.component.html',
  styleUrls: ['./getting-started.component.scss'],
  providers: [
    {
      provide: STEPPER_GLOBAL_OPTIONS,
      useValue: { displayDefaultIndicatorType: false }
    }
  ]
})
export class GettingStartedComponent implements AfterViewInit {

  cardType = HomePageTitleType.GETTING_STARTED;
  steps: Observable<Array<any>> = of([]);
  stepsData: Array<any> = [];
  data: string;
  configParams: BrokerConfig;
  selectedStep = 0;

  constructor(private instructionsService: InstructionsService,
              private dialog: MatDialog,
              private clientCredentialsService: ClientCredentialsService,
              private translate: TranslateService,
              private store: Store<AppState>) {
  }

  ngAfterViewInit(): void {
    this.store.pipe(
      select(selectUserDetails),
      map((user) => user?.additionalInfo?.config)).pipe(
      map((data) => {
          const tcpPort = data ? data[ConfigParams.tcpPort] : null;
          const basicAuthEnabled = data ? data[ConfigParams.basicAuthEnabled] : null;
          this.steps = this.instructionsService.setInstructionsList(basicAuthEnabled);
          this.steps.subscribe((res) => {
            this.stepsData = res;
          });
          // @ts-ignore
          window.mqttPort = tcpPort;
          this.configParams = {} as BrokerConfig;
          this.configParams[ConfigParams.basicAuthEnabled] = basicAuthEnabled;
          this.configParams[ConfigParams.tcpPort] = tcpPort;
          this.configParams[ConfigParams.basicAuthEnabled]  ? this.init('client-app') : this.init('enable-basic-auth');
          return data;
        }
      )).subscribe();
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

  private getStep(id: string) {
    this.instructionsService.getInstruction(id).subscribe(data =>
      this.data = data
    );
  }

  private init(id: string) {
    this.getStep(id);
  }
}
