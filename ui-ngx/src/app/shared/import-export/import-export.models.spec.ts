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

import { describe, expect, it } from 'vitest';
import {
  columnTypeForHeader,
  ImportEntityColumnType,
  importEntityColumnTypeTranslations
} from './import-export.models';

const allowed = [
  ImportEntityColumnType.name,
  ImportEntityColumnType.password,
  ImportEntityColumnType.passwordHash
];

describe('import-export models', () => {
  it('exposes PASSWORD_HASH as a column type with a translation', () => {
    expect(ImportEntityColumnType.passwordHash).toBe('PASSWORD_HASH');
    expect(importEntityColumnTypeTranslations.get(ImportEntityColumnType.passwordHash))
      .toBe('import.column-type.password-hash');
  });

  it('matches an upper-case header to its column type', () => {
    expect(columnTypeForHeader('NAME', allowed)).toBe(ImportEntityColumnType.name);
  });

  it('matches a lower-case header case-insensitively', () => {
    expect(columnTypeForHeader('password_hash', allowed)).toBe(ImportEntityColumnType.passwordHash);
  });

  it('does not confuse PASSWORD with PASSWORD_HASH', () => {
    expect(columnTypeForHeader('Password', allowed)).toBe(ImportEntityColumnType.password);
  });

  it('returns undefined for an unknown header', () => {
    expect(columnTypeForHeader('unknown_col', allowed)).toBeUndefined();
  });

  it('returns undefined for a column type that is not allowed', () => {
    expect(columnTypeForHeader('client_id', allowed)).toBeUndefined();
  });
});
