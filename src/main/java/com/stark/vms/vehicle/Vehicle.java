package com.stark.vms.vehicle;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "vehicles")
public class Vehicle {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "vehicle_number", nullable = false, length = 64, unique = true)
	private String vehicleNumber;

	@Column(name = "company_name", nullable = false, length = 128)
	private String companyName;

	@Column(name = "vehicle_type", nullable = false, length = 64)
	private String vehicleType;

	@Column(nullable = false, length = 64)
	private String brand;

	@Column(nullable = false, length = 64)
	private String model;

	@Convert(converter = VehicleStatusConverter.class)
	@Column(nullable = false, length = 32)
	private VehicleStatus status;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Vehicle() {
	}

	public Vehicle(String vehicleNumber, String companyName, String vehicleType,
			String brand, String model, VehicleStatus status) {
		this.vehicleNumber = vehicleNumber;
		updateDetails(companyName, vehicleType, brand, model, status);
	}

	public void updateDetails(String companyName, String vehicleType, String brand,
			String model, VehicleStatus status) {
		this.companyName = companyName;
		this.vehicleType = vehicleType;
		this.brand = brand;
		this.model = model;
		this.status = status;
	}

	public void rename(String vehicleNumber) {
		this.vehicleNumber = vehicleNumber;
	}

	@PrePersist
	void initializeTimestamps() {
		Instant now = Instant.now();
		if (createdAt == null) {
			createdAt = now;
		}
		updatedAt = now;
	}

	@PreUpdate
	void updateTimestamp() {
		updatedAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public String getVehicleNumber() {
		return vehicleNumber;
	}

	public String getCompanyName() {
		return companyName;
	}

	public String getVehicleType() {
		return vehicleType;
	}

	public String getBrand() {
		return brand;
	}

	public String getModel() {
		return model;
	}

	public VehicleStatus getStatus() {
		return status;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
