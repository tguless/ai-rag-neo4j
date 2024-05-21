package net.pomsoft.neo4jairag.integration;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.ArrayList;


@FeignClient(value = "BackendService", url = "${pomsoft.net.neo4jsvc.url}")
public interface Neo4jServiceCalls {
    @PostMapping(value="/customrest/planTripNoTransfer", consumes = "application/json")
    public ArrayList <ArrayList <ArrayList<Stoptime>>> planTripNoTransfer( @RequestBody TripPlan plan);
}
