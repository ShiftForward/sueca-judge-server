import java.util.*;

class Main {
    static class Card {
        char rank, suit;

        public Card(String s) {
            if (s.equals("X"))
                rank = suit = 'X';
            else {
                rank = s.charAt(0);
                suit = s.charAt(1);
            }
        }

        boolean empty() {
            return rank == 'X' || suit == 'X';
        }
    }

    static class Trick {
        Card cards[];
        int startingPlayer;
        char suit;

        public Trick() {
            cards = new Card[4];
        }
    }

    static class Input {
        int currentPlayer;
        List<Card> cardsInHand;
        int trumpPlayer;
        Card trumpCard;
        Trick currentTrick;
        List<Trick> previousTricks;
        int points[];

        public Input() {
            points = new int[2];
            cardsInHand = new ArrayList<>();
            previousTricks = new ArrayList<>();
            currentTrick = new Trick();
        }
    }

    public static Input readInput() {
        Scanner in = new Scanner(System.in);
        Input input = new Input();
        input.currentPlayer = in.nextInt();
        int NC = in.nextInt();
        for (int i = 0; i < NC; i++)
            input.cardsInHand.add(new Card(in.next()));
        input.trumpPlayer = in.nextInt();
        input.trumpCard = new Card(in.next());
        input.currentTrick.startingPlayer = in.nextInt();
        for (int i = 0; i < 4; i++)
            input.currentTrick.cards[i] = new Card(in.next());
        input.currentTrick.suit = in.next().charAt(0);
        int PT = in.nextInt();
        for (int i = 0; i < PT; i++) {
            Trick t = new Trick();
            t.startingPlayer = in.nextInt();
            for (int j = 0; j < 4; j++)
                t.cards[j] = new Card(in.next());
            t.suit = t.cards[t.startingPlayer].suit;
            input.previousTricks.add(t);
        }
        input.points[0] = in.nextInt();
        input.points[1] = in.nextInt();
        return input;
    }

    public static Card play(Input in) {
        // introduz o teu codigo aqui
    }

    public static void main(String[] args) {
        Input in = readInput();
        Card c = play(in);
        System.out.println("" + c.rank + c.suit);
    }
}
