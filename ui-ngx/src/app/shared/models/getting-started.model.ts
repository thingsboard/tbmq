///
/// Copyright © 2016-2025 The Thingsboard Authors
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
    url: 'https://thingsboard.io/docs/mqtt-broker/getting-started'
  },
  {
    img: '/assets/getting-started/architecture.svg',
    title: 'Architecture',
    url: 'https://thingsboard.io/docs/mqtt-broker/architecture'
  },
  {
    img: '/assets/getting-started/configuration.svg',
    title: 'Configuration',
    url: 'https://thingsboard.io/docs/mqtt-broker/install/config'
  },
  {
    img: '/assets/getting-started/security.svg',
    title: 'Security',
    url: 'https://thingsboard.io/docs/mqtt-broker/security'
  },
  {
    img: '/assets/getting-started/client_type.svg',
    title: 'Client Type',
    url: 'https://thingsboard.io/docs/mqtt-broker/user-guide/mqtt-client-type'
  },
  {
    img: '/assets/getting-started/monitoring.svg',
    title: 'Monitoring',
    url: 'https://thingsboard.io/docs/mqtt-broker/user-guide/ui/monitoring'
  },
  {
    img: '/assets/getting-started/websocket_client.svg',
    title: 'WebSocket Client',
    url: 'https://thingsboard.io/docs/mqtt-broker/user-guide/ui/websocket-client'
  },
  {
    img: '/assets/getting-started/unauthorized-clients.svg',
      title: 'Unauthorized Clients',
    url: 'https://thingsboard.io/docs/mqtt-broker/user-guide/ui/unauthorized-clients'
  },
  {
    img: '/assets/getting-started/troubleshooting.svg',
    title: 'Troubleshooting',
    url: 'https://thingsboard.io/docs/mqtt-broker/troubleshooting'
  },
]

export const gettingStartedFeatures: GettingStartedLink[] = [
  {
    img: '/assets/getting-started/mqtt-protocol.svg',
    title: 'MQTT Protocol',
    url: 'https://thingsboard.io/docs/mqtt-broker/user-guide/mqtt-protocol'
  },
  {
    img: '/assets/getting-started/topics.svg',
    title: 'Topics & Wildcards',
    url: 'https://thingsboard.io/docs/mqtt-broker/user-guide/topics'
  },
  {
    img: '/assets/getting-started/qos.svg',
    title: 'Quality of Service (QoS)',
    url: 'https://thingsboard.io/docs/mqtt-broker/user-guide/qos'
  },
  {
    img: '/assets/getting-started/keep-alive.svg',
    title: 'Keep Alive',
    url: 'https://thingsboard.io/docs/mqtt-broker/user-guide/keep-alive'
  },
  {
    img: '/assets/getting-started/last-will.svg',
    title: 'Last Will & Testament',
    url: 'https://thingsboard.io/docs/mqtt-broker/user-guide/last-will'
  },
  {
    img: '/assets/getting-started/retained-messages.svg',
    title: 'Retained Messages',
    url: 'https://thingsboard.io/docs/mqtt-broker/user-guide/retained-messages'
  },
  {
    img: '/assets/getting-started/shared-subscriptions.svg',
    title: 'Shared Subscriptions',
    url: 'https://thingsboard.io/docs/mqtt-broker/user-guide/shared-subscriptions'
  },
  {
    img: '/assets/getting-started/mqtt-over-ws.svg',
    title: 'MQTT over WebSocket',
    url: 'https://thingsboard.io/docs/mqtt-broker/user-guide/mqtt-over-ws/'
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
    url: 'https://thingsboard.io/docs/contact-us'
  },
  {
    title: 'getting-started.guide.github',
    url: 'https://github.com/thingsboard/tbmq'
  },
  {
    title: 'getting-started.guide.docs',
    url: 'https://thingsboard.io/docs/mqtt-broker/getting-started/'
  },
  /*{
    title: 'getting-started.guide.pricing',
    url: 'https://thingsboard.io/pricing/?section=mqtt-broker-options'
  }*/
]
