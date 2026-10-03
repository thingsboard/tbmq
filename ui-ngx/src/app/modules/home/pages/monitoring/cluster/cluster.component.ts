///
/// ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
///
/// Copyright © 2016-2026 ThingsBoard, Inc. All Rights Reserved.
///
/// NOTICE: All information contained herein is, and remains
/// the property of ThingsBoard, Inc. and its suppliers,
/// if any.  The intellectual and technical concepts contained
/// herein are proprietary to ThingsBoard, Inc.
/// and its suppliers and may be covered by U.S. and Foreign Patents,
/// patents in process, and are protected by trade secret or copyright law.
///
/// Dissemination of this information or reproduction of this material is strictly forbidden
/// unless prior written permission is obtained from COMPANY.
///
/// Access to the source code contained herein is hereby forbidden to anyone except current COMPANY employees,
/// managers or contractors who have executed Confidentiality and Non-disclosure agreements
/// explicitly covering such access.
///
/// The copyright notice above does not evidence any actual or intended publication
/// or disclosure  of  this source code, which includes
/// information that is confidential and/or proprietary, and is a trade secret, of  COMPANY.
/// ANY REPRODUCTION, MODIFICATION, DISTRIBUTION, PUBLIC  PERFORMANCE,
/// OR PUBLIC DISPLAY OF OR THROUGH USE  OF THIS  SOURCE CODE  WITHOUT
/// THE EXPRESS WRITTEN CONSENT OF COMPANY IS STRICTLY PROHIBITED,
/// AND IN VIOLATION OF APPLICABLE LAWS AND INTERNATIONAL TREATIES.
/// THE RECEIPT OR POSSESSION OF THIS SOURCE CODE AND/OR RELATED INFORMATION
/// DOES NOT CONVEY OR IMPLY ANY RIGHTS TO REPRODUCE, DISCLOSE OR DISTRIBUTE ITS CONTENTS,
/// OR TO MANUFACTURE, USE, OR SELL ANYTHING THAT IT  MAY DESCRIBE, IN WHOLE OR IN PART.
///

