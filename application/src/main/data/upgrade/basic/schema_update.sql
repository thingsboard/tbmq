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

CREATE TABLE IF NOT EXISTS client_trace (
    id uuid NOT NULL CONSTRAINT client_trace_pkey PRIMARY KEY,
    created_time bigint NOT NULL,
    client_id varchar(255) NOT NULL CONSTRAINT client_trace_client_id_key UNIQUE,
    expires_at timestamp with time zone NOT NULL,
    trace_level varchar(32) NOT NULL
);


-- UPGRADE FROM VERSION 2.4.0 TO 2.4.1 END
