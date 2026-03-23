import csv
import mysql.connector

conn = mysql.connector.connect(
    host='localhost',
    user='rootsudheesh',
    password='tracetech123',
    database='tracetech',
    auth_plugin='mysql_native_password'
)
cursor = conn.cursor()

with open('C:/Users/Admin/Downloads/academic_calendar.csv', 'r') as f:
    reader = csv.DictReader(f)
    count = 0
    for row in reader:
        cursor.execute("""
            INSERT IGNORE INTO academic_calendar 
            (calendar_date, event_type, event_name, is_college_open)
            VALUES (%s, %s, %s, %s)
        """, (
            row['date'],
            row['event_type'],
            row['event_name'],
            1 if row['is_college_open'].strip().lower() == 'true' else 0
        ))
        count += 1

conn.commit()
print(f"Inserted {count} rows")
cursor.close()
conn.close()
print("Done!")