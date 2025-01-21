let countdownValue = 15;

function updateCountdown() {
    document.querySelector('.countdown-text').textContent = countdownValue;
    document.querySelector('.countdown-circle .progress').style.strokeDashoffset = 
        (251.2 * (15 - countdownValue) / 15);
    countdownValue--;
    if (countdownValue < 0) {
        countdownValue = 15;
        loadScores();
    }
}

async function loadScores() {
    try {
        const timestamp = new Date().getTime();
        
        // Update this line to fetch from the correct location
        const response = await fetch(`scores.json?nocache=${timestamp}`);
        
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        
        const data = await response.json();
        console.log('Fetched scores at:', new Date().toLocaleString(), data);

        if (!data.players || Object.keys(data.players).length === 0) {
            document.getElementById('scores').innerHTML = 'Waiting for first scores to be uploaded...';
            return;
        }

        const scoresDiv = document.getElementById('scores');
        let html = '';

        // Get all unique games from all players
        const allGames = new Set();
        Object.values(data.players).forEach(player => {
            if (player.arcade_scores) {
                Object.keys(player.arcade_scores).forEach(game => allGames.add(game));
            }
        });

        // Convert game IDs to display names
        const gameDisplayNames = {
            'mspacman': 'MS. PAC-MAN',
            'mspacmnf': 'MS. PAC-MAN SPEEDUP',
            'pacman': 'PAC-MAN'
            // Add more game display names as needed
        };

        // Process each game
        for (const game of allGames) {
            // Get scores for this game from all players
            const gameScores = Object.entries(data.players)
                .map(([name, playerData]) => ({
                    name: playerData.player,
                    score: playerData.arcade_scores?.[game]?.high_scores?.[0]?.score || 0,
                    date: playerData.arcade_scores?.[game]?.high_scores?.[0]?.date
                }))
                .sort((a, b) => b.score - a.score);

            // Ensure we always have at least two entries for display
            while (gameScores.length < 2) {
                gameScores.push({ name: '---', score: 0, date: null });
            }

            const displayName = gameDisplayNames[game] || game.toUpperCase();

            html += `
                <div class="game-section">
                    <div class="game-title">${displayName}</div>
                    <table class="score-table">
                        <tr class="header-row">
                            <th>RANK</th>
                            <th>SCORE</th>
                            <th>NAME</th>
                        </tr>
                        <tr class="score-row">
                            <td class="rank-1st">1ST</td>
                            <td class="score-value">${gameScores[0].score > 0 ? gameScores[0].score.toLocaleString() : '---'}</td>
                            <td class="rank-1st">${gameScores[0].name}</td>
                        </tr>
                        <tr class="score-row">
                            <td class="rank-2nd">2ND</td>
                            <td class="score-value">${gameScores[1].score > 0 ? gameScores[1].score.toLocaleString() : '---'}</td>
                            <td class="rank-2nd">${gameScores[1].name}</td>
                        </tr>
                    </table>
                </div>
            `;
        }

        html += `<div class="last-updated">Last Updated: ${data.last_updated}</div>`;
        scoresDiv.innerHTML = html;

    } catch (error) {
        console.error('Error loading scores:', error);
        if (!document.getElementById('scores').innerHTML.includes('table')) {
            document.getElementById('scores').innerHTML = 'Error loading scores. Retrying...';
        }
    }
}

// Initial load
loadScores();

// Update countdown every second
setInterval(updateCountdown, 1000);
