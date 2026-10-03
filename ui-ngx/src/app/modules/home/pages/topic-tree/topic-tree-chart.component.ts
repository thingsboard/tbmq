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

import { AfterViewInit, Component, DestroyRef, ElementRef, OnDestroy, OnInit, ViewChild, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { EMPTY, Observable, Subject, merge } from 'rxjs';
import { catchError, debounceTime, switchMap } from 'rxjs/operators';
import * as echarts from 'echarts/core';
import { BarChart } from 'echarts/charts';
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components';
import { CanvasRenderer } from 'echarts/renderers';
import type { ECharts } from 'echarts/core';
import { MatButtonToggle, MatButtonToggleGroup } from '@angular/material/button-toggle';
import { MatIconButton } from '@angular/material/button';
import { MatFormField, MatLabel, MatPrefix } from '@angular/material/form-field';
import { MatIcon } from '@angular/material/icon';
import { MatInput } from '@angular/material/input';
import { MatOption, MatSelect } from '@angular/material/select';
import { MatProgressBar } from '@angular/material/progress-bar';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { TopicTreeService } from '@core/http/topic-tree.service';
import { TopicChartPoint } from '@shared/models/topic-tree.models';

echarts.use([BarChart, GridComponent, TooltipComponent, LegendComponent, CanvasRenderer]);

const SUBSCRIPTIONS_COLOR = '#4FA889';
const CLIENTS_COLOR = '#D1A656';
const FILTER_MIN_LENGTH = 2;

@Component({
  selector: 'tb-topic-tree-chart',
  templateUrl: './topic-tree-chart.component.html',
  styleUrls: ['./topic-tree-chart.component.scss'],
  imports: [ReactiveFormsModule, MatButtonToggleGroup, MatButtonToggle, MatIconButton, MatFormField, MatLabel, MatPrefix, MatIcon, MatInput, MatSelect, MatOption, MatProgressBar, TranslateModule]
})
export class TopicTreeChartComponent implements OnInit, AfterViewInit, OnDestroy {

  @ViewChild('chart', { static: true }) chartEl: ElementRef<HTMLDivElement>;

  readonly limits = [10, 25, 50, 100];
  readonly depths = [null, 1, 2, 3, 4];
  points: TopicChartPoint[] = [];
  loading = false;

  private readonly fb = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly service = inject(TopicTreeService);
  private readonly translate = inject(TranslateService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly reload$ = new Subject<void>();
  private chart: ECharts;
  private resizeObserver: ResizeObserver;

  readonly form = this.fb.group({
    filter: [this.route.snapshot.queryParamMap.get('filter') ?? ''],
    metric: ['subscriptions' as 'subscriptions' | 'clients'],
    limit: [25],
    depth: [null as number | null]
  });

  ngOnInit(): void {
    merge(this.form.valueChanges.pipe(debounceTime(300)), this.reload$).pipe(
      switchMap(() => this.fetch()),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(points => {
      this.points = points;
      this.loading = false;
      this.render();
    });
    this.reload$.next();
  }

  ngAfterViewInit(): void {
    this.chart = echarts.init(this.chartEl.nativeElement);
    this.chart.on('click', params => {
      const point = this.points[this.points.length - 1 - params.dataIndex];
      if (point) {
        this.router.navigate(['/topic-tree'], { queryParams: { path: point.path } });
      }
    });
    this.resizeObserver = new ResizeObserver(() => this.chart.resize());
    this.resizeObserver.observe(this.chartEl.nativeElement);
    this.render();
  }

  ngOnDestroy(): void {
    this.resizeObserver?.disconnect();
    this.chart?.dispose();
    this.chart = null;
  }

  load(): void {
    this.reload$.next();
  }

  private fetch(): Observable<TopicChartPoint[]> {
    const { filter, metric, limit, depth } = this.form.value;
    const text = (filter ?? '').trim();
    if (text && text.length < FILTER_MIN_LENGTH) {
      return EMPTY;
    }
    this.loading = true;
    return this.service.getChart(text, metric, limit, depth ?? undefined).pipe(
      catchError(() => {
        this.loading = false;
        return EMPTY;
      })
    );
  }

  private render(): void {
    if (!this.chart) {
      return;
    }
    const reversed = [...this.points].reverse();
    const subsLabel = this.translate.instant('topic-tree.column.subscriptions');
    const clientsLabel = this.translate.instant('topic-tree.column.clients');
    this.chartEl.nativeElement.style.height = Math.max(240, this.points.length * 36 + 60) + 'px';
    this.chart.resize();
    this.chart.setOption({
      grid: { left: 8, right: 24, top: 32, bottom: 8, containLabel: true },
      legend: { top: 0 },
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      xAxis: { type: 'value' },
      yAxis: {
        type: 'category',
        data: reversed.map(p => p.path),
        axisLabel: { width: 260, overflow: 'truncate', fontFamily: 'Roboto Mono, monospace' }
      },
      series: [
        { name: subsLabel, type: 'bar', data: reversed.map(p => p.subscriptionsTotal), itemStyle: { color: SUBSCRIPTIONS_COLOR } },
        { name: clientsLabel, type: 'bar', data: reversed.map(p => p.clientsTotal), itemStyle: { color: CLIENTS_COLOR } }
      ]
    }, true);
  }
}
