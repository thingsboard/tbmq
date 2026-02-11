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

import { HelpLinks } from '@shared/models/constants';

export const gettingStartedGuideTitle = (id: string) => gettingStartedGuides.find(el => el.id === id)?.title;

export const gettingStartedGuides: GettingStartedLink[] = [
  {
    id: 'guide-python',
    img: '/assets/getting-started/python.png',
    title: 'getting-started.guide.python-title',
    subtitle: 'getting-started.guide.python-subtitle',
    url: '/getting-started/guide-python'
  },
  {
    id: 'guide-javascript',
    img: '/assets/getting-started/js.png',
    title: 'getting-started.guide.javascript-title',
    subtitle: 'getting-started.guide.javascript-subtitle',
    url: '/getting-started/guide-javascript'
  },
  {
    id: 'guide-arduino',
    img: '/assets/getting-started/arduino.png',
    title: 'getting-started.guide.arduino-title',
    subtitle: 'getting-started.guide.arduino-subtitle',
    url: '/getting-started/guide-arduino'
  },
  {
    id: 'guide-mosquitto',
    img: '/assets/getting-started/mosquitto.svg',
    title: 'getting-started.guide.mosquitto-title',
    subtitle: 'getting-started.guide.mosquitto-subtitle',
    url: '/getting-started/guide-mosquitto'
  },
  {
    id: 'guide-java',
    img: '/assets/getting-started/java.webp',
    title: 'getting-started.guide.java-title',
    subtitle: 'getting-started.guide.java-subtitle',
    url: '/getting-started/guide-java'
  },
  {
    id: 'guide-java-ws',
    img: '/assets/getting-started/java.webp',
    title: 'getting-started.guide.java-ws-title',
    subtitle: 'getting-started.guide.java-ws-subtitle',
    url: '/getting-started/guide-java-ws'
  },
]

export const gettingStartedDocs: GettingStartedLink[] = [
  {
    img: '/assets/getting-started/getting_started.svg',
    title: 'Getting Started',
    url: HelpLinks.linksMap.gettingStarted
  },
  {
    img: '/assets/getting-started/architecture.svg',
    title: 'Architecture',
    url: HelpLinks.linksMap.architecture
  },
  {
    img: '/assets/getting-started/configuration.svg',
    title: 'Configuration',
    url: HelpLinks.linksMap.configuration
  },
  {
    img: '/assets/getting-started/security.svg',
    title: 'Security',
    url: HelpLinks.linksMap.securitySettings
  },
  {
    img: '/assets/getting-started/integrations.svg',
    title: 'Integrations',
    url: HelpLinks.linksMap.integrations
  },
  {
    img: '/assets/getting-started/client_type.svg',
    title: 'Client Type',
    url: HelpLinks.linksMap.clientType
  },
  {
    img: '/assets/getting-started/monitoring.svg',
    title: 'Monitoring',
    url: HelpLinks.linksMap.monitoring
  },
  {
    img: '/assets/getting-started/websocket_client.svg',
    title: 'WebSocket Client',
    url: HelpLinks.linksMap.connection
  },
  {
    img: '/assets/getting-started/unauthorized-clients.svg',
    title: 'Unauthorized Clients',
    url: HelpLinks.linksMap.unauthorizedClient
  },
  {
    img: '/assets/getting-started/blocked-clients.svg',
    title: 'Blocked Clients',
    url: HelpLinks.linksMap.blockedClient
  },
  {
    img: '/assets/getting-started/troubleshooting.svg',
    title: 'Troubleshooting',
    url: HelpLinks.linksMap.troubleshooting
  },
]

export const gettingStartedFeatures: GettingStartedLink[] = [
  {
    img: '/assets/getting-started/mqtt-protocol.svg',
    title: 'MQTT Protocol',
    url: HelpLinks.linksMap.mqttProtocol
  },
  {
    img: '/assets/getting-started/topics.svg',
    title: 'Topics & Wildcards',
    url: HelpLinks.linksMap.topics
  },
  {
    img: '/assets/getting-started/qos.svg',
    title: 'Quality of Service (QoS)',
    url: HelpLinks.linksMap.qos
  },
  {
    img: '/assets/getting-started/clean-persistent-sessions.svg',
    title: 'Clean & Persistent Sessions',
    url: HelpLinks.linksMap.cleanPersistentSessions
  },
  {
    img: '/assets/getting-started/keep-alive.svg',
    title: 'Keep Alive',
    url: HelpLinks.linksMap.keepAlive
  },
  {
    img: '/assets/getting-started/last-will.svg',
    title: 'Last Will & Testament',
    url: HelpLinks.linksMap.lastWill
  },
  {
    img: '/assets/getting-started/retained-messages.svg',
    title: 'Retained Messages',
    url: HelpLinks.linksMap.retainedMessages
  },
  {
    img: '/assets/getting-started/shared-subscriptions.svg',
    title: 'Shared Subscriptions',
    url: HelpLinks.linksMap.sharedSubscriptions
  },
  {
    img: '/assets/getting-started/mqtt-over-ws.svg',
    title: 'MQTT over WebSocket',
    url: HelpLinks.linksMap.mqttOverWs
  },
]

