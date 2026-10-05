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

import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import {
  ClusterMapVm,
  ClusterNodeVm,
  COLUMN_X,
  compactNumber,
  CLIENTS_RING_RADIUS,
  hexagonPoints,
  KAFKA_NODE_ID,
  KAFKA_RADIUS,
  ringArcDash
} from '@home/pages/monitoring/cluster/cluster.models';

@Component({
    selector: 'tb-cluster-map',
    templateUrl: './cluster-map.component.html',
    styleUrls: ['./cluster-map.component.scss'],
    imports: [NgTemplateOutlet, TranslateModule],
    changeDetection: ChangeDetectionStrategy.OnPush
})
export class ClusterMapComponent {

  readonly vm = input.required<ClusterMapVm>();
  readonly selectedId = input<string | null>(null);
  readonly animate = input(true);

  readonly nodeSelected = output<string>();

  readonly columnX = COLUMN_X;
  readonly kafkaNodeId = KAFKA_NODE_ID;
  readonly clientsRingR = CLIENTS_RING_RADIUS;
  readonly compactNumber = compactNumber;
  readonly ringArcDash = ringArcDash;

  onSelect(node: ClusterNodeVm, event?: Event) {
    event?.preventDefault();
    this.nodeSelected.emit(node.id);
  }

  clientTypes(map: ClusterMapVm): {key: string; count: number | null; label: string}[] {
    const known = map.clients.hasSessionStats;
    return [
      {key: 'device', count: known ? map.clients.deviceSessions : null, label: 'monitoring.cluster.devices'},
      {key: 'application', count: known ? map.clients.applicationSessions : null, label: 'monitoring.cluster.applications'},
      {key: 'integration', count: known ? map.clients.integrationSessions : null, label: 'monitoring.cluster.integrations'}
    ];
  }

  onSelectKafka(event?: Event) {
    event?.preventDefault();
    this.nodeSelected.emit(KAFKA_NODE_ID);
  }

  kafkaHaloPoints(map: ClusterMapVm): string {
    return hexagonPoints(map.kafka.x, map.kafka.y, KAFKA_RADIUS + 7);
  }

  particleBegin(slot: number, slots: number, durSec: number): string {
    return `-${(slot * durSec / slots).toFixed(2)}s`;
  }
}
