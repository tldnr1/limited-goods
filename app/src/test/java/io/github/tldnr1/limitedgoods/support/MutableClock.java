package io.github.tldnr1.limitedgoods.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 테스트에서 시각을 조절하는 시계. 여러 스레드가 함께 읽는다.
 */
public class MutableClock extends Clock {

	private final AtomicReference<Instant> now;

	public MutableClock(Instant now) {
		this.now = new AtomicReference<>(now);
	}

	public void set(Instant instant) {
		now.set(instant);
	}

	public void advance(Duration duration) {
		now.updateAndGet(i -> i.plus(duration));
	}

	@Override
	public Instant instant() {
		return now.get();
	}

	@Override
	public ZoneId getZone() {
		return ZoneOffset.UTC;
	}

	@Override
	public Clock withZone(ZoneId zone) {
		throw new UnsupportedOperationException();
	}

}
