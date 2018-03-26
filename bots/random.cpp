#include <cstdlib>
#include <ctime>
#include <iostream>
#include <vector>

using namespace std;

struct Card {
  char rank, suit;

  bool empty() {
    return rank == 'X' || suit == 'X';
  }
};

struct Trick {
  Card cards[4];
  int startingPlayer;
  char suit;
};

struct Input {
  int currentPlayer;
  vector<Card> cardsInHand;
  int trumpPlayer;
  Card trumpCard;
  Trick currentTrick;
  vector<Trick> previousTricks;
  int points[2];
};

void readInput(Input& in) {
  cin >> in.currentPlayer;
  unsigned int NC; cin >> NC;
  in.cardsInHand.resize(NC);
  for (Card& c : in.cardsInHand) {
    cin >> c.rank >> c.suit;
  }
  cin >> in.trumpPlayer;
  cin >> in.trumpCard.rank >> in.trumpCard.suit;
  cin >> in.currentTrick.startingPlayer;
  for (Card& card : in.currentTrick.cards) {
    cin >> card.rank;
    if (card.rank != 'X') {
      cin >> card.suit;
    } else {
      card.suit = 'X';
    }
  }
  cin >> in.currentTrick.suit;
  unsigned int P; cin >> P;
  in.previousTricks.resize(P);
  for (Trick& t : in.previousTricks) {
    cin >> t.startingPlayer;
    for (Card& card : t.cards)
      cin >> card.rank >> card.suit;
    t.suit = t.cards[t.startingPlayer].suit;
  }
  cin >> in.points[0] >> in.points[1];
}

Card play(Input& in) {
  srand(time(NULL));

  vector<Card> validCards;
  if (in.currentPlayer == in.currentTrick.startingPlayer)
    validCards = in.cardsInHand;
  else {
    for (Card& c : in.cardsInHand) {
      if (c.suit == in.currentTrick.suit)
        validCards.push_back(c);
    }
    if (validCards.empty())
      validCards = in.cardsInHand;
  }
  return validCards[rand() % validCards.size()];
}

int main() {
  Input in; readInput(in);
  Card c = play(in);
  cout << c.rank << c.suit << endl;
}
