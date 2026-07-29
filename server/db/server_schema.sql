CREATE TABLE IF NOT EXISTS rooms (
    id TEXT PRIMARY KEY,
    room_name TEXT NOT NULL UNIQUE,
    password TEXT NOT NULL,
    status TEXT NOT NULL,
    host_player_id TEXT NOT NULL,
    guest_player_id TEXT,
    max_players INTEGER NOT NULL DEFAULT 2,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS players (
    id TEXT PRIMARY KEY,
    room_id TEXT NOT NULL,
    name TEXT NOT NULL,
    ws_token TEXT NOT NULL,
    connected INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    FOREIGN KEY (room_id) REFERENCES rooms(id)
);

CREATE TABLE IF NOT EXISTS matches (
    id TEXT PRIMARY KEY,
    room_id TEXT NOT NULL,
    status TEXT NOT NULL,
    started_at TEXT,
    finished_at TEXT,
    winner_player_id TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    FOREIGN KEY (room_id) REFERENCES rooms(id),
    FOREIGN KEY (winner_player_id) REFERENCES players(id)
);
