let fs = require('fs');
let _ = require('lodash');

/**
 * This will return an object in the following format (sample input):
 *
 * {
 *   currentPlayer: 1,
 *   cardsInHand: ["2D", "3H", "4H", "5H", "6H", "7H", "7S", "KS", "QC"],
 *   trumpPlayer: 1,
 *   trumpCard: "2D",
 *   currentTrick: {
 *     startingPlayer: 1,
 *     cards: ["X", "X", "X", "X"],
 *     suit: "X"
 *   },
 *   previousTricks: [{ startingPlayer: 3, cards: ["KC", "AC", "2C", "QC"], suit: "C" }],
 *   points: [0, 17]
 * }
 */
let readInput = () => {
  let inputLines = fs.readFileSync('/dev/stdin').toString().split("\n");
  let input = {};
  input.currentPlayer = parseInt(inputLines[0]);
  input.cardsInHand = _.chain(inputLines[1].split(" ")).drop(1).value();
  input.trumpPlayer = parseInt(inputLines[2]);
  input.trumpCard = inputLines[3];
  input.currentTrick = {};
  let trick = inputLines[4].split(" ");
  input.currentTrick.startingPlayer = parseInt(trick[0]);
  input.currentTrick.cards = _.drop(trick, 1);
  input.currentTrick.suit = inputLines[5];
  input.previousTricks = _.chain(inputLines[6].split(" ")).drop(1).chunk(5).map(arr => {
    let trick = {};
    trick.startingPlayer = parseInt(arr[0]);
    trick.cards = _.drop(arr, 1);
    trick.suit = trick.cards[trick.startingPlayer][1];
    return trick;
  }).value();
  input.points = _.chain(inputLines[7].split(" ")).map(x => parseInt(x)).value();
  return input;
};

let play = input => {
  // introduz o teu codigo aqui
};

console.log(play(readInput()));
