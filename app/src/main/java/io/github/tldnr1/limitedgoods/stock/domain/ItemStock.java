package io.github.tldnr1.limitedgoods.stock.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/**
 * 품목별 남은 수량. 바꾸는 일은 {@link StockRepository}의 조건부 갱신으로만 한다(ADR-0004, ADR-0006).
 */
@Entity
public class ItemStock {

	@Id
	private Long itemId;

	private int remaining;

	protected ItemStock() {
	}

	public Long getItemId() {
		return itemId;
	}

	public int getRemaining() {
		return remaining;
	}

}
