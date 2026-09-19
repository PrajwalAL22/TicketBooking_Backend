package com.Coritech.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.Coritech.beans.Journey;
import com.Coritech.beans.User;
import com.Coritech.exception.UsernameAlreadyExistsException;
import com.Coritech.repository.JourneyRepository;
import com.Coritech.repository.UserRepository;
import com.Coritech.service.impl.UserServiceImpl;

import lombok.RequiredArgsConstructor;

/**
 * Slower integration tier, run against a real Postgres (via Testcontainers)
 * rather than H2's Postgres-compatibility mode: proves Flyway's migrations
 * (V1/V2) actually apply to the real target database, that optimistic
 * locking behaves correctly under the real dialect, and that the V2 unique
 * constraint on username actually rejects a real duplicate at the database
 * level (not just via the application's own findByUsername check).
 */
@SpringBootTest
@Testcontainers
@RequiredArgsConstructor(onConstructor_ = @Autowired)
class BookingServicePostgresIntegrationTest {

	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

	private final BookingService bookingService;
	private final JourneyRepository journeyRepository;
	private final UserRepository userRepository;
	private final UserServiceImpl userService;

	@Test
	void concurrentBookingsNeverOversellSeatsOnRealPostgres() throws InterruptedException {
		Journey journey = new Journey();
		journey.setCityName("PostgresIT-" + System.nanoTime());
		journey.setTicketsRemaining(5);
		journey = journeyRepository.save(journey);
		Long journeyId = journey.getJourneyId();

		User user = new User();
		user.setUsername("pg-it-user-" + System.nanoTime());
		user.setPassword("pass");
		user = userRepository.save(user);
		Long userId = user.getUserId();

		int threadCount = 10;
		ExecutorService executor = Executors.newFixedThreadPool(threadCount);
		CountDownLatch ready = new CountDownLatch(threadCount);
		CountDownLatch start = new CountDownLatch(1);
		AtomicInteger successCount = new AtomicInteger();

		for (int i = 0; i < threadCount; i++) {
			executor.submit(() -> {
				ready.countDown();
				try {
					start.await();
					bookingService.bookTickets(userId, journeyId, 1, null);
					successCount.incrementAndGet();
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				} catch (Exception expectedWhenSoldOutOrContended) {
					// Sold out, or optimistic-lock retries exhausted under contention:
					// acceptable for a losing thread, as long as no thread oversells.
				}
			});
		}

		ready.await();
		start.countDown();
		executor.shutdown();
		assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS));

		Journey updated = journeyRepository.findById(journeyId).orElseThrow();
		assertEquals(5, successCount.get(), "exactly the available seats should have been booked, never more");
		assertEquals(0, updated.getTicketsRemaining());
	}

	@Test
	void duplicateUsernameRegistrationIsRejectedByRealDbConstraint() {
		String username = "pg-it-dup-user-" + System.nanoTime();

		userService.register(username, "password123");
		assertThrows(UsernameAlreadyExistsException.class,
				() -> userService.register(username, "differentPassword456"),
				"second registration with the same username must be rejected by uk_user_username");
	}

}
