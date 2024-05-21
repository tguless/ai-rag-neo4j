package net.pomsoft.neo4jairag.integration;

import lombok.Data;

@Data
public class PlanResult {
    String arrivalTime;
    String departureTime;
    int stopSequence;
    String stopName;
    String tripId;

}
