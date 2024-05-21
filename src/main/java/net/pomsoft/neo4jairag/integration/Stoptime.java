package net.pomsoft.neo4jairag.integration;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Set;

@Data
public class Stoptime {
    private Long id;
    private String arrivalTime;
    private int stopSequence;
    private int departureTimeInt;
    private int arrivalTimeInt;
    private String departureTime;
    private Set<Stop> stops;
    public Set<Trip> trips;
    public Set<Stoptime> precedesTime;

}