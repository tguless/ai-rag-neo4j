package net.pomsoft.neo4jairag.integration;

import lombok.Data;

/**
 * Created by tgulesserian on 5/20/17.
 */
@Data
public class TripPlan {
    private String travelDate;
    private String origStation;
    private String origArrivalTimeLow;
    private String origArrivalTimeHigh;
    private String destStation;
    private String destArrivalTimeLow;
    private String destArrivalTimeHigh;
}
