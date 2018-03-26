#include <stdio.h>
#include <unistd.h>

int main(void) {
  char buff[255];
  read(STDIN_FILENO, buff, 255);
  printf("Hello World (C): %s\n", buff);
  return 0;
}
