from datetime import timedelta, date
import urllib2
from os import listdir
from os.path import isfile, join

def curl_html(url):
	response = urllib2.urlopen(url)
	html = response.read()
# do something
	response.close()  # best practice to close the file
	return html

base_string_old = "https://issues.apache.org/jira/sr/jira.issueviews:searchrequest-excel-all-fields/temp/SearchRequest.xls?jqlQuery=project+%3D+QPID+AND+created+%3E%3D+2015-10-20+AND+created+%3C%3D+2015-10-27&tempMax=200"


base_string = "https://issues.apache.org/jira/sr/jira.issueviews:searchrequest-excel-all-fields/temp/SearchRequest.xls?jqlQuery=project+%3D+QPID+AND+created+%3E%3D+|START_DATE_CODE|+AND+created+%3C%3D+|END_DATE_CODE|&tempMax=200"

def grab_jira_data(base_string):
	start_date = date(2013, 10, 1)
	end_date = date(2015, 10, 21)
	d = start_date
	delta = timedelta(days=7)
	FMT = "%Y-%m-%d"
	while d <= end_date:
		jira_start = d.strftime(FMT)
		jira_end = (d + timedelta(days=6)).strftime(FMT)
		print(jira_end)
		url_string = base_string.replace("|START_DATE_CODE|", jira_start).replace("|END_DATE_CODE|", jira_end)
		f = open(jira_start + "_" + jira_end + ".html", "wr")
		f.write(curl_html(url_string))
		d += delta

def parse_files(mypath):
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

grab_jira_data(base_string)
parse_files("/Users/jroll/IdeaProjects/Thesis/src")
	


