package de.familyhub.rewards;

import com.fasterxml.jackson.annotation.JsonProperty;

// pending: wartet auf die Eltern; approved: genehmigt; rejected: abgelehnt, Punkte zurückgebucht
public enum RedemptionStatus {
    @JsonProperty("pending") PENDING,
    @JsonProperty("approved") APPROVED,
    @JsonProperty("rejected") REJECTED
}
