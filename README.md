Setup:

```shell
# Build the project
mvn clean package

# Open cron tab
crontab -e

# Add entry: Run every hour
0 * * * * java -jar /home/user/programs/notify-changes/notify-changes-1.0-SNAPSHOT-jar-with-dependencies.jar --email=example@gmail.com --sendGridAPI=API --configPath=/home/user/tool-configuration/notify-changes/check-sites.json >> /home/user/tool-configuration/notify-changes/logs.txt
```
