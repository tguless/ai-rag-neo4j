package net.pomsoft.neo4jairag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.pomsoft.neo4jairag.integration.Neo4jServiceCalls;
import net.pomsoft.neo4jairag.integration.PlanResult;
import net.pomsoft.neo4jairag.integration.TripPlan;
import org.json.JSONObject;
import org.mozilla.javascript.IdScriptableObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;

@RestController
public class ChatbotController {

    Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    ChatClient chatClient;

    @Autowired
    Neo4jServiceCalls neo4jServiceCalls;

    @Autowired
    JsonService jsonService;

    @PostMapping("/chatbot")
    public String chatbot(@RequestBody String message) throws Exception {

        SimpleDateFormat dateFormat = new SimpleDateFormat("EEE MMM dd yyyy HH:mm:ss 'GMT'Z (z)");

        String datesRequiredMessage = """
        Today is %s. 
        
        Given the prompt: 
        "%s"
        
        I need to convert any relevant dates in the prompt (there may be multiple) to Javascript Date format.        
        To do it, give me the JS commands to execute without using variables in the format:
          
        {
             '{datetyperequested}' : '{js command to run to set a js variable to the date value as Date object}',
             '{2nd datetyperequested}' : '{js command to run to set a js variable to the date value as Date object}'
        }
        
        Make sure to only return properly formatted json and nothing else in your response. Make sure all the
        required dates are included in the JSON object.
        """.formatted(dateFormat.format(new Date()), message);
        
        logger.info("Sending Prompt to OpenAI: \n" + datesRequiredMessage);

        String datesJson = chatClient.call(datesRequiredMessage);
        datesJson = getTextBetweenMarkers(datesJson).get(0);
        JSONObject datesJsonObject = new JSONObject(datesJson);
        logger.info("Response Returned from OpenAI: \n" + datesJsonObject.toString(4));

        evaluateAndReplace(datesJsonObject);
        logger.info("Response Run Through JS Evaluator: \n" + datesJsonObject.toString(4));

        String chatprompt = """
        Given the following API
            @RequestMapping(value = "/planTrip", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
            @ResponseBody
            public ArrayList<ArrayList<ArrayList<Stoptime>>> planTrip(@RequestBody TripPlan plan)
            
        Where TripPlan has the following structure:
        
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
            
        and where the relevant dates in the format the API expects are as follows:
        
        %s
        
        and all the time fields must have the format '06:46:00'
        and if a destination time window is not provided, either or both destArrivalTimeLow and destArrivalTimeHigh should be set to an empty string
        similarly if an origin station departure time window is not provided, either or both origArrivalTimeLow and origArrivalTimeHigh should be set to an empty string
        for the origStation and destStation only assign a string with the city name and nothing else
        can you give me the JSON payload to pass to this API to get the trip plan for the following
        user request "%s"?
        """.formatted(datesJsonObject.toString(4), message);

        logger.info("Sending Prompt to OpenAI: \n"+ chatprompt);

        String neo4jApiPrompt = chatClient.call(chatprompt);

        if (neo4jApiPrompt.contains("```")) {
            neo4jApiPrompt =  getTextBetweenMarkers(neo4jApiPrompt).get(0);
        }


        logger.info("Response Returned from OpenAI: \n" + neo4jApiPrompt);

        ArrayList <ArrayList <ArrayList<PlanResult>>> neo4jApiResponse = neo4jServiceCalls.planTripNoTransfer(jsonService.convertFromJson(neo4jApiPrompt, TripPlan.class));

        String neo4jResponse = jsonService.convertToJson(neo4jApiResponse);

        String nlResponseToUserPrompt = "Convert this JSON payload of travel itinary to natural language " +  neo4jResponse;

        logger.info("Sending Prompt to OpenAI: \n" + nlResponseToUserPrompt);

        String nlResponseToUserResponse =   chatClient.call(nlResponseToUserPrompt);

        logger.info("Response Returned from OpenAI: \n" + nlResponseToUserResponse);

        return nlResponseToUserResponse;
    }

