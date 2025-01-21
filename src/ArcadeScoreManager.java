import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.net.http.*;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.json.*;

public class ArcadeScoreManager {
    private static final String SETTINGS_FILE = "settings.json";
    private JSONObject settings;
    private String[] supportedGames;
    private String playerName;
    private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private ScheduledFuture<?> scheduledTask;

    public static void main(String[] args) {
        System.out.println("ArcadeScoreManager started.");
        
        // Check if settings exists
        File settingsFile = new File(SETTINGS_FILE);
        if (!settingsFile.exists()) {
            System.out.println("Settings file not found: " + SETTINGS_FILE);
            return;
        }
        
        ArcadeScoreManager manager = new ArcadeScoreManager();
        if (manager.loadSettings()) {
            System.out.println("Initialization complete. Starting file watch...");
            manager.watchForChanges();
        }
    }

    private boolean loadSettings() {
        try {
            // Load settings file
            String content = new String(Files.readAllBytes(Paths.get(SETTINGS_FILE)));
            settings = new JSONObject(content);
            
            // Get player name
            playerName = settings.getString("player_name");
            
            // Get list of supported games
            JSONObject games = settings.getJSONObject("games");
            supportedGames = JSONObject.getNames(games);
            
            // Verify directories and files exist
            String hiScoreDir = settings.getJSONObject("system").getString("hi_score_dir");
            String hi2txtJar = settings.getJSONObject("system").getString("hi2txt_jar");
            
            File directory = new File(hiScoreDir);
            if (!directory.exists() || !directory.isDirectory()) {
                System.out.println("ERROR: The specified hi_score_dir does not exist or is not a directory.");
                System.out.println("Please check the path: " + hiScoreDir);
                return false;
            }
            
            File jarFile = new File(hi2txtJar);
            if (!jarFile.exists()) {
                System.out.println("ERROR: hi2txt.jar not found at the specified path.");
                System.out.println("Please check the path: " + hi2txtJar);
                return false;
            }

            System.out.println("Loaded configuration for player: " + playerName);
            System.out.println("Watching for changes in: " + hiScoreDir);
            System.out.println("Using hi2txt jar: " + hi2txtJar);
            System.out.println("Monitoring games: " + String.join(", ", supportedGames));
            
            return true;
        } catch (Exception e) {
            System.out.println("Error loading settings: " + e.getMessage());
            return false;
        }
    }

