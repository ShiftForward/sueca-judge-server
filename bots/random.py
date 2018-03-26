import sys
import random


class Card:
    def __init__(self, card):
        if card[0] != 'X':
            self.rank = card[0]
            self.suit = card[1]
        else:
            self.rank = ''
            self.suit = 'X'


class Hand:
    def __init__(self, cards_line):
        cards_list = cards_line.split(' ')[1:]
        self.cards = [Card(card) for card in cards_list]


class Trick:
    def __init__(self, trick_data):
        if not isinstance(trick_data, list):
            trick_data = trick_data.split(' ')
        self.cards = [Card(card) for card in trick_data[1:]]
        self.starting_player = int(trick_data[0])
        self.suit = self.cards[self.starting_player].suit


class Input:
    def __init__(self, input_data):
        self.player_number = int(input_data[0])
        self.hand = Hand(input_data[1])
        self.trump_player = int(input_data[2])
        self.trump_card = Card(input_data[3])
        self.current_trick = Trick(input_data[4])
        self.current_suit = input_data[5].strip()

        previous_tricks_data = input_data[6].split(' ')
        tricks_count = int(previous_tricks_data[0])
        self.previous_tricks = []
        for i in range(tricks_count):
            start = i*5+1
            trick = Trick(previous_tricks_data[start:start+5])
            self.previous_tricks.append(trick)

        point_data = input_data[7].split(' ')
        self.points = [int(point_data[0]), int(point_data[1])]


def play(input):
    for card in input.hand.cards:
        if card.suit == input.current_suit:
            return card
    return random.choice(input.hand.cards)


if __name__ == '__main__':
    input = Input(sys.stdin.readlines())
    card = play(input)
    print(card.rank + card.suit)
