package com.stark.vms.common.error;

import com.stark.vms.common.api.ApiErrorResponse;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

@RestControllerAdvice
public class ApiExceptionHandler {

	private static final Logger logger = LoggerFactory.getLogger(ApiExceptionHandler.class);

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiErrorResponse> handleBeanValidation(MethodArgumentNotValidException exception) {
		Map<String, String> fieldErrors = new TreeMap<>();
		exception.getBindingResult().getFieldErrors().forEach(error ->
				fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage() == null
						? "Invalid value." : error.getDefaultMessage()));
		return error(HttpStatus.BAD_REQUEST, "Request validation failed.", fieldErrors);
	}

	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException exception) {
		Map<String, String> fieldErrors = exception.getConstraintViolations().stream()
				.collect(Collectors.toMap(
						violation -> lastPathSegment(violation.getPropertyPath().toString()),
						violation -> violation.getMessage(),
						(first, ignored) -> first,
						TreeMap::new));
		return error(HttpStatus.BAD_REQUEST, "Request validation failed.", fieldErrors);
	}

	@ExceptionHandler({HttpMessageNotReadableException.class})
	public ResponseEntity<ApiErrorResponse> handleUnreadableBody(HttpMessageNotReadableException exception) {
		return error(HttpStatus.BAD_REQUEST, "Request body contains an invalid or unsupported value.", null);
	}

	@ExceptionHandler(DomainValidationException.class)
	public ResponseEntity<ApiErrorResponse> handleDomainValidation(DomainValidationException exception) {
		return error(HttpStatus.BAD_REQUEST, exception.getMessage(), null);
	}

	@ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
	public ResponseEntity<ApiErrorResponse> handleInvalidParameter(Exception exception) {
		String field = exception instanceof MissingServletRequestParameterException missing
				? missing.getParameterName()
				: ((MethodArgumentTypeMismatchException) exception).getName();
		return error(HttpStatus.BAD_REQUEST, "Request parameter is invalid.", Map.of(field, "Invalid or missing value."));
	}

	@ExceptionHandler({DuplicateEmailException.class, DuplicateVehicleNumberException.class})
	public ResponseEntity<ApiErrorResponse> handleConflict(RuntimeException exception) {
		return error(HttpStatus.CONFLICT, exception.getMessage(), null);
	}

	@ExceptionHandler(VehicleNotFoundException.class)
	public ResponseEntity<ApiErrorResponse> handleNotFound(VehicleNotFoundException exception) {
		return error(HttpStatus.NOT_FOUND, exception.getMessage(), null);
	}

	@ExceptionHandler({InvalidCredentialsException.class, InvalidAccessTokenException.class, AuthenticationException.class})
	public ResponseEntity<ApiErrorResponse> handleUnauthorized(Exception exception) {
		String message = exception instanceof InvalidCredentialsException
				? exception.getMessage() : "Authentication is required or the access token is invalid.";
		return error(HttpStatus.UNAUTHORIZED, message, null);
	}

	@ExceptionHandler(AccessDeniedException.class)
	public ResponseEntity<ApiErrorResponse> handleForbidden(AccessDeniedException exception) {
		return error(HttpStatus.FORBIDDEN, "Access denied.", null);
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<ApiErrorResponse> handleDataConflict(DataIntegrityViolationException exception) {
		return error(HttpStatus.CONFLICT, "The request conflicts with existing data.", null);
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<ApiErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException exception) {
		return error(HttpStatus.METHOD_NOT_ALLOWED, "HTTP method is not supported for this resource.", null);
	}

	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<ApiErrorResponse> handleResourceNotFound(NoResourceFoundException exception) {
		return error(HttpStatus.NOT_FOUND, "Resource not found.", null);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception) {
		String correlationId = UUID.randomUUID().toString();
		logger.error("Unhandled API error correlationId={} exceptionType={}",
				correlationId, exception.getClass().getSimpleName());
		return error(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected server error occurred.", null);
	}

	private static ResponseEntity<ApiErrorResponse> error(HttpStatus status, String message,
			Map<String, String> fieldErrors) {
		return ResponseEntity.status(status).body(ApiErrorResponse.of(message, fieldErrors));
	}

	private static String lastPathSegment(String path) {
		int separator = path.lastIndexOf('.');
		return separator < 0 ? path : path.substring(separator + 1);
	}
}
