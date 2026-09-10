package com.Coritech.beans;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "booking")
public class Booking extends Auditable {

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private Long bookingId;

	@ManyToOne
	private User user;

	@ManyToOne
	private Journey journey;

	private int noOfTicketsBooked;

	private boolean isCancelled = false;

	@Column(unique = true, length = 100)
	private String idempotencyKey;

	public String getIdempotencyKey() {
		return idempotencyKey;
	}

	public void setIdempotencyKey(String idempotencyKey) {
		this.idempotencyKey = idempotencyKey;
	}

	public boolean isCancelled() {
		return isCancelled;
	}

	public void setCancelled(boolean isCancelled) {
		this.isCancelled = isCancelled;
	}

	public Long getBookingId() {
		return bookingId;
	}

	public void setBookingId(Long bookingId) {
		this.bookingId = bookingId;
	}

	public User getUser() {
		return user;
	}

	public void setUser(User user) {
		this.user = user;
	}

	public Journey getJourney() {
		return journey;
	}

	public void setJourney(Journey journey) {
		this.journey = journey;
	}

	public int getNoOfTicketsBooked() {
		return noOfTicketsBooked;
	}

	public void setNoOfTicketsBooked(int noOfTicketsBooked) {
		this.noOfTicketsBooked = noOfTicketsBooked;
	}

	public Booking(Long bookingId, User user, Journey journey, int noOfTicketsBooked) {
		super();
		this.bookingId = bookingId;
		this.user = user;
		this.journey = journey;
		this.noOfTicketsBooked = noOfTicketsBooked;
	}

	public Booking() {
		super();
	}

	@Override
	public String toString() {
		return "Booking [bookingId=" + bookingId + ", user=" + user + ", journey=" + journey + ", noOfTicketsBooked="
				+ noOfTicketsBooked + "]";
	}

}
