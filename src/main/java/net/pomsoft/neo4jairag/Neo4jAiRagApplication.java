package net.pomsoft.neo4jairag;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class Neo4jAiRagApplication {

	public static void main(String[] args) {
		SpringApplication.run(Neo4jAiRagApplication.class, args);
	}

}
