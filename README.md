MAME High Score Web Display From Multiple Cabinets and Locations

Purpose:
Automatically track and display high scores from multiple MAME arcade cabinets on a web page.

Components:
1. Java Programs:
   - ArcadeSetup.java: A menu-driven configuration utility for managing player names, GitHub settings, game lists, and system parameters for the arcade high score tracking system.
   - ArcadeScoreManager.java: Main program that monitors and uploads scores
   - Dependencies: org.json-1.6-20240205.jar, hi2txt.jar

2. Score Files:
   - thom-scores.json and justin-scores.json
   - Stored on GitHub Pages repository: jdotfite/arcade-high-scores
   - Also backed up to GitHub Gist

3. Web Display:
   - Hosted on GitHub Pages: https://jdotfite.github.io/arcade-high-scores/
   - Auto-refreshes every 15 seconds
   - Shows ranked scores for both players
   - Tracks three games: MS. PAC-MAN, MS. PAC-MAN FAST, PAC-MAN

Technical Details:
- Uses Java WatchService to monitor .hi files
- 30-second debounce on file changes
- GitHub API for file updates
- Configurable per-player setup

Current Status:
System is operational with separate score tracking for each cabinet and combined display on the webpage.

Required Setup:
- GitHub token with gist and repo permissions
- Java environment with required JARs
- MAME cabinet with .hi files in ../hiscore directory
