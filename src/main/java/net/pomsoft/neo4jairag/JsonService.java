package net.pomsoft.neo4jairag;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class JsonService {

    private final ObjectMapper objectMapper;

    @Autowired
    public JsonService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String convertToJson(Object data) throws Exception {
        return objectMapper.writeValueAsString(data);
    }

    public <T> T convertFromJson(String json, Class<T> clazz) throws Exception {
        return objectMapper.readValue(json, clazz);
    }
}
