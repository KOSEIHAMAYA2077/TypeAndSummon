package protocol;

public enum ServerMessageType {
    AUTH_OK,
    ROOM_STATE,
    START,
    WORD,
    OPPONENT_INPUT,
    ANSWER_RESULT,
    STATE_UPDATE,
    BATTLE_LOG,
    LEVEL_INFO,
    FINISH,
    ERROR,
    UNKNOWN
}
