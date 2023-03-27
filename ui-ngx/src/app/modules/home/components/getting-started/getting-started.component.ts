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
import { MqttClientCredentials } from '@shared/models/client-crenetials.model';
import { EntityType, entityTypeResources, entityTypeTranslations } from '@shared/models/entity-type.models';
import { MqttClientCredentialsComponent } from '@home/pages/mqtt-client-credentials/mqtt-client-credentials.component';
import { AddEntityDialogComponent } from '@home/components/entity/add-entity-dialog.component';
import { AddEntityDialogData } from '@home/models/entity/entity-component.models';
import { MqttClientCredentialsService } from '@core/http/mqtt-client-credentials.service';
import { Router } from '@angular/router';
import { BrokerConfig, ConfigParams } from '@shared/models/stats.model';
import { select, Store } from '@ngrx/store';
import { selectUserDetails } from '@core/auth/auth.selectors';
import { map, take } from 'rxjs/operators';
import { AppState } from '@core/core.state';

@Component({
  selector: 'tb-getting-started',
  templateUrl: './getting-started.component.html',
  styleUrls: ['./getting-started.component.scss']
})
export class GettingStartedComponent implements AfterViewInit {

  steps: Observable<Array<any>> = of([]);
  data: Observable<string>;
  configParams: BrokerConfig;
  expandedStep = 1;

  constructor(private instructionsService: InstructionsService,
              private dialog: MatDialog,
              private mqttClientCredentialsService: MqttClientCredentialsService,
              private router: Router,
              private store: Store<AppState>) {
  }

  private init(id: string) {
    this.data = this.getStep(id);
  }

  getStep(id: string): Observable<string> {
    return this.instructionsService.getGetStartedInstruction(id);
  }

  stepActive(index): boolean {
    return index + 1 === this.expandedStep;
  }

  stepDone(index): boolean {
    return this.expandedStep > index + 1;
  }

  stepNotDone(index): boolean {
    return this.expandedStep < index + 1;
  }

  expandedChange(step: any) {
    this.expandedStep = step?.position;
    this.data = this.getStep(step.id);
  }

  navigate(path: string) {
    this.router.navigateByUrl(`/${path}`);
  }

  addClientCredentials() {
    const config = new EntityTableConfig<MqttClientCredentials>();
    config.entityType = EntityType.MQTT_CLIENT_CREDENTIALS;
    config.entityComponent = MqttClientCredentialsComponent;
    config.entityTranslations = entityTypeTranslations.get(EntityType.MQTT_CLIENT_CREDENTIALS);
    config.entityResources = entityTypeResources.get(EntityType.MQTT_CLIENT_CREDENTIALS);
    const $entity = this.dialog.open<AddEntityDialogComponent, AddEntityDialogData<MqttClientCredentials>,
      MqttClientCredentials>(AddEntityDialogComponent, {
      disableClose: true,
      panelClass: ['tb-dialog', 'tb-fullscreen-dialog'],
      data: {
        entitiesTableConfig: config
      }
    }).afterClosed();
    $entity.subscribe(
      (entity) => {
        if (entity) {
          this.mqttClientCredentialsService.saveMqttClientCredentials(entity).subscribe();
        }
      }
    );
  }

  ngAfterViewInit(): void {
    this.store.pipe(
      select(selectUserDetails),
      map((user) => user.additionalInfo?.config)).pipe(
      map((data) => {
        const portMqtt = data[ConfigParams.PORT_MQTT];
        const basicAuth = data[ConfigParams.BASIC_AUTH];
        this.steps = this.instructionsService.setSteps(basicAuth);
        // @ts-ignore
        window.mqttPort = portMqtt;
        this.configParams = {} as BrokerConfig;
        this.configParams[ConfigParams.BASIC_AUTH] = basicAuth;
        this.configParams[ConfigParams.PORT_MQTT] = portMqtt;
        this.configParams[ConfigParams.BASIC_AUTH]  ? this.init('client') : this.init('subscribe');
        return data;
      }
    )).subscribe();
  }
}
