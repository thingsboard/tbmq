///
/// Copyright © 2016-2026 The Thingsboard Authors
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

import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';
import { HttpClient } from '@angular/common/http';

@Injectable({
  providedIn: 'root'
})
export class InstructionsService {

  private STEP_TITLE_MAP: Record<string, string> = {
    'client-app': 'getting-started.step-client-app',
    'client-device': 'getting-started.step-client-dev',
    'subscribe': 'getting-started.step-subscribe',
    'publish': 'getting-started.step-publish',
    'session': 'getting-started.step-session',
    'enable-basic-auth': 'getting-started.step-enable-basic-auth',
  };

  private isDemoCloud = false;

  constructor(
    private http: HttpClient
  ) {
  }

  public getInstruction(id: string): Observable<string> {
    return this.http.get(`/assets/getting-started/${id}.md`, { responseType: 'text' });
  }

  public setInstructionsList(basicAuthEnabled: boolean): Observable<Array<any>> {
    const basicSteps = ['subscribe', 'publish', 'session'];
    const defaultSteps = ['client-app', 'client-device', ...basicSteps];
    const stepsList = this.isDemoCloud ? basicSteps : defaultSteps;
    if (!basicAuthEnabled) {
      stepsList.unshift('enable-basic-auth');
    }
    const steps = this.getStepsByIds(stepsList);
    // @ts-ignore
    steps.map((el, index) => el.position = index + 1);
    return of(steps);
  }

  private getStepsByIds(ids: string[]): { id: string; title: string }[] {
    return ids.map(id => ({
      id: id,
      title: this.STEP_TITLE_MAP[id]
    }));
  }

}
