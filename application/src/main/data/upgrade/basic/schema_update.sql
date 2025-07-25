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

-- UPGRADE FROM VERSION 2.1.0 TO 2.2.0 START

ALTER TABLE mqtt_client_credentials ADD COLUMN IF NOT EXISTS additional_info varchar;
UPDATE mqtt_client_credentials SET credentials_type = 'X_509' WHERE credentials_type = 'SSL';

-- UPGRADE FROM VERSION 2.1.0 TO 2.2.0 END