    private void evaluateAndReplace(JSONObject json)  {

        json.keySet().forEach(key -> {
            Context ctx = Context.enter();
            Scriptable scope = ctx.initStandardObjects();
            Object result = ctx.evaluateString(scope, json.getString(key), "script", 1, null);

            SimpleDateFormat gtfsDateFormat = new SimpleDateFormat("yyyyMMdd");

            // Assume the result is a Date object, format it to a string
            try {
                if (result instanceof Date) {
                    json.put(key, gtfsDateFormat.format(result));
                } else if (result instanceof Double) {
                    json.put(key,  gtfsDateFormat.format(new Date((((Double) result)).longValue())));
                } else if (result instanceof IdScriptableObject) {
                    SimpleDateFormat dateFormat = new SimpleDateFormat("EEE MMM dd yyyy HH:mm:ss 'GMT'Z (z)");
                    // Set the time zone to handle the GMT offset
                    dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
                    Date date = dateFormat.parse(ctx.toString(result));
                    json.put(key, gtfsDateFormat.format(date));
                } else {
                    try {
                        SimpleDateFormat dateFormat = new SimpleDateFormat("EEE MMM dd HH:mm:ss z yyyy");
                        Date date = dateFormat.parse(ctx.toString(result));
                        json.put(key, gtfsDateFormat.format(date));
                    } catch (ParseException e)  {
                        // Format "2024-05-01T13:00:00.765Z"
                        Instant instant = Instant.parse(ctx.toString(result));
                        Date date = Date.from(instant);
                        json.put(key, gtfsDateFormat.format(date));
                    }

                }
            }catch (ParseException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public static String removeJsonPrefix(String input, String formatText) {
        // Check if the input starts with "json {"
        String prefix = formatText + "\n{";
        if (input.startsWith(prefix)) {
            // Remove the prefix
            return input.substring(prefix.length() - 1); // Keep the leading '{'
        }

        // Return the input unchanged if it doesn't start with the prefix
        return input;
    }

    public static String removeJsonPrefix(String input){
        input = removeJsonPrefix(input, "json");
        input = removeJsonPrefix(input, "JSON");
        input = removeJsonPrefix(input, "javascript");
        input = removeJsonPrefix(input, "JAVASCRIPT");
        return input;
    }

    public  List<String> getTextBetweenMarkers(String input) {
        List<String> results = new ArrayList<>();

        // Regular expression to match and capture content between two "```" markers
        Pattern pattern = Pattern.compile("```(.*?)```", Pattern.DOTALL); // DOTALL makes '.' match newlines
        Matcher matcher = pattern.matcher(input);

        while (matcher.find()) {
            // Add the captured group to the results list
            results.add(removeJsonPrefix(matcher.group(1)));
        }

        if (results.size() == 0) {
            results.add(input);
        }

        return results;
    }

    public void handleGPT4Response(String gpt4Response) {
        try {
            // Parse GPT-4 response into JSON
            ObjectMapper mapper = new ObjectMapper();
            JsonNode responseNode = mapper.readTree(gpt4Response);

            String endpoint = responseNode.get("endpoint").asText();
            JsonNode params = responseNode.get("params");

            // Build query parameters string
            StringBuilder queryParams = new StringBuilder("?");
            params.fields().forEachRemaining(entry -> {
                queryParams.append(entry.getKey()).append("=").append(entry.getValue().asText()).append("&");
            });

            // Create full URL with parameters
            URL url = new URL(endpoint + queryParams.toString());

            // Make API call
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            // Read response
            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String inputLine;
            StringBuilder content = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                content.append(inputLine);
            }

            // Close connections
            in.close();
            conn.disconnect();

            // Handle GTFS API response (e.g., display it or parse further)
            System.out.println(content.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
