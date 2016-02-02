from datetime import timedelta, date, datetime
import sys
import urllib2
from os import listdir
from os.path import isfile, join
from html_to_csv import *

#should take a file directory
#a jira URL
#and a start date
def curl_html(url):
	response = urllib2.urlopen(url)
	html = response.read()
	response.close()  # best practice to close the file
	return html

base_string_old = "https://issues.apache.org/jira/sr/jira.issueviews:searchrequest-excel-all-fields/temp/SearchRequest.xls?jqlQuery=project+%3D+QPID+AND+created+%3E%3D+2015-10-20+AND+created+%3C%3D+2015-10-27&tempMax=200"


base_string = "https://issues.apache.org/jira/sr/jira.issueviews:searchrequest-excel-all-fields/temp/SearchRequest.xls?jqlQuery=project+%3D+QPID+AND+created+%3E%3D+|START_DATE_CODE|+AND+created+%3C%3D+|END_DATE_CODE|&tempMax=200"

base_string_new = "https://issues.apache.org/jira/sr/jira.issueviews:searchrequest-excel-all-fields/temp/SearchRequest.xls?jqlQuery=project+%3D+QPID"

def format_jira(base_string, start_date, end_date):
	new_string = "{0}+AND+created+%3E%3D+{1}+AND+created+%3C%3D+{2}&tempMax=200".format(base_string, start_date, end_date)
	print(new_string)
	return new_string
def parse_date(date_string):
	nums = [int(num) for num in date_string.split("-")]
	return date(nums[0], nums[1], nums[2])


def print_args():
	for i in range(0, len(sys.argv)):
		print("%d:%s" % (i, sys.argv[i]))
#open output file in create/trunk mode
#grab data
#call html_to_csv on the file
"""
def scrape_url(tup):
	url = tup[0]
	week_file = tup[1]
	master_out = tup[2] 
    f = open(week_file, "wr")
		f.write(curl_html(url_string))
		f.close()
		include_header = False
		if d == start_date:
			include_header = True
		
		read_html(week_out_file, out_file_name, 10, header=include_header) 
"""
def grab_jira_data(jira_url, out_file_name, path,  start_date):
	start_date = parse_date(start_date)
	end_date_time = datetime.now()
	end_date = date(end_date_time.year, end_date_time.month, end_date_time.day)
	d = start_date
	delta = timedelta(days=7)
	FMT = "%Y-%m-%d"
	open(out_file_name, "wr").close()
	while d <= end_date:
		jira_start = d.strftime(FMT)
		jira_end = (d + timedelta(days=6)).strftime(FMT)
		print(jira_end)
		#url_string = base_string.replace("|START_DATE_CODE|", jira_start).replace("|END_DATE_CODE|", jira_end)
		url_string = format_jira(jira_url, jira_start, jira_end)
		week_out_file = path + jira_start + "_" + jira_end + ".html"
		f = open(week_out_file, "wr")
		f.write(curl_html(url_string))
		f.close()
		include_header = False
		if d == start_date:
			include_header = True
		
		read_html(week_out_file, out_file_name, 10, header=include_header)
		         
		d += delta


def parse_files(mypath, filename):
	onlyfiles = [ join(mypath, f) for f in listdir(mypath) if isfile(join(mypath,f)) and "html" in f]
	file_count = 0
	master_file = open(join(mypath, "master.htm"), "wr")
	for filename in onlyfiles:
		f = open(filename, "r+")
		text = f.read().split("\n")
		text = text[:71] + text[88:]

		count = 0
	
		for line in text:
			if "rowHeader" in line:
				print(line)
			if file_count != 0 and "rowHeader" in line:
				header = count
				 
			if "end-of-stable-message" in line:
				print("end found" + str(count))
				break
			count+=1
		text = text[:count] + text[count + 6:-7]
		if file_count != 0:
			text = text[:header] + text[header + 334:]
		master_file.write("\n".join(text))
		file_count += 1
	master_file.write("</tr> \
                </tbody> \
    			</table> \
				</tr>    \
				</table>  \
                           \
				</body>   \
				</html>") 		
def main():
	if len(sys.argv) < 5:
		print("Please enter a storage directory, a jira URL, and a start date")
	print_args()
	outfile = sys.argv[1]
	path = sys.argv[2]
	jira_url = sys.argv[3]
	start_date = sys.argv[4]
	grab_jira_data(jira_url, outfile, path, start_date)

#ex python jira_data.py master_out.csv test/ https://issues.apache.org/jira/sr/jira.issueviews:searchrequest-excel-all-fields/temp/SearchRequest.xls?jqlQuery=project+%3D+QPID+AND+created+%3E%3D+|START_DATE_CODE|+AND+created+%3C%3D+|END_DATE_CODE|&tempMax=200 2015-10-21
main()
