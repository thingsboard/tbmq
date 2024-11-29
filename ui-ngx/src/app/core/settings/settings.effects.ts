///
/// Copyright © 2016-2024 The Thingsboard Authors
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

import { Router } from '@angular/router';
import { Injectable } from '@angular/core';
import { Actions } from '@ngrx/effects';
import { TranslateService } from '@ngx-translate/core';

import { SettingsActions, } from './settings.actions';
import { TitleService } from '@app/core/services/title.service';

@Injectable()
export class SettingsEffects {
  constructor(
      private actions$: Actions<SettingsActions>,
      // private faviconService: FaviconService,
      private router: Router,
      private titleService: TitleService,
      private translate: TranslateService
  ) {
  }

  /*setTitle = createEffect(() => merge(
    this.actions$.pipe(ofType(SettingsActionTypes.CHANGE_LANGUAGE, SettingsActionTypes.CHANGE_WHITE_LABELING)),
    this.router.events.pipe(filter(event => event instanceof ActivationEnd))
  ).pipe(
    tap(() => {
      this.titleService.setTitle(
        this.router.routerState.snapshot.root,
        this.translate
      );
    })
  ), {dispatch: false});

  setFavicon = createEffect(() => merge(
    this.actions$.pipe(ofType(SettingsActionTypes.CHANGE_WHITE_LABELING)),
  ).pipe(
    tap(() => {
      this.faviconService.setFavicon();
    })
  ), {dispatch: false});*/
}
