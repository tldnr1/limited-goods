package io.github.tldnr1.limitedgoods.event.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/**
 * 미리 등록된 품목. 앱은 읽기만 한다.
 */
@Entity
public class Item {

	@Id
	private Long id;

	private Long eventId;

	private int saleQuantity;

	private int perBuyerLimit;

	private long price;

	protected Item() {
	}

	public Long getId() {
		return id;
	}

	public Long getEventId() {
		return eventId;
	}

	public int getSaleQuantity() {
		return saleQuantity;
	}

	public int getPerBuyerLimit() {
		return perBuyerLimit;
	}

	public long getPrice() {
		return price;
	}

}
