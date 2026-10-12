package io.github.tldnr1.limitedgoods.common;

import org.springframework.http.HttpStatus;

/**
 * 규칙이나 요청 형식에 막혀 처리하지 않은 요청. 트랜잭션 안에서 던지면 롤백된다.
 */
public class ApiException extends RuntimeException {

	private final HttpStatus status;
	private final String code;

	public ApiException(HttpStatus status, String code, String message) {
		super(message);
		this.status = status;
		this.code = code;
	}

	public static ApiException invalidRequest(String message) {
		return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", message);
	}

	public HttpStatus status() {
		return status;
	}

	public String code() {
		return code;
	}

}
