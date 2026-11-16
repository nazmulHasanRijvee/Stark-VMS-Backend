package com.stark.vms.driver;

import com.stark.vms.vehicle.Vehicle;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;

@Entity
@Table(name = "drivers")
public class Driver {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 120)
	private String name;

	@Column(nullable = false, length = 32)
	private String phone;

	@Column(name = "license_number", nullable = false, length = 64, unique = true)
	private String licenseNumber;

	@Column(name = "license_expiry", nullable = false)
	private LocalDate licenseExpiry;

	@Convert(converter = DriverStatusConverter.class)
	@Column(nullable = false, length = 16)
	private DriverStatus status;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@OneToOne(mappedBy = "currentDriver", fetch = FetchType.LAZY)
	private Vehicle assignedVehicle;

	protected Driver() {
	}

	public Driver(String name, String phone, String licenseNumber, LocalDate licenseExpiry, DriverStatus status) {
		this.name = name;
		this.phone = phone;
		this.licenseNumber = normalizeLicenseNumber(licenseNumber);
		this.licenseExpiry = licenseExpiry;
		this.status = status;
	}

	public void updateDetails(String name, String phone, String licenseNumber,
			LocalDate licenseExpiry, DriverStatus status) {
		this.name = name;
		this.phone = phone;
		this.licenseNumber = normalizeLicenseNumber(licenseNumber);
		this.licenseExpiry = licenseExpiry;
		this.status = status;
	}

	@PrePersist
	void initializeTimestamps() {
		Instant now = Instant.now();
		if (createdAt == null) createdAt = now;
		updatedAt = now;
	}

	@PreUpdate
	void updateTimestamp() {
		updatedAt = Instant.now();
	}

	public static String normalizeLicenseNumber(String licenseNumber) {
		return licenseNumber == null ? null : licenseNumber.strip().toUpperCase(Locale.ROOT);
	}

	public Long getId() { return id; }
	public String getName() { return name; }
	public String getPhone() { return phone; }
	public String getLicenseNumber() { return licenseNumber; }
	public LocalDate getLicenseExpiry() { return licenseExpiry; }
	public DriverStatus getStatus() { return status; }
	public Instant getCreatedAt() { return createdAt; }
	public Instant getUpdatedAt() { return updatedAt; }
	public Vehicle getAssignedVehicle() { return assignedVehicle; }

	public void setAssignedVehicle(Vehicle assignedVehicle) {
		this.assignedVehicle = assignedVehicle;
	}
}
