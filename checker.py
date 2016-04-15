file1 = "dd_drupal.csv"
file2 = "drupal_data.csv"
totalMap = []
myMap = []
for line in open(file2).readlines():
	arr = line.split(",")
	myMap.append(arr[0])

uniqueTickets = []
for line in open(file1).readlines():
	arr = line.split(",")
	totalMap.append(arr[1])	


print(myMap)
print(totalMap)
for ticket in myMap:
	if ticket not in totalMap:
		print(ticket)
