#include <iostream>
#include <string>

using namespace std;

int player, handSize;
string cards[10];

int main() {
  cin >> player >> handSize;
  for(int i = 0; i < handSize; i++)
    cin >> cards[i];

  // returns the first card; always valid if this is the first card in a trick
  cout << cards[0] << endl;
  return 0;
}
