package io.github.tldnr1.limitedgoods.purchase.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PurchaseProperties.class)
class PurchaseConfig {

	/**
	 * 앱 안의 재처리 스케줄러(ADR-0002). 테스트는 끄고 {@link PurchaseExpiry#run()}을 직접 부른다.
	 */
	@Configuration(proxyBeanMethods = false)
	@EnableScheduling
	@ConditionalOnProperty(name = "limited-goods.reprocessing.enabled", havingValue = "true", matchIfMissing = true)
	static class ExpiryScheduler {

		private final PurchaseExpiry expiry;

		ExpiryScheduler(PurchaseExpiry expiry) {
			this.expiry = expiry;
		}

		@Scheduled(fixedDelayString = "${limited-goods.reprocessing.interval}")
		void expire() {
			expiry.run();
		}

	}

}
