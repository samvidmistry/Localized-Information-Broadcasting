#!/usr/bin/env python

import serial
import sys

ser = serial.Serial(
                port='/dev/serial0',
                baudrate=9600,
                parity=serial.PARITY_NONE,
                stopbits=serial.STOPBITS_ONE,
                bytesize=serial.EIGHTBITS,
                timeout=1)
try:
    print "Serial is open: " + str(ser.isOpen())

    print "Now Writing"
    ser.write(sys.argv[1] + "\r\n")

    print "Did write, now read"
    while(True):
        x = ser.readline()
        print "got '" + x + "'"

    ser.close()
except serial.serialutil.SerialException:
    print "Exception"