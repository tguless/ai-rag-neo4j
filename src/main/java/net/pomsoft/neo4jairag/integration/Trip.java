package net.pomsoft.neo4jairag.integration;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Set;

@Data
public class Trip {

    @EqualsAndHashCode.Include
    private String tripId;
    private String serviceId;
    public Set<Route> routes;
    public Set<Stoptime> stoptimes;
    public Set<CalendarDate> calendarDates;
}
