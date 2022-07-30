///
/// Copyright © 2016-2022 The Thingsboard Authors
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

// tslint:disable-next-line:no-reference
/// <reference path="../../../../src/typings/rawloader.typings.d.ts" />

import { Inject, Injectable, NgZone } from '@angular/core';
import { WINDOW } from '@core/services/window.service';
import {
  baseUrl,
  deepClone,
  guid,
  isDefined,
  isUndefined
} from '@core/utils';
import { TranslateService } from '@ngx-translate/core';
import materialIconsCodepoints from '!raw-loader!material-design-icons/iconfont/codepoints';
import { Observable, of, ReplaySubject } from 'rxjs';

const commonMaterialIcons: Array<string> = ['more_horiz', 'more_vert', 'open_in_new',
  'visibility', 'play_arrow', 'arrow_back', 'arrow_downward',
  'arrow_forward', 'arrow_upwards', 'close', 'refresh', 'menu', 'show_chart', 'multiline_chart', 'pie_chart', 'insert_chart', 'people',
  'person', 'domain', 'devices_other', 'now_widgets', 'dashboards', 'map', 'pin_drop', 'my_location', 'extension', 'search',
  'settings', 'notifications', 'notifications_active', 'info', 'info_outline', 'warning', 'list', 'file_download', 'import_export',
  'share', 'add', 'edit', 'done'];

// @dynamic
@Injectable({
  providedIn: 'root'
})
export class UtilsService {

  materialIcons: Array<string> = [];

  constructor(@Inject(WINDOW) private window: Window,
              private zone: NgZone,
              private translate: TranslateService) {
    let frame: Element = null;
    try {
      frame = window.frameElement;
    } catch (e) {
      // ie11 fix
    }
  }

  public guid(): string {
    return guid();
  }

  public getMaterialIcons(): Observable<Array<string>> {
    if (this.materialIcons.length) {
      return of(this.materialIcons);
    } else {
      const materialIconsSubject = new ReplaySubject<Array<string>>();
      this.zone.runOutsideAngular(() => {
        const codepointsArray = materialIconsCodepoints
          .split('\n')
          .filter((codepoint) => codepoint && codepoint.length);
        codepointsArray.forEach((codepoint) => {
          const values = codepoint.split(' ');
          if (values && values.length === 2) {
            this.materialIcons.push(values[0]);
          }
        });
        materialIconsSubject.next(this.materialIcons);
      });
      return materialIconsSubject.asObservable();
    }
  }

  public getCommonMaterialIcons(): Array<string> {
    return commonMaterialIcons;
  }

  public getQueryParam(name: string): string {
    const url = this.window.location.href;
    name = name.replace(/[\[\]]/g, '\\$&');
    const regex = new RegExp('[?&]' + name + '(=([^&#]*)|&|#|$)');
    const results = regex.exec(url);
    if (!results) {
      return null;
    }
    if (!results[2]) {
      return '';
    }
    return decodeURIComponent(results[2].replace(/\+/g, ' '));
  }

  public removeQueryParams(keys: Array<string>) {
    let params = this.window.location.search;
    for (const key of keys) {
      params = this.updateUrlQueryString(params, key, null);
    }
    const baseUrlPart = [baseUrl(), this.window.location.pathname].join('');
    this.window.history.replaceState({}, '', baseUrlPart + params);
  }

  public updateQueryParam(name: string, value: string | null) {
    const baseUrlPart = [baseUrl(), this.window.location.pathname].join('');
    const urlQueryString = this.window.location.search;
    const params = this.updateUrlQueryString(urlQueryString, name, value);
    this.window.history.replaceState({}, '', baseUrlPart + params);
  }

  private updateUrlQueryString(urlQueryString: string, name: string, value: string | null): string {
    let newParam = '';
    let params = '';
    if (value !== null) {
      newParam = name + '=' + value;
    }
    if (urlQueryString) {
      const keyRegex = new RegExp('([\?&])' + name + '[^&]*');
      if (urlQueryString.match(keyRegex) !== null) {
        if (newParam) {
          newParam = '$1' + newParam;
        }
        params = urlQueryString.replace(keyRegex, newParam);
        if (params.startsWith('&')) {
          params = '?' + params.substring(1);
        }
      } else if (newParam) {
        params = urlQueryString + '&' + newParam;
      }
    } else if (newParam) {
      params = '?' + newParam;
    }
    return params;
  }

  public deepClone<T>(target: T, ignoreFields?: string[]): T {
    return deepClone(target, ignoreFields);
  }

  public isUndefined(value: any): boolean {
    return isUndefined(value);
  }

  public isDefined(value: any): boolean {
    return isDefined(value);
  }
}
