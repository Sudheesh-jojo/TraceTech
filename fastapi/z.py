import csv

with open('C:/Users/Admin/Downloads/academic_calendar.csv', 'r') as f:
    reader = csv.DictReader(f)
    print(reader.fieldnames)
    for i, row in enumerate(reader):
        print(row)
        if i == 2:
            break