package io.github.tldnr1.limitedgoods.purchase.web;

import java.time.Instant;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import io.github.tldnr1.limitedgoods.common.ApiException;
import io.github.tldnr1.limitedgoods.common.ArrivalTimeFilter;
import io.github.tldnr1.limitedgoods.purchase.application.HoldCommand;
import io.github.tldnr1.limitedgoods.purchase.application.HoldPurchaseService;
import io.github.tldnr1.limitedgoods.purchase.application.HoldResult;
import io.github.tldnr1.limitedgoods.purchase.application.PurchaseQuery;
import io.github.tldnr1.limitedgoods.purchase.application.PurchaseQuery.PurchaseView;

@RestController
class PurchaseController {

	private static final String BUYER_HEADER = "X-Buyer-Id";
	private static final int BUYER_ID_MAX_LENGTH = 64;

	record HoldRequest(List<Line> items) {
		record Line(Long itemId, Integer quantity) {
		}
	}

	record HoldResponse(long purchaseId, Instant paymentDeadlineAt) {
	}

	private final HoldPurchaseService holdPurchase;
	private final PurchaseQuery purchaseQuery;

	PurchaseController(HoldPurchaseService holdPurchase, PurchaseQuery purchaseQuery) {
		this.holdPurchase = holdPurchase;
		this.purchaseQuery = purchaseQuery;
	}

	@PostMapping("/events/{eventId}/purchases")
	ResponseEntity<HoldResponse> hold(@PathVariable long eventId,
			@RequestHeader(name = BUYER_HEADER, required = false) String buyerId, @RequestBody HoldRequest request,
			@RequestAttribute(ArrivalTimeFilter.ATTRIBUTE) Instant arrivedAt) {
		HoldResult result = holdPurchase
			.hold(new HoldCommand(eventId, validBuyerId(buyerId), quantities(request), arrivedAt));
		return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
			.body(new HoldResponse(result.purchaseId(), result.paymentDeadlineAt()));
	}

	@GetMapping("/purchases/{purchaseId}")
	PurchaseView get(@PathVariable long purchaseId,
			@RequestHeader(name = BUYER_HEADER, required = false) String buyerId) {
		return purchaseQuery.get(purchaseId, validBuyerId(buyerId));
	}

	private static String validBuyerId(String buyerId) {
		if (buyerId == null || buyerId.isBlank() || buyerId.length() > BUYER_ID_MAX_LENGTH) {
			throw ApiException.invalidRequest(BUYER_HEADER + " 헤더는 1~64자여야 합니다.");
		}
		return buyerId;
	}

	private static SortedMap<Long, Integer> quantities(HoldRequest request) {
		if (request.items() == null || request.items().isEmpty()) {
			throw ApiException.invalidRequest("품목이 없습니다.");
		}
		SortedMap<Long, Integer> quantities = new TreeMap<>();
		for (HoldRequest.Line line : request.items()) {
			if (line == null || line.itemId() == null || line.quantity() == null || line.quantity() <= 0) {
				throw ApiException.invalidRequest("품목 ID와 1 이상의 수량이 필요합니다.");
			}
			if (quantities.putIfAbsent(line.itemId(), line.quantity()) != null) {
				throw ApiException.invalidRequest("같은 품목이 두 번 들어 있습니다.");
			}
		}
		return quantities;
	}

}
