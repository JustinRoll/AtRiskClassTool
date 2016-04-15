import sys
f = open(sys.argv[1])

line = f.readline()
headerCount = len(line.split("|"))

while line:
	lineCount = len(line.split("|"))
	if lineCount != headerCount:
		print("line and header do not match")
		print(line)
		break
	else:
		print("good line")
	line = f.readline()

f.close()
	
