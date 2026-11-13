--
-- Copyright © 2016-2026 The Thingsboard Authors
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

-- UPGRADE FROM VERSION 2.4.0 TO 2.4.1 START

CREATE TABLE IF NOT EXISTS authorization_policy (
    id uuid NOT NULL CONSTRAINT authorization_policy_pkey PRIMARY KEY,
    created_time bigint NOT NULL,
    name varchar(255) NOT NULL,
    authorization_rules jsonb NOT NULL,
    additional_info varchar,
    CONSTRAINT authorization_policy_name_unq_key UNIQUE (name)
);

ALTER TABLE mqtt_client_credentials
    ADD COLUMN IF NOT EXISTS authorization_policy_id uuid;

ALTER TABLE mqtt_client_credentials
    DROP CONSTRAINT IF EXISTS fk_mqtt_client_credentials_authorization_policy;

ALTER TABLE mqtt_client_credentials
    ADD CONSTRAINT fk_mqtt_client_credentials_authorization_policy
        FOREIGN KEY (authorization_policy_id) REFERENCES authorization_policy(id);

CREATE INDEX IF NOT EXISTS idx_mqtt_client_credentials_authorization_policy_id
    ON mqtt_client_credentials (authorization_policy_id);

-- UPGRADE FROM VERSION 2.4.0 TO 2.4.1 END
