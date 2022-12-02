import {
  EntityTableColumn,
  EntityTableConfig
} from '@home/models/entity/entities-table-config.models';
import { TranslateService } from '@ngx-translate/core';
import { DatePipe } from '@angular/common';
import { MatDialog } from '@angular/material/dialog';
import { TimePageLink } from '@shared/models/page/page-link';
import { Observable } from 'rxjs';
import { PageData } from '@shared/models/page/page-data';
import { EntityId } from '@shared/models/id/entity-id';
import { MqttClientSessionService } from "@core/http/mqtt-client-session.service";
import {
  SessionsDetailsDialogComponent,
  SessionsDetailsDialogData
} from "@home/pages/sessions/sessions-details-dialog.component";
import {
  connectionStateColor,
  connectionStateTranslationMap,
  DetailedClientSessionInfo
} from "@shared/models/mqtt-session.model";
import { clientTypeTranslationMap } from "@shared/models/mqtt-client.model";

export class SessionsTableConfig extends EntityTableConfig<DetailedClientSessionInfo, TimePageLink> {

  constructor(private mqttClientSessionService: MqttClientSessionService,
              private translate: TranslateService,
              private datePipe: DatePipe,
              private dialog: MatDialog,
              public entityId: EntityId = null) {
    super();
    this.loadDataOnInit = true;
    this.detailsPanelEnabled = false;
    this.selectionEnabled = false;
    this.searchEnabled = true;
    this.addEnabled = false;
    this.entitiesDeleteEnabled = false;
    this.tableTitle = this.translate.instant('mqtt-client-session.type-sessions');
    this.entityTranslations = {
      noEntities: 'mqtt-client-session.no-session-text',
      search: 'mqtt-client-session.search'
    };
    // this.actionsColumnTitle = 'mqtt-client-session.session-details';

    this.entitiesFetchFunction = pageLink => this.fetchSessions(pageLink);
    this.handleRowClick = ($event, entity) => this.showSessionDetails($event, entity);

    this.columns.push(
      new EntityTableColumn<DetailedClientSessionInfo>('clientId', 'mqtt-client.client-id', '25%'),
      new EntityTableColumn<DetailedClientSessionInfo>('connectionState', 'mqtt-client-session.connect', '25%',
        (entity) => this.translate.instant(connectionStateTranslationMap.get(entity.connectionState)),
        (entity) => ({ color: connectionStateColor.get(entity.connectionState) })
      ),
      new EntityTableColumn<DetailedClientSessionInfo>('nodeId', 'mqtt-client-session.node-id', '25%'),
      new EntityTableColumn<DetailedClientSessionInfo>('clientType', 'mqtt-client.client-type', '25%',
        (entity) => this.translate.instant(clientTypeTranslationMap.get(entity.clientType))
      )
    );
    /*this.cellActionDescriptors.push(
      {
        name: this.translate.instant('mqtt-client-session.session-details'),
        icon: 'more_horiz',
        isEnabled: () => true,
        onAction: ($event, entity) => this.showSessionDetails($event, entity)
      }
    );*/
  }

  private fetchSessions(pageLink: TimePageLink): Observable<PageData<DetailedClientSessionInfo>> {
    return this.mqttClientSessionService.getShortClientSessionInfos(pageLink);
  }

  private showSessionDetails($event: Event, entity: DetailedClientSessionInfo) {
    if ($event) {
      $event.stopPropagation();
    }
    this.mqttClientSessionService.getDetailedClientSessionInfo(entity.clientId).subscribe(
      session => {
        this.dialog.open<SessionsDetailsDialogComponent, SessionsDetailsDialogData>(SessionsDetailsDialogComponent, {
          disableClose: true,
          panelClass: ['tb-dialog', 'tb-fullscreen-dialog'],
          data: {
            session: session
          }
        }).afterClosed().subscribe(() =>{
          this.table.updateData();
        });
      }
    );
    return false;
  }
}
