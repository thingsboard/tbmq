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

import { beforeAll, describe, expect, it, vi } from 'vitest';
import { filterByCapability } from '../../core/utils/capability.utils';

type Model = typeof import('./getting-started.model');

let model: Model;

beforeAll(async () => {
  // the model reads window.location at module load; Vitest runs in node
  vi.stubGlobal('window', { location: { origin: 'http://localhost' } });
  model = await import('./getting-started.model');
});

const ids = (steps: { id: string }[]) => steps.map(step => step.id);

describe('getGettingStartedSteps', () => {
  it('cluster edition keeps today\'s steps', () => {
    expect(model.getGettingStartedSteps(false, true)).toEqual(model.gettingStartedStepsConfig);
    const app = model.getGettingStartedSteps(false, true).find(step => step.id === model.GettingStartedStepId.CLIENT_APP);
    expect(app?.button?.action).toBe(model.GettingStartedButtonAction.ADD_APP_CREDENTIALS);
    expect(app?.textKey).toBe('getting-started.step.step-2.text');
  });

  it('lite edition has no APPLICATION credentials step', () => {
    const steps = model.getGettingStartedSteps(false, false);
    expect(ids(steps)).toEqual(ids(model.gettingStartedStepsConfig));
    expect(steps.some(step => step.button?.action === model.GettingStartedButtonAction.ADD_APP_CREDENTIALS)).toBe(false);
    const subscriber = steps.find(step => step.id === model.GettingStartedStepId.CLIENT_APP);
    expect(subscriber?.titleKey).toBe('getting-started.step.step-2-lite.title');
    expect(subscriber?.textKey).toBe('getting-started.step.step-2-lite.text');
    expect(subscriber?.button?.action).toBe(model.GettingStartedButtonAction.ADD_SUBSCRIBER_CREDENTIALS);
    expect(subscriber?.button?.labelKey).toBe('getting-started.step.step-2-lite.title');
  });

  it('lite edition steps reference no application keys', () => {
    const steps = model.getGettingStartedSteps(false, false);
    expect(JSON.stringify(steps)).not.toContain('step-2.');
    expect(steps.some(step => step.textKey === 'getting-started.step.step-4.text')).toBe(false);
    expect(JSON.stringify(steps)).not.toContain('getting-started.step.step-4.text');
    const subscribe = steps.find(step => step.titleKey === 'getting-started.step.step-4.title');
    expect(subscribe?.textKey).toBe('getting-started.step.step-4-lite.text');
  });

  it('basic auth enabled drops the BASIC_AUTH step in both editions', () => {
    for (const applicationClients of [true, false]) {
      const withBasicAuth = model.getGettingStartedSteps(true, applicationClients);
      const without = model.getGettingStartedSteps(false, applicationClients);
      expect(ids(withBasicAuth)).not.toContain(model.GettingStartedStepId.BASIC_AUTH);
      expect(ids(without)).toContain(model.GettingStartedStepId.BASIC_AUTH);
      expect(withBasicAuth.length).toBe(without.length - 1);
    }
  });
});

describe('gettingStartedStepCommands', () => {
  const subscribe = (username: string) =>
    model.gettingStartedStepCommands[model.GettingStartedStepId.SUBSCRIBE]('cid', 'host', '1883', username);

  it('subscribe command uses the given username as the password too', () => {
    expect(subscribe('tbmq_sub')).toContain('-u tbmq_sub -P tbmq_sub');
    expect(subscribe('tbmq_sub')).not.toContain('tbmq_app');
    expect(subscribe('tbmq_app')).toContain('-u tbmq_app -P tbmq_app');
  });
});

describe('gettingStartedDocs', () => {
  const titles = (links: { title: string }[]) => links.map(link => link.title);

  it('lite edition drops the Integrations and Client Type cards', () => {
    const lite = filterByCapability(model.gettingStartedDocs, { applicationClients: false, integrations: false });
    expect(titles(lite)).not.toContain('Integrations');
    expect(titles(lite)).not.toContain('Client Type');
    expect(lite.length).toBe(model.gettingStartedDocs.length - 2);
  });

  it('cluster edition and unloaded config keep every card', () => {
    expect(filterByCapability(model.gettingStartedDocs, { applicationClients: true, integrations: true })).toEqual(model.gettingStartedDocs);
    expect(filterByCapability(model.gettingStartedDocs, undefined)).toEqual(model.gettingStartedDocs);
  });
});
