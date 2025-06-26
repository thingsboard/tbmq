--
-- Copyright © 2016-2020 The Thingsboard Authors
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

/** SYSTEM **/

/** Admin **/
INSERT INTO broker_user (id, created_time, email, authority)
VALUES ('5a797660-4612-11e7-a919-92ebcb67fe33', 1592576748000, 'sysadmin@thingsboard.org', 'SYS_ADMIN');

INSERT INTO user_credentials (id, created_time, user_id, enabled, password)
VALUES ('61441950-4612-11e7-a919-92ebcb67fe33', 1592576748000, '5a797660-4612-11e7-a919-92ebcb67fe33', true,
        '$2a$10$5JTB8/hxWc9WAy62nCGSxeefl3KWmipA9nFpVdDa0/xfIseeBB4Bu');

/** System settings **/
INSERT INTO admin_settings (id, created_time, key, json_value)
VALUES ('6a2266e4-4612-11e7-a919-92ebcb67fe33', 1592576748000, 'general', '{
	"baseUrl": "http://localhost:8083"
}');

INSERT INTO admin_settings (id, created_time, key, json_value)
VALUES ('6eaaefa6-4612-11e7-a919-92ebcb67fe33', 1592576748000, 'mail', '{
	"mailFrom": "Thingsboard <sysadmin@localhost.localdomain>",
	"smtpProtocol": "smtp",
	"smtpHost": "localhost",
	"smtpPort": "25",
	"timeout": "10000",
	"enableTls": false,
	"tlsVersion": "TLSv1.2",
	"username": "",
	"password": ""
}');

INSERT INTO admin_settings (id, created_time, key, json_value)
VALUES (
           '7ca448f2-780e-4b38-ac15-c064a8f20bb5',
           1748975222000,
           'mqttAuthorization',
           '{"priorities":["MQTT_BASIC","X_509","JWT"]}'
       );

INSERT INTO mqtt_auth_provider (id, created_time, enabled, type, configuration, additional_info)
VALUES
-- MQTT_BASIC
('bc0a90bf-de56-4953-92ca-7ef7b159c2fd', 1748975222000, false, 'MQTT_BASIC', '{"type": "MQTT_BASIC"}'::jsonb, '{}'),

-- X_509
('e5b71665-0a22-4398-8f93-5c7c3c145a7d', 1748975222000, false, 'X_509', '{"type": "X_509", "skipValidityCheckForClientCert": false}'::jsonb, '{}'),

-- JWT
('5590d101-628f-414c-9960-5966a9c42c31', 1748975222000, false, 'JWT', '{
  "type": "JWT",
  "authClaims": {},
  "clientTypeClaims": {},
  "authRules": {},
  "defaultClientType": "DEVICE",
  "jwtVerifierConfiguration": {
    "jwtVerifierType": "ALGORITHM_BASED",
    "jwtSignAlgorithmConfiguration": {
      "secret": "please-change-this-32-char-jwt-secret",
      "algorithm": "HMAC_BASED"
    }
  }
}'::jsonb, '{}'),

-- SCRAM
('3a7ffd44-eb9c-4334-951d-a8bb3cb4804f', 1748975222000, false, 'SCRAM', '{"type": "SCRAM"}'::jsonb, '{}');
