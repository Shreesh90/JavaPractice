package CardScoreGame;

import java.util.List;

public record SignatureRule(String name, List<Integer> signature, int weight) implements HandTypeRule {
    public SignatureRule {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Rule name must not be blank");
        }
        if (signature == null || signature.isEmpty()) {
            throw new IllegalArgumentException("Signature must not be empty for rule: " + name);
        }
        signature = List.copyOf(signature);
    }

    @Override
    public boolean matches(Hand hand) {
        return hand.signature().equals(signature);
    }
}
