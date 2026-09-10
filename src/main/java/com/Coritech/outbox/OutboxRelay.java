package com.Coritech.outbox;

import java.time.Instant;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Stands in for a real message-broker publisher (Kafka/RabbitMQ/etc.):
 * periodically polls for outbox rows that haven't been "published" yet and
 * logs them as if they'd been handed off, then marks them published. Kept
 * self-contained (no broker infrastructure) since the point being
 * demonstrated is the outbox pattern's durability guarantee — the row is
 * committed atomically with the business change in
 * {@code BookingServiceImpl}, so relaying it is just a matter of *when*,
 * never *whether*.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(value = "app.outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {

	private final OutboxEventRepository outboxEventRepository;

	@Scheduled(fixedDelay = 5000)
	@Transactional
	public void relayPendingEvents() {
		List<OutboxEvent> pending = outboxEventRepository.findTop50ByPublishedAtIsNullOrderByIdAsc();
		if (pending.isEmpty()) {
			return;
		}

		for (OutboxEvent event : pending) {
			log.info("Publishing outbox event {} ({}) for {}#{}: {}", event.getId(), event.getEventType(),
					event.getAggregateType(), event.getAggregateId(), event.getPayload());
			event.setPublishedAt(Instant.now());
		}
		outboxEventRepository.saveAll(pending);
	}

}
