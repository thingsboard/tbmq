/**
 * Copyright © 2016-2025 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.mail.MessagingException;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolation;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.util.function.ThrowingFunction;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.context.request.async.DeferredResult;
import org.thingsboard.mqtt.broker.common.data.UnauthorizedClient;
import org.thingsboard.mqtt.broker.common.data.User;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardErrorCode;
import org.thingsboard.mqtt.broker.common.data.exception.ThingsboardException;
import org.thingsboard.mqtt.broker.common.data.id.HasId;
import org.thingsboard.mqtt.broker.common.data.integration.Integration;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.page.SortOrder;
import org.thingsboard.mqtt.broker.common.data.page.TimePageLink;
import org.thingsboard.mqtt.broker.common.data.security.MqttAuthProvider;
import org.thingsboard.mqtt.broker.common.data.security.MqttClientCredentials;
import org.thingsboard.mqtt.broker.common.data.util.StringUtils;
import org.thingsboard.mqtt.broker.dao.client.MqttClientCredentialsService;
import org.thingsboard.mqtt.broker.dao.client.provider.MqttAuthProviderService;
import org.thingsboard.mqtt.broker.dao.client.unauthorized.UnauthorizedClientService;
import org.thingsboard.mqtt.broker.dao.exception.IncorrectParameterException;
import org.thingsboard.mqtt.broker.dao.integration.IntegrationService;
import org.thingsboard.mqtt.broker.dao.service.ConstraintValidator;
import org.thingsboard.mqtt.broker.dao.service.Validator;
import org.thingsboard.mqtt.broker.dao.user.UserService;
import org.thingsboard.mqtt.broker.dao.user.UserServiceImpl;
import org.thingsboard.mqtt.broker.dto.RetainedMsgDto;
import org.thingsboard.mqtt.broker.exception.DataValidationException;
import org.thingsboard.mqtt.broker.exception.ThingsboardErrorResponseHandler;
import org.thingsboard.mqtt.broker.queue.TbQueueAdmin;
import org.thingsboard.mqtt.broker.service.mqtt.auth.MqttAuthProviderManagerService;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.BlockedClientService;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.data.BlockedClient;
import org.thingsboard.mqtt.broker.service.mqtt.client.blocked.data.BlockedClientType;
import org.thingsboard.mqtt.broker.service.mqtt.client.cleanup.ClientSessionCleanUpService;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionStatsService;
import org.thingsboard.mqtt.broker.service.mqtt.retain.RetainedMsgListenerService;
import org.thingsboard.mqtt.broker.service.security.model.ChangePasswordRequest;
import org.thingsboard.mqtt.broker.service.security.model.SecurityUser;
import org.thingsboard.mqtt.broker.service.security.model.token.JwtTokenFactory;
import org.thingsboard.mqtt.broker.service.security.system.SystemSecurityService;
import org.thingsboard.mqtt.broker.service.subscription.shared.SharedSubscriptionPaginationService;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.thingsboard.mqtt.broker.dao.service.Validator.validateId;
import static org.thingsboard.mqtt.broker.dao.service.Validator.validateString;

@Slf4j
public abstract class BaseController {

    @Autowired
    private ThingsboardErrorResponseHandler errorResponseHandler;

    @Autowired
    protected UserService userService;
    @Autowired
    protected MqttClientCredentialsService mqttClientCredentialsService;
    @Autowired
    protected MqttAuthProviderManagerService mqttAuthProviderManagerService;
    @Autowired
    protected MqttAuthProviderService mqttAuthProviderService;
    @Autowired
    protected ClientSessionStatsService clientSessionStatsService;
    @Autowired
    protected RetainedMsgListenerService retainedMsgListenerService;
    @Autowired
    protected TbQueueAdmin tbQueueAdmin;
    @Autowired
    protected SharedSubscriptionPaginationService sharedSubscriptionPaginationService;
    @Autowired
    protected ClientSessionCleanUpService clientSessionCleanUpService;
    @Autowired
    protected SystemSecurityService systemSecurityService;
    @Autowired
    protected UnauthorizedClientService unauthorizedClientService;
    @Autowired
    protected IntegrationService integrationService;
    @Autowired
    protected BlockedClientService blockedClientService;
    @Autowired
    protected JwtTokenFactory tokenFactory;

    @Value("${server.log_controller_error_stack_trace}")
    @Getter
    private boolean logControllerErrorStackTrace;

    @ExceptionHandler(Exception.class)
    public void handleControllerException(Exception e, HttpServletResponse response) {
        ThingsboardException thingsboardException = handleException(e);
        if (thingsboardException.getErrorCode() == ThingsboardErrorCode.GENERAL && thingsboardException.getCause() instanceof Exception
                && StringUtils.equals(thingsboardException.getCause().getMessage(), thingsboardException.getMessage())) {
            e = (Exception) thingsboardException.getCause();
        } else {
            e = thingsboardException;
        }
        errorResponseHandler.handle(e, response);
    }

    @ExceptionHandler(ThingsboardException.class)
    public void handleThingsboardException(ThingsboardException ex, HttpServletResponse response) {
        errorResponseHandler.handle(ex, response);
    }

    /**
     * @deprecated Exceptions that are not of {@link ThingsboardException} type
     * are now caught and mapped to {@link ThingsboardException} by
     * {@link ExceptionHandler} {@link BaseController#handleControllerException(Exception, HttpServletResponse)}
     * which basically acts like the following boilerplate:
     * {@code
     *  try {
     *      someExceptionThrowingMethod();
     *  } catch (Exception e) {
     *      throw handleException(e);
     *  }
     * }
     * */
    @Deprecated
    ThingsboardException handleException(Exception exception) {
        return handleException(exception, true);
    }

    private ThingsboardException handleException(Exception exception, boolean logException) {
        if (logException && logControllerErrorStackTrace) {
            try {
                SecurityUser user = getCurrentUser();
                log.error("[{}] Error", user.getId(), exception);
            } catch (Exception e) {
                log.error("Error", exception);
            }
        }

        Throwable cause = exception.getCause();
        if (exception instanceof ThingsboardException) {
            return (ThingsboardException) exception;
        } else if (exception instanceof IllegalArgumentException || exception instanceof IncorrectParameterException
                || exception instanceof DataValidationException || cause instanceof IncorrectParameterException) {
            return new ThingsboardException(exception.getMessage(), ThingsboardErrorCode.BAD_REQUEST_PARAMS);
        } else if (exception instanceof MessagingException) {
            return new ThingsboardException("Unable to send mail", ThingsboardErrorCode.GENERAL);
        } else if (exception instanceof AsyncRequestTimeoutException) {
            return new ThingsboardException("Request timeout", ThingsboardErrorCode.GENERAL);
        } else if (exception instanceof DataAccessException) {
            if (!logControllerErrorStackTrace) { // not to log the error twice
                log.warn("Database error: {} - {}", exception.getClass().getSimpleName(), ExceptionUtils.getRootCauseMessage(exception));
            }
            if (cause instanceof ConstraintViolationException) {
                return new ThingsboardException(ExceptionUtils.getRootCause(exception).getMessage(), ThingsboardErrorCode.BAD_REQUEST_PARAMS);
            } else {
                return new ThingsboardException("Database error", ThingsboardErrorCode.GENERAL);
            }
        }
        return new ThingsboardException(exception.getMessage(), exception, ThingsboardErrorCode.GENERAL);
    }

    /**
     * Handles validation error for controller method arguments annotated with @{@link jakarta.validation.Valid}
     * */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public void handleValidationError(MethodArgumentNotValidException validationError, HttpServletResponse response) {
        List<ConstraintViolation<Object>> constraintsViolations = validationError.getFieldErrors().stream()
                .map(fieldError -> {
                    try {
                        return (ConstraintViolation<Object>) fieldError.unwrap(ConstraintViolation.class);
                    } catch (Exception e) {
                        log.warn("FieldError source is not of type ConstraintViolation");
                        return null; // should not happen
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        String errorMessage = "Validation error: " + ConstraintValidator.getErrorMessage(constraintsViolations);
        ThingsboardException thingsboardException = new ThingsboardException(errorMessage, ThingsboardErrorCode.BAD_REQUEST_PARAMS);
        handleControllerException(thingsboardException, response);
    }

    <T> T checkNotNull(T reference) throws ThingsboardException {
        return checkNotNull(reference, "Requested item wasn't found!");
    }

    <T> T checkNotNull(Optional<T> reference) throws ThingsboardException {
        return checkNotNull(reference, "Requested item wasn't found!");
    }

    <T> T checkNotNull(Optional<T> reference, String notFoundMessage) throws ThingsboardException {
        if (reference.isPresent()) {
            return reference.get();
        } else {
            throw new ThingsboardException(notFoundMessage, ThingsboardErrorCode.ITEM_NOT_FOUND);
        }
    }

    <T> T checkNotNull(T reference, String notFoundMessage) throws ThingsboardException {
        if (reference == null) {
            throw new ThingsboardException(notFoundMessage, ThingsboardErrorCode.ITEM_NOT_FOUND);
        }
        return reference;
    }

    void checkParameter(String name, String param) throws ThingsboardException {
        if (StringUtils.isEmpty(param)) {
            throw new ThingsboardException("Parameter '" + name + "' can't be empty!", ThingsboardErrorCode.BAD_REQUEST_PARAMS);
        }
    }

    void checkArrayParameter(String name, String[] params) throws ThingsboardException {
        if (params == null || params.length == 0) {
            throw new ThingsboardException("Parameter '" + name + "' can't be empty!", ThingsboardErrorCode.BAD_REQUEST_PARAMS);
        } else {
            for (String param : params) {
                checkParameter(name, param);
            }
        }
    }

    protected <T> T checkEnumParameter(String name, String param, Function<String, T> valueOf) throws ThingsboardException {
        try {
            return valueOf.apply(param.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ThingsboardException(name + " \"" + param + "\" is not supported!", ThingsboardErrorCode.BAD_REQUEST_PARAMS);
        }
    }

    User checkUserId(UUID userId) throws ThingsboardException {
        try {
            validateId(userId, "Incorrect userId " + userId);
            User user = userService.findUserById(userId);
            return checkNotNull(user);
        } catch (Exception e) {
            throw handleException(e, false);
        }
    }

    Integration checkIntegrationId(UUID integrationId) throws ThingsboardException {
        validateId(integrationId, "Incorrect integrationId " + integrationId);
        Integration integrationById = integrationService.findIntegrationById(integrationId);
        return checkNotNull(integrationById);
    }

    MqttClientCredentials checkClientCredentialsId(UUID clientCredentialsId) throws ThingsboardException {
        validateId(clientCredentialsId, "Incorrect clientCredentialsId " + clientCredentialsId);
        Optional<MqttClientCredentials> credentials = mqttClientCredentialsService.getCredentialsById(clientCredentialsId);
        return checkNotNull(credentials);
    }

    MqttAuthProvider checkAuthProviderId(UUID authProviderId) throws ThingsboardException {
        validateId(authProviderId, "Incorrect authProviderId " + authProviderId);
        Optional<MqttAuthProvider> authProvider = mqttAuthProviderService.getAuthProviderById(authProviderId);
        return checkNotNull(authProvider);
    }

    UnauthorizedClient checkUnauthorizedClient(String clientId) throws ThingsboardException {
        validateString(clientId, "Incorrect clientId " + clientId);
        Optional<UnauthorizedClient> client = unauthorizedClientService.findUnauthorizedClient(clientId);
        return checkNotNull(client);
    }

    RetainedMsgDto checkRetainedMsg(String topicName) throws ThingsboardException {
        validateString(topicName, "Incorrect topicName " + topicName);
        RetainedMsgDto retainedMsg = retainedMsgListenerService.getRetainedMsgForTopic(topicName);
        return checkNotNull(retainedMsg);
    }

    BlockedClient checkBlockedClient(BlockedClientType type, String key) throws ThingsboardException {
        validateString(key, "Incorrect key " + key);
        BlockedClient blockedClient = blockedClientService.getBlockedClient(type, key);
        return checkNotNull(blockedClient);
    }

    protected <E extends HasId, I extends UUID> E checkEntityId(I entityId, ThrowingFunction<I, E> findingFunction) throws ThingsboardException {
        try {
            validateId(entityId, "Invalid entity id");
            E entity = findingFunction.apply(entityId);
            return checkNotNull(entity, "entity with id [" + entityId + "] is not found");
        } catch (Exception e) {
            throw handleException(e, false);
        }
    }

    protected SecurityUser getCurrentUser() throws ThingsboardException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof SecurityUser) {
            return (SecurityUser) authentication.getPrincipal();
        } else {
            throw new ThingsboardException("You aren't authorized to perform this operation!", ThingsboardErrorCode.AUTHENTICATION);
        }
    }

    UUID toUUID(String id) throws ThingsboardException {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw handleException(e, false);
        }
    }

    PageLink createPageLink(int pageSize, int page, String textSearch, String sortProperty, String sortOrder) throws ThingsboardException {
        if (StringUtils.isNotEmpty(sortProperty)) {
            if (!Validator.isValidProperty(sortProperty)) {
                throw new IllegalArgumentException("Invalid sort property");
            }
            SortOrder.Direction direction = SortOrder.Direction.ASC;
            if (StringUtils.isNotEmpty(sortOrder)) {
                try {
                    direction = SortOrder.Direction.valueOf(sortOrder.toUpperCase());
                } catch (IllegalArgumentException e) {
                    throw new ThingsboardException("Unsupported sort order '" + sortOrder + "'! Only 'ASC' or 'DESC' types are allowed.", ThingsboardErrorCode.BAD_REQUEST_PARAMS);
                }
            }
            SortOrder sort = new SortOrder(sortProperty, direction);
            return new PageLink(pageSize, page, textSearch, sort);
        } else {
            return new PageLink(pageSize, page, textSearch);
        }
    }

    void validatePassword(BCryptPasswordEncoder passwordEncoder,
                          ChangePasswordRequest changePasswordRequest,
                          String currentRealPassword) throws ThingsboardException {

        var currentPassword = StringUtils.isEmpty(changePasswordRequest.getCurrentPassword()) ? null : changePasswordRequest.getCurrentPassword();
        var newPassword = StringUtils.isEmpty(changePasswordRequest.getNewPassword()) ? null : changePasswordRequest.getNewPassword();

        if (currentPassword == null && currentRealPassword == null) {
            log.debug("Current password matches!");
        } else if (currentPassword == null || currentRealPassword == null) {
            throw new ThingsboardException("Current password doesn't match!", ThingsboardErrorCode.BAD_REQUEST_PARAMS);
        } else {
            if (!passwordEncoder.matches(currentPassword, currentRealPassword)) {
                throw new ThingsboardException("Current password doesn't match!", ThingsboardErrorCode.BAD_REQUEST_PARAMS);
            }
        }
        if (newPassword == null && currentRealPassword == null) {
            throw new ThingsboardException("New password should be different from existing!", ThingsboardErrorCode.BAD_REQUEST_PARAMS);
        } else if (newPassword == null || currentRealPassword == null) {
            log.debug("New password is different!");
        } else {
            if (passwordEncoder.matches(newPassword, currentRealPassword)) {
                throw new ThingsboardException("New password should be different from existing!", ThingsboardErrorCode.BAD_REQUEST_PARAMS);
            }
        }
    }

    void handleError(Throwable e, final DeferredResult<ResponseEntity> response, HttpStatus defaultErrorStatus) {
        ResponseEntity responseEntity;
        if (e instanceof IllegalArgumentException || e instanceof IncorrectParameterException || e instanceof DataValidationException) {
            responseEntity = new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        } else {
            responseEntity = new ResponseEntity<>(defaultErrorStatus);
        }
        response.setResult(responseEntity);
    }

    TimePageLink createTimePageLink(int pageSize, int page, String textSearch,
                                    String sortProperty, String sortOrder, Long startTime, Long endTime) throws ThingsboardException {
        PageLink pageLink = this.createPageLink(pageSize, page, textSearch, sortProperty, sortOrder);
        return new TimePageLink(pageLink, startTime, endTime);
    }

    Set<Integer> collectIntegerQueryParams(String[] array) {
        if (array == null || array.length == 0) {
            return Set.of();
        }
        Set<Integer> resultSet = new HashSet<>();
        for (String strValue : array) {
            if (StringUtils.isNotEmpty(strValue)) {
                resultSet.add(Integer.valueOf(strValue));
            }
        }
        return resultSet;
    }

    List<Boolean> collectBooleanQueryParams(String[] array) {
        if (array == null || array.length == 0) {
            return List.of();
        }
        List<Boolean> resultList = new ArrayList<>();
        for (String strValue : array) {
            if (StringUtils.isNotEmpty(strValue)) {
                resultList.add(Boolean.valueOf(strValue));
            }
        }
        return resultList;
    }

    protected void throwRealCause(ExecutionException e) throws Exception {
        if (e.getCause() != null && e.getCause() instanceof Exception) {
            throw (Exception) e.getCause();
        } else {
            throw e;
        }
    }

    protected <E> PageData<E> toPageData(List<E> entities, PageLink pageLink) {
        int totalElements = entities.size();
        int totalPages = pageLink.getPageSize() > 0 ? (int) Math.ceil((float) totalElements / pageLink.getPageSize()) : 1;
        boolean hasNext = false;
        if (pageLink.getPageSize() > 0) {
            int startIndex = pageLink.getPageSize() * pageLink.getPage();
            int endIndex = startIndex + pageLink.getPageSize();
            if (entities.size() <= startIndex) {
                entities = Collections.emptyList();
            } else {
                if (endIndex > entities.size()) {
                    endIndex = entities.size();
                }
                entities = new ArrayList<>(entities.subList(startIndex, endIndex));
            }
            hasNext = totalElements > startIndex + entities.size();
        }
        return new PageData<>(entities, totalPages, totalElements, hasNext);
    }

    protected <E extends Enum<E>> List<E> parseEnumList(Class<E> enumType, String[] values) {
        if (values == null || values.length == 0) {
            return List.of();
        }
        List<E> result = new ArrayList<>();
        for (String val : values) {
            if (StringUtils.isNotEmpty(val)) {
                result.add(Enum.valueOf(enumType, val));
            }
        }
        return result;
    }

    protected <E extends Enum<E>> Set<E> parseEnumSet(Class<E> enumType, String[] values) {
        if (values == null || values.length == 0) {
            return Set.of();
        }
        Set<E> result = new HashSet<>();
        for (String val : values) {
            if (StringUtils.isNotEmpty(val)) {
                result.add(Enum.valueOf(enumType, val));
            }
        }
        return result;
    }

    protected <T> ResponseEntity<T> response(HttpStatus status) {
        return ResponseEntity.status(status).build();
    }

    protected <T> ResponseEntity<T> redirectTo(String location) {
        URI uri;
        try {
            uri = URI.create(location);
        } catch (IllegalArgumentException e) {
            log.error("Failed to create URI from '{}'", location, e);
            throw e;
        }
        return ResponseEntity.status(HttpStatus.SEE_OTHER)
                .location(uri)
                .build();
    }

    protected User filterSensitiveUserData(User user) {
        if (user.getAdditionalInfo() instanceof ObjectNode additionalInfo) {
            JsonNode passwordHistory = additionalInfo.remove(UserServiceImpl.USER_PASSWORD_HISTORY);

            boolean isPasswordChanged = passwordHistory != null && passwordHistory.size() > 1;
            additionalInfo.put(UserServiceImpl.USER_PASSWORD_CHANGED, isPasswordChanged);
        }
        return user;
    }

}
