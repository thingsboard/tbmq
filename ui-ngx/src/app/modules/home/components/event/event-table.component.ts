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

import {
  AfterViewInit,
  ChangeDetectorRef,
  Component,
  Input,
  OnDestroy,
  OnInit,
  ViewContainerRef,
  input,
  output,
  viewChild
} from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { DatePipe } from '@angular/common';
import { MatDialog } from '@angular/material/dialog';
import { EntitiesTableComponent } from '@home/components/entity/entities-table.component';
import { EventTableConfig } from './event-table-config';
import { EventService } from '@core/http/event.service';
import { DialogService } from '@core/services/dialog.service';
import { EventBody, EventType } from '@shared/models/event.models';
import { Overlay } from '@angular/cdk/overlay';
import { Subscription } from 'rxjs';
import { isNotEmptyStr } from '@core/utils';

@Component({
  selector: 'tb-event-table',
  templateUrl: './event-table.component.html',
  styleUrls: ['./event-table.component.scss'],
  imports: [
    EntitiesTableComponent
  ]
})
export class EventTableComponent implements OnInit, AfterViewInit, OnDestroy {

  readonly tenantId = input<string>();
  readonly defaultEventType = input<EventType>();
  readonly disabledEventTypes = input<Array<EventType>>();
  readonly isReadOnly = input<boolean>(false);

  activeValue = false;
  dirtyValue = false;
  entityIdValue: string;

  get active(): boolean {
    return this.activeValue;
  }

  @Input()
  set active(active: boolean) {
    if (this.activeValue !== active) {
      this.activeValue = active;
      if (this.activeValue && this.dirtyValue) {
        this.dirtyValue = false;
        this.entitiesTable().updateData();
      }
    }
  }

  @Input()
  set entityId(entityId: string) {
    this.entityIdValue = entityId;
    if (this.eventTableConfig && this.eventTableConfig.entityId !== entityId) {
      this.eventTableConfig.eventType = this.defaultEventType();
      this.eventTableConfig.entityId = entityId;
      this.entitiesTable().resetSortAndFilter(this.activeValue);
      if (!this.activeValue) {
        this.dirtyValue = true;
      }
    }
  }

  private functionTestButtonLabelValue: string;

  get functionTestButtonLabel(): string {
    return this.functionTestButtonLabelValue;
  }

  @Input()
  set functionTestButtonLabel(value: string) {
    if (isNotEmptyStr(value)) {
      this.functionTestButtonLabelValue = value;
    } else {
      this.functionTestButtonLabelValue = '';
    }
    if (this.eventTableConfig) {
      this.eventTableConfig.testButtonLabel = this.functionTestButtonLabel;
      this.eventTableConfig.updateCellAction();
    }
  }

  readonly debugEventSelected = output<EventBody>();

  readonly entitiesTable = viewChild(EntitiesTableComponent);

  eventTableConfig: EventTableConfig;

  private isEmptyData$: Subscription;

  constructor(private eventService: EventService,
              private dialogService: DialogService,
              private translate: TranslateService,
              private datePipe: DatePipe,
              private dialog: MatDialog,
              private overlay: Overlay,
              private viewContainerRef: ViewContainerRef,
              private cd: ChangeDetectorRef) {
  }

  ngOnInit() {
    this.dirtyValue = !this.activeValue;
    this.eventTableConfig = new EventTableConfig(
      this.eventService,
      this.dialogService,
      this.translate,
      this.datePipe,
      this.dialog,
      this.entityIdValue,
      this.tenantId(),
      this.defaultEventType(),
      this.disabledEventTypes(),
      this.overlay,
      this.viewContainerRef,
      this.cd,
      this.isReadOnly(),
      this.functionTestButtonLabel
    );
  }

  ngAfterViewInit() {
    this.isEmptyData$ = this.entitiesTable().dataSource.isEmpty().subscribe(value => this.eventTableConfig.hideClearEventAction = value);
  }

  ngOnDestroy() {
    if (this.isEmptyData$) {
      this.isEmptyData$.unsubscribe();
    }
  }

}
