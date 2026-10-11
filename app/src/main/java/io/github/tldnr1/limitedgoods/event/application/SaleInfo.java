package io.github.tldnr1.limitedgoods.event.application;

import java.time.Instant;
import java.util.Map;

/**
 * 구매 판단에 필요한 행사의 판매 정보.
 */
public record SaleInfo(long eventId, Instant saleStartsAt, Map<Long, ItemTerms> items) {

	public record ItemTerms(int perBuyerLimit, long price) {
	}

	public boolean hasStartedAt(Instant arrivedAt) {
		return !arrivedAt.isBefore(saleStartsAt);
	}

}
