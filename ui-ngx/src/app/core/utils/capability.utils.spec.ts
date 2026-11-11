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
import type { MenuSection } from '../services/menu.models';
import {
  configTableEntries,
  filterSectionsByCapability,
  isCapabilityEnabled,
  requiredCapabilities
} from './capability.utils';

const section = (id: string, extra: Partial<MenuSection> = {}): MenuSection =>
  ({ id, name: id, type: 'link', path: `/${id}`, icon: 'x', ...extra });

const home = section('home');
const kafkaToggle = section('kafka_management', { pages: [section('kafka_topics')] });
const manage = section('shared_subscriptions');
const apps = section('shared_subscriptions_application');
const sharedToggle = section('shared_subscriptions_management', { pages: [] });

describe('capability.utils', () => {
  it('allows when nothing is required or config is not loaded', () => {
    expect(isCapabilityEnabled({ kafka: false }, undefined)).toBe(true);
    expect(isCapabilityEnabled(undefined, 'kafka')).toBe(true);
    expect(isCapabilityEnabled(null, 'kafka')).toBe(true);
  });

  it('denies a disabled capability', () => {
    expect(isCapabilityEnabled({ kafka: false }, 'kafka')).toBe(false);
  });

  it('allows an enabled or unlisted capability', () => {
    expect(isCapabilityEnabled({ kafka: true }, 'kafka')).toBe(true);
    expect(isCapabilityEnabled({ kafka: true }, 'integrations')).toBe(true);
  });

  it('drops disabled sections and pages, keeps the rest', () => {
    const out = filterSectionsByCapability([
      home,
      { ...kafkaToggle, capability: 'kafka' },
      { ...sharedToggle, id: 'shared_subscriptions', pages: [manage, { ...apps, capability: 'applicationClients' }] }
    ], { kafka: false, applicationClients: false });
    expect(out.map(s => s.id)).toEqual(['home', 'shared_subscriptions']);
    expect(out[1].pages.map(p => p.id)).toEqual(['shared_subscriptions']);
  });

  it('drops a toggle left with no pages', () => {
    const out = filterSectionsByCapability([
      home,
      { ...sharedToggle, pages: [{ ...apps, capability: 'applicationClients' }] }
    ], { applicationClients: false });
    expect(out.map(s => s.id)).toEqual(['home']);
  });

  it('keeps everything when capabilities are missing', () => {
    const sections = [home, { ...kafkaToggle, capability: 'kafka' as const }];
    expect(filterSectionsByCapability(sections, undefined)).toEqual(sections);
  });

  it('collects capabilities from every route level', () => {
    expect(requiredCapabilities([undefined, { capability: 'kafka' }, {}])).toEqual(['kafka']);
  });

  it('keeps edition and capabilities out of the config table', () => {
    expect(configTableEntries({ tcpPort: 1883, edition: 'cluster', capabilities: { kafka: true } } as any)
      .map(([k]) => k)).toEqual(['tcpPort']);
  });
});
