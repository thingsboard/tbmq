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

import type { EditionCapabilities, TbmqEdition } from '../../shared/models/config.model';
import { ClientType } from '../../shared/models/client.model';
import { isCapabilityEnabled } from './capability.utils';

export function allowedClientTypes(capabilities: Partial<EditionCapabilities> | null | undefined): ClientType[] {
  return isCapabilityEnabled(capabilities, 'applicationClients')
    ? [ClientType.DEVICE, ClientType.APPLICATION]
    : [ClientType.DEVICE];
}

export function isNodeIdVisible(capabilities: Partial<EditionCapabilities> | null | undefined): boolean {
  return isCapabilityEnabled(capabilities, 'cluster');
}

// appTitle is env.appTitle, passed in because the environment file needs build-time globals that Vitest does not define.
export function editionProductName(appTitle: string, edition: TbmqEdition | undefined): string {
  return edition === 'lite' ? `${appTitle} Lite` : appTitle;
}

interface ParsedVersion {
  numbers: [number, number, number];
  preRelease: string | null;
}

// MAJOR[.MINOR[.PATCH]][EDITION][-PRE], e.g. 2.4.1PE, 2.5.0-beta, 2.5.0-SNAPSHOT
const VERSION_PATTERN = /^(\d+)(?:\.(\d+))?(?:\.(\d+))?[A-Za-z]*(?:-(.+))?$/;

function parseVersion(version: string | null | undefined): ParsedVersion | null {
  const match = version ? VERSION_PATTERN.exec(version.trim()) : null;
  if (!match) {
    return null;
  }
  return {
    numbers: [Number(match[1]), Number(match[2] ?? 0), Number(match[3] ?? 0)],
    preRelease: match[4] ?? null
  };
}

export function stripSnapshotSuffix(version: string): string {
  return version.replace(/-SNAPSHOT$/i, '');
}

export function isNewerVersion(newest: string | null | undefined, current: string | null | undefined): boolean {
  const newestVersion = parseVersion(newest);
  const currentVersion = parseVersion(current);
  if (!newestVersion || !currentVersion) {
    return false;
  }
  for (let i = 0; i < 3; i++) {
    if (newestVersion.numbers[i] !== currentVersion.numbers[i]) {
      return newestVersion.numbers[i] > currentVersion.numbers[i];
    }
  }
  if (newestVersion.preRelease === currentVersion.preRelease) {
    return false;
  }
  if (newestVersion.preRelease === null) {
    return true;
  }
  if (currentVersion.preRelease === null) {
    return false;
  }
  return newestVersion.preRelease > currentVersion.preRelease;
}

// TBMQ Lite never keeps the default password: the server forces its change at the first sign-in, for the seeded sysadmin
// and for an admin added with it, and TBMQ_ADMIN_PASSWORD may not be the default. A sysadmin set from TBMQ_ADMIN_PASSWORD
// has a single password, which the cluster edition's check (one password = the default one) would flag.
export function isDefaultPasswordNudgeShown(edition: TbmqEdition | undefined, isPasswordChanged: boolean | undefined): boolean {
  return edition !== 'lite' && isPasswordChanged === false;
}

export function filterCredentialsHomeCards<T extends { key: string; filter: { clientTypeList: ClientType[] } }>(
  cards: T[], capabilities: Partial<EditionCapabilities> | null | undefined): T[] {
  if (isCapabilityEnabled(capabilities, 'applicationClients')) {
    return cards;
  }
  const allowed = allowedClientTypes(capabilities);
  return cards
    .filter(card => card.key !== 'applicationCredentialsCount')
    .map(card => ({
      ...card,
      filter: { ...card.filter, clientTypeList: card.filter.clientTypeList.filter(type => allowed.includes(type)) }
    }));
}
