package io.github.tldnr1.limitedgoods.common;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
class ApiExceptionHandler {

	record ErrorBody(String code, String message) {
	}

	@ExceptionHandler(ApiException.class)
	ResponseEntity<ErrorBody> handle(ApiException e) {
		return ResponseEntity.status(e.status()).body(new ErrorBody(e.code(), e.getMessage()));
	}

	@ExceptionHandler({ HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class })
	ResponseEntity<ErrorBody> handleUnreadable(Exception e) {
		return handle(ApiException.invalidRequest("요청 형식이 잘못되었습니다."));
	}

}
