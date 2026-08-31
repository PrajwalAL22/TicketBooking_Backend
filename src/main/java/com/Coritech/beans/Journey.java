package com.Coritech.beans;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "journeys")
public class Journey {

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private Long journeyId;

	private String cityName;

	private int ticketsRemaining;

	@Version
	private Integer version;

	public Long getJourneyId() {
		return journeyId;
	}

	public void setJourneyId(Long journeyId) {
		this.journeyId = journeyId;
	}

	public String getCityName() {
		return cityName;
	}

	public void setCityName(String cityName) {
		this.cityName = cityName;
	}

	public int getTicketsRemaining() {
		return ticketsRemaining;
	}

	public void setTicketsRemaining(int ticketsRemaining) {
		this.ticketsRemaining = ticketsRemaining;
	}

	public Integer getVersion() {
		return version;
	}

	public void setVersion(Integer version) {
		this.version = version;
	}

	public Journey(Long journeyId, String cityName, int ticketsRemaining) {
		super();
		this.journeyId = journeyId;
		this.cityName = cityName;
		this.ticketsRemaining = ticketsRemaining;
	}

	public Journey() {
		super();
	}

	@Override
	public String toString() {
		return "Journeys [journeyId=" + journeyId + ", cityName=" + cityName + ", ticketsRemaining=" + ticketsRemaining
				+ "]";
	}

}
