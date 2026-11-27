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

import { InterceptorHttpParams } from '../interceptors/interceptor-http-params';
import { HttpHeaders } from '@angular/common/http';
import { InterceptorConfig } from '../interceptors/interceptor-config';

export type QueryParams = { [param: string]: string | string[] };

export interface RequestConfig {
  ignoreLoading?: boolean;
  ignoreErrors?: boolean;
  resendRequest?: boolean;
  queryParams?: QueryParams;
}

export function cleanQueryParams(params: QueryParams): { [param: string]: string | string[] } {
  const cleaned: { [param: string]: string | string[] } = {};
  for (const key of Object.keys(params)) {
    const value = params[key];
    if (value !== null && value !== undefined) {
      if (Array.isArray(value) && value.length > 0) {
        cleaned[key] = value;
      } else if (!Array.isArray(value) && value !== '') {
        cleaned[key] = value;
      }
    }
  }
  return cleaned;
}

export function createDefaultHttpOptions(config?: RequestConfig) {
  if (!config) {
    config = {};
  }
  const queryParams = config.queryParams ? cleanQueryParams(config.queryParams) : undefined;
  return defaultHttpOptions(config.ignoreLoading, config.ignoreErrors, config.resendRequest, queryParams);
}

export function defaultHttpOptionsFromConfig(config?: RequestConfig) {
  if (!config) {
    config = {};
  }
  return defaultHttpOptions(config.ignoreLoading, config.ignoreErrors, config.resendRequest);
}

export function defaultHttpOptions(ignoreLoading: boolean = false,
                                   ignoreErrors: boolean = false,
                                   resendRequest: boolean = false,
                                   queryParams?: { [param: string]: string | string[] }) {
  return {
    headers: new HttpHeaders({'Content-Type': 'application/json'}),
    params: new InterceptorHttpParams(new InterceptorConfig(ignoreLoading, ignoreErrors, resendRequest), queryParams)
  };
}

export function defaultHttpUploadOptions(ignoreLoading: boolean = false,
                                         ignoreErrors: boolean = false,
                                         resendRequest: boolean = false) {
  return {
    params: new InterceptorHttpParams(new InterceptorConfig(ignoreLoading, ignoreErrors, resendRequest))
  };
}
