/* author: ch@murgatroid.com */

#ifndef _SERIAL_READ_STRING_H_
#define _SERIAL_READ_STRING_H_

#include <stddef.h>

class SerialReadString {
public:
    /* Reads a CR-terminated string into buf which is (at least) bufsize.

       Returns the number of characeters in the string when a CR is
       found before we exceed the size of the buffer.  The CR is eaten.

       Returns -2 if Serial.read() ever returns -2 (RX FIFO overflow).

       Returns -1 if the buffer overflows.

       Buffer is left null-terminated in every case.
    */

    /* return type can't be ssize_t as that lives in unistd.h */
    static int read_cr_terminated(char *buf, size_t bufsiz, unsigned long timeout_ms);

private:
    SerialReadString();
    static int read_stop(unsigned long stop);
};

#endif
