/**
 * Copyright © 2016-2024 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thingsboard.mqtt.broker.dao.sqlts.sql;

import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
public class JpaSqlTimeseriesDaoTest {

    private JpaSqlTimeseriesDao jpaSqlTimeseriesDao;

    @Before
    public void setUp() {
        jpaSqlTimeseriesDao = new JpaSqlTimeseriesDao(null);
    }

    @Test
    public void testGetPartitioning() {
        // Test partitioning by YEARS
        jpaSqlTimeseriesDao.setPartitioning("YEARS");

        LocalDateTime date = LocalDate.of(2023, 5, 1).atStartOfDay();
        String expectedPartition = "ts_kv_2023";
        String actualPartition = jpaSqlTimeseriesDao.getPartitionByDate(date);
        assertThat(actualPartition).isEqualTo(expectedPartition);

        date = LocalDate.of(2024, 11, 5).atStartOfDay();
        expectedPartition = "ts_kv_2024";
        actualPartition = jpaSqlTimeseriesDao.getPartitionByDate(date);
        assertThat(actualPartition).isEqualTo(expectedPartition);

        // Test partitioning by MONTHS
        jpaSqlTimeseriesDao.setPartitioning("MONTHS");

        date = LocalDate.of(2022, 1, 4).atStartOfDay();
        expectedPartition = "ts_kv_2022_01";
        actualPartition = jpaSqlTimeseriesDao.getPartitionByDate(date);
        assertThat(actualPartition).isEqualTo(expectedPartition);

        date = LocalDate.of(2022, 12, 1).atStartOfDay();
        expectedPartition = "ts_kv_2022_12";
        actualPartition = jpaSqlTimeseriesDao.getPartitionByDate(date);
        assertThat(actualPartition).isEqualTo(expectedPartition);

        // Test partitioning by DAYS
        jpaSqlTimeseriesDao.setPartitioning("DAYS");

        date = LocalDate.of(2023, 2, 4).atStartOfDay();
        expectedPartition = "ts_kv_2023_02_04";
        actualPartition = jpaSqlTimeseriesDao.getPartitionByDate(date);
        assertThat(actualPartition).isEqualTo(expectedPartition);

        date = LocalDate.of(2023, 12, 1).atStartOfDay();
        expectedPartition = "ts_kv_2023_12_01";
        actualPartition = jpaSqlTimeseriesDao.getPartitionByDate(date);
        assertThat(actualPartition).isEqualTo(expectedPartition);
    }

    @Test
    public void testCheckNeedDropTableByYears() {
        jpaSqlTimeseriesDao.setPartitioning("YEARS");

        LocalDateTime date = LocalDate.of(2023, 5, 1).atStartOfDay();
        String tableName = "ts_kv_2022";
        boolean actualCheckDropTable = jpaSqlTimeseriesDao.checkNeedDropTable(date, tableName);
        assertThat(actualCheckDropTable).isTrue();

        date = LocalDate.of(2023, 12, 31).atStartOfDay();
        tableName = "ts_kv_2023";
        actualCheckDropTable = jpaSqlTimeseriesDao.checkNeedDropTable(date, tableName);
        assertThat(actualCheckDropTable).isFalse();

        date = LocalDate.of(2023, 12, 31).atStartOfDay();
        tableName = "ts_kv_2023_01"; // setting not correct table name for YEARS partitioning
        actualCheckDropTable = jpaSqlTimeseriesDao.checkNeedDropTable(date, tableName);
        assertThat(actualCheckDropTable).isFalse();
    }

    @Test
    public void testCheckNeedDropTableByMonths() {
        jpaSqlTimeseriesDao.setPartitioning("MONTHS");

        LocalDateTime date = LocalDate.of(2023, 1, 1).atStartOfDay();
        String tableName = "ts_kv_2022_12";
        boolean actualCheckDropTable = jpaSqlTimeseriesDao.checkNeedDropTable(date, tableName);
        assertThat(actualCheckDropTable).isTrue();

        date = LocalDate.of(2023, 8, 1).atStartOfDay();
        tableName = "ts_kv_2023_04";
        actualCheckDropTable = jpaSqlTimeseriesDao.checkNeedDropTable(date, tableName);
        assertThat(actualCheckDropTable).isTrue();

        date = LocalDate.of(2023, 12, 31).atStartOfDay();
        tableName = "ts_kv_2023_12";
        actualCheckDropTable = jpaSqlTimeseriesDao.checkNeedDropTable(date, tableName);
        assertThat(actualCheckDropTable).isFalse();

        date = LocalDate.of(2023, 12, 31).atStartOfDay();
        tableName = "ts_kv_2023_01_01"; // setting not correct table name for MONTHS partitioning
        actualCheckDropTable = jpaSqlTimeseriesDao.checkNeedDropTable(date, tableName);
        assertThat(actualCheckDropTable).isFalse();
    }

    @Test
    public void testCheckNeedDropTableByDays() {
        jpaSqlTimeseriesDao.setPartitioning("DAYS");

        LocalDateTime date = LocalDate.of(2023, 1, 2).atStartOfDay();
        String tableName = "ts_kv_2023_01_01";
        boolean actualCheckDropTable = jpaSqlTimeseriesDao.checkNeedDropTable(date, tableName);
        assertThat(actualCheckDropTable).isTrue();

        date = LocalDate.of(2023, 8, 1).atStartOfDay();
        tableName = "ts_kv_2023_04_25";
        actualCheckDropTable = jpaSqlTimeseriesDao.checkNeedDropTable(date, tableName);
        assertThat(actualCheckDropTable).isTrue();

        date = LocalDate.of(2023, 12, 31).atStartOfDay();
        tableName = "ts_kv_2023_12_31";
        actualCheckDropTable = jpaSqlTimeseriesDao.checkNeedDropTable(date, tableName);
        assertThat(actualCheckDropTable).isFalse();

        date = LocalDate.of(2023, 12, 31).atStartOfDay();
        tableName = "ts_kv_2023"; // setting not correct table name for DAYS partitioning
        actualCheckDropTable = jpaSqlTimeseriesDao.checkNeedDropTable(date, tableName);
        assertThat(actualCheckDropTable).isFalse();
    }

    @Test
    public void testGetPartitioningByYear() {
        jpaSqlTimeseriesDao.setPartitioning("YEARS");

        LocalDateTime date = LocalDate.of(2023, 5, 1).atStartOfDay();
        String expectedPartition = "ts_kv_2023";
        String actualPartition = jpaSqlTimeseriesDao.getPartitionByDate(date);
        assertThat(actualPartition).isEqualTo(expectedPartition);

        date = LocalDate.of(2023, 1, 5).atStartOfDay();
        expectedPartition = "ts_kv_2023";
        actualPartition = jpaSqlTimeseriesDao.getPartitionByDate(date);
        assertThat(actualPartition).isEqualTo(expectedPartition);

        date = LocalDate.of(2022, 1, 4).atStartOfDay();
        expectedPartition = "ts_kv_2022";
        actualPartition = jpaSqlTimeseriesDao.getPartitionByDate(date);
        assertThat(actualPartition).isEqualTo(expectedPartition);

        date = LocalDate.of(2022, 12, 1).atStartOfDay();
        expectedPartition = "ts_kv_2022";
        actualPartition = jpaSqlTimeseriesDao.getPartitionByDate(date);
        assertThat(actualPartition).isEqualTo(expectedPartition);
    }


    @Test
    public void testGetPartitioningByMonths() {
        jpaSqlTimeseriesDao.setPartitioning("MONTHS");

        LocalDateTime date = LocalDate.of(2023, 5, 1).atStartOfDay();
        String expectedPartition = "ts_kv_2023_05";
        String actualPartition = jpaSqlTimeseriesDao.getPartitionByDate(date);
        assertThat(actualPartition).isEqualTo(expectedPartition);

        date = LocalDate.of(2023, 12, 5).atStartOfDay();
        expectedPartition = "ts_kv_2023_12";
        actualPartition = jpaSqlTimeseriesDao.getPartitionByDate(date);
        assertThat(actualPartition).isEqualTo(expectedPartition);

        date = LocalDate.of(2022, 9, 4).atStartOfDay();
        expectedPartition = "ts_kv_2022_09";
        actualPartition = jpaSqlTimeseriesDao.getPartitionByDate(date);
        assertThat(actualPartition).isEqualTo(expectedPartition);

        date = LocalDate.of(2023, 1, 1).atStartOfDay();
        expectedPartition = "ts_kv_2023_01";
        actualPartition = jpaSqlTimeseriesDao.getPartitionByDate(date);
        assertThat(actualPartition).isEqualTo(expectedPartition);

        date = LocalDate.of(2022, 1, 12).atStartOfDay();
        expectedPartition = "ts_kv_2022_01";
        actualPartition = jpaSqlTimeseriesDao.getPartitionByDate(date);
        assertThat(actualPartition).isEqualTo(expectedPartition);
    }

    @Test
    public void testGetPartitioningByDays() {
        jpaSqlTimeseriesDao.setPartitioning("DAYS");

        LocalDateTime date = LocalDate.of(2023, 2, 7).atStartOfDay();
        String expectedPartition = "ts_kv_2023_02_07";
        String actualPartition = jpaSqlTimeseriesDao.getPartitionByDate(date);
        assertThat(actualPartition).isEqualTo(expectedPartition);

        date = LocalDate.of(2023, 12, 5).atStartOfDay();
        expectedPartition = "ts_kv_2023_12_05";
        actualPartition = jpaSqlTimeseriesDao.getPartitionByDate(date);
        assertThat(actualPartition).isEqualTo(expectedPartition);

        date = LocalDate.of(2022, 3, 4).atStartOfDay();
        expectedPartition = "ts_kv_2022_03_04";
        actualPartition = jpaSqlTimeseriesDao.getPartitionByDate(date);
        assertThat(actualPartition).isEqualTo(expectedPartition);

        date = LocalDate.of(2022, 1, 23).atStartOfDay();
        expectedPartition = "ts_kv_2022_01_23";
        actualPartition = jpaSqlTimeseriesDao.getPartitionByDate(date);
        assertThat(actualPartition).isEqualTo(expectedPartition);

        date = LocalDate.of(2022, 1, 31).atStartOfDay();
        expectedPartition = "ts_kv_2022_01_31";
        actualPartition = jpaSqlTimeseriesDao.getPartitionByDate(date);
        assertThat(actualPartition).isEqualTo(expectedPartition);

        date = LocalDate.of(2024, 2, 29).atStartOfDay();
        expectedPartition = "ts_kv_2024_02_29";
        actualPartition = jpaSqlTimeseriesDao.getPartitionByDate(date);
        assertThat(actualPartition).isEqualTo(expectedPartition);
    }

}
