import csv
import mysql.connector
from datetime import datetime

conn = mysql.connector.connect(
    host='localhost',
    user='rootsudheesh',
    password='tracetech123',
    database='tracetech',
    auth_plugin='mysql_native_password'
)
cursor = conn.cursor()

cursor.execute("DELETE FROM sales_actuals")
conn.commit()
print("Cleared existing sales data")

with open('C:/Users/Admin/Downloads/canteen_sales.csv', 'r') as f:
    reader = csv.DictReader(f)
    count = 0
    for row in reader:
        try:
            cursor.execute("""
                INSERT INTO sales_actuals 
                (item_id, sale_date, qty_sold, qty_prepared, qty_wasted, revenue, waste_cost, submitted_at)
                VALUES (%s, %s, %s, %s, %s, %s, %s, %s)
            """, (
                int(row['item_id']),
                row['date'],
                int(row['qty_sold']),
                int(row['qty_prepared']),
                int(row['qty_wasted']),
                float(row['total_revenue']),
                float(row['total_waste_cost']),
                datetime.now()
            ))
            count += 1
            if count % 1000 == 0:
                print(f"Inserted {count} rows...")
                conn.commit()
        except Exception as e:
            print(f"Error on row {count}: {e}")
            continue

conn.commit()
print(f"Done! Total inserted: {count} rows")
cursor.close()
conn.close()