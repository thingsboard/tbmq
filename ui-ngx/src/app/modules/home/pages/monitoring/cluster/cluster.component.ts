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

import { ChangeDetectionStrategy, Component, computed, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { HelpComponent } from '@shared/components/help.component';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { MatButton, MatIconButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { MatTooltip } from '@angular/material/tooltip';
import { FullscreenDirective } from '@shared/components/fullscreen.directive';
import { forkJoin, Observable, of, timer } from 'rxjs';
import { catchError, map, startWith, switchMap } from 'rxjs/operators';
import { TimeseriesService } from '@core/http/timeseries.service';
import { ClientSessionService } from '@core/http/client-session.service';
import { KafkaService } from '@core/http/kafka.service';
import { IntegrationService } from '@core/http/integration.service';
import { ConfigService } from '@core/http/config.service';
import { TimeService } from '@core/services/time.service';
import { RequestConfig } from '@core/http/http-utils';
import { PageLink } from '@shared/models/page/page-link';
import { TOTAL_ENTITY_ID } from '@shared/models/chart.model';
import { ServiceType } from '@shared/models/resource-usage.model';
import { MINUTE, SECOND } from '@shared/models/time/time.models';
import { LiveStatsService } from '@core/services/live-stats.service';
import { emptyRateSeries, statsIntervalMinutes } from '@shared/models/live-stats.model';
import { SettingsService } from '@core/http/settings.service';
import { defaultResourceUsageAlertSettings } from '@shared/models/settings.models';
import { ClusterMapComponent } from '@home/pages/monitoring/cluster/cluster-map.component';
import { ClusterNodeInspectorComponent } from '@home/pages/monitoring/cluster/cluster-node-inspector.component';
import {
  assembleClusterVm,
  ClusterCadence,
  ClusterRawData,
  CLUSTER_RATE_KEYS,
  emptyClusterRawData,
  HISTORY_WINDOW_MS,
  KAFKA_NODE_ID,
  NODE_SERIES_KEYS,
  secondsSince,
  SESSIONS_KEY
} from '@home/pages/monitoring/cluster/cluster.models';

// Session counts come straight from the session cache, so they are the one source worth polling this often.
const SESSIONS_REFRESH_MS = 3 * SECOND;
// Consumer-group lag goes through the Kafka admin client and integrations rarely change.
const SLOW_SOURCES_REFRESH_MS = MINUTE;
// Staleness and "no heartbeat for N s" follow this clock, independent of which source refreshed last.
const CLOCK_TICK_MS = 5 * SECOND;
const DEFAULT_HEARTBEAT_SEC = 60;
const MIN_SERVICES_REFRESH_MS = 5 * SECOND;
const QUIET_REQUEST: RequestConfig = {ignoreLoading: true, ignoreErrors: true};
const TOTAL_SERIES_KEYS: readonly string[] = [...CLUSTER_RATE_KEYS, SESSIONS_KEY];

const quiet = <T>(source: Observable<T>): Observable<T | null> => source.pipe(catchError(() => of(null)));

type SlowSources = Pick<ClusterRawData, 'kafkaBrokers' | 'consumerGroups' | 'integrations'>;

// A source that failed this round keeps its previous value instead of being cleared.
function withoutNulls<T extends object>(values: T): Partial<T> {
  return Object.fromEntries(Object.entries(values).filter(([, value]) => value !== null)) as Partial<T>;
}

@Component({
    selector: 'tb-cluster',
    templateUrl: './cluster.component.html',
    styleUrls: ['./cluster.component.scss'],
    imports: [TranslateModule, MatButton, MatIconButton, MatIcon, MatTooltip, FullscreenDirective, HelpComponent, ClusterMapComponent,
      ClusterNodeInspectorComponent],
    changeDetection: ChangeDetectionStrategy.OnPush,
    host: {'(keydown.escape)': 'clearSelection()'}
})
export class ClusterComponent implements OnInit {

  private readonly timeseriesService = inject(TimeseriesService);
  private readonly clientSessionService = inject(ClientSessionService);
  private readonly kafkaService = inject(KafkaService);
  private readonly integrationService = inject(IntegrationService);
  private readonly configService = inject(ConfigService);
  private readonly timeService = inject(TimeService);
  private readonly liveStats = inject(LiveStatsService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  private readonly cadence: ClusterCadence = {
    statsIntervalMinutes: statsIntervalMinutes(this.configService.brokerConfig),
    heartbeatMs: (this.configService.brokerConfig?.systemInfoPersistFrequencySec || DEFAULT_HEARTBEAT_SEC) * SECOND
  };
  private readonly raw = signal<ClusterRawData>(emptyClusterRawData());
  readonly now = signal(Date.now());

  readonly thresholds = toSignal(inject(SettingsService).getResourceUsageThresholds(),
    {initialValue: defaultResourceUsageAlertSettings});
  readonly hideInactive = signal(false);
  readonly vm = computed(() => assembleClusterVm(this.raw(), this.cadence, this.thresholds(), this.hideInactive(), this.now()));
  readonly alertSummary = computed(() => {
    const alerts = this.vm().alerts;
    return alerts.length ? {count: alerts.length, level: alerts[0].level} : null;
  });
  readonly selectedId = signal<string | null>(null);
  readonly servicesOk = signal(true);
  readonly lastUpdated = signal<number | null>(null);
  readonly staleSeconds = signal(0);
  readonly fullscreen = signal(false);
  readonly historyWindow = signal(HISTORY_WINDOW_MS);

  readonly selectedNode = computed(() => {
    const id = this.selectedId();
    const {brokers, executors} = this.vm();
    return brokers.concat(executors).find(node => node.key === id) ?? null;
  });
  readonly selectedKafka = computed(() => this.selectedId() === KAFKA_NODE_ID ? this.vm().kafka : null);
  // Executors and Kafka have no message timeseries, so their inspector shows no chart and nothing is watched for them.
  private readonly seriesSource = computed(() => {
    const id = this.selectedId();
    const isBroker = !id || this.vm().brokers.some(broker => broker.key === id);
    return {entityId: isBroker ? (id ?? TOTAL_ENTITY_ID) : null, windowMs: this.historyWindow()};
  }, {equal: (a, b) => a.entityId === b.entityId && a.windowMs === b.windowMs});
  readonly selectedSeries = toSignal(toObservable(this.seriesSource).pipe(
    switchMap(({entityId, windowMs}) => {
      if (!entityId) {
        return of(emptyRateSeries(CLUSTER_RATE_KEYS));
      }
      // The cluster total has no CPU, memory or disk series.
      const keys = entityId === TOTAL_ENTITY_ID ? TOTAL_SERIES_KEYS : NODE_SERIES_KEYS;
      return this.liveStats.watch(entityId, keys, {windowMs});
    })
  ), {initialValue: emptyRateSeries(CLUSTER_RATE_KEYS)});
  private readonly brokerIds = computed(() => (this.raw().services?.data ?? [])
      .filter(service => service.serviceType === ServiceType.TBMQ)
      .map(service => service.serviceId)
      .sort(),
    {equal: (a, b) => a.join() === b.join()});
  private readonly brokerIds$ = toObservable(this.brokerIds);

  ngOnInit() {
    const nodeId = this.route.snapshot.queryParamMap.get('nodeId');
    if (nodeId) {
      this.selectedId.set(nodeId);
    }
    this.hideInactive.set(this.route.snapshot.queryParamMap.get('hideInactive') === 'true');
    this.pollServices();
    this.pollSessions();
    this.pollRates();
    this.pollSlowSources();
    timer(CLOCK_TICK_MS, CLOCK_TICK_MS).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.now.set(Date.now()));
  }

  toggleSelection(id: string) {
    this.select(this.selectedId() === id ? null : id);
  }

  clearSelection() {
    if (this.selectedId()) {
      this.select(null);
    }
  }

  // Double-clicking a node is two selection toggles already; only empty map space resets to the total.
  onMapDoubleClick(event: MouseEvent) {
    if (!(event.target as Element).closest('.tb-node')) {
      this.clearSelection();
    }
  }

  toggleHideInactive() {
    this.hideInactive.update(hide => !hide);
    if (this.selectionHidden()) {
      this.selectedId.set(null);
    }
    this.syncQueryParams();
  }

  setHistoryWindow(windowMs: number) {
    this.historyWindow.set(windowMs);
  }

  private select(id: string | null) {
    this.selectedId.set(id);
    this.syncQueryParams();
  }

  private syncQueryParams() {
    this.router.navigate([], {relativeTo: this.route, queryParams: {nodeId: this.selectedId(), hideInactive: this.hideInactive() || null},
      queryParamsHandling: 'merge', replaceUrl: true});
  }

  // Nodes persist their system info once per heartbeat; polling twice as often keeps the map at most half a beat behind.
  private pollServices() {
    const refreshMs = Math.max(MIN_SERVICES_REFRESH_MS, this.cadence.heartbeatMs / 2);
    this.poll(refreshMs, () => this.timeseriesService.getServiceInfos(QUIET_REQUEST)).subscribe(services => {
      if (!services) {
        const lastUpdated = this.lastUpdated();
        this.servicesOk.set(false);
        this.staleSeconds.set(secondsSince(lastUpdated, Date.now()));
        return;
      }
      this.patchRaw({services});
      this.lastUpdated.set(Date.now());
      this.servicesOk.set(true);
      this.dropMissingSelection();
    });
  }

  private pollSessions() {
    this.poll(SESSIONS_REFRESH_MS, () => this.clientSessionService.getClientSessionsStatsByNode(QUIET_REQUEST))
      .subscribe(sessionsByNode => sessionsByNode && this.patchRaw({sessionsByNode}));
  }

  // Message rates are persisted at each stats-interval boundary, so they are fetched right after it, in one request for
  // every broker, and again whenever the set of brokers changes.
  private pollRates() {
    this.brokerIds$.pipe(
      switchMap(brokerIds => this.timeService.getSyncTimer(this.cadence.statsIntervalMinutes * MINUTE).pipe(
        startWith(0),
        switchMap(() => quiet(this.timeseriesService.getLatestTimeseriesBatch(brokerIds,
          [...CLUSTER_RATE_KEYS], QUIET_REQUEST)))
      )),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(latest => latest && this.patchRaw({latest}));
  }

  private pollSlowSources() {
    this.poll(SLOW_SOURCES_REFRESH_MS, () => {
      const sources: {[K in keyof SlowSources]: Observable<SlowSources[K]>} = {
        kafkaBrokers: quiet(this.kafkaService.getKafkaBrokers(new PageLink(100), QUIET_REQUEST)),
        consumerGroups: quiet(this.kafkaService.getKafkaConsumerGroups(new PageLink(1000), QUIET_REQUEST)),
        integrations: quiet(this.integrationService.getIntegrationsInfo(new PageLink(1000), QUIET_REQUEST))
      };
      return forkJoin(sources).pipe(map(withoutNulls));
    }).subscribe(sources => sources && this.patchRaw(sources));
  }

  private poll<T>(periodMs: number, request: () => Observable<T>): Observable<T | null> {
    return timer(0, periodMs).pipe(
      switchMap(() => quiet(request())),
      takeUntilDestroyed(this.destroyRef)
    );
  }

  private patchRaw(patch: Partial<ClusterRawData>) {
    this.now.set(Date.now());
    this.raw.update(raw => ({...raw, ...patch}));
  }

  // A node that left the map (removed, or hidden as inactive) cannot stay selected.
  private dropMissingSelection() {
    if (this.selectionHidden()) {
      this.select(null);
    }
  }

  private selectionHidden(): boolean {
    const selected = this.selectedId();
    return !!selected && selected !== KAFKA_NODE_ID && !this.selectedNode();
  }
}
