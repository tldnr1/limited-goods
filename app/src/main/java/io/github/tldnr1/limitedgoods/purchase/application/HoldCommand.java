package io.github.tldnr1.limitedgoods.purchase.application;

import java.time.Instant;
import java.util.SortedMap;

/**
 * @param quantities 품목 ID 순서로 정렬한 품목별 수량. 형식 검증을 마친 값이다.
 */
public record HoldCommand(long eventId, String buyerId, SortedMap<Long, Integer> quantities, Instant arrivedAt) {
}
