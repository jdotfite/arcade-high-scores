#!/bin/bash

# Check if Java is installed
if ! command -v java &> /dev/null; then
    echo "Java not found. Installing..."
    sudo apt update
    sudo apt install -y default-jdk
else
    echo "Java is already installed."
fi

# Copy service file
sudo cp arcade-scores.service /etc/systemd/system/

# Make start script executable
chmod +x start_arcade.sh

# Enable and start the service
sudo systemctl daemon-reload
sudo systemctl enable arcade-scores
sudo systemctl start arcade-scores

echo "Installation complete!"
echo "Please edit settings.json with your GitHub details, then restart the service:"
echo "sudo systemctl restart arcade-scores"

