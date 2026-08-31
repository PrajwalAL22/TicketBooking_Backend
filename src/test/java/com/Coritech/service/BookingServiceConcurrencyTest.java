package com.Coritech.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.Coritech.beans.Booking;
import com.Coritech.beans.Journey;
import com.Coritech.beans.User;
import com.Coritech.exception.ForbiddenException;
import com.Coritech.repository.BookingRepository;
import com.Coritech.repository.JourneyRepository;
import com.Coritech.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@SpringBootTest
@RequiredArgsConstructor(onConstructor_ = @Autowired)
class BookingServiceConcurrencyTest {

	private final BookingService bookingService;
	private final JourneyRepository journeyRepository;
	private final UserRepository userRepository;
	private final BookingRepository bookingRepository;

	private Long journeyId;
	private Long userId;

	@BeforeEach
	void setUp() {
		Journey journey = new Journey();
		journey.setCityName("ConcurrencyTestCity-" + System.nanoTime());
		journey.setTicketsRemaining(5);
		journey = journeyRepository.save(journey);
		journeyId = journey.getJourneyId();

		User user = new User();
		user.setUsername("concurrency-user-" + System.nanoTime());
		user.setPassword("pass");
		user = userRepository.save(user);
		userId = user.getUserId();
	}

	@Test
	void concurrentBookingsNeverOversellSeats() throws InterruptedException {
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
					// SeatsUnavailableException once seats run out, or BookingConflictException
					// if retries are exhausted under contention: both are acceptable outcomes
					// for a losing thread, as long as no thread oversells.
				}
			});
		}

		ready.await();
		start.countDown();
		executor.shutdown();
		assertEquals(true, executor.awaitTermination(10, TimeUnit.SECONDS));

		Journey updated = journeyRepository.findById(journeyId).orElseThrow();
		assertEquals(5, successCount.get(), "exactly the available seats should have been booked, never more");
		assertEquals(0, updated.getTicketsRemaining());
	}

	@Test
	void duplicateIdempotencyKeyDoesNotDoubleBook() {
		String key = "idem-key-" + System.nanoTime();

		Booking first = bookingService.bookTickets(userId, journeyId, 2, key);
		Booking second = bookingService.bookTickets(userId, journeyId, 2, key);

		assertEquals(first.getBookingId(), second.getBookingId());

		Journey updated = journeyRepository.findById(journeyId).orElseThrow();
		assertEquals(3, updated.getTicketsRemaining(), "seats should only be decremented once");

		List<Booking> bookings = bookingRepository.findByUserUserId(userId);
		assertEquals(1, bookings.size());
	}

	@Test
	void cancellingTwiceIsIdempotentAndOnlyRefundsOnce() {
		Booking booking = bookingService.bookTickets(userId, journeyId, 2, null);

		bookingService.cancelTickets(userId, booking.getBookingId());
		bookingService.cancelTickets(userId, booking.getBookingId());

		Journey updated = journeyRepository.findById(journeyId).orElseThrow();
		assertEquals(5, updated.getTicketsRemaining(), "cancelling an already-cancelled booking must not double-refund");
	}

	@Test
	void cannotCancelSomeoneElsesBooking() {
		Booking booking = bookingService.bookTickets(userId, journeyId, 2, null);

		User otherUser = new User();
		otherUser.setUsername("other-user-" + System.nanoTime());
		otherUser.setPassword("pass");
		otherUser = userRepository.save(otherUser);

		Long otherUserId = otherUser.getUserId();
		assertThrows(ForbiddenException.class,
				() -> bookingService.cancelTickets(otherUserId, booking.getBookingId()));

		Journey updated = journeyRepository.findById(journeyId).orElseThrow();
		assertEquals(3, updated.getTicketsRemaining(), "a rejected cancel attempt must not refund seats");
	}
}
