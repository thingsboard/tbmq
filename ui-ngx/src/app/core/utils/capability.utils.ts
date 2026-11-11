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

import type { BrokerConfig, Capability, EditionCapabilities } from '../../shared/models/config.model';
import type { MenuSection } from '../services/menu.models';

export function isCapabilityEnabled(capabilities: Partial<EditionCapabilities> | null | undefined,
                                    capability?: Capability): boolean {
  if (!capability || !capabilities) {
    return true;
  }
  return capabilities[capability] !== false;
}

export function filterSectionsByCapability(sections: MenuSection[],
                                           capabilities: Partial<EditionCapabilities> | null | undefined): MenuSection[] {
  return sections
    .filter(section => isCapabilityEnabled(capabilities, section.capability))
    .flatMap(section => {
      if (!section.pages?.length) {
        return [section];
      }
      const pages = filterSectionsByCapability(section.pages, capabilities);
      return pages.length ? [{ ...section, pages }] : [];
    });
}

export function requiredCapabilities(routeData: Array<{ capability?: Capability } | undefined>): Capability[] {
  const result: Capability[] = [];
  for (const data of routeData) {
    if (data?.capability && !result.includes(data.capability)) {
      result.push(data.capability);
    }
  }
  return result;
}

export function configTableEntries(config: BrokerConfig): Array<[string, unknown]> {
  return Object.entries(config).filter(([key]) => key !== 'edition' && key !== 'capabilities');
}
