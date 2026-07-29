package server.protocol;

public enum MessageType {
    AUTH,
    AUTH_OK,
    GET_ROOM,
    ROOM_STATE,
    SELECT_LEVEL,
    TYPING_UPDATE,
    START,
    WORD,
    OPPONENT_INPUT,
    ANSWER_RESULT,
    STATE_UPDATE,
    BATTLE_LOG,
    LEVEL_INFO,
    FINISH,
    ERROR
}
