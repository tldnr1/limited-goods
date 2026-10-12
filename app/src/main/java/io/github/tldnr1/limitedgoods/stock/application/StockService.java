package io.github.tldnr1.limitedgoods.stock.application;

import java.util.Map;
import java.util.SortedMap;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.github.tldnr1.limitedgoods.common.ApiException;
import io.github.tldnr1.limitedgoods.stock.domain.StockRepository;

/**
 * 잡기와 반환. 부르는 쪽 트랜잭션 안에서만 실행하며, 수량 모듈은 구매를 모른다.
 * 여러 품목은 품목 ID 순서로 갱신해 교착을 피한다.
 */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class StockService {

	private final StockRepository stocks;

	StockService(StockRepository stocks) {
		this.stocks = stocks;
	}

	/**
	 * 모든 품목을 잡거나, 하나라도 모자라면 품절로 던진다. 던지면 부르는 쪽 트랜잭션이 롤백되어 앞서 잡은 품목도 돌아간다.
	 */
	public void take(SortedMap<Long, Integer> quantities) {
		for (Map.Entry<Long, Integer> e : quantities.entrySet()) {
			if (stocks.take(e.getKey(), e.getValue()) == 0) {
				throw new ApiException(HttpStatus.CONFLICT, "SOLD_OUT", "남은 수량이 없습니다.");
			}
		}
	}

	public void giveBack(SortedMap<Long, Integer> quantities) {
		quantities.forEach(stocks::giveBack);
	}

}
