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
    img: '/assets/getting-started/troubleshooting.svg',
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
    img: '/assets/getting-started/troubleshooting.svg',
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

export const gettingStartedActions: GettingStartedLink[] = [
  {
    title: 'getting-started.guide.releases',
    url: 'https://github.com/thingsboard/tbmq/releases'
  },
  {
    title: 'getting-started.guide.support',
    url: HelpLinks.linksMap.help
  },
  {
    title: 'getting-started.guide.github',
    url: 'https://github.com/thingsboard/tbmq'
  },
  {
    title: 'home.rest-api',
    url: window.location.origin + '/swagger-ui.html'
  },
  {
    title: 'home.integration-with-thingsboard',
    url: HelpLinks.linksMap.connectToThingsBoard
  },
  {
    title: 'home.performance-tests',
    url: HelpLinks.linksMap.perfTest100m
  },
  {
    title: 'getting-started.guide.docs',
    url: HelpLinks.linksMap.gettingStarted
  },
  {
    title: 'getting-started.guide.pricing',
    url: HelpLinks.linksMap.pricing
  }
]
