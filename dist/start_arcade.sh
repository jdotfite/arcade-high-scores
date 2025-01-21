#!/bin/bash
cd /home/pi/arcade-scores
echo "Current directory: $(pwd)" > arcade.log
echo "Java version:" >> arcade.log
java -version >> arcade.log 2>&1
echo "Listing directory contents:" >> arcade.log
ls -l >> arcade.log
echo "Running ArcadeScoreManager:" >> arcade.log
java -cp .:org.json-1.6-20240205.jar ArcadeScoreManager >> arcade.log 2>&1
