--
-- Copyright © 2016-2025 The Thingsboard Authors
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

-- UPGRADE FROM VERSION 2.0.0 TO 2.0.1 START

ALTER TABLE broker_user DROP COLUMN IF EXISTS search_text;
ALTER TABLE mqtt_client_credentials DROP COLUMN IF EXISTS search_text;
ALTER TABLE application_shared_subscription DROP COLUMN IF EXISTS search_text;
ALTER TABLE websocket_connection DROP COLUMN IF EXISTS search_text;

-- UPGRADE FROM VERSION 2.0.0 TO 2.0.1 END
