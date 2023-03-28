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

import { AfterViewInit, Component, Input, OnDestroy, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { forkJoin, interval, Observable, Subject, timer } from 'rxjs';
import { retry, switchMap, takeUntil } from 'rxjs/operators';
import { StatsService } from '@core/http/stats.service';
import { MatDialog } from '@angular/material/dialog';
import { AddEntityDialogComponent } from '@home/components/entity/add-entity-dialog.component';
import { AddEntityDialogData } from '@home/models/entity/entity-component.models';
import { ClientCredentialsInfo, MqttClientCredentials } from '@shared/models/client-crenetials.model';
import { EntityTableConfig } from '@home/models/entity/entities-table-config.models';
import { EntityType, entityTypeResources, entityTypeTranslations } from '@shared/models/entity-type.models';
import { MqttClientCredentialsComponent } from '@home/pages/mqtt-client-credentials/mqtt-client-credentials.component';
import { MqttClientCredentialsService } from '@core/http/mqtt-client-credentials.service';
import { MqttClientSessionService } from '@core/http/mqtt-client-session.service';
import { ClientSessionStatsInfo } from '@shared/models/session.model';

@Component({
  selector: 'tb-monitor-cards',
  templateUrl: './monitor-cards.component.html',
  styleUrls: ['./monitor-cards.component.scss']
})
export class MonitorCardsComponent implements OnInit, AfterViewInit, OnDestroy {

  @Input()
  isLoading$: Observable<boolean>;

  pollData$: Observable<any>;
  private stopPolling = new Subject();

  sessionsValue: ClientSessionStatsInfo;
  clientCredentialsValue: ClientCredentialsInfo;

  constructor(private statsService: StatsService,
              private dialog: MatDialog,
              private mqttClientCredentialsService: MqttClientCredentialsService,
              private mqttClientSessionService: MqttClientSessionService,
              private router: Router) {
  }

  ngOnInit(): void {
    this.pollData$ = timer(0, 10000).pipe(
      switchMap(() => forkJoin(
        this.mqttClientSessionService.getClientSessionsStats(),
        this.mqttClientCredentialsService.getClientCredentialsStatsInfo()
      )),
      retry(),
      takeUntil(this.stopPolling)
    );
  }

  ngOnDestroy(): void {
    this.stopPolling.next();
  }

  viewDocumentation(page: string) {
    window.open(`https://thingsboard.io/docs/mqtt-broker/${page}`, '_blank');
  }

  navigateToPage(page: string) {
    this.router.navigateByUrl(`/${page}`);
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
    this.startPolling();
  }

  startPolling() {
    this.pollData$.subscribe(data => {
      this.sessionsValue = data[0];
      this.clientCredentialsValue = data[1];
    });
  }
}
