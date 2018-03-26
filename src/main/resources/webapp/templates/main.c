#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

typedef struct {
  char rank, suit;
} Card;

typedef struct {
  Card cards[4];
  int startingPlayer;
  char suit;
} Trick;

typedef struct {
  int currentPlayer;
  int nCardsInHand;
  Card* cardsInHand;
  int trumpPlayer;
  Card trumpCard;
  Trick currentTrick;
  int nPreviousTricks;
  Trick* previousTricks;
  int points[2];
} Input;

int empty(Card* c) {
  return c->rank == 'X' || c->suit == 'X';
}

void readInput(Input* in) {
  scanf("%d", &in->currentPlayer);
  scanf("%d", &in->nCardsInHand);
  in->cardsInHand = (Card*) malloc(in->nCardsInHand * sizeof(Card));
  for (int i = 0; i < in->nCardsInHand; ++i) {
    Card* c = &in->cardsInHand[i];
    scanf(" %c%c", &c->rank, &c->suit);
  }
  scanf("%d", &in->trumpPlayer);
  scanf(" %c%c", &in->trumpCard.rank, &in->trumpCard.suit);
  scanf("%d", &in->currentTrick.startingPlayer);
  for (int i = 0; i < 4; ++i) {
    scanf(" %c", &in->currentTrick.cards[i].rank);
    if (in->currentTrick.cards[i].rank != 'X')
      scanf(" %c", &in->currentTrick.cards[i].suit);
    else
      in->currentTrick.cards[i].suit = 'X';
  }
  scanf(" %c", &in->currentTrick.suit);
  scanf("%d", &in->nPreviousTricks);
  in->previousTricks = (Trick*) malloc(in->nPreviousTricks * sizeof(Trick));
  for (int i = 0; i < in->nPreviousTricks; ++i) {
    Trick* t = &in->previousTricks[i];
    scanf("%d", &t->startingPlayer);
    for (int j = 0; j < 4; ++j)
      scanf(" %c%c", &t->cards[j].rank, &t->cards[j].suit);
    t->suit = t->cards[t->startingPlayer].suit;
  }
  scanf("%d %d", &in->points[0], &in->points[1]);
}

Card play(Input* in) {
  // introduz o teu codigo aqui
}

int main() {
  Input in; readInput(&in);
  Card c = play(&in);
  printf("%c%c\n", c.rank, c.suit);
  return 0;
}