import { ChangeDetectionStrategy, Component, computed, inject, OnDestroy, OnInit, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { MatIconButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { MatTooltip } from '@angular/material/tooltip';
import { FullscreenDirective } from '@shared/components/fullscreen.directive';
import { forkJoin, Observable, of, Subject, timer } from 'rxjs';
import { catchError, map, retry, switchMap, takeUntil } from 'rxjs/operators';
import { TimeseriesService } from '@core/http/timeseries.service';
import { ClientSessionService } from '@core/http/client-session.service';
import { KafkaService } from '@core/http/kafka.service';
import { IntegrationService } from '@core/http/integration.service';
import { ConfigService } from '@core/http/config.service';
import { RequestConfig } from '@core/http/http-utils';
import { PageLink } from '@shared/models/page/page-link';
import { PageData } from '@shared/models/page/page-data';
import { ChartKey, TimeseriesData, TOTAL_ENTITY_ID } from '@shared/models/chart.model';
import { ServiceType } from '@shared/models/resource-usage.model';
import { KafkaBroker, KafkaConsumerGroup } from '@shared/models/kafka.model';
import { IntegrationInfo } from '@shared/models/integration.models';
import { SECOND } from '@shared/models/time/time.models';
import { LiveStatsService } from '@core/services/live-stats.service';
import { emptyRateSeries } from '@shared/models/live-stats.model';
import { ClusterMapComponent } from '@home/pages/monitoring/cluster/cluster-map.component';
import { ClusterNodeInspectorComponent } from '@home/pages/monitoring/cluster/cluster-node-inspector.component';
import {
  assembleClusterVm,
  ClusterMapVm,
  ClusterRawData,
  CLUSTER_RATE_KEYS,
  emptyClusterVm,
  HISTORY_WINDOW_MS,
  KAFKA_NODE_ID
} from '@home/pages/monitoring/cluster/cluster.models';

const REFRESH_INTERVAL = 10 * SECOND;
// Consumer-group lag is computed through the Kafka admin client, so it is refreshed less often than node stats.
const KAFKA_REFRESH_EVERY_TICKS = 6;
const QUIET_REQUEST: RequestConfig = {ignoreLoading: true, ignoreErrors: true};
const MESSAGE_RATE_KEYS: ChartKey[] = [...CLUSTER_RATE_KEYS];

const quiet = <T>(source: Observable<T>): Observable<T | null> => source.pipe(catchError(() => of(null)));

@Component({
    selector: 'tb-cluster',
    templateUrl: './cluster.component.html',
    styleUrls: ['./cluster.component.scss'],
    imports: [TranslateModule, MatIconButton, MatIcon, MatTooltip, FullscreenDirective, ClusterMapComponent, ClusterNodeInspectorComponent],
    changeDetection: ChangeDetectionStrategy.OnPush
})
export class ClusterComponent implements OnInit, OnDestroy {

  readonly vm = signal<ClusterMapVm>(emptyClusterVm());
  readonly selectedId = signal<string | null>(null);
  readonly lastTickOk = signal(true);
  readonly staleSeconds = signal(0);
  readonly fullscreen = signal(false);
  readonly historyWindow = signal(HISTORY_WINDOW_MS);
  readonly animate = !window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  readonly selectedNode = computed(() => {
    const id = this.selectedId();
    const {brokers, executors} = this.vm();
    return brokers.concat(executors).find(node => node.id === id) ?? null;
  });
  readonly selectedKafka = computed(() => this.selectedId() === KAFKA_NODE_ID ? this.vm().kafka : null);
  // Executors and Kafka have no message timeseries, so their inspector shows no chart and nothing is watched for them.
  private readonly seriesSource = computed(() => {
    const id = this.selectedId();
    const isBroker = !id || this.vm().brokers.some(broker => broker.id === id);
    return {entityId: isBroker ? (id ?? TOTAL_ENTITY_ID) : null, windowMs: this.historyWindow()};
  }, {equal: (a, b) => a.entityId === b.entityId && a.windowMs === b.windowMs});
  readonly selectedSeries = toSignal(toObservable(this.seriesSource).pipe(
    switchMap(({entityId, windowMs}) => entityId
      ? this.liveStats.watch(entityId, MESSAGE_RATE_KEYS, {windowMs})
      : of(emptyRateSeries(MESSAGE_RATE_KEYS)))
  ), {initialValue: emptyRateSeries(MESSAGE_RATE_KEYS)});

  private readonly timeseriesService = inject(TimeseriesService);
  private readonly clientSessionService = inject(ClientSessionService);
  private readonly kafkaService = inject(KafkaService);
  private readonly integrationService = inject(IntegrationService);
  private readonly configService = inject(ConfigService);
  private readonly liveStats = inject(LiveStatsService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  private lastUpdated: number | null = null;
  private kafkaBrokers: PageData<KafkaBroker> | null = null;
  private consumerGroups: PageData<KafkaConsumerGroup> | null = null;
  private integrations: PageData<IntegrationInfo> | null = null;
  private readonly destroy$ = new Subject<void>();

  ngOnInit() {
    const nodeId = this.route.snapshot.queryParamMap.get('nodeId');
    if (nodeId) {
      this.selectedId.set(nodeId);
    }
    timer(0, REFRESH_INTERVAL).pipe(
      switchMap(tick => this.loadTick(tick)),
      retry({delay: REFRESH_INTERVAL}),
      takeUntil(this.destroy$)
    ).subscribe(raw => this.applyTick(raw));
  }

  ngOnDestroy() {
    this.destroy$.next();
    this.destroy$.complete();
  }

  toggleSelection(id: string) {
    this.select(this.selectedId() === id ? null : id);
  }

  clearSelection() {
    this.select(null);
  }

  setHistoryWindow(windowMs: number) {
    this.historyWindow.set(windowMs);
  }

  private select(id: string | null) {
    this.selectedId.set(id);
    this.router.navigate([], {relativeTo: this.route, queryParams: {nodeId: id}, queryParamsHandling: 'merge', replaceUrl: true});
  }

  private loadTick(tick: number): Observable<ClusterRawData> {
    const refreshKafka = tick % KAFKA_REFRESH_EVERY_TICKS === 0;
    return forkJoin({
      services: quiet(this.timeseriesService.getServiceInfos(QUIET_REQUEST)),
      sessionsByNode: quiet(this.clientSessionService.getClientSessionsStatsByNode(QUIET_REQUEST)),
      latestTotal: quiet(this.timeseriesService.getLatestTimeseries(TOTAL_ENTITY_ID, MESSAGE_RATE_KEYS)),
      kafkaBrokers: refreshKafka
        ? quiet(this.kafkaService.getKafkaBrokers(new PageLink(100), QUIET_REQUEST))
        : of(this.kafkaBrokers),
      consumerGroups: refreshKafka
        ? quiet(this.kafkaService.getKafkaConsumerGroups(new PageLink(1000), QUIET_REQUEST))
        : of(this.consumerGroups),
      integrations: refreshKafka
        ? quiet(this.integrationService.getIntegrationsInfo(new PageLink(1000), QUIET_REQUEST))
        : of(this.integrations)
    }).pipe(
      switchMap(partial => {
        const brokerIds = (partial.services?.data ?? [])
          .filter(service => service.serviceType === ServiceType.TBMQ)
          .map(service => service.serviceId);
        return this.loadBrokerRates(brokerIds).pipe(
          map(latestByBroker => ({...partial, latestByBroker}))
        );
      })
    );
  }

  private loadBrokerRates(brokerIds: string[]): Observable<Record<string, TimeseriesData | null>> {
    if (!brokerIds.length) {
      return of({});
    }
    return forkJoin(brokerIds.map(id =>
      quiet(this.timeseriesService.getLatestTimeseries(id, MESSAGE_RATE_KEYS)).pipe(map(data => [id, data] as const))
    )).pipe(
      map(entries => Object.fromEntries(entries))
    );
  }

  private applyTick(raw: ClusterRawData) {
    if (!raw.services) {
      this.lastTickOk.set(false);
      this.staleSeconds.set(this.lastUpdated ? Math.round((Date.now() - this.lastUpdated) / SECOND) : 0);
      return;
    }
    this.kafkaBrokers = raw.kafkaBrokers ?? this.kafkaBrokers;
    this.consumerGroups = raw.consumerGroups ?? this.consumerGroups;
    this.integrations = raw.integrations ?? this.integrations;

    const vm = assembleClusterVm(raw, this.vm(), this.statsInterval());
    this.vm.set(vm);
    this.lastUpdated = Date.now();
    this.lastTickOk.set(true);
    this.ensureSelection(vm);
  }

  private ensureSelection(vm: ClusterMapVm) {
    const selected = this.selectedId();
    if (selected && selected !== KAFKA_NODE_ID && !vm.brokers.concat(vm.executors).some(node => node.id === selected)) {
      this.select(null);
    }
  }

  private statsInterval(): number {
    return this.configService.brokerConfig?.statsCollectionInterval || 1;
  }
}
