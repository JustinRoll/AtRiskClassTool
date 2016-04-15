import sys

def countChar(line):
	count = 0
	for c in line:
		if c == "?":
			count += 1

	return count 

def testLine(line, header):
	sonars = ["violations", "complexity", "ncloc"]
 	if countChar(line) >= 1:
		qs = []
		for i in range(0, len(lineArr)):
			if "?" in lineArr[i] and  header[i] not in sonars:
				print(header[i]) 

f = open(sys.argv[1])

line = f.readline()
header = line.split("\t")
line = f.readline()
one = 0
two = 0
three = 0
four_more = 0
good = 0

while line:
	lineArr = line.split("\t")
	if countChar(line) == 0:
		good += 1
	elif countChar(line) == 1:
		one += 1
	elif countChar(line) == 2:
		two += 1
	elif countChar(line) == 3:
		three += 1
	else:
		four_more += 1

#	testLine(line, header)			

	line = f.readline()

print("----------")
print("Good:%d One:%d Two:%d Three %d four_more: %d" %(good, one, two, three, four_more))

