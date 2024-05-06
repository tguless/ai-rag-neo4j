package net.pomsoft.neo4jairag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.json.JSONObject;
import org.mozilla.javascript.IdScriptableObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

    @GetMapping("/chatbot")
    public String chatbot(@RequestParam(value = "message", defaultValue = "I am going to leave from Woodridge train station in NJ to go to Hoboken NJ between 7 and 8 am today  what are my train options? ") String message) {

        SimpleDateFormat dateFormat = new SimpleDateFormat("EEE MMM dd yyyy HH:mm:ss 'GMT'Z (z)");

        String datesRequiredMessage = "Today is " + dateFormat.format(new Date()) +". Given the prompt \"" + message + "\" \n" +
                "I need to convert any relevant dates in the prompt (there may be multiple) to Javascript Date format \n" +
                ", to do it, give me the JS commands to execute without using variables in the format:  {\n" +
                "     '{datetyperequested}' : '{js command to run to set a js variable to the date value as Date object}', \n" +
                "     '{2nd datetyperequested}' : '{js command to run to set a js variable to the date value as Date object}' \n" +
                "}\n" +
                "make sure to only return properly formatted json and nothing else in your response. Make sure all the \n" +
                "required dates are included in the JSON object.";

        logger.info("Sending Prompt to OpenAI: \n" + datesRequiredMessage);

        String datesJson = chatClient.call(datesRequiredMessage);
        datesJson = getTextBetweenMarkers(datesJson).get(0);
        JSONObject datesJsonObject = new JSONObject(datesJson);
        logger.info("Response Returned from OpenAI: \n" + datesJsonObject.toString(4));
        /*
        Example output from prompt   "I am going to leave from Westwood train station in NJ to go to point pleasant NJ after 9am tomorrow"
        {
            'currentDate': 'new Date()',
            'tomorrowDate': 'new Date(new Date().setDate(new Date().getDate() + 1))'
        }
        */
        evaluateAndReplace(datesJsonObject);
        logger.info("Response Run Through JS Evaluator: \n" + datesJsonObject.toString(4));
        /*
            Example output from evaluate call:
             {
                "departureDate":"20240501",
                "arrivalDate":"20240501"
             }
         */

        String chatprompt =
                "Given the following API \n" +
                        "    @RequestMapping(  value = \"/planTrip\", " +
                        "                      method = RequestMethod.POST, " +
                        "                      produces = MediaType.APPLICATION_JSON_VALUE)\n" +
                        "    @ResponseBody\n" +
                        "    public ArrayList <ArrayList <ArrayList<Stoptime>>>  planTrip( @RequestBody TripPlan plan)\n " +
                        "where TripPlan has the following structure \n   " +
                        "@Data\n" +
                        "public class TripPlan {\n" +
                        "    private String travelDate;\n" +
                        "    private String origStation;\n" +
                        "    private String origArrivalTimeLow;\n" +
                        "    private String origArrivalTimeHigh;\n" +
                        "    private String destStation;\n" +
                        "    private String destArrivalTimeLow;\n" +
                        "    private String destArrivalTimeHigh;\n" +
                        "}\n" +
                        "and where where the relevant dates in the format the API expects are as follows \n " +
                        datesJsonObject.toString(4) + "\n" +
                        "and all the time fields must have the format '06:46:00' \n" +
                        "can you give me the JSON payload to pass to this API to get the trip plan for the following \n" +
                        "user request \"" + message + "\"?";

        logger.info("Sending Prompt to OpenAI: \n"+ chatprompt);

        String neo4jApiPrompt = chatClient.call(chatprompt);

        if (neo4jApiPrompt.contains("```")) {
            neo4jApiPrompt =  getTextBetweenMarkers(neo4jApiPrompt).get(0);
        }

        String neo4jApiRespons = handleGPT4ResponseTest(neo4jApiPrompt);

        String nlResponseToUserPrompt = "Convert this JSON payload of travel itinary to natural language " +    neo4jApiRespons;

        String nlResponseToUserResponse =   chatClient.call(nlResponseToUserPrompt);

        logger.info("Response Returned from OpenAI: \n" + nlResponseToUserResponse);

        return nlResponseToUserResponse;
    }

    public String  handleGPT4ResponseTest(String finalResult ) {
        return "[\n" +
                "    [\n" +
                "        {\n" +
                "            \"arrivalTime\": \"07:43:00\",\n" +
                "            \"departureTime\": \"07:43:00\",\n" +
                "            \"stopName\": \"WOOD-RIDGE\",\n" +
                "            \"stopSequence\": 15,\n" +
                "            \"tripId\": \"2815\"\n" +
                "        },\n" +
                "        {\n" +
                "            \"arrivalTime\": \"07:54:00\",\n" +
                "            \"departureTime\": \"07:54:00\",\n" +
                "            \"stopName\": \"FRANK R LAUTENBERG SECAUCUS LOWER LEVEL\",\n" +
                "            \"stopSequence\": 16,\n" +
                "            \"tripId\": \"2815\"\n" +
                "        },\n" +
                "        {\n" +
                "            \"arrivalTime\": \"08:05:00\",\n" +
                "            \"departureTime\": \"08:05:00\",\n" +
                "            \"stopName\": \"HOBOKEN\",\n" +
                "            \"stopSequence\": 17,\n" +
                "            \"tripId\": \"2815\"\n" +
                "        }\n" +
                "    ],\n" +
                "    [\n" +
                "        {\n" +
                "            \"arrivalTime\": \"07:27:00\",\n" +
                "            \"departureTime\": \"07:27:00\",\n" +
                "            \"stopName\": \"WOOD-RIDGE\",\n" +
                "            \"stopSequence\": 16,\n" +
                "            \"tripId\": \"2821\"\n" +
                "        },\n" +
                "        {\n" +
                "            \"arrivalTime\": \"07:38:00\",\n" +
                "            \"departureTime\": \"07:38:00\",\n" +
                "            \"stopName\": \"FRANK R LAUTENBERG SECAUCUS LOWER LEVEL\",\n" +
                "            \"stopSequence\": 17,\n" +
                "            \"tripId\": \"2821\"\n" +
                "        },\n" +
                "        {\n" +
                "            \"arrivalTime\": \"07:49:00\",\n" +
                "            \"departureTime\": \"07:49:00\",\n" +
                "            \"stopName\": \"HOBOKEN\",\n" +
                "            \"stopSequence\": 18,\n" +
                "            \"tripId\": \"2821\"\n" +
                "        }\n" +
                "    ]\n" +
                "]";
    }

    private void evaluateAndReplace(JSONObject json)  {
        ScriptEngineManager manager = new ScriptEngineManager();
        ScriptEngine engine = manager.getEngineByName("nashorn");

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
