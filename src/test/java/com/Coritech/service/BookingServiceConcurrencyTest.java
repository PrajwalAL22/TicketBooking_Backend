package com.Coritech.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import com.Coritech.beans.Booking;
import com.Coritech.beans.Journey;
import com.Coritech.beans.User;
import com.Coritech.exception.ForbiddenException;
import com.Coritech.outbox.OutboxEvent;
import com.Coritech.outbox.OutboxEventRepository;
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
	private final OutboxEventRepository outboxEventRepository;

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
	void partialCancelRefundsOnlyThoseTicketsAndKeepsBookingActive() {
		Booking booking = bookingService.bookTickets(userId, journeyId, 4, null);

		bookingService.cancelTickets(userId, booking.getBookingId(), 1);

		Booking after = bookingRepository.findById(booking.getBookingId()).orElseThrow();
		assertEquals(4, after.getNoOfTicketsBooked(), "original booked count is preserved as history");
		assertEquals(1, after.getNoOfTicketsCancelled());
		assertEquals(3, after.getNoOfTicketsActive());
		assertFalse(after.isCancelled(), "booking is only fully cancelled once no active tickets remain");
		assertEquals(2, journeyRepository.findById(journeyId).orElseThrow().getTicketsRemaining(),
				"5 - 4 booked + 1 refunded");
	}

	@Test
	void partialCancelsAccumulateAndFinalOneMarksBookingCancelled() {
		Booking booking = bookingService.bookTickets(userId, journeyId, 3, null);

		bookingService.cancelTickets(userId, booking.getBookingId(), 1);
		bookingService.cancelTickets(userId, booking.getBookingId(), 2);

		Booking after = bookingRepository.findById(booking.getBookingId()).orElseThrow();
		assertEquals(3, after.getNoOfTicketsCancelled());
		assertTrue(after.isCancelled());
		assertEquals(5, journeyRepository.findById(journeyId).orElseThrow().getTicketsRemaining());

		// A full cancel on an already fully-cancelled booking stays a no-op.
		bookingService.cancelTickets(userId, booking.getBookingId());
		assertEquals(5, journeyRepository.findById(journeyId).orElseThrow().getTicketsRemaining());
	}

	@Test
	void fullCancelAfterPartialCancelRefundsOnlyTheRemainingTickets() {
		Booking booking = bookingService.bookTickets(userId, journeyId, 4, null);

		bookingService.cancelTickets(userId, booking.getBookingId(), 1);
		bookingService.cancelTickets(userId, booking.getBookingId());

		Booking after = bookingRepository.findById(booking.getBookingId()).orElseThrow();
		assertEquals(4, after.getNoOfTicketsCancelled());
		assertTrue(after.isCancelled());
		assertEquals(5, journeyRepository.findById(journeyId).orElseThrow().getTicketsRemaining(),
				"the 1 already-refunded ticket must not be refunded a second time");
	}

	@Test
	void cancellingMoreThanActiveTicketsIsRejectedWithoutRefunding() {
		Booking booking = bookingService.bookTickets(userId, journeyId, 2, null);
		bookingService.cancelTickets(userId, booking.getBookingId(), 1);

		assertThrows(IllegalArgumentException.class,
				() -> bookingService.cancelTickets(userId, booking.getBookingId(), 2));
		assertThrows(IllegalArgumentException.class,
				() -> bookingService.cancelTickets(userId, booking.getBookingId(), 0));

		Booking after = bookingRepository.findById(booking.getBookingId()).orElseThrow();
		assertEquals(1, after.getNoOfTicketsCancelled());
		assertEquals(4, journeyRepository.findById(journeyId).orElseThrow().getTicketsRemaining());
	}

	@Test
	void partialCancelRecordsOutboxEventWithCancelledCount() {
		Booking booking = bookingService.bookTickets(userId, journeyId, 3, null);
		bookingService.cancelTickets(userId, booking.getBookingId(), 2);

		OutboxEvent cancelEvent = outboxEventRepository.findAll().stream()
				.filter(e -> booking.getBookingId().equals(e.getAggregateId()))
				.filter(e -> "BookingCancelled".equals(e.getEventType()))
				.findFirst().orElseThrow();

		assertTrue(cancelEvent.getPayload().contains("\"ticketsCancelledInThisEvent\":2"), cancelEvent.getPayload());
		assertTrue(cancelEvent.getPayload().contains("\"cancelled\":false"), cancelEvent.getPayload());
	}

	@Test
	void concurrentPartialCancelsNeverRefundMoreThanBooked() throws InterruptedException {
		Booking booking = bookingService.bookTickets(userId, journeyId, 5, null);
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
					bookingService.cancelTickets(userId, booking.getBookingId(), 1);
					successCount.incrementAndGet();
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				} catch (Exception expectedOnceNothingIsLeftToCancel) {
					// IllegalArgumentException once all 5 tickets are cancelled.
				}
			});
		}

		ready.await();
		start.countDown();
		executor.shutdown();
		assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));

		assertEquals(5, successCount.get(), "exactly the 5 booked tickets can be cancelled, never more");
		Booking after = bookingRepository.findById(booking.getBookingId()).orElseThrow();
		assertEquals(5, after.getNoOfTicketsCancelled());
		assertEquals(5, journeyRepository.findById(journeyId).orElseThrow().getTicketsRemaining(),
				"journey seats end at exactly the original 5: no over-refund");
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

	@Test
	void bookingAndCancellingRecordsOutboxEvents() {
		Booking booking = bookingService.bookTickets(userId, journeyId, 2, null);
		bookingService.cancelTickets(userId, booking.getBookingId());

		List<OutboxEvent> events = outboxEventRepository.findAll().stream()
				.filter(e -> booking.getBookingId().equals(e.getAggregateId()))
				.toList();

		assertTrue(events.stream().anyMatch(e -> "BookingConfirmed".equals(e.getEventType())),
				"a BookingConfirmed outbox event should be recorded in the same transaction as the booking");
		assertTrue(events.stream().anyMatch(e -> "BookingCancelled".equals(e.getEventType())),
				"a BookingCancelled outbox event should be recorded in the same transaction as the cancellation");
		assertTrue(events.stream().allMatch(e -> e.getPublishedAt() == null),
				"events aren't published until the relay picks them up (disabled in tests)");
	}

	@Test
	void getTicketsBookedByUserForEachCityIsPaginated() {
		bookingService.bookTickets(userId, journeyId, 1, null);
		bookingService.bookTickets(userId, journeyId, 1, null);
		bookingService.bookTickets(userId, journeyId, 1, null);

		Page<Booking> firstPage = bookingService.getTicketsBookedByUserForEachCity(userId, PageRequest.of(0, 2));

		assertEquals(2, firstPage.getContent().size());
		assertEquals(3, firstPage.getTotalElements());
		assertEquals(2, firstPage.getTotalPages());

		Page<Booking> secondPage = bookingService.getTicketsBookedByUserForEachCity(userId, PageRequest.of(1, 2));
		assertEquals(1, secondPage.getContent().size());
	}
}
