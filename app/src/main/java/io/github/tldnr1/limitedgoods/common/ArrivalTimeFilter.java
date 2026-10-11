package io.github.tldnr1.limitedgoods.common;

import java.io.IOException;
import java.time.Clock;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 앱이 요청을 처음 받는 지점에서 도착 시각을 기록한다. Q2의 순서와 T1의 기한 판단에 같은 값을 쓴다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ArrivalTimeFilter extends OncePerRequestFilter {

	public static final String ATTRIBUTE = "arrivedAt";

	private final Clock clock;

	ArrivalTimeFilter(Clock clock) {
		this.clock = clock;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		request.setAttribute(ATTRIBUTE, clock.instant());
		chain.doFilter(request, response);
	}

}
