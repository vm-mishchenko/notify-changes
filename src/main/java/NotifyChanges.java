import com.google.gson.Gson;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.File;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import org.apache.commons.cli.*;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * TODOS:
 * 1. move configuration to external JSON file
 * 2. notify via Email and Telegram about changes
 */
public class NotifyChanges {
    public static void main(String[] args) throws Exception {
        // create Options object
        Options options = new Options();

        // add a option
        Option input = new Option(null, "configPath", true, "Path to configuration file.");
        input.setRequired(true);
        options.addOption(input);

        // Create a command line parser
        CommandLineParser parser = new DefaultParser();

        // parse the options passed as command line arguments
        CommandLine cmd = parser.parse(options, args);

        File configFile = new File(cmd.getOptionValue("configPath"));

        if (!configFile.exists()) {
            throw new Error("Config file does not exists. Set the right path to 'configPath' argument. Current value of path: " + cmd.getOptionValue("configPath"));
        }

        // create Gson instance
        Gson gson = new Gson();

        // create a reader
        Reader reader = Files.newBufferedReader(Paths.get(cmd.getOptionValue("configPath")));

        // convert JSON array to list of books
        List<SiteParseConfiguration> siteParseConfigurations = Arrays.asList(gson.fromJson(reader, SiteParseConfiguration[].class));

        // close reader
        reader.close();

        OkHttpClient client = new OkHttpClient();

        List<Runnable> calls = siteParseConfigurations.stream().map((configuration) -> {
            return new Runnable() {
                @Override
                public void run() {
                    Request request = new Request.Builder()
                            .url(configuration.url)
                            .build();

                    try {
                        Response response = client.newCall(request).execute();
                        Document doc = Jsoup.parse(response.body().string());
                        String result = doc.select(configuration.cssQuery).get(0).text();

                        if (doc.select(configuration.cssQuery).get(0).text().equals(configuration.expectedResult)) {
                            System.out.format("NO CHANGES: '%s' = %s \n", configuration.name, configuration.expectedResult);
                        } else {
                            System.out.format("!CHANGES: %s = %s \n", result, configuration.expectedResult);
                        }
                    } catch (Exception e) {
                        System.out.println("Error!");
                        System.out.println(e);
                    }
                }
            };
        }).collect(Collectors.toList());

        ExecutorService executorService = Executors.newFixedThreadPool(2);

        for (Runnable call : calls) {
            executorService.execute(call);
        }

        executorService.shutdown();
    }

    static class SiteParseConfiguration {
        String url;
        String name;
        String cssQuery;
        String expectedResult;
    }
}
