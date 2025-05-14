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

import { Inject, Injectable } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { TranslateService } from '@ngx-translate/core';

import { SettingsActions, SettingsActionTypes, } from './settings.actions';
import { distinctUntilChanged, map, tap, withLatestFrom } from 'rxjs';
import { select, Store } from '@ngrx/store';
import { selectSettingsState } from './settings.selectors';
import { updateUserLang } from '@core/settings/settings.utils';
import { DOCUMENT } from '@angular/common';
import { AppState } from '@core/core.state';
import { LocalStorageService } from '@core/local-storage/local-storage.service';

export const SETTINGS_KEY = 'SETTINGS';

@Injectable()
export class SettingsEffects {
  constructor(
      private actions$: Actions<SettingsActions>,
      private store: Store<AppState>,
      private localStorageService: LocalStorageService,
      private translate: TranslateService,
      @Inject(DOCUMENT) private document: Document,
  ) {
  }

  setTranslateServiceLanguage = createEffect(() => this.actions$.pipe(
    ofType(
      SettingsActionTypes.CHANGE_LANGUAGE,
    ),
    withLatestFrom(this.store.pipe(select(selectSettingsState))),
    map(settings => settings[1]),
    distinctUntilChanged((a, b) => a?.userLang === b?.userLang),
    tap(setting => {
      this.localStorageService.setItem(SETTINGS_KEY, setting);
      updateUserLang(this.translate, this.document, setting.userLang);
    })
  ), {dispatch: false});
}
