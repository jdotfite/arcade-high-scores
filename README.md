MAME High Score Web Display From Multiple Cabinets and Locations

# Arcade High Score Tracker
Automatically track and upload high scores from arcade games running on a Raspberry Pi with RetroPie.

## Features
- Monitors high score files for supported arcade games
- Automatically uploads new high scores to a GitHub repository
- Supports multiple players
- Easy to configure and set up
- Web interface for displaying scores

## Prerequisites
- Raspberry Pi running RetroPie
- Internet connection
- GitHub account

## Quick Installation
1. Clone this repository:

git clone https://github.com/yourusername/arcade-high-scores.git
cd arcade-high-scores/dist

2. Run the installation script:
./install.sh

3. Edit the settings file with your GitHub details:
nano /home/pi/arcade-high-scores/settings.json

4. Restart the service:
sudo systemctl restart arcade-scores


## Manual Installation
1. Ensure Java is installed:
sudo apt update
sudo apt install default-jdk


2. Copy files to the installation directory:
sudo mkdir -p /home/pi/arcade-high-scores
sudo cp -R * /home/pi/arcade-high-scores/

3. Copy the service file:
sudo cp arcade-scores.service /etc/systemd/system/

4. Make the start script executable:
chmod +x /home/pi/arcade-high-scores/start_arcade.sh

5. Enable and start the service:
sudo systemctl daemon-reload
sudo systemctl enable arcade-scores
sudo systemctl start arcade-scores

## Configuration
Edit `/home/pi/arcade-high-scores/settings.json` to configure:
- GitHub token, repository details, and Gist ID
- `hi_score_dir` path (default: "/home/pi/RetroPie/roms/arcade/fbneo")
- Monitored games

Restart the service after changes:
sudo systemctl restart arcade-scores


## Web Interface
To view the high scores:
1. Open a web browser
2. Navigate to `https://username.github.io/web/arcade-high-scores/`

## Troubleshooting
- Check service status: 
sudo systemctl status arcade-scores

- View logs: 
cat /home/pi/arcade-high-scores/arcade.log
