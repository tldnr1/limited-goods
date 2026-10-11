package io.github.tldnr1.limitedgoods.purchase.domain;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;

/**
 * 구매. 생성과 상태 전이는 경합 경로라 {@link PurchaseRepository}의 조건부 SQL로만 하고, 엔티티는 읽기에 쓴다(ADR-0004).
 */
@Entity
public class Purchase {

	@Id
	private Long id;

	private Long eventId;

	private String buyerId;

	@Enumerated(EnumType.STRING)
	private PurchaseStatus status;

	@Enumerated(EnumType.STRING)
	private FailureReason failureReason;

	private Instant arrivedAt;

	private Instant heldAt;

	private Instant paymentDeadlineAt;

	protected Purchase() {
	}

	public boolean isOwnedBy(String buyerId) {
		return this.buyerId.equals(buyerId);
	}

	public Long getId() {
		return id;
	}

	public PurchaseStatus getStatus() {
		return status;
	}

	public FailureReason getFailureReason() {
		return failureReason;
	}

	public Instant getPaymentDeadlineAt() {
		return paymentDeadlineAt;
	}

}
