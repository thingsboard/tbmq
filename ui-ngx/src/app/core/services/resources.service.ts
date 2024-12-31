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

import { ComponentFactory, Inject, Injectable, Type } from '@angular/core';
import { DOCUMENT } from '@angular/common';
import { Observable, ReplaySubject } from 'rxjs';
import { HttpClient } from '@angular/common/http';
import { select, Store } from '@ngrx/store';
import { selectIsAuthenticated } from '@core/auth/auth.selectors';
import { AppState } from '@core/core.state';

export interface ModulesWithFactories {
    modules: Type<any>[];
    factories: ComponentFactory<any>[];
}

@Injectable({
    providedIn: 'root'
})
export class ResourcesService {

    private loadedJsonResources: { [url: string]: ReplaySubject<any> } = {};
    private loadedResources: { [url: string]: ReplaySubject<void> } = {};
    private loadedModules: { [url: string]: ReplaySubject<Type<any>[]> } = {};
    private loadedModulesAndFactories: { [url: string]: ReplaySubject<ModulesWithFactories> } = {};

    private anchor = this.document.getElementsByTagName('head')[0] || this.document.getElementsByTagName('body')[0];

    constructor(@Inject(DOCUMENT) private readonly document: any,
                protected store: Store<AppState>,
                private http: HttpClient) {
        this.store.pipe(select(selectIsAuthenticated)).subscribe(() => this.clearModulesCache());
    }

    public loadJsonResource<T>(url: string, postProcess?: (data: T) => T): Observable<T> {
        if (this.loadedJsonResources[url]) {
            return this.loadedJsonResources[url].asObservable();
        }
        const subject = new ReplaySubject<any>();
        this.loadedJsonResources[url] = subject;
        this.http.get<T>(url).subscribe(
            {
                next: (o) => {
                    if (postProcess) {
                        o = postProcess(o);
                    }
                    this.loadedJsonResources[url].next(o);
                    this.loadedJsonResources[url].complete();
                },
                error: () => {
                    this.loadedJsonResources[url].error(new Error(`Unable to load ${url}`));
                    delete this.loadedJsonResources[url];
                }
            }
        );
        return subject.asObservable();
    }

    private clearModulesCache() {
        this.loadedModules = {};
        this.loadedModulesAndFactories = {};
    }
}
