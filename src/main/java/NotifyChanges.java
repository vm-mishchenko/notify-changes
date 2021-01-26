import com.google.gson.Gson;
import com.sendgrid.Method;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.commons.cli.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class NotifyChanges {
    public static void main(String[] args) throws Exception {
        // create Options object
        Options options = new Options();

        // add a option
        Option input = new Option(null, "configPath", true, "Path to configuration file.");
        input.setRequired(true);
        options.addOption(input);

        Option emailAddress = new Option(null, "email", true, "Email that serves as FROM and TO.");
        emailAddress.setRequired(true);
        options.addOption(emailAddress);

        Option sendGridAPI = new Option(null, "sendGridAPI", true, "SendGrid API key.");
        sendGridAPI.setRequired(true);
        options.addOption(sendGridAPI);

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

        SendGridEmailService sendGridEmailService = new SendGridEmailService(cmd.getOptionValue("sendGridAPI"));

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
                        String newResult = doc.select(configuration.cssQuery).get(0).text();

                        SimpleDateFormat formatter = new SimpleDateFormat("dd MMM HH:mm");
                        if (doc.select(configuration.cssQuery).get(0).text().equals(configuration.expectedResult)) {
                            System.out.format("%s NO CHANGES: '%s' = %s \n", formatter.format(new Date()), configuration.name, configuration.expectedResult);
                        } else {
                            sendGridEmailService.email(cmd.getOptionValue("email"), cmd.getOptionValue("email"), "CHANGED: " + configuration.name, newResult).send();
                            System.out.format("%s !CHANGES: %s = %s \n", formatter.format(new Date()), configuration.name, newResult);
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

    static class SendGridEmailService {
        SendGrid sendGrid;

        public SendGridEmailService(String sendGridAPI) {
            this.sendGrid = new SendGrid(sendGridAPI);
        }

        NotificationEmail email(String fromString, String toString, String subject, String body) {
            return new NotificationEmail(this.sendGrid, fromString, toString, subject, body);
        }
    }

    static class NotificationEmail {
        private SendGrid sendGrid;
        private Mail mail;

        public NotificationEmail(SendGrid sendGrid, String fromString, String toString, String subject, String body) {
            this.sendGrid = sendGrid;

            Email from = new Email(fromString);
            Email to = new Email(toString);
            Content content = new Content("text/plain", body);
            this.mail = new Mail(from, subject, to, content);
        }

        void send() {
            com.sendgrid.Request request = new com.sendgrid.Request();

            try {
                request.setMethod(Method.POST);
                request.setEndpoint("mail/send");
                request.setBody(this.mail.build());
                this.sendGrid.api(request);
            } catch (IOException ex) {
                System.out.println(ex);
            }
        }
    }

    static class SiteParseConfiguration {
        String url;
        String name;
        String cssQuery;
        String expectedResult;
    }
}
