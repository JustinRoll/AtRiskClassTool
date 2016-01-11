import sys
import xlrd
import csv

def csv_from_excel(inputXls, outputCsv):

   wb = xlrd.open_workbook(inputXls)
   sh = wb.sheet_by_name('Sheet1')
   your_csv_file = open(outputCsv, 'wb')
   wr = csv.writer(your_csv_file)

   for rownum in xrange(sh.nrows):
       wr.writerow(sh.row_values(rownum))

   your_csv_file.close()

if len(sys.argv) < 3:
	print("Please input more file names")
	exit()

csv_from_excel(sys.argv[1], sys.argv[2])
