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

CREATE TABLE IF NOT EXISTS admin_settings (
    id uuid NOT NULL CONSTRAINT admin_settings_pkey PRIMARY KEY,
    created_time bigint NOT NULL,
    json_value varchar,
    key varchar(255)
);

CREATE TABLE IF NOT EXISTS broker_user (
    id uuid NOT NULL CONSTRAINT broker_user_pkey PRIMARY KEY,
    created_time bigint NOT NULL,
    additional_info varchar,
    authority varchar(255),
    email varchar(255) UNIQUE,
    first_name varchar(255),
    last_name varchar(255),
    search_text varchar(255)
);

CREATE TABLE IF NOT EXISTS user_credentials (
    id uuid NOT NULL CONSTRAINT user_credentials_pkey PRIMARY KEY,
    created_time bigint NOT NULL,
    activate_token varchar(255) UNIQUE,
    enabled boolean,
    password varchar(255),
    reset_token varchar(255) UNIQUE,
    user_id uuid UNIQUE
);

CREATE TABLE IF NOT EXISTS mqtt_client_credentials (
    id uuid NOT NULL CONSTRAINT mqtt_client_credentials_pkey PRIMARY KEY,
    created_time bigint NOT NULL,
    name varchar(255),
    client_type varchar(255),
    credentials_id varchar,
    credentials_type varchar(255),
    credentials_value varchar,
    search_text varchar(255),
    CONSTRAINT mqtt_client_credentials_id_unq_key UNIQUE (credentials_id)
);

CREATE TABLE IF NOT EXISTS device_publish_msg (
    client_id varchar(255) NOT NULL,
    serial_number bigint NOT NULL,
    topic varchar NOT NULL,
    time bigint NOT NULL,
    packet_id int,
    packet_type varchar(255),
    qos int NOT NULL,
    payload BINARY NOT NULL,
    user_properties varchar,
    retain boolean,
    CONSTRAINT device_publish_msg_pkey PRIMARY KEY (client_id, serial_number)
);

CREATE TABLE IF NOT EXISTS device_session_ctx (
    client_id varchar(255) NOT NULL CONSTRAINT device_session_ctx_pkey PRIMARY KEY,
    last_updated_time bigint NOT NULL,
    last_serial_number bigint,
    last_packet_id int
);

CREATE TABLE IF NOT EXISTS application_session_ctx (
    client_id varchar(255) NOT NULL CONSTRAINT application_session_ctx_pkey PRIMARY KEY,
    last_updated_time bigint NOT NULL,
    publish_msg_infos varchar,
    pubrel_msg_infos varchar
);

CREATE TABLE IF NOT EXISTS generic_client_session_ctx (
    client_id varchar(255) NOT NULL CONSTRAINT generic_client_session_ctx_pkey PRIMARY KEY,
    last_updated_time bigint NOT NULL,
    qos2_publish_packet_ids varchar
);

CREATE TABLE IF NOT EXISTS application_shared_subscription
(
    id           uuid    NOT NULL
        CONSTRAINT application_shared_subscription_pkey PRIMARY KEY,
    created_time bigint  NOT NULL,
    topic        varchar NOT NULL,
    partitions   int     NOT NULL,
    name         varchar(255),
    search_text  varchar(255),
    CONSTRAINT application_shared_subscription_topic_unq_key UNIQUE (topic)
);

CREATE TABLE IF NOT EXISTS ts_kv
(
    entity_id varchar(255) NOT NULL,
    key       int          NOT NULL,
    ts        bigint       NOT NULL,
    long_v    bigint,
    CONSTRAINT ts_kv_pkey PRIMARY KEY (entity_id, key, ts)
) PARTITION BY RANGE (ts);

CREATE TABLE IF NOT EXISTS ts_kv_dictionary
(
    key    varchar(255) NOT NULL,
    key_id serial UNIQUE,
    CONSTRAINT ts_key_id_pkey PRIMARY KEY (key)
);
