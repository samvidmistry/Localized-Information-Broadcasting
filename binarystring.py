import sys

alphabet = "abcdefghijklmnopqrstuvwxyz "
arg_len = len(sys.argv)
if(arg_len <= 1):
    print("Please provide a message as command line argument")
    sys.exit()

if(len(sys.argv[1]) >= 30):
    print("Only upto 30 character long message is allowed")
    sys.exit()

messageIndex = -1;
silent = False
for i in range(1, len(sys.argv)):
    if sys.argv[i] == "-s":
        silent = True

message = sys.argv[arg_len - 1]
bitString = ""

for c in message:
    index = alphabet.index(c)
    binary = format(index, '05b')
    bitString += binary

for i in range(30 - len(message)):
    bitString += format(0, '05b')

size = len(message)
featureString = list("00000")

if silent:
    featureString[1] = '1'

bitStringWithSize = "".join(featureString) + format(size, '05b') + bitString
print(bitStringWithSize)
print(len(bitStringWithSize))
uuid = bitStringWithSize[0:128]
major = bitStringWithSize[128:144]
minor = bitStringWithSize[144:160]
hstr = '%0*X' % ((len(uuid) + 3) // 4, int(uuid, 2))
hmajor = '%0*X' % ((len(major) + 3) // 4, int(major, 2))
hminor = '%0*X' % ((len(minor) + 3) // 4, int(minor, 2))

print("IBE0 - " + hstr[0:8])
print("IBE1 - " + hstr[8:16])
print("IBE2 - " + hstr[16:24])
print("IBE3 - " + hstr[24:32])

#for i in range(len(hstr)):
#    if(i > 0 and (i % 8) == 0):
#        print(' ', end='')
#    print(hstr[i], end='')

print("MARJ - 0x" + hmajor + " = " + str(int(hmajor, 16)))
print("MINO - 0x" + hminor + " = " + str(int(hminor, 16)))
#print(hmajor + ' ' + hminor)
