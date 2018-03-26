#include <stdio.h>
#include <unistd.h>

int main(void) {
  fprintf(stderr, "Starting infinite loop...\n");
  while (1) {}
  printf("Time paradox!\n");
  return 0;
}
