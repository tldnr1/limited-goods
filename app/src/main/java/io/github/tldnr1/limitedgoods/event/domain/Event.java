package io.github.tldnr1.limitedgoods.event.domain;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/**
 * 미리 등록된 행사. 앱은 읽기만 한다.
 */
@Entity
public class Event {

	@Id
	private Long id;

	private Instant saleStartsAt;

	protected Event() {
	}

	public Long getId() {
		return id;
	}

	public Instant getSaleStartsAt() {
		return saleStartsAt;
	}

}
