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
import { NativeDatetimeAdapter } from '@mat-datetimepicker/core';

@Injectable()
export class CustomDateAdapter extends NativeDatetimeAdapter {

  format(date: Date, displayFormat: any): string {
    if (!this.isValid(date)) {
      return '';
    }
    const dtf = new Intl.DateTimeFormat(this.locale, {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      hour12: false
    });
    return dtf.format(date).replace(',', '');
  }

  parse(value: string | number): Date {
    if (typeof value === 'number') {
      return new Date(value);
    }
    if (!value) {
      return null;
    }
    const match = value.trim().match(/^(\d{2})[/\-.](\d{2})[/\-.](\d{4})[ ,]+(\d{2}):(\d{2})$/);
    if (match) {
      const [, dd, mm, yyyy, hh, mi] = match;
      return new Date(Number(yyyy), Number(mm) - 1, Number(dd), Number(hh), Number(mi));
    }
    const parsed = Date.parse(value);
    return isNaN(parsed) ? null : new Date(parsed);
  }
}
