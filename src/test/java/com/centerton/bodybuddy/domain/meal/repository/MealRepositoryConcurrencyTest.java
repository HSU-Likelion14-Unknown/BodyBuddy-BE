package com.centerton.bodybuddy.domain.meal.repository;

import com.centerton.bodybuddy.domain.auth.util.AccessKeyGenerator;
import com.centerton.bodybuddy.domain.meal.entity.Meal;
import com.centerton.bodybuddy.domain.meal.entity.MealItem;
import com.centerton.bodybuddy.domain.meal.entity.MealItemSource;
import com.centerton.bodybuddy.domain.user.entity.User;
import com.centerton.bodybuddy.domain.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:meal-lock-test;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MealRepositoryConcurrencyTest {

    private static final String USER_ID = "concurrency-user";
    private static final String RAW_ACCESS_KEY = "concurrency-access-key";

    @Autowired private UserRepository userRepository;
    @Autowired private MealRepository mealRepository;
    @Autowired private MealItemRepository mealItemRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private TransactionTemplate transactionTemplate;
    private String mealId;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.executeWithoutResult(status -> {
            User user = userRepository.save(User.builder()
                    .userId(USER_ID)
                    .accessKeyHash(AccessKeyGenerator.hash(RAW_ACCESS_KEY))
                    .onboardingCompletedAt(LocalDateTime.now())
                    .build());
            Meal meal = Meal.createText(user, "초기 식사", LocalDateTime.now());
            meal.markReviewRequired();
            meal.confirm(LocalDateTime.now());
            mealId = mealRepository.saveAndFlush(meal).getMealId();
        });
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void serializesConcurrentMealItemReplacementsWithPessimisticLock() throws Exception {
        CountDownLatch firstLocked = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondAttempting = new CountDownLatch(1);
        CountDownLatch secondLocked = new CountDownLatch(1);
        AtomicInteger activeWriters = new AtomicInteger();
        AtomicInteger maximumActiveWriters = new AtomicInteger();

        Future<?> first = executor.submit(() -> replaceItems(
                "첫 번째 수정",
                () -> { },
                () -> {
                    firstLocked.countDown();
                    await(releaseFirst);
                },
                activeWriters,
                maximumActiveWriters
        ));
        assertThat(firstLocked.await(5, TimeUnit.SECONDS)).isTrue();

        Future<?> second = executor.submit(() -> replaceItems(
                "두 번째 수정",
                secondAttempting::countDown,
                secondLocked::countDown,
                activeWriters,
                maximumActiveWriters
        ));
        assertThat(secondAttempting.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(secondLocked.await(300, TimeUnit.MILLISECONDS)).isFalse();

        releaseFirst.countDown();
        first.get(5, TimeUnit.SECONDS);
        second.get(5, TimeUnit.SECONDS);

        assertThat(secondLocked.getCount()).isZero();
        assertThat(maximumActiveWriters).hasValue(1);
        List<MealItem> savedItems = mealItemRepository
                .findAllByMealMealIdOrderBySortOrderAsc(mealId);
        assertThat(savedItems)
                .extracting(MealItem::getFoodName)
                .containsExactly("두 번째 수정");
    }

    private void replaceItems(String foodName, Runnable beforeLock, Runnable afterLock,
                              AtomicInteger activeWriters,
                              AtomicInteger maximumActiveWriters) {
        transactionTemplate.executeWithoutResult(status -> {
            beforeLock.run();
            Meal meal = mealRepository.findOwnedByIdForUpdate(mealId, USER_ID).orElseThrow();
            int active = activeWriters.incrementAndGet();
            maximumActiveWriters.accumulateAndGet(active, Math::max);
            try {
                afterLock.run();
                mealItemRepository.deleteAllByMealMealId(mealId);
                mealItemRepository.flush();
                mealItemRepository.saveAndFlush(MealItem.builder()
                        .mealItemId(java.util.UUID.randomUUID().toString())
                        .meal(meal)
                        .foodName(foodName)
                        .amount(BigDecimal.ONE)
                        .amountUnit("인분")
                        .source(MealItemSource.USER_EDITED)
                        .sortOrder(0)
                        .build());
            } finally {
                activeWriters.decrementAndGet();
            }
        });
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out while coordinating concurrent transactions");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while coordinating concurrent transactions", exception);
        }
    }
}
