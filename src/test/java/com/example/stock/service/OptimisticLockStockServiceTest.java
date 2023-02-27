package com.example.stock.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.stock.domain.Stock;
import com.example.stock.repository.StockRepository;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class OptimisticLockStockServiceTest {

  @Autowired
  private OptimisticLockStockService stockService;

  @Autowired
  private StockRepository stockRepository;

  @BeforeEach
  void setUp() {
    Stock stock = new Stock(1L, 100L);
    stockRepository.saveAndFlush(stock);
  }

  @AfterEach
  void tearDown() {
    stockRepository.deleteAll();
  }

  @Test
  void decreaseStock() {
    stockService.decrease(1L, 1L);
    Stock stock = stockRepository.findById(1L).orElseThrow();
    assertEquals(99, stock.getQuantity());
  }

  @Test
  void 동시에_100개의_요청() throws InterruptedException {
    int threadCount = 100;
    ExecutorService executorService = Executors.newFixedThreadPool(32);
    CountDownLatch latch = new CountDownLatch(threadCount);
    for (int i = 0; i < threadCount; i++) {
      executorService.submit(() -> {
        try {
          stockService.decrease(1L, 1L);
        } finally {
          latch.countDown();
        }
      });
    }

    latch.await();

    Stock stock = stockRepository.findById(1L).orElseThrow();

    assertEquals(0, stock.getQuantity());
  }
}