export interface GettingStartedLink {
  title: string;
  url: string;
  id?: string;
  img?: string;
  subtitle?: string;
}

export const gettingStartedActionsDeveloper: GettingStartedLink[] = [
  {
    title: 'getting-started.guide.docs',
    url: HelpLinks.linksMap.gettingStarted
  },
  {
    title: 'home.rest-api',
    url: window.location.origin + '/swagger-ui.html'
  },
  {
    title: 'getting-started.guide.github',
    url: 'https://github.com/thingsboard/tbmq'
  },
  {
    title: 'getting-started.guide.releases',
    url: HelpLinks.linksMap.releases
  },
]

export const gettingStartedActionsProduct: GettingStartedLink[] = [
  {
    title: 'home.performance-tests',
    url: HelpLinks.linksMap.perfTest100m
  },
  {
    title: 'home.integration-with-thingsboard',
    url: HelpLinks.linksMap.connectToThingsBoard
  },
  {
    title: 'getting-started.guide.pricing',
    url: HelpLinks.linksMap.pricing
  },
  {
    title: 'getting-started.guide.support',
    url: HelpLinks.linksMap.help
  }
]

export enum GettingStartedStepId {
  BASIC_AUTH = 'BASIC_AUTH',
  CLIENT_APP = 'CLIENT_APP',
  CLIENT_DEVICE = 'CLIENT_DEVICE',
  SUBSCRIBE = 'SUBSCRIBE',
  PUBLISH = 'PUBLISH',
  SESSION = 'SESSION',
}

export enum GettingStartedButtonAction {
  ADD_APP_CREDENTIALS = 'add-app-credentials',
  ADD_DEVICE_CREDENTIALS = 'add-device-credentials',
  OPEN_SESSIONS = 'open-sessions'
}

export interface GettingStartedStepButton {
  action: GettingStartedButtonAction;
  icon: string;
  labelKey: string;
}

export interface GettingStartedStepCommand {
  template: string;
  afterTextKey?: string;
}

export interface GettingStartedStep {
  id: GettingStartedStepId;
  titleKey: string;
  textKey: string;
  button?: GettingStartedStepButton;
  command?: GettingStartedStepCommand;
}

export const gettingStartedStepCommands = {
  [GettingStartedStepId.SUBSCRIBE]: (randomClientId: string, mqttHost: string, mqttPort: string) => `
    \`\`\`bash
    mosquitto_sub -d -q 1 -h ${mqttHost} -p ${mqttPort} -t tbmq/demo/+ -i ${randomClientId} -u tbmq_app -P tbmq_app -c -v{:copy-code}
    \`\`\`
  `,
  [GettingStartedStepId.PUBLISH]: (mqttHost: string, mqttPort: string) => `
    \`\`\`bash
    mosquitto_pub -d -q 1 -h ${mqttHost} -p ${mqttPort} -t tbmq/demo/topic -u tbmq_dev -P tbmq_dev -m "Hello World"{:copy-code}
    \`\`\`
  `
};

export const gettingStartedStepsConfig: GettingStartedStep[] = [
  {
    id: GettingStartedStepId.BASIC_AUTH,
    titleKey: 'getting-started.step.step-1.title',
    textKey: 'getting-started.step.step-1.text'
  },
  {
    id: GettingStartedStepId.CLIENT_APP,
    titleKey: 'getting-started.step.step-2.title',
    textKey: 'getting-started.step.step-2.text',
    button: {
      action: GettingStartedButtonAction.ADD_APP_CREDENTIALS,
      icon: 'desktop_mac',
      labelKey: 'getting-started.step.step-2.title'
    }
  },
  {
    id: GettingStartedStepId.CLIENT_DEVICE,
    titleKey: 'getting-started.step.step-3.title',
    textKey: 'getting-started.step.step-3.text',
    button: {
      action: GettingStartedButtonAction.ADD_DEVICE_CREDENTIALS,
      icon: 'devices_other',
      labelKey: 'getting-started.step.step-3.title'
    }
  },
  {
    id: GettingStartedStepId.SUBSCRIBE,
    titleKey: 'getting-started.step.step-4.title',
    textKey: 'getting-started.step.step-4.text',
    command: {
      template: GettingStartedStepId.SUBSCRIBE
    }
  },
  {
    id: GettingStartedStepId.PUBLISH,
    titleKey: 'getting-started.step.step-5.title',
    textKey: 'getting-started.step.step-5.text',
    command: {
      template: GettingStartedStepId.PUBLISH,
      afterTextKey: 'getting-started.step.step-5.after-command'
    }
  },
  {
    id: GettingStartedStepId.SESSION,
    titleKey: 'getting-started.step.step-6.title',
    textKey: 'getting-started.step.step-6.text',
    button: {
      action: GettingStartedButtonAction.OPEN_SESSIONS,
      icon: 'mdi:book-multiple',
      labelKey: 'getting-started.step.step-6.title'
    }
  }
];

export function getGettingStartedSteps(basicAuthEnabled: boolean): GettingStartedStep[] {
  if (basicAuthEnabled) {
    return gettingStartedStepsConfig.filter(step => step.id !== GettingStartedStepId.BASIC_AUTH);
  }
  return gettingStartedStepsConfig;
}

