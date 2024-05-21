package net.pomsoft.neo4jairag.integration;

import lombok.Data;

import java.util.Set;

@Data
public class CalendarDate {

    private Long id;
    private String serviceId;
    private String date;
    private String exceptionType;
    public Set<Trip> trips;

}
