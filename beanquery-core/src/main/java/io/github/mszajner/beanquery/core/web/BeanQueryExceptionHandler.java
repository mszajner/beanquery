/*
 * Copyright 2026 Mirosław Szajner
 * SPDX-License-Identifier: Apache-2.0
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
package io.github.mszajner.beanquery.core.web;

import io.github.mszajner.beanquery.core.metadata.UnknownEntityException;
import io.github.mszajner.beanquery.core.query.InvalidQueryException;
import io.github.mszajner.beanquery.core.security.QueryAccessDeniedException;
import java.util.List;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Error handling scoped to {@link BeanQueryController}.
 *
 * <ul>
 *   <li>{@link UnknownEntityException} &rarr; {@code 404}</li>
 *   <li>{@link QueryAccessDeniedException} &rarr; {@code 403} with {@code { "error": "access_denied" }}</li>
 *   <li>{@link InvalidQueryException} &rarr; {@code 400} with {@code { "errors": [ ... ] }}</li>
 *   <li>a malformed / unparseable body &rarr; {@code 400} with the same shape</li>
 *   <li>anything else (incl. a misconfigured authorizer) &rarr; {@code 500} with a generic
 *       message, details logged server-side</li>
 * </ul>
 */
@RestControllerAdvice(assignableTypes = BeanQueryController.class)
public class BeanQueryExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Log log = LogFactory.getLog(BeanQueryExceptionHandler.class);

    @ExceptionHandler(UnknownEntityException.class)
    public ResponseEntity<ErrorResponse> handleUnknownEntity(UnknownEntityException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(QueryAccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(QueryAccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorResponse("access_denied"));
    }

    @ExceptionHandler(InvalidQueryException.class)
    public ResponseEntity<ValidationErrorResponse> handleInvalidQuery(InvalidQueryException ex) {
        return ResponseEntity.badRequest().body(new ValidationErrorResponse(ex.getErrors()));
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return ResponseEntity.badRequest().body(new ValidationErrorResponse(List.of(rootMessage(ex))));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unhandled beanquery error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Internal server error"));
    }

    private static String rootMessage(Throwable ex) {
        Throwable cause = ex;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        return (message == null || message.isBlank()) ? "request body could not be parsed" : message;
    }
}
