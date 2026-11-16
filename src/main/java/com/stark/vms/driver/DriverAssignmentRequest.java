package com.stark.vms.driver;

import jakarta.validation.constraints.NotNull;

public record DriverAssignmentRequest(@NotNull Long driverId) {
}
