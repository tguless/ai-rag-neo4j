package net.pomsoft.neo4jairag.integration;

import lombok.Data;

import java.util.Set;

@Data
public class Route {

    private String shortName;
    private String longName;
    private String routeId;
    private long type;
    private Set<Trip> trips;

}
