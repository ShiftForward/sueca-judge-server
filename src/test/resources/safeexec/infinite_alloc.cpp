#include <vector>

using namespace std;

vector<long long> someInts;

int main() {
  long long x = 0, sum = 0;
  while(++x) {
    someInts.push_back(x);
    sum += someInts[x];
  }
  return 0;
}
