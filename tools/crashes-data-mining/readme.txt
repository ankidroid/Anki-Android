--------------------------------------------
Data mining on crash reports
--------------------------------------------

Probably not useful for most people.
Using mostly Pentaho CE.
This is a work in progress.

--------------------------------------------
Usage
--------------------------------------------

Run:
mysql -u root -p < create-database.sql
wget http://inimailbot.appspot.com/ankidroid_triage/export_bug_csv > crashes.csv

Install Pentaho PDI
With PDI Spoon, execute etl.ktr

TODO: cube, biserver
