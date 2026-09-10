package com.Coritech.outbox;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Transactional outbox row: written in the same DB transaction as the
 * business change it describes, so the event can never be silently lost
 * even if the message broker (or, here, the relay that stands in for one)
 * is temporarily unavailable. {@link com.Coritech.outbox.OutboxRelay} polls
 * for unpublished rows and marks them once delivered.
 */
@Entity
@Table(name = "outbox_event")
public class OutboxEvent {

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private Long id;

	private String aggregateType;

	private Long aggregateId;

	private String eventType;

	@Column(columnDefinition = "text")
	private String payload;

	private Instant createdAt;

	private Instant publishedAt;

	protected OutboxEvent() {
		super();
	}

	public OutboxEvent(String aggregateType, Long aggregateId, String eventType, String payload) {
		this.aggregateType = aggregateType;
		this.aggregateId = aggregateId;
		this.eventType = eventType;
		this.payload = payload;
		this.createdAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public String getAggregateType() {
		return aggregateType;
	}

	public Long getAggregateId() {
		return aggregateId;
	}

	public String getEventType() {
		return eventType;
	}

	public String getPayload() {
		return payload;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getPublishedAt() {
		return publishedAt;
	}

	public void setPublishedAt(Instant publishedAt) {
		this.publishedAt = publishedAt;
	}

}
