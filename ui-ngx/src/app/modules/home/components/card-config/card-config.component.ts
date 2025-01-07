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

import { Component } from '@angular/core';
import {
  BrokerConfigTable,
  ConfigParams,
  ConfigParamsTranslationMap,
} from '@shared/models/config.model';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { TranslateService, TranslateModule } from '@ngx-translate/core';
import { ConfigService } from '@core/http/config.service';
import { HomePageTitleType } from '@shared/models/home-page.model';
import { EntityColumn, EntityTableColumn, formatBytes } from '@home/models/entity/entities-table-config.models';
import { DomSanitizer } from '@angular/platform-browser';
import { map } from 'rxjs/operators';
import { EntitiesTableHomeNoPagination } from '../entity/entities-table-no-pagination.component';
import { FlexModule } from '@angular/flex-layout/flex';
import { CardTitleButtonComponent } from '@shared/components/button/card-title-button.component';
import { MatTable, MatColumnDef, MatHeaderCellDef, MatHeaderCell, MatCellDef, MatCell, MatHeaderRowDef, MatHeaderRow, MatRowDef, MatRow } from '@angular/material/table';
import { MatSort, MatSortHeader } from '@angular/material/sort';
import { NgFor, NgStyle, NgIf } from '@angular/common';
import { ExtendedModule } from '@angular/flex-layout/extended';
import { CopyButtonComponent } from '@shared/components/button/copy-button.component';
import { MatIcon } from '@angular/material/icon';
import { MatTooltip } from '@angular/material/tooltip';

@Component({
    selector: 'tb-card-config',
    templateUrl: './card-config.component.html',
    styleUrls: ['./card-config.component.scss'],
    standalone: true,
    imports: [FlexModule, CardTitleButtonComponent, MatTable, MatSort, NgFor, MatColumnDef, MatHeaderCellDef, MatHeaderCell, MatSortHeader, MatCellDef, MatCell, NgStyle, ExtendedModule, NgIf, TranslateModule, CopyButtonComponent, MatIcon, MatTooltip, MatHeaderRowDef, MatHeaderRow, MatRowDef, MatRow]
})
export class CardConfigComponent extends EntitiesTableHomeNoPagination<BrokerConfigTable> {

  cardType = HomePageTitleType.CONFIG;
  configParamsTranslationMap = ConfigParamsTranslationMap;
  configParams = ConfigParams;

  fetchEntities$ = () => this.configService.getBrokerConfigPageData().pipe(
    map(data => {
      data.data = data.data.filter(
        el => el.key !== ConfigParams.existsBasicCredentials && el.key !== ConfigParams.existsX509Credentials
      );
      return data;
    })
  );
  tooltipContent = (type) => `${this.translate.instant('config.warning', {type: this.translate.instant(type)})}`;

  private config = this.configService.brokerConfig;

  constructor(protected store: Store<AppState>,
              private translate: TranslateService,
              private configService: ConfigService,
              protected domSanitizer: DomSanitizer) {
    super(domSanitizer);
  }

  getColumns() {
    const columns: Array<EntityColumn<BrokerConfigTable>> = [];
    columns.push(
      new EntityTableColumn<BrokerConfigTable>('key', 'config.key', '70%'),
      new EntityTableColumn<BrokerConfigTable>('value', 'config.value', '30%',
          entity => entity.value, () => ({color: 'rgba(0,0,0,0.54)'}))
    );
    return columns;
  }

  gotoDocs(page: string){
    window.open(`https://thingsboard.io/docs/mqtt-broker/${page}`, '_blank');
  }

  formatBytesToValue(entity: BrokerConfigTable): string {
    if (entity.key === ConfigParams.tlsMaxPayloadSize || entity.key === ConfigParams.tcpMaxPayloadSize ||
       entity.key === ConfigParams.wsMaxPayloadSize || entity.key === ConfigParams.wssMaxPayloadSize) {
      return formatBytes(entity.value);
    }
    if (typeof entity.value === 'boolean') {
      return entity.value ? this.translate.instant('common.enabled') : this.translate.instant('common.disabled');
    }
    return entity.value;
  }

  showCopyButton(entity: BrokerConfigTable, configParams: any): boolean {
    return entity.key === configParams.tlsPort || entity.key === configParams.tcpPort ||
           entity.key === configParams.wsPort || entity.key === configParams.wssPort;
  }

  showWarningIcon(entity: BrokerConfigTable, configParams: any): boolean {
    return !entity.value &&
           ((entity.key === configParams.x509AuthEnabled && this.config.existsX509Credentials) ||
            (entity.key === configParams.basicAuthEnabled && this.config.existsBasicCredentials));
  }
}
