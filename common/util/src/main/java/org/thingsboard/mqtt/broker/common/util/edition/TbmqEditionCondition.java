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
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.core.type.ClassMetadata;
import org.springframework.core.type.MethodMetadata;
import org.springframework.util.ClassUtils;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Matches when the edition read from the classpath marker equals the value of the
 * {@link ConditionalOnTbmqEdition} present on the annotated element. The marker is read through the class
 * loader only, never through the Spring {@code Environment}, so no property, environment variable or system
 * property can change it. An element carrying {@link ConditionalOnTbmqEdition} for different editions
 * (e.g. both {@link ClusterComponent} and {@link StandaloneComponent}) is a programming error and fails fast.
 */
public class TbmqEditionCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        ClassLoader classLoader = context.getClassLoader() != null ? context.getClassLoader() : ClassUtils.getDefaultClassLoader();
        TbmqEdition required = requiredEdition(metadata);
        return TbmqEditionResolver.resolve(classLoader) == required;
    }

    private static TbmqEdition requiredEdition(AnnotatedTypeMetadata metadata) {
        Set<TbmqEdition> editions = metadata.getAnnotations().stream(ConditionalOnTbmqEdition.class)
                .map(annotation -> annotation.getEnum("value", TbmqEdition.class))
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(TbmqEdition.class)));
        if (editions.size() > 1) {
            throw new IllegalStateException("Conflicting edition annotations " + editions + " on " + describe(metadata)
                    + ", a bean can belong to only one TBMQ edition");
        }
        return editions.iterator().next();
    }

    private static String describe(AnnotatedTypeMetadata metadata) {
        if (metadata instanceof MethodMetadata method) {
            return method.getDeclaringClassName() + "#" + method.getMethodName();
        }
        if (metadata instanceof ClassMetadata type) {
            return type.getClassName();
        }
        return String.valueOf(metadata);
    }

}
