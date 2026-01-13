/**
 * Copyright © 2016-2026 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.config;

import jakarta.annotation.PreDestroy;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.Before;
import org.junit.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HistoricalDataReportPropertiesTest {

    private ValidatorFactory factory;
    private Validator validator;

    @Before
    public void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @PreDestroy
    public void tearDown() {
        factory.close();
    }

    @Test
    public void testIntervalValid() {
        HistoricalDataReportProperties props = new HistoricalDataReportProperties();
        props.setInterval(5);

        Set<ConstraintViolation<HistoricalDataReportProperties>> violations = validator.validate(props);
        assertTrue(violations.isEmpty());
    }

    @Test
    public void testIntervalInvalidTooHigh() {
        HistoricalDataReportProperties props = new HistoricalDataReportProperties();
        props.setInterval(61);

        Set<ConstraintViolation<HistoricalDataReportProperties>> violations = validator.validate(props);
        assertFalse(violations.isEmpty());
    }

    @Test
    public void testIntervalInvalidTooLow() {
        HistoricalDataReportProperties props = new HistoricalDataReportProperties();
        props.setInterval(0);

        Set<ConstraintViolation<HistoricalDataReportProperties>> violations = validator.validate(props);
        assertFalse(violations.isEmpty());
    }

}
