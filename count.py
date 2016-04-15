import sys
fname = sys.argv[1]

lines = open(fname).readlines()
prev_ticket = None
touched = 0
tickets = []
total = set([])

for line in lines:
	if line == lines[0]:
		print("header skipped")
		continue
	#skip header

	arr = line.split("\t")
	ticket = arr[0]
	total.add(ticket)
 	if prev_ticket != None and ticket != prev_ticket:
		if touched == 0:
			print(prev_ticket)
			tickets.append(prev_ticket)
		touched = 0
		prev_ticket = ticket 

	if arr[-1].strip() == "yes":
		touched += 1



print len(tickets)
print(tickets)
print len(total)
"""for line in lines:
	if line == lines[0]:
		continue
	arr = line.split("\t")
	ticket = arr[0]
	if ticket not in tickets:
		outfile.write(line)""" 
