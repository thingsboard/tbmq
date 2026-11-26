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
import { ClientType } from '../../shared/models/client.model';
import {
  allowedClientTypes,
  editionProductName,
  filterCredentialsHomeCards,
  isDefaultPasswordNudgeShown,
  isNewerVersion,
  isNodeIdVisible,
  stripSnapshotSuffix
} from './edition.utils';

const card = (key: string, clientTypeList: ClientType[]) =>
  ({ key, label: key, path: '/authentication/client-credentials', value: 0, filter: { clientTypeList } });

const cards = () => [
  card('deviceCredentialsCount', [ClientType.DEVICE]),
  card('applicationCredentialsCount', [ClientType.APPLICATION]),
  card('totalCount', [ClientType.DEVICE, ClientType.APPLICATION])
];

describe('edition.utils', () => {
  it('allowedClientTypes', () => {
    expect(allowedClientTypes({ applicationClients: false })).toEqual([ClientType.DEVICE]);
    expect(allowedClientTypes({ applicationClients: true })).toEqual([ClientType.DEVICE, ClientType.APPLICATION]);
    expect(allowedClientTypes(undefined)).toEqual([ClientType.DEVICE, ClientType.APPLICATION]);
  });

  it('isNodeIdVisible', () => {
    expect(isNodeIdVisible({ cluster: false })).toBe(false);
    expect(isNodeIdVisible({ cluster: true })).toBe(true);
    expect(isNodeIdVisible(undefined)).toBe(true);
  });

  it('editionProductName', () => {
    expect(editionProductName('TBMQ', 'lite')).toBe('TBMQ Lite');
    expect(editionProductName('TBMQ', 'cluster')).toBe('TBMQ');
    expect(editionProductName('TBMQ', undefined)).toBe('TBMQ');
    expect(editionProductName('Custom', 'lite')).toBe('Custom Lite');
  });

  describe('isNewerVersion', () => {
    it.each([
      ['2.5.0', '2.5.0-beta', true],
      ['2.4.3', '2.5.0-beta', false],
      ['2.10.0', '2.9.0', true],
      ['2.5.0', '2.5.0-SNAPSHOT', true],
      ['2.5', '2.5.0', false],
      [undefined, '2.5.0', false],
      [null, '2.5.0', false],
      ['2.5.0', '2.5.0', false],
      ['2.4.2PE', '2.4.1PE', true],
      ['2.4.1PE', '2.4.1PE', false],
      ['2.5.0-beta', '2.5.0', false],
      ['2.5.0-beta2', '2.5.0-beta', true],
      ['2.5.1', '2.5', true],
      ['2.5.0', undefined, false],
      ['garbage', '2.5.0', false]
    ] as Array<[string | null | undefined, string | null | undefined, boolean]>)(
      'isNewerVersion(%s, %s) = %s', (newest, current, expected) => {
        expect(isNewerVersion(newest, current)).toBe(expected);
      });
  });

  describe('stripSnapshotSuffix', () => {
    it.each([
      ['2.5.0-SNAPSHOT', '2.5.0'],
      ['2.5.0-snapshot', '2.5.0'],
      ['2.5.0-beta', '2.5.0-beta'],
      ['2.4.1PE', '2.4.1PE'],
      ['2.4.1PE-SNAPSHOT', '2.4.1PE']
    ])('stripSnapshotSuffix(%s) = %s', (input, expected) => {
      expect(stripSnapshotSuffix(input)).toBe(expected);
    });
  });

  describe('filterCredentialsHomeCards', () => {
    it('drops the application card and narrows client types in Lite', () => {
      const input = cards();
      const result = filterCredentialsHomeCards(input, { applicationClients: false });
      expect(result.map(c => c.key)).toEqual(['deviceCredentialsCount', 'totalCount']);
      expect(result[1].filter.clientTypeList).toEqual([ClientType.DEVICE]);
      expect(input).toEqual(cards());
    });

    it('keeps cards unchanged for cluster and unloaded config', () => {
      expect(filterCredentialsHomeCards(cards(), { applicationClients: true })).toEqual(cards());
      expect(filterCredentialsHomeCards(cards(), undefined)).toEqual(cards());
    });
  });

  describe('isDefaultPasswordNudgeShown', () => {
    it('shows the nudge in the cluster edition only while the password is unchanged', () => {
      expect(isDefaultPasswordNudgeShown('cluster', false)).toBe(true);
      expect(isDefaultPasswordNudgeShown('cluster', true)).toBe(false);
      expect(isDefaultPasswordNudgeShown('cluster', undefined)).toBe(false);
      expect(isDefaultPasswordNudgeShown(undefined, false)).toBe(true);
    });

    it('never shows it in TBMQ Lite', () => {
      expect(isDefaultPasswordNudgeShown('lite', false)).toBe(false);
    });
  });
});
