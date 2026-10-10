package io.github.tldnr1.limitedgoods;

import org.springframework.boot.SpringApplication;

public class TestLimitedGoodsApplication {

	public static void main(String[] args) {
		SpringApplication.from(LimitedGoodsApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
