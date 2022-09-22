--------------------------------------------
Data mining on crash reports
--------------------------------------------

Probably not useful for most people.
Using these configuration files, you can generate reports about AnkiDroid users, based on anonymous data found in the crash reports.
To do so, we use the open-source Business Intelligence suite Pentaho CE.

--------------------------------------------
Usage
--------------------------------------------

Run:
mysql -u root -p < create-database.sql
wget http://inimailbot.appspot.com/ankidroid_triage/export_bug_csv > crashes.csv

Install Pentaho PDI
Run PDI Spoon and execute etl.ktr which will load the data into your MySQL database.

Install Pentaho PRD
Run the report designer and preview the *.prt files to see the reports.
For instance, file "result.png" shows what is produced by report-about-android-versions.prpt
You can create your own reports about many things by copying and modifying this PRT file.
