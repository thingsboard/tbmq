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

-- CREATE OR REPLACE PROCEDURE export_device_publish_msgs(IN path_to_file varchar)
--     LANGUAGE plpgsql
-- AS $$
-- BEGIN
--     EXECUTE format('COPY (SELECT * FROM device_publish_msg) TO %L WITH CSV HEADER', path_to_file);
-- END
-- $$;

CREATE OR REPLACE PROCEDURE export_device_publish_msgs(IN msg_limit int, IN path_to_file varchar)
    LANGUAGE plpgsql
AS $$
BEGIN
    EXECUTE format('COPY (
                        SELECT *
                        FROM device_publish_msg dpm
                        WHERE serial_number >= (
                            SELECT min(serial_number)
                            FROM (
                                SELECT serial_number
                                FROM device_publish_msg
                                WHERE client_id = dpm.client_id
                                ORDER BY serial_number DESC
                                LIMIT %L
                            ) AS subquery
                        ) ORDER BY client_id, serial_number ASC
                    ) TO %L WITH CSV HEADER', msg_limit, path_to_file);
END
$$;
