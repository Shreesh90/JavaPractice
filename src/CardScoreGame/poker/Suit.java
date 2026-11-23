package CardScoreGame.poker;

public enum Suit {
    H, D, S, C;

    public static Suit fromSymbol(char symbol) {
        for (Suit suit : values()) {
            if (suit.name().charAt(0) == symbol) {
                return suit;
            }
        }
        throw new IllegalArgumentException("Unknown suit '" + symbol + "'");
    }
}
