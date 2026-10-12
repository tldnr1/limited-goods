package io.github.tldnr1.limitedgoods.event.application;

import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import io.github.tldnr1.limitedgoods.common.ApiException;
import io.github.tldnr1.limitedgoods.event.domain.Event;
import io.github.tldnr1.limitedgoods.event.domain.EventRepository;
import io.github.tldnr1.limitedgoods.event.domain.Item;
import io.github.tldnr1.limitedgoods.event.domain.ItemRepository;

@Service
public class SaleInfoQuery {

	private final EventRepository events;
	private final ItemRepository items;

	SaleInfoQuery(EventRepository events, ItemRepository items) {
		this.events = events;
		this.items = items;
	}

	public SaleInfo get(long eventId) {
		Event event = events.findById(eventId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "EVENT_NOT_FOUND", "행사가 없습니다."));
		Map<Long, SaleInfo.ItemTerms> terms = items.findByEventId(eventId)
			.stream()
			.collect(Collectors.toMap(Item::getId, i -> new SaleInfo.ItemTerms(i.getPerBuyerLimit(), i.getPrice())));
		return new SaleInfo(event.getId(), event.getSaleStartsAt(), terms);
	}

}
