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
package org.thingsboard.mqtt.broker.common.util.edition;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.ClassUtils;

/**
 * Matches when the edition read from the classpath marker equals the value of the merged
 * {@link ConditionalOnTbmqEdition}. The marker is read through the class loader only, never through the
 * Spring {@code Environment}, so no property, environment variable or system property can change it.
 */
public class TbmqEditionCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        ClassLoader classLoader = context.getClassLoader() != null ? context.getClassLoader() : ClassUtils.getDefaultClassLoader();
        MergedAnnotation<ConditionalOnTbmqEdition> annotation = metadata.getAnnotations().get(ConditionalOnTbmqEdition.class);
        return TbmqEditionResolver.resolve(classLoader) == annotation.getEnum("value", TbmqEdition.class);
    }

}
