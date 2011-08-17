/* author: ch@murgatroid.com */

#include "WProgram.h"
#include "SerReadStr.h"

#define time_after(a,b)	((long)(b) - (long)(a) < 0)

int SerialReadString::read_stop(unsigned long stop)
{
    while (1) {
	int cc = Serial.read();
	if (cc != -1) {
	    return cc;
	}

	unsigned long now = millis();
	if (time_after(now, stop)) {
	    return -2;
	}
    }
}

int SerialReadString::read_cr_terminated(char *buf, size_t bufsiz, unsigned long timeout_ms)
{
    unsigned long start = millis();
    unsigned long stop = start + timeout_ms;
    for (int i = 0; i < bufsiz - 1; i++) {
	int cc = read_stop(stop);
	if (cc == -2) {
	    buf[i] = 0;
	    return -2;
	} else if (cc == '\r') {
	    buf[i] = 0;
	    return i;
	} else {
	    buf[i] = cc;
	}
    }
    buf[bufsiz - 1] = 0;
    return -1;
}
