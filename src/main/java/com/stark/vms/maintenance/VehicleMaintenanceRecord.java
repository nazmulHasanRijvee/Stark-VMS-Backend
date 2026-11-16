package com.stark.vms.maintenance;

import com.stark.vms.vehicle.Vehicle;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "vehicle_maintenance_records")
public class VehicleMaintenanceRecord {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "vehicle_id", nullable = false)
	private Vehicle vehicle;

	@Column(name = "maintenance_date", nullable = false)
	private LocalDate maintenanceDate;

	@Column(name = "maintenance_type", nullable = false, length = 128)
	private String maintenanceType;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal cost;

	@Column(nullable = false, length = 128)
	private String workshop;

	@Column(length = 2000)
	private String notes;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected VehicleMaintenanceRecord() {
	}

	public VehicleMaintenanceRecord(Vehicle vehicle, LocalDate maintenanceDate, String maintenanceType,
			BigDecimal cost, String workshop, String notes, Instant createdAt) {
		this.vehicle = vehicle;
		this.maintenanceDate = maintenanceDate;
		this.maintenanceType = maintenanceType;
		this.cost = cost;
		this.workshop = workshop;
		this.notes = notes;
		this.createdAt = createdAt;
	}

	public Long getId() { return id; }
	public Vehicle getVehicle() { return vehicle; }
	public LocalDate getMaintenanceDate() { return maintenanceDate; }
	public String getMaintenanceType() { return maintenanceType; }
	public BigDecimal getCost() { return cost; }
	public String getWorkshop() { return workshop; }
	public String getNotes() { return notes; }
	public Instant getCreatedAt() { return createdAt; }
}
