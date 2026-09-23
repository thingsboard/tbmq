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

import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { Router } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { MatButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { TsValue } from '@shared/models/chart.model';
import {
  resourceUsageStatusStyle,
  resourceUsageStatusTranslationMap,
  serviceTypeMap
} from '@shared/models/resource-usage.model';
import { formatLargeNumber } from '@core/utils';
import { ClusterSparklineComponent } from '@home/pages/monitoring/cluster/cluster-sparkline.component';
import { ClusterNodeVm, compactNumber, UsageLevel, usageLevel } from '@home/pages/monitoring/cluster/cluster.models';

interface MeterVm {
  label: string;
  value: number | null;
  level: UsageLevel;
}

@Component({
    selector: 'tb-cluster-node-inspector',
    templateUrl: './cluster-node-inspector.component.html',
    styleUrls: ['./cluster-node-inspector.component.scss'],
    imports: [TranslateModule, MatButton, MatIcon, ClusterSparklineComponent],
    changeDetection: ChangeDetectionStrategy.OnPush
})
export class ClusterNodeInspectorComponent {

  readonly node = input<ClusterNodeVm | null>(null);
  readonly samples = input<TsValue[]>([]);

  readonly statusStyle = computed(() => resourceUsageStatusStyle.get(this.node()?.status));
  readonly statusLabel = computed(() => resourceUsageStatusTranslationMap.get(this.node()?.status));
  readonly serviceTypeLabel = computed(() => serviceTypeMap.get(this.node()?.serviceType));
  readonly meters = computed<MeterVm[]>(() => {
    const node = this.node();
    if (!node) {
      return [];
    }
    return [
      {label: 'monitoring.resource-usage.cpu-usage', value: node.cpu, level: usageLevel(node.cpu)},
      {label: 'monitoring.resource-usage.memory-usage', value: node.ram, level: usageLevel(node.ram)},
      {label: 'monitoring.resource-usage.disk-usage', value: node.disk, level: usageLevel(node.disk)}
    ];
  });

  readonly formatLargeNumber = formatLargeNumber;
  readonly compactNumber = compactNumber;

  private readonly router = inject(Router);

  openSessions(node: ClusterNodeVm) {
    this.router.navigate(['/sessions'], {queryParams: {nodeIdList: node.id}});
  }
}
