--
-- Copyright © 2016-2023 The Thingsboard Authors
--
-- Licensed under the Apache License, Version 2.0 (the "License");
-- you may not use this file except in compliance with the License.
-- You may obtain a copy of the License at
--
--     http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.
--

CREATE TABLE IF NOT EXISTS websocket_connection
(
    id            uuid         NOT NULL
        CONSTRAINT websocket_connection_pkey PRIMARY KEY,
    created_time  bigint       NOT NULL,
    name          varchar(255) NOT NULL,
    user_id       uuid,
    configuration jsonb,
    search_text   varchar(255),
    CONSTRAINT name_unq_key UNIQUE (name),
    CONSTRAINT fk_user_id
        FOREIGN KEY (user_id) REFERENCES broker_user (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS websocket_subscription
(
    id                      uuid   NOT NULL
        CONSTRAINT websocket_subscription_pkey PRIMARY KEY,
    created_time            bigint NOT NULL,
    websocket_connection_id uuid   NOT NULL,
    configuration           jsonb,
    CONSTRAINT fk_websocket_connection_id
        FOREIGN KEY (websocket_connection_id) REFERENCES websocket_connection (id) ON DELETE CASCADE
);