    private void watchForChanges() {
        try {
            String hiScoreDir = settings.getJSONObject("system").getString("hi_score_dir");
            WatchService watchService = FileSystems.getDefault().newWatchService();
            Path path = Paths.get(hiScoreDir);
            path.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);

            while (true) {
                WatchKey key = watchService.take();
                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.ENTRY_MODIFY) {
                        String fileName = event.context().toString();
                        if (fileName.endsWith(".hi")) {
                            long debounceDelay = settings.getJSONObject("system").getLong("debounce_delay");
                            System.out.println("Change detected in: " + fileName + " - waiting " + (debounceDelay/1000) + " seconds for changes to settle...");
                            scheduleProcessing(fileName);
                        }
                    }
                }
                key.reset();
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            scheduler.shutdown();
        }
    }

    private synchronized void scheduleProcessing(String changedFile) {
        if (scheduledTask != null && !scheduledTask.isDone()) {
            scheduledTask.cancel(false);
        }
        
        long debounceDelay = settings.getJSONObject("system").getLong("debounce_delay");
        scheduledTask = scheduler.schedule(() -> {
            System.out.println("No changes detected for " + (debounceDelay/1000) + " seconds, processing files...");
            collectAndUploadScores(changedFile);
        }, debounceDelay, TimeUnit.MILLISECONDS);
    }

    private JSONObject getLocalScores() {
        try {
            File localFile = new File("local_scores.json");
            if (localFile.exists()) {
                String content = new String(Files.readAllBytes(Paths.get("local_scores.json")));
                return new JSONObject(content);
            }
        } catch (Exception e) {
            System.out.println("Could not read local scores: " + e.getMessage());
        }
        
        JSONObject defaultScores = new JSONObject();
        defaultScores.put("player", playerName);
        defaultScores.put("arcade_scores", new JSONObject());
        defaultScores.put("last_updated", new Date().toString());
        return defaultScores;
    }

    private void saveLocalScores(JSONObject scores) {
        try (FileWriter file = new FileWriter("local_scores.json")) {
            file.write(scores.toString(2));
        } catch (Exception e) {
            System.out.println("Error saving local scores: " + e.getMessage());
        }
    }

    private void collectAndUploadScores(String changedFile) {
        try {
            String gameId = changedFile.substring(0, changedFile.length() - 3);

            if (!Arrays.asList(supportedGames).contains(gameId)) {
                System.out.println("Skipping unsupported game: " + gameId);
                return;
            }

            String hiScoreDir = settings.getJSONObject("system").getString("hi_score_dir");
            String hi2txtJar = settings.getJSONObject("system").getString("hi2txt_jar");
            String hiFile = hiScoreDir + "/" + changedFile;
            System.out.println("Processing file: " + hiFile);

            JSONObject newScore = null;
            try {
                ProcessBuilder pb = new ProcessBuilder("java", "-jar", hi2txtJar, "-ra", hiFile);
                Process process = pb.start();
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                boolean headerSkipped = false;

                while ((line = reader.readLine()) != null) {
                    System.out.println("hi2txt output: " + line);
                    if (!headerSkipped) {
                        headerSkipped = true;
                        continue;
                    }
                    newScore = parseScore(line);
                    break;
                }
            } catch (Exception e) {
                System.out.println("Error processing " + gameId + ": " + e.getMessage());
                return;
            }

            if (newScore == null) {
                System.out.println("No score data found for " + gameId);
                return;
            }

            JSONObject localScores = getLocalScores();
            JSONObject localArcadeScores = localScores.optJSONObject("arcade_scores");
            if (localArcadeScores == null) {
                localArcadeScores = new JSONObject();
                localScores.put("arcade_scores", localArcadeScores);
            }

            JSONObject existingScore = localArcadeScores.optJSONObject(gameId);
            int newScoreValue = newScore.getJSONArray("high_scores").getJSONObject(0).getInt("score");
            int oldScoreValue = 0;

            if (existingScore != null && existingScore.has("high_scores")) {
                oldScoreValue = existingScore.getJSONArray("high_scores").getJSONObject(0).getInt("score");
            }

            if (newScoreValue > oldScoreValue) {
                System.out.println(String.format("New high score for %s! %d > %d", gameId, newScoreValue, oldScoreValue));

                // Add display name from settings
                JSONObject games = settings.getJSONObject("games");
                if (games.has(gameId)) {
                    newScore.put("display_name", games.getJSONObject(gameId).getString("display_name"));
                }

                localArcadeScores.put(gameId, newScore);
                localScores.put("last_updated", new Date().toString());
                saveLocalScores(localScores);

                JSONObject allScores = fetchExistingScores();
                if (allScores == null) {
                    allScores = new JSONObject();
                    allScores.put("players", new JSONObject());
                    allScores.put("last_updated", new Date().toString());
                }

                JSONObject players = allScores.optJSONObject("players");
                if (players == null) {
                    players = new JSONObject();
                    allScores.put("players", players);
                }

                players.put(playerName, localScores);
                allScores.put("last_updated", new Date().toString());

                uploadToGithub(allScores.toString());
            } else {
                System.out.println(String.format("Score not higher than existing (%d <= %d), skipping update", 
                    newScoreValue, oldScoreValue));
            }

        } catch (Exception e) {
            System.out.println("Error in collectAndUploadScores: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private JSONObject parseScore(String line) {
        System.out.println("Parsing score line: " + line);
        String[] parts = line.split("\\|");
        System.out.println("Split parts: " + Arrays.toString(parts));

        JSONObject scoreObj = new JSONObject();
        JSONArray highScores = new JSONArray();
        JSONObject highScore = new JSONObject();

        try {
            if (parts.length >= 2) {
                highScore.put("rank", Integer.parseInt(parts[0].trim()));
                highScore.put("score", Integer.parseInt(parts[1].trim()));
                
                String scoreString = (parts.length >= 3) ? parts[2].trim() : parts[1].trim();
                highScore.put("score_string", scoreString);
                
                highScore.put("date", new Date().toString());
            } else {
                System.out.println("Warning: Invalid score format: " + line);
                highScore.put("rank", 1);
                highScore.put("score", 0);
                highScore.put("score_string", "0");
                highScore.put("date", new Date().toString());
            }
        } catch (NumberFormatException e) {
            System.out.println("Warning: Unable to parse numbers in score: " + Arrays.toString(parts));
            highScore.put("rank", 1);
            highScore.put("score", 0);
            highScore.put("score_string", "0");
            highScore.put("date", new Date().toString());
        }

        highScores.put(highScore);
        scoreObj.put("high_scores", highScores);
        return scoreObj;
    }

    private JSONObject fetchExistingScores() {
        try {
            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
            
            String repoOwner = settings.getJSONObject("github").getString("repo_owner");
            String repoName = settings.getJSONObject("github").getString("repo_name");
            
            String url = String.format("https://raw.githubusercontent.com/%s/%s/main/scores.json",
                repoOwner, repoName);
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                return new JSONObject(response.body());
            }
        } catch (Exception e) {
            System.out.println("No existing scores file found: " + e.getMessage());
        }
        return null;
    }

    private void uploadToGithub(String content) {
        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

        int maxRetries = 3;
        int retryCount = 0;

        while (retryCount < maxRetries) {
            try {
                String repoOwner = settings.getJSONObject("github").getString("repo_owner");
                String repoName = settings.getJSONObject("github").getString("repo_name");
                String repoUrl = String.format("https://api.github.com/repos/%s/%s/contents/scores.json",
                    repoOwner, repoName);
                
                System.out.println("Uploading to GitHub Pages at: " + repoUrl);

                JSONObject fileContent = new JSONObject();
                fileContent.put("message", "Update arcade scores");
                fileContent.put("content", Base64.getEncoder().encodeToString(content.getBytes()));

                String token = settings.getJSONObject("github").getString("token");
                HttpRequest getRequest = HttpRequest.newBuilder()
                    .uri(URI.create(repoUrl))
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/vnd.github.v3+json")
                    .GET()
                    .build();

                HttpResponse<String> getResponse = client.send(getRequest, HttpResponse.BodyHandlers.ofString());
                
                if (getResponse.statusCode() == 200) {
                    JSONObject existing = new JSONObject(getResponse.body());
                    fileContent.put("sha", existing.getString("sha"));
                    System.out.println("Found existing file, including SHA for update");
                }

                HttpRequest uploadRequest = HttpRequest.newBuilder()
                    .uri(URI.create(repoUrl))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/vnd.github.v3+json")
                    .PUT(HttpRequest.BodyPublishers.ofString(fileContent.toString()))
                    .build();

                HttpResponse<String> response = client.send(uploadRequest, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 200 || response.statusCode() == 201) {
                    System.out.println("Successfully uploaded to GitHub Pages!");
                    updateGist(content);
                    return;
                } else if (response.statusCode() == 409) {
                    System.out.println("Conflict detected, retrying... (Attempt " + (retryCount + 1) + " of " + maxRetries + ")");
					retryCount++;
                    if (retryCount < maxRetries) {
                        Thread.sleep(1000);
                        continue;
                    }
                }
                System.out.println("Error uploading to GitHub Pages: " + response.statusCode());
                System.out.println("Response body: " + response.body());
                break;
            } catch (Exception e) {
                System.out.println("Exception when uploading to GitHub: " + e.getMessage());
                e.printStackTrace();
                retryCount++;
                if (retryCount < maxRetries) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    continue;
                }
                break;
            }
        }
    }

    private void updateGist(String content) {
        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

        JSONObject gistContent = new JSONObject();
        JSONObject files = new JSONObject();
        JSONObject scoresFile = new JSONObject();
        scoresFile.put("content", content);
        files.put("scores.json", scoresFile);
        gistContent.put("files", files);
        gistContent.put("description", "Arcade High Scores Backup");

        try {
            String gistId = settings.getJSONObject("github").getString("gist_id");
            String token = settings.getJSONObject("github").getString("token");
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.github.com/gists/" + gistId))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .header("Accept", "application/vnd.github.v3+json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(gistContent.toString()))
                .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                System.out.println("Successfully updated backup Gist!");
            } else {
                System.out.println("Error updating Gist: " + response.statusCode());
                System.out.println("Response body: " + response.body());
            }
        } catch (Exception e) {
            System.out.println("Exception when updating Gist: " + e.getMessage());
            e.printStackTrace();
        }
    }
}