import sys
fname = sys.argv[1]


outfile = open("digitaldemocracy_bigtable.txt", "wr")
lines = open(fname).readlines()
current_ticket = lines[1]
touched = 0
outfile.write(lines[0])
tickets = []

for line in lines:
	if line == lines[0]:
		continue

	arr = line.split("\t")
	ticket = arr[0]
	if arr[-1].strip() == "yes":
		touched += 1
	if ticket != current_ticket:
		if touched == 0:
			print(ticket)
			tickets.append(ticket)
		touched = 0
		current_ticket = ticket

for line in lines:
	if line == lines[0]:
		continue
	arr = line.split("\t")
	ticket = arr[0]
	if ticket not in tickets:
		outfile.write(line)
