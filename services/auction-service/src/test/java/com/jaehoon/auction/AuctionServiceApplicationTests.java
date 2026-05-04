package com.jaehoon.auction;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.jaehoon.auction.repository.AuctionRepository;
import com.jaehoon.auction.repository.OutboxEventRepository;

// "test" 프로파일이 DataSource/JPA를 제외하므로 JPA 레포지토리를 Mock으로 대체해 컨텍스트 로딩 검증
@SpringBootTest
@ActiveProfiles("test")
class AuctionServiceApplicationTests {

	@MockitoBean
	AuctionRepository auctionRepository;

	@MockitoBean
	OutboxEventRepository outboxEventRepository;

	@Test
	void contextLoads() {
	}

}
