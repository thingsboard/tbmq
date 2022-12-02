import { Component, Input, OnInit, ViewChild } from '@angular/core';
import { EntityId } from "@shared/models/id/entity-id";
import { SessionsTableConfig } from "@home/pages/sessions/sessions-table-config";
import { EntitiesTableComponent } from "@home/components/entity/entities-table.component";
import { MqttClientSessionService } from "@core/http/mqtt-client-session.service";
import { TranslateService } from "@ngx-translate/core";
import { DatePipe } from "@angular/common";
import { MatDialog } from "@angular/material/dialog";

@Component({
  selector: 'tb-sessions-table',
  templateUrl: './sessions-table.component.html',
  styleUrls: ['./sessions-table.component.scss']
})
export class SessionsTableComponent implements OnInit {

  @Input()
  detailsMode: boolean;

  activeValue = false;
  dirtyValue = false;
  entityIdValue: EntityId;

  @Input()
  set active(active: boolean) {
    if (this.activeValue !== active) {
      this.activeValue = active;
      if (this.activeValue && this.dirtyValue) {
        this.dirtyValue = false;
        this.entitiesTable.updateData();
      }
    }
  }

  @Input()
  set entityId(entityId: EntityId) {
    this.entityIdValue = entityId;
    if (this.sessionsTableConfig && this.sessionsTableConfig.entityId !== entityId) {
      this.sessionsTableConfig.entityId = entityId;
      this.entitiesTable.resetSortAndFilter(this.activeValue);
      if (!this.activeValue) {
        this.dirtyValue = true;
      }
    }
  }

  @ViewChild(EntitiesTableComponent, {static: true}) entitiesTable: EntitiesTableComponent;

  sessionsTableConfig: SessionsTableConfig

  constructor(private mqttClientSessionService: MqttClientSessionService,
              private translate: TranslateService,
              private datePipe: DatePipe,
              private dialog: MatDialog) {
  }

  ngOnInit(): void {
    this.dirtyValue = !this.activeValue;
    this.sessionsTableConfig = new SessionsTableConfig(
      this.mqttClientSessionService,
      this.translate,
      this.datePipe,
      this.dialog,
      this.entityIdValue
    );
  }

}
