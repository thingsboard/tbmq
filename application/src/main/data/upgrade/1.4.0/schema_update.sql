--
-- Copyright © 2016-2024 The Thingsboard Authors
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

CREATE OR REPLACE PROCEDURE export_device_publish_msgs(IN msg_limit int, IN path_to_file varchar)
    LANGUAGE plpgsql
AS
$$
BEGIN
    EXECUTE format(
            'COPY (
                SELECT dpm.client_id,
                       dpm.topic,
                       dpm.time,
                       dpm.packet_id,
                       dpm.packet_type,
                       dpm.qos,
                       dpm.payload,
                       dpm.user_properties,
                       dpm.retain,
                       dpm.msg_expiry_interval,
                       dpm.payload_format_indicator,
                       dpm.content_type,
                       dpm.response_topic,
                       dpm.correlation_data
                FROM device_publish_msg dpm
                JOIN device_session_ctx dsc ON dpm.client_id = dsc.client_id
                WHERE dpm.serial_number >= (dsc.last_serial_number - %L + 1)
                ORDER BY dpm.client_id, dpm.serial_number ASC
            ) TO %L WITH CSV HEADER',
            msg_limit,
            path_to_file);
END
$$;

CREATE TABLE IF NOT EXISTS ts_kv_latest (
    entity_id varchar (255) NOT NULL,
    key int NOT NULL,
    ts bigint NOT NULL,
    long_v bigint,
    CONSTRAINT ts_kv_latest_pkey PRIMARY KEY (entity_id, key)
);
