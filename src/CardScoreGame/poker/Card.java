package CardScoreGame.poker;

public record Card(String rank, Suit suit) {
    public Card {
        if (rank == null || rank.isBlank() || suit == null) {
            throw new IllegalArgumentException("Card needs a rank and a suit");
        }
    }

    /** Suit is the last character, rank is everything before it ("10H" → "10", H). */
    public static Card parse(String text, RankOrder rankOrder) {
        if (text == null || text.length() < 2) {
            throw new IllegalArgumentException("Invalid card: " + text);
        }
        String rank = text.substring(0, text.length() - 1);
        if (!rankOrder.isValid(rank)) {
            throw new IllegalArgumentException("Unknown rank '" + rank + "' in card: " + text);
        }
        return new Card(rank, Suit.fromSymbol(text.charAt(text.length() - 1)));
    }
}
